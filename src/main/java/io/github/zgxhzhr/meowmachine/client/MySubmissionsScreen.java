package io.github.zgxhzhr.meowmachine.client;

import io.github.zgxhzhr.meowmachine.network.EditMySubmissionPacket;
import io.github.zgxhzhr.meowmachine.network.MySubmissionsPacket;
import io.github.zgxhzhr.meowmachine.network.Network;
import io.github.zgxhzhr.meowmachine.network.ReviewListRequestPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 我的投稿：当前草稿 + 人工队列里自己的稿子。
 *
 * <p>每行有「编辑」：当前草稿直接打开；还在队列里的会**撤回**后再打开
 * （以前这个界面只能看，进得来出不去，是个死角）。
 */
public class MySubmissionsScreen extends Screen {

    private static final int PANEL_W = 340;
    private static final int ROW_H = 24;
    private static final int MAX_ROWS = 8;
    private static final int ROW_BTN_W = 48;
    private static final int TEXT_W = PANEL_W - ROW_BTN_W - 12;

    private final List<MySubmissionsPacket.Entry> rows;
    private int shown;

    public MySubmissionsScreen(MySubmissionsPacket packet) {
        super(Component.translatable("gui.meow_machine.my_title"));
        this.rows = packet.rows();
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int left = cx - PANEL_W / 2;
        int top = 40;

        int fit = Math.max(1, (this.height - 110) / ROW_H);
        this.shown = Math.min(rows.size(), Math.min(MAX_ROWS, fit));

        for (int i = 0; i < shown; i++) {
            MySubmissionsPacket.Entry e = rows.get(i);
            addRenderableWidget(Button.builder(
                            Component.translatable("gui.meow_machine.btn.edit"),
                            b -> Network.CHANNEL.sendToServer(
                                    new EditMySubmissionPacket(e.id())))
                    .bounds(left + PANEL_W - ROW_BTN_W, top + i * ROW_H + 2, ROW_BTN_W, 18).build());
        }

        int y = this.height - 28;
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.meow_machine.my_review_list"),
                        b -> Network.CHANNEL.sendToServer(new ReviewListRequestPacket()))
                .bounds(cx - 104, y, 100, 20).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.meow_machine.close"),
                        b -> this.onClose())
                .bounds(cx + 4, y, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int cx = this.width / 2;
        int left = cx - PANEL_W / 2;
        int top = 40;

        g.drawCenteredString(this.font, this.title, cx, 20, 0xFFFFFF);

        if (shown == 0) {
            g.drawCenteredString(this.font,
                    Component.translatable("gui.meow_machine.my_empty"), cx, this.height / 2, 0xAAAAAA);
        }

        for (int i = 0; i < shown; i++) {
            MySubmissionsPacket.Entry e = rows.get(i);
            int y = top + i * ROW_H;
            g.drawString(this.font, Component.literal(e.shortId()), left, y + 3, 0xFFFF80, false);
            g.drawString(this.font,
                    Component.literal(trimToWidth(e.title(), TEXT_W - 100)),
                    left + 68, y + 3, 0xDDDDDD, false);
            Component state = Component.translatable("gui.meow_machine.state." + e.state());
            g.drawString(this.font, state, left + TEXT_W - 4, y + 13,
                    e.pending() ? 0xFFAA44 : 0xAAAAAA, false);
        }

        if (rows.size() > shown) {
            g.drawCenteredString(this.font,
                    Component.translatable("gui.meow_machine.my_more", rows.size() - shown),
                    cx, this.height - 44, 0xFF8080);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private String trimToWidth(String s, int maxWidth) {
        if (s == null) {
            return "";
        }
        if (this.font.width(s) <= maxWidth) {
            return s;
        }
        String ellipsis = "…";
        int i = s.length();
        while (i > 0 && this.font.width(s.substring(0, i) + ellipsis) > maxWidth) {
            i--;
        }
        return s.substring(0, i) + ellipsis;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
