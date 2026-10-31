package io.github.zgxhzhr.meowmachine.client;

import io.github.zgxhzhr.meowmachine.network.MySubmissionsPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 我的投稿：当前草稿 + 人工队列里自己的稿子。
 *
 * <p>入口在投稿编辑界面的「我的投稿」按钮。以前提交完只看得到一张梗图弹窗，
 * 稿子在队列里排到哪、有没有被打回，作者没有任何地方可查。
 */
public class MySubmissionsScreen extends Screen {

    private static final int PANEL_W = 340;
    private static final int ROW_H = 22;
    private static final int MAX_ROWS = 8;

    private final List<MySubmissionsPacket.Entry> rows;
    private int shown;

    public MySubmissionsScreen(MySubmissionsPacket packet) {
        super(Component.translatable("gui.meow_machine.my_title"));
        this.rows = packet.rows();
    }

    @Override
    protected void init() {
        int fit = Math.max(1, (this.height - 100) / ROW_H);
        this.shown = Math.min(rows.size(), Math.min(MAX_ROWS, fit));
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.meow_machine.close"),
                        b -> this.onClose())
                .bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());
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
            String id = e.shortId();
            g.drawString(this.font, Component.literal(id), left, y + 5, 0xFFFF80, false);
            g.drawString(this.font,
                    Component.literal(trimToWidth(e.title(), 150)), left + 70, y + 5, 0xDDDDDD, false);
            Component state = Component.translatable("gui.meow_machine.state." + e.state());
            g.drawString(this.font, state, left + PANEL_W - 96, y + 5,
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
