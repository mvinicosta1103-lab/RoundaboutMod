package net.hydra.jojomod.client.gui;

import net.hydra.jojomod.stand.powers.PowersStickyFingers;
import net.hydra.jojomod.util.C2SPacketUtil;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** One-shot selection for zipping one of the Sticky Fingers user's own limbs off. */
public final class StickyFingersUnzipScreen extends Screen implements NoCancelInputScreen {
    private static final int[] PARTS = {
            PowersStickyFingers.ZIPPER_PART_LEFT_ARM,
            PowersStickyFingers.ZIPPER_PART_RIGHT_ARM,
            PowersStickyFingers.ZIPPER_PART_LEFT_LEG,
            PowersStickyFingers.ZIPPER_PART_RIGHT_LEG
    };
    private static final String[] LABELS = {
            "gui.roundabout.sticky_fingers_unzip.left_arm",
            "gui.roundabout.sticky_fingers_unzip.right_arm",
            "gui.roundabout.sticky_fingers_unzip.left_leg",
            "gui.roundabout.sticky_fingers_unzip.right_leg"
    };

    public StickyFingersUnzipScreen() {
        super(GameNarrator.NO_TITLE);
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int top = height / 2 - 22;
        int buttonWidth = 126;
        int gap = 8;
        int left = centerX - buttonWidth - gap / 2;
        int right = centerX + gap / 2;
        addPartButton(left, top, buttonWidth, 20, 0);
        addPartButton(right, top, buttonWidth, 20, 1);
        addPartButton(left, top + 27, buttonWidth, 20, 2);
        addPartButton(right, top + 27, buttonWidth, 20, 3);
    }

    private void addPartButton(int x, int y, int buttonWidth, int buttonHeight, int partIndex) {
        addRenderableWidget(Button.builder(Component.translatable(LABELS[partIndex]), button -> {
            C2SPacketUtil.tryIntPowerPacket(PowersStickyFingers.ZIPPER_UNZIP_PART, PARTS[partIndex]);
            onClose();
        }).bounds(x, y, buttonWidth, buttonHeight).build());
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int top = height / 2 - 64;
        g.drawCenteredString(font, Component.translatable("gui.roundabout.sticky_fingers_unzip.title"),
                width / 2, top, 0xFFFFFFFF);
        g.drawCenteredString(font, Component.translatable("gui.roundabout.sticky_fingers_unzip.subtitle"),
                width / 2, top + 16, 0xFFA0A0A0);
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, Component.translatable("gui.roundabout.sticky_fingers_unzip.hint"),
                width / 2, height / 2 + 65, 0xFFA0A0A0);
    }

    @Override public void onClose() { minecraft.setScreen(null); }
}
