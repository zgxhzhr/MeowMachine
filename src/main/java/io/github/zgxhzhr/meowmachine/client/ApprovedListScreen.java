package io.github.zgxhzhr.meowmachine.client;

import io.github.zgxhzhr.meowmachine.network.ApprovedListPacket;
import io.github.zgxhzhr.meowmachine.network.EditMySubmissionPacket;
import io.github.zgxhzhr.meowmachine.network.Network;
import io.github.zgxhzhr.meowmachine.network.RequestReturnPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 已通过条目：任何人可看、可提交编辑、可申请退回；审核员/OP 可直接退回。
 *
 * <p>支持翻页（条目多了也不会只显示前 9 条）。
 */
public class ApprovedListScreen extends Screen {

    private static final int PANEL_W = 400;
    private static final int ROW_H = 26;
    private static final int MAX_ROWS = 9;
    private static final int BTN_H = 18;
    private static final int VIEW_W = 48;
    private static final int EDIT_W = 48;
    private static final int RETURN_W = 56;

    private final List<ApprovedListPacket.Entry> entries;
    private final boolean canFinalize;
    private int perPage = MAX_ROWS;
    private int page = 0;
    private int pageCount = 1;

    public ApprovedListScreen(ApprovedListPacket packet) {
        super(Component.translatable("gui.meow_machine.approved_title"));
        this.entries = packet.entries();
        this.canFinalize = packet.canFinalize();
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int left = cx - PANEL_W / 2;
        int top = 34;
        int fit = Math.max(1, (this.height - 100) / ROW_H);
        this.perPage = Math.max(1, Math.min(MAX_ROWS, fit));
        this.pageCount = Math.max(1, (entries.size() + perPage - 1) / perPage);
        this.page = Math.max(0, Math.min(page, pageCount - 1));

        int from = page * perPage;
        int to = Math.min(entries.size(), from + perPage);
        for (int i = from; i < to; i++) {
            ApprovedListPacket.Entry e = entries.get(i);
            int y = top + (i - from) * ROW_H + 2;
            int x = left + PANEL_W - (VIEW_W + EDIT_W + RETURN_W + 12);
            addRenderableWidget(Button.builder(
                            Component.translatable("gui.meow_machine.btn.view"),
                            b -> this.minecraft.setScreen(new ApprovedViewScreen(this, e)))
                    .bounds(x, y, VIEW_W, BTN_H).build());
            addRenderableWidget(Button.builder(
                            Component.translatable("gui.meow_machine.btn.edit"),
                            b -> {
                                Network.CHANNEL.sendToServer(new EditMySubmissionPacket(e.id()));
                                this.onClose();
                            })
                    .bounds(x + VIEW_W + 4, y, EDIT_W, BTN_H).build());
            addRenderableWidget(Button.builder(
                            Component.translatable(canFinalize
                                    ? "gui.meow_machine.btn.return_now"
                                    : "gui.meow_machine.btn.request_return"),
                            b -> {
                                if (this.minecraft != null) {
                                    this.minecraft.setScreen(new ReturnReasonScreen(
                                            this, e.id(), canFinalize));
                                }
                            })
                    .bounds(x + VIEW_W + EDIT_W + 8, y, RETURN_W, BTN_H).build());
        }

        int by = this.height - 28;
        if (pageCount > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"),
                            b -> {
                                this.page = Math.max(0, page - 1);
                                this.rebuildWidgets();
                            })
                    .bounds(cx - 230, by, 22, 20).build());
            addRenderableWidget(Button.builder(Component.literal(">"),
                            b -> {
                                this.page = Math.min(pageCount - 1, page + 1);
                                this.rebuildWidgets();
                            })
                    .bounds(cx + 208, by, 22, 20).build());
        }
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.meow_machine.close"), b -> this.onClose())
                .bounds(cx - 50, by, 100, 20).build());
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
        int top = 34;

        g.drawCenteredString(this.font, this.title, cx, 14, 0xFFFFFF);
        if (pageCount > 1) {
            g.drawString(this.font, Component.literal((page + 1) + "/" + pageCount),
                    cx - 200, this.height - 22, 0xAAAAAA, false);
        }
        if (entries.isEmpty()) {
            g.drawCenteredString(this.font,
                    Component.translatable("gui.meow_machine.approved_empty"),
                    cx, this.height / 2, 0xAAAAAA);
        }

        int from = page * perPage;
        int to = Math.min(entries.size(), from + perPage);
        for (int i = from; i < to; i++) {
            ApprovedListPacket.Entry e = entries.get(i);
            int y = top + (i - from) * ROW_H;
            g.drawString(this.font, Component.literal(e.shortId() + "  " + e.author()
                            + "  第 " + (e.revision() + 1) + " 版"),
                    left, y + 2, 0xFFFF80, false);
            String line = e.title();
            if (e.returnRequester() != null && !e.returnRequester().isBlank()) {
                line = line + "   §c[申请退回：" + e.returnRequester()
                        + (e.returnReason().isBlank() ? "" : "：" + e.returnReason()) + "]";
            }
            g.drawString(this.font, Component.literal(trim(line, PANEL_W - 180)),
                    left, y + 13, 0xDDDDDD, false);
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
