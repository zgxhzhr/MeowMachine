package io.github.zgxhzhr.meowmachine.client;

import io.github.zgxhzhr.meowmachine.network.Network;
import io.github.zgxhzhr.meowmachine.network.ReviewListPacket;
import io.github.zgxhzhr.meowmachine.network.ReviewListRequestPacket;
import io.github.zgxhzhr.meowmachine.richtext.RichDoc;
import io.github.zgxhzhr.meowmachine.richtext.RichHtml;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 审核详情：看到与投稿端**一致的排版**（真表格、真列表），并就地裁决。
 *
 * <p>以前这里只显示剥掉标签的纯文本，表格会被压成一串，审核员看不出排版问题；
 * 现在服务端把富文本正文一并下发，客户端用 {@link RichReadView} 渲染。
 */
public class ReviewDetailScreen extends Screen {

    private static final int PANEL_W = 380;
    private static final int LINE_H = 10;
    private static final int TOP = 56;

    private final ReviewListPacket.Entry entry;
    private final boolean canFinalize;
    private final boolean assistant;
    private final java.util.List<String> approvedStickers;

    private final java.util.List<String> diffLines = new java.util.ArrayList<>();
    private RichReadView view = null;
    private int scroll = 0;
    private int bodyTop = TOP;
    private int viewHeight;

    public ReviewDetailScreen(ReviewListPacket.Entry entry, boolean canFinalize, boolean assistant,
                              java.util.List<String> approvedStickers) {
        super(Component.translatable("gui.meow_machine.detail_title"));
        this.entry = entry;
        this.canFinalize = canFinalize;
        this.assistant = assistant;
        this.approvedStickers = approvedStickers == null ? java.util.List.of() : approvedStickers;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int left = cx - PANEL_W / 2;
        int bottom = this.height - 34;

        this.diffLines.clear();
        for (String d : entry.diffLines()) {
            if (this.diffLines.size() >= 3) {
                break;
            }
            this.diffLines.add(d);
        }
        this.bodyTop = TOP + this.diffLines.size() * LINE_H + (this.diffLines.isEmpty() ? 0 : 2);
        this.viewHeight = bottom - this.bodyTop - 6;

        RichDoc doc;
        String html = entry.bodyHtml();
        if (html != null && !html.isBlank()) {
            doc = RichHtml.parseLoose(html);
        } else {
            String text = entry.body() == null ? "" : entry.body();
            doc = RichHtml.parseLoose(text);
        }
        this.view = RichReadView.layout(this.font, doc, left + 8, PANEL_W - 16);
        if (this.view.texts().isEmpty() && this.view.height() == 0) {
            this.view = RichReadView.layout(this.font,
                    RichHtml.parseLoose(Component.translatable(
                            "gui.meow_machine.detail_empty").getString()), left + 8, PANEL_W - 16);
        }
        this.scroll = 0;

        int bw = 72;
        int gap = 5;
        int by = this.height - 28;
        if (canFinalize) {
            int x0 = cx - (bw * 5 + gap * 4) / 2;
            addRenderableWidget(Button.builder(
                            Component.translatable("gui.meow_machine.btn.pass"), b -> decide("PASS"))
                    .bounds(x0, by, bw, 20).build());
            addRenderableWidget(Button.builder(
                            Component.translatable("gui.meow_machine.btn.revise"), b -> decide("REVISE"))
                    .bounds(x0 + bw + gap, by, bw, 20).build());
            addRenderableWidget(Button.builder(
                            Component.translatable("gui.meow_machine.btn.return"), b -> decide("RETURN"))
                    .bounds(x0 + (bw + gap) * 2, by, bw, 20).build());
            addRenderableWidget(Button.builder(
                            Component.translatable("gui.meow_machine.opinions_button"),
                            b -> this.minecraft.setScreen(new ReviewOpinionsScreen(entry)))
                    .bounds(x0 + (bw + gap) * 3, by, bw, 20).build());
            addRenderableWidget(Button.builder(
                            Component.translatable("gui.meow_machine.back"),
                            b -> Network.CHANNEL.sendToServer(new ReviewListRequestPacket()))
                    .bounds(x0 + (bw + gap) * 4, by, bw, 20).build());
        } else {
            int x0 = cx - (bw * 3 + gap * 2) / 2;
            addRenderableWidget(Button.builder(
                            Component.translatable("gui.meow_machine.note_write"),
                            b -> this.minecraft.setScreen(new AddNoteScreen(entry)))
                    .bounds(x0, by, bw, 20).build());
            addRenderableWidget(Button.builder(
                            Component.translatable("gui.meow_machine.opinions_button"),
                            b -> this.minecraft.setScreen(new ReviewOpinionsScreen(entry)))
                    .bounds(x0 + bw + gap, by, bw, 20).build());
            addRenderableWidget(Button.builder(
                            Component.translatable("gui.meow_machine.back"),
                            b -> Network.CHANNEL.sendToServer(new ReviewListRequestPacket()))
                    .bounds(x0 + (bw + gap) * 2, by, bw, 20).build());
        }
        clampScroll();
    }

