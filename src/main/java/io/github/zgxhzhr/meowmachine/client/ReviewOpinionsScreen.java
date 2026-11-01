package io.github.zgxhzhr.meowmachine.client;

import io.github.zgxhzhr.meowmachine.network.ReviewListPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 审核意见汇总界面：**AI 意见 / 本地规则预检 / 助理意见** 三栏放在一起。
 *
 * <p>以前这些都塞在「审核详情」的正文上下，挤在一起看不清；现在详情页只管正文，
 * 意见用单独的按钮跳到这里看。
 */
public class ReviewOpinionsScreen extends Screen {

    private static final int PANEL_W = 380;
    private static final int LINE_H = 10;
    private static final int TOP = 44;

    private final ReviewListPacket.Entry entry;
    private final List<String> lines = new ArrayList<>();
    private int scroll = 0;
    private int viewHeight;

    public ReviewOpinionsScreen(ReviewListPacket.Entry entry) {
        super(Component.translatable("gui.meow_machine.opinions_title"));
        this.entry = entry;
    }

    @Override
    protected void init() {
        this.viewHeight = this.height - 34 - TOP - 4;
        lines.clear();

        String none = Component.translatable("gui.meow_machine.detail.none").getString();

        lines.add("—— " + Component.translatable(
                "gui.meow_machine.detail.ai_opinions").getString() + " ——");
        if (entry.aiOpinions().isEmpty()) {
            lines.add(none);
        } else {
            for (String o : entry.aiOpinions()) {
                lines.addAll(wrap(o, PANEL_W - 16));
            }
        }
        lines.add("");

        lines.add("—— " + Component.translatable(
                "gui.meow_machine.detail.local_rules").getString() + " ——");
        List<io.github.zgxhzhr.meowmachine.review.ReviewFinding> pre =
                io.github.zgxhzhr.meowmachine.review.LocalRules.check(entry.title(), entry.body());
        if (pre.isEmpty()) {
            lines.add(none);
        } else {
            for (var f : pre) {
                String text = "第 " + (f.ruleId() == null ? "?" : f.ruleId()) + " 条："
                        + (f.reason() == null ? "" : f.reason());
                lines.addAll(wrap(text, PANEL_W - 16));
            }
        }
        lines.add("");

        lines.add("—— " + Component.translatable(
                "gui.meow_machine.note_section").getString() + " ——");
        if (entry.notes().isEmpty()) {
            lines.add(none);
        } else {
            for (String note : entry.notes()) {
                lines.addAll(wrap(note, PANEL_W - 16));
            }
        }

        clampScroll();
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.meow_machine.close"), b -> this.onClose())
                .bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());
    }

    private List<String> wrap(String text, int maxWidth) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            out.add("");
            return out;
        }
        if (this.font.width(text) <= maxWidth) {
            out.add(text);
            return out;
        }
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (this.font.width(cur.toString() + ch) > maxWidth && cur.length() > 0) {
                out.add(cur.toString());
                cur.setLength(0);
            }
            cur.append(ch);
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        }
        return out;
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

        g.drawCenteredString(this.font, this.title, cx, 14, 0xFFFFFF);
        g.drawString(this.font,
                Component.literal(trim(entry.shortId() + "  " + entry.author(), PANEL_W))
                        .copy().append("  ").append(trim(entry.title(), 160)),
                left, 26, 0xFFFF80, false);

        g.fill(left - 1, TOP - 1, left + PANEL_W + 1, TOP + viewHeight + 1, 0xFF808080);
        g.fill(left, TOP, left + PANEL_W, TOP + viewHeight, 0xFF101010);

        int visible = viewHeight / LINE_H;
        for (int i = 0; i < visible && scroll + i < lines.size(); i++) {
            String line = lines.get(scroll + i);
            boolean header = line.startsWith("—— ");
            g.drawString(this.font, Component.literal(line), left + 8, TOP + 4 + i * LINE_H,
                    header ? 0xFFAA44 : 0xE0E0E0, false);
        }
        if (lines.size() > visible) {
            g.drawString(this.font,
                    Component.literal((int) Math.min(lines.size(), scroll + visible) + "/" + lines.size()),
                    left + PANEL_W - 40, TOP - 12, 0x808080, false);
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
