<#
.SYNOPSIS
  Remove todos os comentarios escritos por um autor (padrao: Yagami) dos arquivos Java do repositorio.

.DESCRIPTION
  - Usa "git blame" para saber quais linhas sao suas. Comentarios do codigo original
    (Hydraheads e outros colaboradores) NAO sao tocados.
  - So mexe em comentarios (// ... e /* ... */). Strings e caracteres sao respeitados.
  - Antes de gravar cada arquivo, compara o codigo antes/depois (sem comentarios e sem
    espacos). Se houver qualquer diferenca, o arquivo NAO e alterado.
  - Preserva quebras de linha (CRLF/LF) e BOM de cada arquivo.

.PARAMETER RepoPath
  Pasta do repositorio (padrao: pasta atual).

.PARAMETER Author
  Nome do autor no Git cujos comentarios serao removidos (padrao: Yagami).

.PARAMETER DryRun
  Apenas mostra o que seria removido, sem alterar nenhum arquivo.

.PARAMETER IncludeUncommitted
  Tambem remove comentarios de linhas ainda nao commitadas (git blame: "Not Committed Yet").
  Por padrao so conta como seu o que ja esta commitado com o seu nome, para nunca tocar
  em codigo de outras pessoas por engano (ex.: arquivo inteiro com quebra de linha trocada).

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File .\remover-comentarios-sticky-fingers.ps1 -DryRun
  powershell -ExecutionPolicy Bypass -File .\remover-comentarios-sticky-fingers.ps1
#>
[CmdletBinding()]
param(
    [string]$RepoPath = (Get-Location).Path,
    [string]$Author = 'Yagami',
    [switch]$DryRun,
    [switch]$IncludeUncommitted
)

$ErrorActionPreference = 'Stop'

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    throw 'git nao encontrado no PATH. Instale o Git for Windows e tente de novo.'
}

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

$csharp = @'
using System;
using System.Collections.Generic;
using System.Text;

public static class CommentStripper
{
    // Retorna os intervalos [inicio, fim) de todos os comentarios, ignorando strings e chars.
    public static List<int[]> Spans(string s)
    {
        List<int[]> spans = new List<int[]>();
        int i = 0, n = s.Length;
        while (i < n)
        {
            char c = s[i];
            if (c == '/' && i + 1 < n && s[i + 1] == '/')
            {
                int j = s.IndexOf('\n', i);
                if (j < 0) j = n;
                spans.Add(new int[] { i, j });
                i = j;
            }
            else if (c == '/' && i + 1 < n && s[i + 1] == '*')
            {
                int j = s.IndexOf("*/", i + 2, StringComparison.Ordinal);
                j = (j < 0) ? n : j + 2;
                spans.Add(new int[] { i, j });
                i = j;
            }
            else if (c == '"' && i + 2 < n && s[i + 1] == '"' && s[i + 2] == '"')
            {
                int j = s.IndexOf("\"\"\"", i + 3, StringComparison.Ordinal);
                i = (j < 0) ? n : j + 3;
            }
            else if (c == '"' || c == '\'')
            {
                int j = i + 1;
                while (j < n && s[j] != c)
                {
                    j += (s[j] == '\\') ? 2 : 1;
                }
                i = j + 1;
            }
            else
            {
                i++;
            }
        }
        return spans;
    }

    // Codigo sem comentarios e sem espacos em branco (usado so para conferir que nada mudou).
    public static string CodeOnly(string s)
    {
        List<int[]> spans = Spans(s);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        foreach (int[] sp in spans)
        {
            Append(sb, s, last, sp[0]);
            last = sp[1];
        }
        Append(sb, s, last, s.Length);
        return sb.ToString();
    }

    private static void Append(StringBuilder sb, string s, int a, int b)
    {
        for (int k = a; k < b; k++)
        {
            if (!char.IsWhiteSpace(s[k])) sb.Append(s[k]);
        }
    }

    // Remove os comentarios cujas linhas sao TODAS do autor (ownLine[i] == true).
    public static string Clean(string src, bool[] ownLine, out int removed)
    {
        removed = 0;
        List<int[]> spans = Spans(src);

        List<int> starts = new List<int>();
        starts.Add(0);
        for (int k = 0; k < src.Length; k++)
        {
            if (src[k] == '\n') starts.Add(k + 1);
        }

        bool[] mask = new bool[src.Length];
        foreach (int[] sp in spans)
        {
            int l1 = LineOf(starts, sp[0]);
            int l2 = LineOf(starts, Math.Max(sp[0], sp[1] - 1));
            bool all = true;
            for (int l = l1; l <= l2; l++)
            {
                if (l >= ownLine.Length || !ownLine[l]) { all = false; break; }
            }
            if (all)
            {
                for (int k = sp[0]; k < sp[1]; k++) mask[k] = true;
                removed++;
            }
        }
        if (removed == 0) return src;

        string[] lines = src.Split('\n');
        List<string> res = new List<string>();
        int pos = 0;
        bool dropped = false;
        foreach (string ln in lines)
        {
            StringBuilder sb = new StringBuilder();
            bool touched = false;
            for (int k = 0; k < ln.Length; k++)
            {
                if (mask[pos + k]) touched = true;
                else sb.Append(ln[k]);
            }
            pos += ln.Length + 1;
            string seg = sb.ToString();

            // linha que era so comentario: apaga a linha inteira
            if (touched && seg.Trim().Length == 0)
            {
                dropped = true;
                continue;
            }
            // comentario no fim de uma linha de codigo: tira so o comentario e os espacos sobrando
            if (touched)
            {
                seg = seg.TrimEnd(' ', '\t', '\r') + (ln.EndsWith("\r") ? "\r" : "");
            }
            bool blank = seg.Trim().Length == 0;
            // evita deixar linhas em branco duplicadas por causa dos comentarios removidos
            if (blank && dropped && res.Count > 0 && res[res.Count - 1].Trim().Length == 0) continue;
            if (blank && dropped && res.Count > 0 && res[res.Count - 1].TrimEnd('\r').EndsWith("{")) continue;
            res.Add(seg);
            dropped = false;
        }
        return string.Join("\n", res.ToArray());
    }

