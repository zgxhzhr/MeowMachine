package io.github.zgxhzhr.meowmachine.client;

import io.github.zgxhzhr.meowmachine.network.DecidePacket;
import io.github.zgxhzhr.meowmachine.network.Network;
import io.github.zgxhzhr.meowmachine.network.ReviewListPacket;
import io.github.zgxhzhr.meowmachine.network.ReviewListRequestPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 待审列表。
 *
 * <p>列表本身只够扫一眼（短 id / 作者 / 标题）；要**看正文**得点「查看」进入
 * {@link ReviewDetailScreen}——以前审核员在列表里根本读不到投稿内容，
 * 只能靠命令盲判，这是这轮补的缺口。
 */
public class ReviewerScreen extends Screen {

    private static final int MAX_ROWS = 8;
    private static final int ROW_H = 28;
    private static final int PANEL_W = 360;
    private static final int BUTTONS_W = 204;
    private static final int TEXT_W = PANEL_W - BUTTONS_W - 8;

    private final List<ReviewListPacket.Entry> entries;
    /** 助理为 false：只能查看与写意见，不能终裁 */
    private final boolean canFinalize;
    /** 助理：只查看与写意见 */
    private final boolean assistant;
    private int shown;

    public ReviewerScreen(ReviewListPacket packet) {
        super(Component.translatable("gui.meow_machine.reviewer_title"));
        this.entries = packet.entries();
        this.canFinalize = packet.canFinalize();
        this.assistant = packet.assistant();
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int left = cx - PANEL_W / 2;
        int top = 40;

        int fit = Math.max(1, (this.height - 100) / ROW_H);
        this.shown = Math.min(entries.size(), Math.min(MAX_ROWS, fit));

        for (int i = 0; i < shown; i++) {
            ReviewListPacket.Entry e = entries.get(i);
            int y = top + i * ROW_H + 4;
            int bx = left + PANEL_W - BUTTONS_W;

            addRenderableWidget(Button.builder(
                            Component.translatable("gui.meow_machine.btn.view"),
                            b -> openDetail(e))
                    .bounds(bx, y, 42, 18).build());
            if (canFinalize) {
                addRenderableWidget(Button.builder(
                                Component.translatable("gui.meow_machine.btn.pass"),
                                b -> decide(e, "PASS"))
                        .bounds(bx + 46, y, 50, 18).build());
                addRenderableWidget(Button.builder(
                                Component.translatable("gui.meow_machine.btn.revise"),
                                b -> decide(e, "REVISE"))
                        .bounds(bx + 100, y, 50, 18).build());
                addRenderableWidget(Button.builder(
                                Component.translatable("gui.meow_machine.btn.return"),
                                b -> decide(e, "RETURN"))
                        .bounds(bx + 154, y, 50, 18).build());
            }
        }

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.meow_machine.refresh"),
                        b -> Network.CHANNEL.sendToServer(new ReviewListRequestPacket()))
                .bounds(cx - 104, this.height - 32, 100, 20).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.meow_machine.close"), b -> this.onClose())
                .bounds(cx + 4, this.height - 32, 100, 20).build());
    }

    private void openDetail(ReviewListPacket.Entry entry) {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new ReviewDetailScreen(entry, canFinalize, assistant));
        }
    }

    private void decide(ReviewListPacket.Entry entry, String verdict) {
        if ("PASS".equals(verdict)) {
            Network.CHANNEL.sendToServer(new DecidePacket(entry.fullId(), verdict, -1, ""));
            this.onClose();
            return;
        }
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

        if (!canFinalize) {
            g.drawCenteredString(this.font,
                    Component.translatable("gui.meow_machine.assistant_hint"),
                    cx, 30, 0xFFAA44);
        }

        if (shown == 0) {
            g.drawCenteredString(this.font,
                    Component.translatable("command.meow_machine.queue_empty"),
                    cx, this.height / 2, 0xAAAAAA);
        }

        for (int i = 0; i < shown; i++) {
            ReviewListPacket.Entry e = entries.get(i);
            int y = top + i * ROW_H;
            g.drawString(this.font,
                    Component.literal(trimToWidth(e.shortId() + "  " + e.author(), TEXT_W)),
                    left, y + 3, 0xFFFF80, false);
            g.drawString(this.font,
                    Component.literal(trimToWidth(e.title(), TEXT_W)),
                    left, y + 14, 0xDDDDDD, false);
        }

        if (entries.size() > shown) {
            g.drawCenteredString(this.font,
                    Component.translatable("gui.meow_machine.more", entries.size() - shown),
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
