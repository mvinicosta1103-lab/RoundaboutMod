package net.hydra.jojomod.client.gui;

import net.hydra.jojomod.access.IStickyFingersDismemberment;
import net.hydra.jojomod.event.powers.StandUser;
import net.hydra.jojomod.stand.powers.PowersStickyFingers;
import net.hydra.jojomod.util.C2SPacketUtil;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Persistent multi-select for parts removed by successful hits while Zip Marking Mode is enabled. */
public final class StickyFingersAutoUnzipScreen extends Screen implements NoCancelInputScreen {
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

    private int config;
    private Button enabledButton;
    private final Button[] partButtons = new Button[PARTS.length];

    public StickyFingersAutoUnzipScreen() {
        super(GameNarrator.NO_TITLE);
    }

    @Override
    protected void init() {
        config = minecraft.player instanceof IStickyFingersDismemberment state
                ? state.roundabout$getStickyFingersAutoUnzipConfig() : 0;
        int centerX = width / 2;
        int top = height / 2 - 38;
        int buttonWidth = 140;
        int gap = 6;

        enabledButton = addRenderableWidget(Button.builder(enabledLabel(), button -> {
            config ^= PowersStickyFingers.ZIPPER_AUTO_UNZIP_ENABLED;
            sendConfig();
            enabledButton.setMessage(enabledLabel());
        }).bounds(centerX - buttonWidth / 2, top - 4, buttonWidth, 20).build());

        int left = centerX - buttonWidth - gap / 2;
        int right = centerX + gap / 2;
        int[] xs = {left, left, right, left, right};
        int[] ys = {top + 22, top + 48, top + 48, top + 74, top + 74};
        for (int i = 0; i < PARTS.length; i++) {
            final int index = i;
            partButtons[i] = addRenderableWidget(Button.builder(partLabel(i), button -> {
                config ^= PARTS[index];
                sendConfig();
                button.setMessage(partLabel(index));
            }).bounds(xs[i], ys[i], buttonWidth, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.roundabout.sticky_fingers_unzip.done"),
                button -> onClose()).bounds(centerX - 50, top + 103, 100, 20).build());
    }

    private Component enabledLabel() {
        boolean enabled = (config & PowersStickyFingers.ZIPPER_AUTO_UNZIP_ENABLED) != 0;
        return Component.translatable("gui.roundabout.sticky_fingers_unzip.auto_enabled",
                Component.translatable(enabled ? "options.on" : "options.off"));
    }

    private Component partLabel(int index) {
        boolean selected = (config & PARTS[index]) != 0;
        return Component.literal(selected ? "[x] " : "[ ] ")
                .append(Component.translatable(LABELS[index]));
    }

    private void sendConfig() {
        C2SPacketUtil.tryIntPowerPacket(PowersStickyFingers.ZIPPER_AUTO_UNZIP_CONFIG, config);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int top = height / 2 - 63;
        g.drawCenteredString(font, Component.translatable("gui.roundabout.sticky_fingers_unzip.auto_title"),
                width / 2, top, 0xFFFFFFFF);
        g.drawCenteredString(font, Component.translatable("gui.roundabout.sticky_fingers_unzip.auto_subtitle"),
                width / 2, top + 16, 0xFFA0A0A0);
        super.render(g, mouseX, mouseY, partialTick);
        boolean zipMode = minecraft.player instanceof StandUser user && user.roundabout$getUniqueStandModeToggle();
        g.drawCenteredString(font, Component.translatable(zipMode
                        ? "gui.roundabout.sticky_fingers_unzip.auto_zip_on"
                        : "gui.roundabout.sticky_fingers_unzip.auto_zip_off"),
                width / 2, height / 2 + 78, zipMode ? 0xFF80FF80 : 0xFFFFC060);
    }

    @Override public void onClose() { minecraft.setScreen(null); }
}
