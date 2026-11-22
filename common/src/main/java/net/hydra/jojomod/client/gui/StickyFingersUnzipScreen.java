package net.hydra.jojomod.client.gui;

import net.hydra.jojomod.stand.powers.PowersStickyFingers;
import net.hydra.jojomod.util.C2SPacketUtil;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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

    @Override
    protected void init() {
        int centerX = width / 2;
        int top = height / 2 - 30;
        int buttonWidth = 122;
        int gap = 6;
        addPartButton(centerX - buttonWidth / 2, top - 2, buttonWidth, 20, 0);
        addPartButton(centerX - buttonWidth - gap / 2, top + 24, buttonWidth, 20, 1);
        addPartButton(centerX + gap / 2, top + 24, buttonWidth, 20, 2);
        addPartButton(centerX - buttonWidth - gap / 2, top + 50, buttonWidth, 20, 3);
        addPartButton(centerX + gap / 2, top + 50, buttonWidth, 20, 4);
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
        int top = height / 2 - 61;
        g.drawCenteredString(font, Component.translatable("gui.roundabout.sticky_fingers_unzip.title"),
                width / 2, top, 0xFFFFFFFF);
        g.drawCenteredString(font, Component.translatable("gui.roundabout.sticky_fingers_unzip.subtitle"),
                width / 2, top + 16, 0xFFA0A0A0);
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, Component.translatable("gui.roundabout.sticky_fingers_unzip.hint"),
                width / 2, height / 2 + 79, 0xFFA0A0A0);
    }

    @Override public void onClose() { minecraft.setScreen(null); }
}
