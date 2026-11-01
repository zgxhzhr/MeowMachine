package io.github.zgxhzhr.meowmachine.client;

import io.github.zgxhzhr.meowmachine.network.DecidePacket;
import io.github.zgxhzhr.meowmachine.network.Network;
import io.github.zgxhzhr.meowmachine.network.ReviewListPacket;
import io.github.zgxhzhr.meowmachine.network.ReviewListRequestPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 审核详情：**能看到投稿正文**，并就地裁决。
 *
 * <p>投稿正文现在可能是 HTML（富文本），这里显示的是服务端剥好标签的纯文本——
 * 审核判的是内容与格式，不需要在游戏里渲染富文本。
 */
public class ReviewDetailScreen extends Screen {

    private static final int PANEL_W = 380;
    private static final int LINE_H = 10;
    private static final int TOP = 56;

    private final ReviewListPacket.Entry entry;
    private final boolean canFinalize;
    private final boolean assistant;
    private final List<String> lines = new ArrayList<>();
    private int scroll = 0;
    private int viewHeight;
    /** 正文框顶部（有预检行时下移） */
    private int bodyTop = TOP;
    /** 顶部三栏摘要：AI 意见 / 本地规则 / 助理意见（正文框外） */
    private final java.util.List<String> headLines = new java.util.ArrayList<>();

