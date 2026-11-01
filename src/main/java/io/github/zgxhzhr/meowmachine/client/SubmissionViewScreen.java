package io.github.zgxhzhr.meowmachine.client;

import io.github.zgxhzhr.meowmachine.network.MySubmissionsPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 只读查看自己的某篇投稿。
 *
 * <p>「我的投稿」里已通过的稿子是只读的（不能再编辑），但**得能看内容**——
 * 以前通过后既不能改也点不开，等于查无此人。
 */
public class SubmissionViewScreen extends Screen {

    private static final int PANEL_W = 380;
    private static final int LINE_H = 10;
    private static final int TOP = 56;

    private final MySubmissionsPacket.Entry entry;
    private final List<String> lines = new ArrayList<>();
    private int scroll = 0;
    private int viewHeight;

    public SubmissionViewScreen(MySubmissionsPacket.Entry entry) {
        super(Component.translatable("gui.meow_machine.view_title"));
        this.entry = entry;
    }

    @Override
    protected void init() {
        int bottom = this.height - 34;
        this.viewHeight = bottom - TOP - 6;
        lines.clear();
        String body = entry.body() == null ? "" : entry.body();
        if (body.isBlank()) {
            lines.add(Component.translatable("gui.meow_machine.view_empty").getString());
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
        clampScroll();

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.meow_machine.close"), b -> this.onClose())
                .bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());
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
                Component.literal(entry.shortId() + "  " + trim(entry.title(), PANEL_W - 60)),
                left, 28, 0xFFFF80, false);
        g.drawString(this.font,
                Component.translatable("gui.meow_machine.state." + entry.state()),
                left + PANEL_W - 60, 28, 0xAAAAAA, false);

        g.fill(left - 1, TOP - 1, left + PANEL_W + 1, TOP + viewHeight + 1, 0xFF808080);
        g.fill(left, TOP, left + PANEL_W, TOP + viewHeight, 0xFF101010);

        int visible = viewHeight / LINE_H;
        for (int i = 0; i < visible && scroll + i < lines.size(); i++) {
            g.drawString(this.font, Component.literal(lines.get(scroll + i)),
                    left + 8, TOP + 4 + i * LINE_H, 0xE0E0E0, false);
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
