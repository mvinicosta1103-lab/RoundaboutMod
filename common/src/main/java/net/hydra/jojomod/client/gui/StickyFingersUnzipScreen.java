package net.hydra.jojomod.client.gui;

import net.hydra.jojomod.stand.powers.PowersStickyFingers;
import net.hydra.jojomod.util.C2SPacketUtil;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Direct limb selection for Sticky Fingers. Each selection is sent as one server-validated unzip command. */
public final class StickyFingersUnzipScreen extends Screen implements NoCancelInputScreen {
    private static final int[] PARTS = {
            PowersStickyFingers.ZIPPER_PART_HEAD,
            PowersStickyFingers.ZIPPER_PART_LEFT_ARM,
            PowersStickyFingers.ZIPPER_PART_RIGHT_ARM,
            PowersStickyFingers.ZIPPER_PART_LEFT_LEG,
            PowersStickyFingers.ZIPPER_PART_RIGHT_LEG
    };
    private static final String[] LABELS = {
            "gui.roundabout.sticky_fingers_unzip.head",
            "gui.roundabout.sticky_fingers_unzip.left_arm",
            "gui.roundabout.sticky_fingers_unzip.right_arm",
            "gui.roundabout.sticky_fingers_unzip.left_leg",
            "gui.roundabout.sticky_fingers_unzip.right_leg"
    };

    public StickyFingersUnzipScreen() { super(GameNarrator.NO_TITLE); }

    @Override protected void init() { }
    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xB9000713);
        int panelW = 250, panelH = 172, x = (width - panelW) / 2, y = (height - panelH) / 2;
        // Custom Sticky Fingers panel: dark blue plate, cyan inner edge, and gold zipper teeth.
        g.fill(x - 3, y - 3, x + panelW + 3, y + panelH + 3, 0xFF07101D);
        g.fill(x, y, x + panelW, y + panelH, 0xFF123A70);
        g.fill(x + 3, y + 3, x + panelW - 3, y + panelH - 3, 0xFF08182C);
        g.fill(x + 8, y + 8, x + panelW - 8, y + 33, 0xFF1458A0);
        g.fill(x + 8, y + 32, x + panelW - 8, y + 34, 0xFFFFC83D);
        // A central gold zipper rail frames the title, with teeth like the stand's zipper motif.
        g.fill(x + 18, y + 42, x + 22, y + 136, 0xFFB88420);
        for (int toothY = y + 44; toothY < y + 136; toothY += 8) {
            g.fill(x + 13, toothY, x + 22, toothY + 3, 0xFFFFD65A);
            g.fill(x + 22, toothY + 4, x + 29, toothY + 7, 0xFFFFD65A);
        }
        g.drawCenteredString(font, Component.translatable("gui.roundabout.sticky_fingers_unzip.title"), width / 2, y + 13, 0xFFFFFFFF);
        g.drawCenteredString(font, Component.translatable("gui.roundabout.sticky_fingers_unzip.subtitle"), width / 2, y + 37, 0xFF8BE8FF);

        int[][] positions = {{0, 0}, {-1, 1}, {1, 1}, {-1, 2}, {1, 2}};
        int centerX = width / 2, baseY = y + 52;
        for (int i = 0; i < PARTS.length; i++) {
            int bx = centerX + positions[i][0] * 84 - 38;
            int by = baseY + positions[i][1] * 32;
            boolean hovered = mouseX >= bx && mouseX < bx + 76 && mouseY >= by && mouseY < by + 27;
            int fill = hovered ? 0xFF246EAA : 0xFF102C4D;
            g.fill(bx, by, bx + 76, by + 27, 0xFFFFC83D);
            g.fill(bx + 2, by + 2, bx + 74, by + 25, fill);
            g.fill(bx + 7, by + 5, bx + 10, by + 22, 0xFFFFD65A);
            g.drawCenteredString(font, Component.translatable(LABELS[i]), bx + 42, by + 9, 0xFFFFFFFF);
        }
        g.drawCenteredString(font, Component.translatable("gui.roundabout.sticky_fingers_unzip.hint"), width / 2,
                y + panelH - 14, 0xFFB7C8D8);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int panelH = 172, y = (height - panelH) / 2, baseY = y + 52;
        int[][] positions = {{0, 0}, {-1, 1}, {1, 1}, {-1, 2}, {1, 2}};
        for (int i = 0; i < PARTS.length; i++) {
            int bx = width / 2 + positions[i][0] * 84 - 38;
            int by = baseY + positions[i][1] * 32;
            if (mouseX >= bx && mouseX < bx + 76 && mouseY >= by && mouseY < by + 27) {
                C2SPacketUtil.tryIntPowerPacket(PowersStickyFingers.ZIPPER_UNZIP_PART, PARTS[i]);
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public void onClose() { minecraft.setScreen(null); }
}
