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
 * 审核界面：待审列表 + 每行三个裁决按钮。
 *
 * <p>不做滚动列表（一次最多显示 8 条）：待审多到这个数时，
 * 靠命令 {@code /meow review list} 配合短 id 裁决更实际。
 *
 * <p>本次改的真问题：以前作者/标题文字从 {@code left+110} 起、按钮从 {@code left+184} 起，
 * 18 个字符的标题会直接压在「通过」按钮上。现在行内分两行文字（id+作者 / 标题），
 * 文字区宽度固定，超出按像素宽度截断。
 */
public class ReviewerScreen extends Screen {

    private static final int MAX_ROWS = 8;
    private static final int ROW_H = 28;
    private static final int PANEL_W = 340;
    private static final int BUTTONS_W = 156;
    private static final int TEXT_W = PANEL_W - BUTTONS_W - 8;

    private final List<ReviewListPacket.Entry> entries;
    /** 实际显示几条：按窗口高度算，矮窗口（GUI scale 4）少显示几行，别顶到关闭按钮 */
    private int shown;

    public ReviewerScreen(ReviewListPacket packet) {
        super(Component.translatable("gui.meow_machine.reviewer_title"));
        this.entries = packet.entries();
        this.shown = Math.min(entries.size(), MAX_ROWS);
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int left = cx - PANEL_W / 2;
        int top = 40;

        // 顶部标题 + 底部关闭按钮占掉约 100px，剩下的按行高算能放几行
        int fit = Math.max(1, (this.height - 100) / ROW_H);
        this.shown = Math.min(entries.size(), Math.min(MAX_ROWS, fit));

        for (int i = 0; i < shown; i++) {
            ReviewListPacket.Entry e = entries.get(i);
            int y = top + i * ROW_H + 4;

            // 三个按钮：通过 / 协改 / 打回
            addRenderableWidget(Button.builder(
                            Component.translatable("gui.meow_machine.btn.pass"),
                            b -> decide(e, "PASS"))
                    .bounds(left + PANEL_W - BUTTONS_W, y, 50, 18).build());
            addRenderableWidget(Button.builder(
                            Component.translatable("gui.meow_machine.btn.revise"),
                            b -> decide(e, "REVISE"))
                    .bounds(left + PANEL_W - BUTTONS_W + 54, y, 50, 18).build());
            addRenderableWidget(Button.builder(
                            Component.translatable("gui.meow_machine.btn.return"),
                            b -> decide(e, "RETURN"))
                    .bounds(left + PANEL_W - BUTTONS_W + 108, y, 48, 18).build());
        }

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.meow_machine.close"),
                        b -> this.onClose())
                .bounds(cx - 50, this.height - 32, 100, 20).build());
    }

    private void decide(ReviewListPacket.Entry entry, String verdict) {
        if ("PASS".equals(verdict)) {
            // 通过不需要原因，直接发
            Network.CHANNEL.sendToServer(new DecidePacket(entry.fullId(), verdict, -1, ""));
            this.onClose();
            return;
        }
        // 协改 / 打回：先让审核员选原因（规则号 + 一句话）
        if (this.minecraft != null) {
            this.minecraft.setScreen(new DecisionScreen(entry, verdict));
        }
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
                    Component.translatable("command.meow_machine.queue_empty"),
                    cx, this.height / 2, 0xAAAAAA);
        }

        for (int i = 0; i < shown; i++) {
            ReviewListPacket.Entry e = entries.get(i);
            int y = top + i * ROW_H;
            // 第一行：短 id + 作者；第二行：标题。都限制在文字区宽度内。
            g.drawString(this.font,
                    Component.literal(trimToWidth(e.shortId() + "  " + e.author(), TEXT_W)),
                    left, y + 3, 0xFFFF80, false);
            g.drawString(this.font,
                    Component.literal(trimToWidth(e.title(), TEXT_W)),
                    left, y + 14, 0xDDDDDD, false);
        }

        if (entries.size() > shown) {
            g.drawCenteredString(this.font,
                    Component.translatable("gui.meow_machine.more",
                            entries.size() - shown),
                    cx, this.height - 44, 0xFF8080);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    /** 按像素宽度截断（比按字符数靠谱：中文/英文宽度差一倍） */
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
