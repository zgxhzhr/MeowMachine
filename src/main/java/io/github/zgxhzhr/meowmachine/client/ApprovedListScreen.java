package io.github.zgxhzhr.meowmachine.client;

import io.github.zgxhzhr.meowmachine.network.ApprovedListPacket;
import io.github.zgxhzhr.meowmachine.network.EditMySubmissionPacket;
import io.github.zgxhzhr.meowmachine.network.Network;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** 已通过条目列表：任何人可看；每行可「查看」或「提交编辑」。 */
public class ApprovedListScreen extends Screen {

    private static final int PANEL_W = 380;
    private static final int ROW_H = 24;
    private static final int MAX_ROWS = 9;

    private final List<ApprovedListPacket.Entry> entries;
    private int shown;

    public ApprovedListScreen(ApprovedListPacket packet) {
        super(Component.translatable("gui.meow_machine.approved_title"));
        this.entries = packet.entries();
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int left = cx - PANEL_W / 2;
        int top = 34;
        int fit = Math.max(1, (this.height - 90) / ROW_H);
        this.shown = Math.min(entries.size(), Math.min(MAX_ROWS, fit));

        for (int i = 0; i < shown; i++) {
            ApprovedListPacket.Entry e = entries.get(i);
            int y = top + i * ROW_H + 2;
            addRenderableWidget(Button.builder(
                            Component.translatable("gui.meow_machine.btn.view"),
                            b -> this.minecraft.setScreen(new ApprovedViewScreen(this, e)))
                    .bounds(left + PANEL_W - 150, y, 70, 18).build());
            addRenderableWidget(Button.builder(
                            Component.translatable("gui.meow_machine.btn.edit"),
                            b -> {
                                Network.CHANNEL.sendToServer(new EditMySubmissionPacket(e.id()));
                                this.onClose();
                            })
                    .bounds(left + PANEL_W - 76, y, 70, 18).build());
        }

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.meow_machine.close"), b -> this.onClose())
                .bounds(cx - 50, this.height - 28, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int cx = this.width / 2;
        int left = cx - PANEL_W / 2;
        int top = 34;

        g.drawCenteredString(this.font, this.title, cx, 14, 0xFFFFFF);
        if (shown == 0) {
            g.drawCenteredString(this.font,
                    Component.translatable("gui.meow_machine.approved_empty"),
                    cx, this.height / 2, 0xAAAAAA);
        }
        for (int i = 0; i < shown; i++) {
            ApprovedListPacket.Entry e = entries.get(i);
            int y = top + i * ROW_H;
            g.drawString(this.font, Component.literal(e.shortId() + "  " + e.author()),
                    left, y + 3, 0xFFFF80, false);
            g.drawString(this.font, Component.literal(trim(e.title(), PANEL_W - 160)),
                    left, y + 13, 0xDDDDDD, false);
        }
        if (entries.size() > shown) {
            g.drawCenteredString(this.font,
                    Component.translatable("gui.meow_machine.more", entries.size() - shown),
                    cx, this.height - 44, 0xFF8080);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private String trim(String s, int maxWidth) {
        if (s == null) {
            return "";
        }
        if (this.font.width(s) <= maxWidth) {
            return s;
        }
        int i = s.length();
        while (i > 0 && this.font.width(s.substring(0, i) + "…") > maxWidth) {
            i--;
        }
        return s.substring(0, i) + "…";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
