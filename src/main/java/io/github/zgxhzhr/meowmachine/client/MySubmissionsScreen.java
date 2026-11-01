package io.github.zgxhzhr.meowmachine.client;

import io.github.zgxhzhr.meowmachine.network.EditMySubmissionPacket;
import io.github.zgxhzhr.meowmachine.network.MySubmissionsPacket;
import io.github.zgxhzhr.meowmachine.network.Network;
import io.github.zgxhzhr.meowmachine.network.OpenAdminRequestPacket;
import io.github.zgxhzhr.meowmachine.network.ReviewListRequestPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 我的投稿：当前草稿 + 队列里的稿子 + 已出结论的历史。
 *
 * <p>支持翻页（历史一多就看不全），每行有「结论 / 查看 / 编辑」。
 */
public class MySubmissionsScreen extends Screen {

    private static final int PANEL_W = 340;
    private static final int ROW_H = 24;
    private static final int MAX_ROWS = 8;
    private static final int BTN_H = 18;
    private static final int VERDICT_W = 42;
    private static final int PREVIEW_W = 42;
    private static final int EDIT_W = 44;
    private static final int ROW_BTNS = VERDICT_W + PREVIEW_W + EDIT_W + 8;

    private final List<MySubmissionsPacket.Entry> rows;
    private int perPage = MAX_ROWS;
    private int page = 0;
    private int pageCount = 1;

    public MySubmissionsScreen(MySubmissionsPacket packet) {
        super(Component.translatable("gui.meow_machine.my_title"));
        this.rows = packet.rows();
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int left = cx - PANEL_W / 2;
        int top = 40;
        int fit = Math.max(1, (this.height - 120) / ROW_H);
        this.perPage = Math.max(1, Math.min(MAX_ROWS, fit));
        this.pageCount = Math.max(1, (rows.size() + perPage - 1) / perPage);
        this.page = Math.max(0, Math.min(page, pageCount - 1));

        int from = page * perPage;
        int to = Math.min(rows.size(), from + perPage);
        for (int i = from; i < to; i++) {
            MySubmissionsPacket.Entry e = rows.get(i);
            int y = top + (i - from) * ROW_H + 2;
            int x = left + PANEL_W - ROW_BTNS + 4;
            boolean hasVerdict = "returned".equals(e.state()) || "revising".equals(e.state())
                    || "approved".equals(e.state());
            if (hasVerdict) {
                addRenderableWidget(Button.builder(
                                Component.translatable("gui.meow_machine.btn.verdict"),
                                b -> {
                                    if (this.minecraft != null) {
                                        this.minecraft.setScreen(new VerdictScreen(this, e));
                                    }
                                })
                        .bounds(x, y, VERDICT_W, BTN_H).build());
            }
            addRenderableWidget(Button.builder(
                            Component.translatable("gui.meow_machine.btn.preview"),
                            b -> {
                                if (this.minecraft != null) {
                                    this.minecraft.setScreen(new SubmissionViewScreen(e));
                                }
                            })
                    .bounds(x + VERDICT_W + 4, y, PREVIEW_W, BTN_H).build());
            if (e.editable()) {
                addRenderableWidget(Button.builder(
                                Component.translatable("gui.meow_machine.btn.edit"),
                                b -> Network.CHANNEL.sendToServer(new EditMySubmissionPacket(e.id())))
                        .bounds(x + VERDICT_W + PREVIEW_W + 8, y, EDIT_W, BTN_H).build());
            }
        }

        int by = this.height - 28;
        if (pageCount > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"),
                            b -> {
                                this.page = Math.max(0, page - 1);
                                this.rebuildWidgets();
                            })
                    .bounds(cx - 206, by, 22, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"),
                            b -> {
                                this.page = Math.min(pageCount - 1, page + 1);
                                this.rebuildWidgets();
                            })
                    .bounds(cx + 184, by, 22, 20).build());
        }
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.meow_machine.my_review_list"),
                        b -> Network.CHANNEL.sendToServer(new ReviewListRequestPacket()))
                .bounds(cx - 152, by, 96, 20).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.meow_machine.my_admin"),
                        b -> Network.CHANNEL.sendToServer(new OpenAdminRequestPacket()))
                .bounds(cx - 52, by, 96, 20).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.meow_machine.close"),
                        b -> this.onClose())
                .bounds(cx + 48, by, 96, 20).build());
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (pageCount <= 1) {
            return super.mouseScrolled(mx, my, delta);
        }
        int next = delta > 0 ? page - 1 : page + 1;
        next = Math.max(0, Math.min(pageCount - 1, next));
        if (next != page) {
            this.page = next;
            this.rebuildWidgets();
        }
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int cx = this.width / 2;
        int left = cx - PANEL_W / 2;
        int top = 40;

        g.drawCenteredString(this.font, this.title, cx, 20, 0xFFFFFF);
        if (pageCount > 1) {
            g.drawString(this.font, Component.literal((page + 1) + "/" + pageCount),
                    cx - 178, this.height - 22, 0xAAAAAA, false);
        }

        if (rows.isEmpty()) {
            g.drawCenteredString(this.font,
                    Component.translatable("gui.meow_machine.my_empty"), cx, this.height / 2, 0xAAAAAA);
        }

        int from = page * perPage;
        int to = Math.min(rows.size(), from + perPage);
        for (int i = from; i < to; i++) {
            MySubmissionsPacket.Entry e = rows.get(i);
            int y = top + (i - from) * ROW_H;
            g.drawString(this.font, Component.literal(e.shortId()), left, y + 2, 0xFFFF80, false);
            Component state = Component.translatable("gui.meow_machine.state." + e.state());
            g.drawString(this.font, state, left + 62, y + 2,
                    e.pending() ? 0xFFAA44 : 0xAAAAAA, false);
            g.drawString(this.font,
                    Component.literal(trimToWidth(e.title(), PANEL_W - ROW_BTNS - 12)),
                    left, y + 13, 0xDDDDDD, false);
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
