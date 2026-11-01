package io.github.zgxhzhr.meowmachine.client;

import io.github.zgxhzhr.meowmachine.network.DecidePacket;
import io.github.zgxhzhr.meowmachine.network.Network;
import io.github.zgxhzhr.meowmachine.network.ReviewListPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 通过时挑梗图：审核员可以从 approved 系列里选一张，也可以交回默认随机。
 *
 * <p>以前「通过」直接发裁决，图由服务端随机挑；现在先在这里选。
 */
public class PassStickerScreen extends Screen {

    private static final int PANEL_W = 360;
    private static final int BTN_W = 168;
    private static final int BTN_H = 20;
    private static final int GAP = 6;

    private final Screen parent;
    private final ReviewListPacket.Entry entry;
    private final List<String> approved;
    private net.minecraft.client.gui.components.EditBox reasonBox;

    public PassStickerScreen(Screen parent, ReviewListPacket.Entry entry, List<String> approved) {
        super(Component.translatable("gui.meow_machine.pass_sticker_title"));
        this.parent = parent;
        this.entry = entry;
        this.approved = approved;
    }

    @Override
    protected void init() {
        io.github.zgxhzhr.meowmachine.client.ImeCompat.onTextScreenOpened();
        int cx = this.width / 2;
        int left = cx - PANEL_W / 2;
        int top = 40;

        // 两列铺开
        for (int i = 0; i < approved.size(); i++) {
            String id = approved.get(i);
            int col = i % 2;
            int row = i / 2;
            addRenderableWidget(Button.builder(stickerName(id), b -> passWith(id))
                    .bounds(left + col * (BTN_W + GAP), top + row * (BTN_H + GAP), BTN_W, BTN_H)
                    .build());
        }

        int rows = (approved.size() + 1) / 2;
        int y = top + rows * (BTN_H + GAP) + 6;

        // 通过没有「条款」可引用，这里可以写一句允许通过的理由（会显示给作者）
        this.reasonBox = new net.minecraft.client.gui.components.EditBox(this.font,
                left, y, PANEL_W, 20,
                Component.translatable("gui.meow_machine.pass_reason_hint"));
        this.reasonBox.setMaxLength(120);
        this.reasonBox.setHint(Component.translatable("gui.meow_machine.pass_reason_hint"));
        this.addRenderableWidget(this.reasonBox);
        y += 26;

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.meow_machine.pass_sticker_default"),
                        b -> passWith(""))
                .bounds(cx - 94, y, 90, 20).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.meow_machine.cancel"),
                        b -> {
                            if (this.minecraft != null) {
                                this.minecraft.setScreen(parent);
                            }
                        })
                .bounds(cx + 4, y, 90, 20).build());
    }

    private Component stickerName(String id) {
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        return Component.translatable("item.meow_machine.sticker_" + path);
    }

    private void passWith(String stickerId) {
        String reason = reasonBox == null ? "" : reasonBox.getValue().strip();
        Network.CHANNEL.sendToServer(
                new DecidePacket(entry.fullId(), "PASS", -1, reason, stickerId));
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int cx = this.width / 2;
        g.drawCenteredString(this.font, this.title, cx, 16, 0xFFFFFF);
        g.drawCenteredString(this.font,
                Component.literal(entry.shortId() + "  " + entry.title()),
                cx, 28, 0xFFFF80);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