    private void decide(String verdict) {
        if ("PASS".equals(verdict)) {
            if (this.minecraft != null) {
                this.minecraft.setScreen(new PassStickerScreen(this, entry, approvedStickers));
            }
            return;
        }
        if (this.minecraft != null) {
            this.minecraft.setScreen(new DecisionScreen(entry, verdict));
        }
    }

    private void clampScroll() {
        int max = Math.max(0, view.height() - viewHeight + 6);
        scroll = Math.max(0, Math.min(scroll, max));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll -= (int) Math.signum(delta) * 12;
        clampScroll();
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int cx = this.width / 2;
        int left = cx - PANEL_W / 2;

        g.drawCenteredString(this.font, this.title, cx, 12, 0xFFFFFF);
        g.drawString(this.font, Component.literal(entry.shortId() + "  " + entry.author()),
                left, 28, 0xFFFF80, false);
        g.drawString(this.font, Component.literal(trim(entry.title(), PANEL_W)),
                left, 40, 0xDDDDDD, false);

        int dy = TOP;
        for (String d : this.diffLines) {
            g.drawString(this.font, Component.literal(trim(d, PANEL_W)), left, dy, 0xFFAA44, false);
            dy += LINE_H;
        }

        g.fill(left - 1, bodyTop - 1, left + PANEL_W + 1, bodyTop + viewHeight + 1, 0xFF808080);
        g.fill(left, bodyTop, left + PANEL_W, bodyTop + viewHeight, 0xFF101010);

        int originY = bodyTop - scroll;
        g.enableScissor(left, bodyTop, left + PANEL_W, bodyTop + viewHeight);
        for (RichReadView.BorderOp b : view.borders()) {
            int y1 = originY + b.y1();
            int y2 = originY + b.y2();
            if (y2 < bodyTop || y1 > bodyTop + viewHeight) {
                continue;
            }
            g.fill(b.x1(), y1, b.x1() + 1, Math.max(y1 + 1, y2), b.color());
            if (b.y1() == b.y2()) {
                g.fill(b.x1(), y1, b.x2(), y1 + 1, b.color());
            }
        }
        for (RichReadView.TextOp t : view.texts()) {
            int y = originY + t.y();
            if (y < bodyTop - LINE_H || y > bodyTop + viewHeight) {
                continue;
            }
            g.drawString(this.font, Component.literal(t.text()), t.x(), y, t.color(), false);
        }
        g.disableScissor();

        if (view.height() > viewHeight) {
            g.drawString(this.font,
                    Component.literal((scroll * 100 / Math.max(1, view.height())) + "%"),
                    left + PANEL_W - 34, bodyTop - 12, 0x808080, false);
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