    private static int LineOf(List<int> starts, int off)
    {
        int lo = 0, hi = starts.Count - 1;
        while (lo < hi)
        {
            int mid = (lo + hi + 1) / 2;
            if (starts[mid] <= off) lo = mid; else hi = mid - 1;
        }
        return lo;
    }
}
'@

if (-not ([System.Management.Automation.PSTypeName]'CommentStripper').Type) {
    Add-Type -TypeDefinition $csharp -Language CSharp
}

# --- repositorio ---------------------------------------------------------------------------
$root = (& git -C $RepoPath rev-parse --show-toplevel 2>$null)
if ($LASTEXITCODE -ne 0 -or -not $root) {
    throw "'$RepoPath' nao e um repositorio Git. Rode o script dentro da pasta do RoundaboutMod."
}
$root = $root.Trim()
Write-Host "Repositorio : $root"
Write-Host "Autor       : $Author"
if ($DryRun) { Write-Host 'Modo        : DRY-RUN (nenhum arquivo sera alterado)' -ForegroundColor Yellow }

$dirty = @(& git -C $root status --porcelain)
if ($dirty.Count -gt 0 -and -not $DryRun) {
    Write-Host "Aviso: voce tem $($dirty.Count) alteracao(oes) nao commitada(s). Considere fazer commit antes," -ForegroundColor Yellow
    Write-Host '       assim da para desfazer tudo com: git checkout -- .' -ForegroundColor Yellow
}

# --- arquivos .java que o autor mexeu --------------------------------------------------------
$names = & git -C $root log "--author=$Author" --name-only --format= -- '*.java'
$files = $names | Where-Object { $_ -and $_.Trim() -ne '' } | Sort-Object -Unique |
         Where-Object { Test-Path -LiteralPath (Join-Path $root $_) }

if (-not $files) {
    Write-Host "Nenhum arquivo .java encontrado para o autor '$Author'. Confira o nome com: git log --format=%an | sort -u" -ForegroundColor Red
    return
}

$utf8 = New-Object System.Text.UTF8Encoding($false)
$totalRemoved = 0
$changedFiles = 0

foreach ($rel in $files) {
    $full = Join-Path $root $rel
    $name = Split-Path $rel -Leaf

    # quem escreveu cada linha
    $blame = & git -C $root blame --line-porcelain -- $rel 2>$null
    if ($LASTEXITCODE -ne 0) { Write-Host "  [pulado] $name (git blame falhou)" -ForegroundColor Yellow; continue }
    $authors = New-Object System.Collections.Generic.List[string]
    $cur = $null
    foreach ($l in $blame) {
        if ($l.StartsWith('author ')) { $cur = $l.Substring(7) }
        elseif ($l.Length -gt 0 -and $l[0] -eq "`t") { [void]$authors.Add($cur) }
    }

    # le o arquivo preservando BOM
    $bytes = [System.IO.File]::ReadAllBytes($full)
    $hasBom = ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF)
    $off = 0
    if ($hasBom) { $off = 3 }
    $text = $utf8.GetString($bytes, $off, $bytes.Length - $off)

    $lineCount = $text.Split("`n").Length
    if ($text.EndsWith("`n")) { $lineCount-- }
    if ($lineCount -ne $authors.Count) {
        Write-Host "  [pulado] $name (blame nao bate com o arquivo: $($authors.Count) x $lineCount linhas)" -ForegroundColor Yellow
        continue
    }

    $own = New-Object bool[] $authors.Count
    for ($i = 0; $i -lt $authors.Count; $i++) {
        $own[$i] = ($authors[$i] -eq $Author)
        if ($IncludeUncommitted -and $authors[$i] -eq 'Not Committed Yet') { $own[$i] = $true }
    }

    $removed = 0
    $newText = [CommentStripper]::Clean($text, $own, [ref]$removed)
    if ($removed -eq 0) { continue }

    # seguranca: o codigo (sem comentarios/espacos) tem que ser identico
    if ([CommentStripper]::CodeOnly($text) -ne [CommentStripper]::CodeOnly($newText)) {
        Write-Host "  [pulado] $name (verificacao falhou, arquivo NAO alterado)" -ForegroundColor Red
        continue
    }

    if (-not $DryRun) {
        $out = $utf8.GetBytes($newText)
        if ($hasBom) { $out = [byte[]](@(0xEF, 0xBB, 0xBF) + $out) }
        [System.IO.File]::WriteAllBytes($full, $out)
    }
    $totalRemoved += $removed
    $changedFiles++
    Write-Host ('  {0,4} comentarios  {1}' -f $removed, $name)
}

Write-Host ''
if ($DryRun) {
    Write-Host "DRY-RUN: $totalRemoved comentarios seriam removidos em $changedFiles arquivos." -ForegroundColor Cyan
} else {
    Write-Host "Pronto: $totalRemoved comentarios removidos em $changedFiles arquivos." -ForegroundColor Green
    Write-Host 'Para conferir: git diff --stat   |   Para desfazer (se nao commitou): git checkout -- .'
}
