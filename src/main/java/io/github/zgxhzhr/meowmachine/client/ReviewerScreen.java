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
 * <p>不做滚动列表（第一版限制一次最多显示 8 条）：待审多到这个数时，
 * 靠命令 {@code /meow review list} 配合短 id 裁决更实际。
 */
public class ReviewerScreen extends Screen {

    private static final int MAX_ROWS = 8;
    private static final int ROW_H = 24;
    private static final int PANEL_W = 340;

    private final List<ReviewListPacket.Entry> entries;
    private final int shown;

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

        for (int i = 0; i < shown; i++) {
            ReviewListPacket.Entry e = entries.get(i);
            int y = top + i * ROW_H;

            // 三个按钮：通过 / 协改 / 打回
            addRenderableWidget(Button.builder(
                            Component.translatable("gui.meow_machine.btn.pass"),
                            b -> decide(e, "PASS"))
                    .bounds(left + PANEL_W - 156, y, 50, 18).build());
            addRenderableWidget(Button.builder(
                            Component.translatable("gui.meow_machine.btn.revise"),
                            b -> decide(e, "REVISE"))
                    .bounds(left + PANEL_W - 102, y, 50, 18).build());
            addRenderableWidget(Button.builder(
                            Component.translatable("gui.meow_machine.btn.return"),
                            b -> decide(e, "RETURN"))
                    .bounds(left + PANEL_W - 48, y, 48, 18).build());
        }

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.meow_machine.close"),
                        b -> this.onClose())
                .bounds(cx - 50, this.height - 40, 100, 20).build());
    }

    private void decide(ReviewListPacket.Entry entry, String verdict) {
        Network.CHANNEL.sendToServer(new DecidePacket(entry.fullId(), verdict));
        this.onClose();
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
            // 短 id + 作者 + 标题
            g.drawString(this.font,
                    Component.literal(e.shortId() + "  " + e.author()),
                    left, y + 5, 0xFFFF80, false);
            g.drawString(this.font,
                    Component.literal(trim(e.title())),
                    left + 110, y + 5, 0xDDDDDD, false);
        }

        if (entries.size() > MAX_ROWS) {
            g.drawCenteredString(this.font,
                    Component.translatable("gui.meow_machine.more",
                            entries.size() - MAX_ROWS),
                    cx, this.height - 56, 0xFF8080);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private static String trim(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 18 ? s : s.substring(0, 17) + "…";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