    public ReviewDetailScreen(ReviewListPacket.Entry entry, boolean canFinalize, boolean assistant) {
        super(Component.translatable("gui.meow_machine.detail_title"));
        this.entry = entry;
        this.canFinalize = canFinalize;
        this.assistant = assistant;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int left = cx - PANEL_W / 2;
        int bottom = this.height - 34;
        this.viewHeight = bottom - TOP - 6;

        lines.clear();
        String body = entry.body() == null ? "" : entry.body();
        if (body.isBlank()) {
            lines.add(Component.translatable("gui.meow_machine.detail_empty").getString());
        } else {
            for (String raw : body.split("\n", -1)) {
                if (this.font.width(raw) <= PANEL_W - 16) {
                    lines.add(raw);
                    continue;
                }
                StringBuilder cur = new StringBuilder();
                for (int i = 0; i < raw.length(); i++) {
                    char ch = raw.charAt(i);
                    if (this.font.width(cur.toString() + ch) > PANEL_W - 16 && cur.length() > 0) {
                        lines.add(cur.toString());
                        cur.setLength(0);
                    }
                    cur.append(ch);
                }
                lines.add(cur.toString());
            }
        }

        if (!entry.notes().isEmpty()) {
            lines.add("");
            lines.add("—— " + Component.translatable("gui.meow_machine.note_section").getString() + " ——");
            for (String note : entry.notes()) {
                if (this.font.width(note) <= PANEL_W - 16) {
                    lines.add(note);
                    continue;
                }
                StringBuilder cur = new StringBuilder();
                for (int i = 0; i < note.length(); i++) {
                    char ch = note.charAt(i);
                    if (this.font.width(cur.toString() + ch) > PANEL_W - 16 && cur.length() > 0) {
                        lines.add(cur.toString());
                        cur.setLength(0);
                    }
                    cur.append(ch);
                }
                lines.add(cur.toString());
            }
        }

        // 顶部三栏摘要（AI 意见 / 本地规则预检 / 助理意见），显示在正文框外面
        headLines.clear();
        if (!entry.aiOpinions().isEmpty()) {
            headLines.add(Component.translatable("gui.meow_machine.detail.ai_opinions").getString()
                    + "：" + ruleSummary(entry.aiOpinions()));
        }
        java.util.List<io.github.zgxhzhr.meowmachine.review.ReviewFinding> pre =
                io.github.zgxhzhr.meowmachine.review.LocalRules.check(entry.title(), entry.body());
        if (!pre.isEmpty()) {
            java.util.List<String> ids = new java.util.ArrayList<>();
            for (var f : pre) {
                ids.add("第" + (f.ruleId() == null ? "?" : f.ruleId()) + "条");
            }
            headLines.add(Component.translatable(
                    "gui.meow_machine.detail.local_rules").getString() + "：" + String.join(" / ", ids));
        }
        if (!entry.notes().isEmpty()) {
            headLines.add(Component.translatable("gui.meow_machine.note_section").getString()
                    + "：" + entry.notes().size() + " 条");
        }
        this.bodyTop = TOP + Math.max(0, headLines.size() * 11);

        int bw = 84;
        int gap = 6;
        int by = this.height - 28;
        if (canFinalize) {
            int x0 = cx - (bw * 4 + gap * 3) / 2;
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
                            Component.translatable("gui.meow_machine.back"),
                            b -> Network.CHANNEL.sendToServer(new ReviewListRequestPacket()))
                    .bounds(x0 + (bw + gap) * 3, by, bw, 20).build());
        } else {
            int x0 = cx - (bw * 2 + gap) / 2;
            addRenderableWidget(Button.builder(
                            Component.translatable("gui.meow_machine.note_write"),
                            b -> this.minecraft.setScreen(new AddNoteScreen(entry)))
                    .bounds(x0, by, bw, 20).build());
            addRenderableWidget(Button.builder(
                            Component.translatable("gui.meow_machine.back"),
                            b -> Network.CHANNEL.sendToServer(new ReviewListRequestPacket()))
                    .bounds(x0 + bw + gap, by, bw, 20).build());
        }
        clampScroll();
    }

    private void decide(String verdict) {
        if ("PASS".equals(verdict)) {
            Network.CHANNEL.sendToServer(new DecidePacket(entry.fullId(), verdict, -1, ""));
            this.onClose();
            return;
        }
        if (this.minecraft != null) {
            this.minecraft.setScreen(new DecisionScreen(entry, verdict));
        }
    }

    private void clampScroll() {
        int max = Math.max(0, lines.size() - viewHeight / LINE_H);
        scroll = Math.max(0, Math.min(scroll, max));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll -= (int) Math.signum(delta) * 2;
        clampScroll();
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int cx = this.width / 2;
        int left = cx - PANEL_W / 2;

        g.drawCenteredString(this.font, this.title, cx, 12, 0xFFFFFF);
        g.drawString(this.font,
                Component.literal(entry.shortId() + "  " + entry.author()),
                left, 28, 0xFFFF80, false);
        g.drawString(this.font,
                Component.literal(trim(entry.title(), PANEL_W)), left, 40, 0xDDDDDD, false);

        for (int i = 0; i < headLines.size(); i++) {
            g.drawString(this.font, Component.literal(trim(headLines.get(i), PANEL_W)),
                    left, bodyTop - 11 * (headLines.size() - i), 0xFFAA44, false);
        }
        g.fill(left - 1, bodyTop - 1, left + PANEL_W + 1, bodyTop + viewHeight + 1, 0xFF808080);
        g.fill(left, bodyTop, left + PANEL_W, bodyTop + viewHeight, 0xFF101010);

        int visible = viewHeight / LINE_H;
        for (int i = 0; i < visible && scroll + i < lines.size(); i++) {
            g.drawString(this.font, Component.literal(lines.get(scroll + i)),
                    left + 8, bodyTop + 4 + i * LINE_H, 0xE0E0E0, false);
        }
        if (lines.size() > visible) {
            g.drawString(this.font,
                    Component.literal((int) Math.min(lines.size(), scroll + visible) + "/" + lines.size()),
                    left + PANEL_W - 40, bodyTop - 12, 0x808080, false);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    /** "第 7 条：xxx" 这类行只取条号做摘要 */
    private String ruleSummary(java.util.List<String> lines) {
        java.util.List<String> ids = new java.util.ArrayList<>();
        for (String line : lines) {
            int idx = line.indexOf("：");
            ids.add(idx > 0 ? line.substring(0, idx) : line);
        }
        return String.join(" / ", ids);
    }

    private String trim(String s, int maxWidth) {
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
