package io.github.zgxhzhr.meowmachine.client;

import io.github.zgxhzhr.meowmachine.network.Network;
import io.github.zgxhzhr.meowmachine.network.NewSubmissionPacket;
import io.github.zgxhzhr.meowmachine.network.OpenEditorPacket;
import io.github.zgxhzhr.meowmachine.network.SaveDraftPacket;
import io.github.zgxhzhr.meowmachine.network.SubmitPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * 投稿编辑界面：标题（单行 EditBox）+ 正文（**自绘多行输入区**）。
 *
 * <p>为什么正文不用 EditBox：MC 1.20.1 的 {@code EditBox} 是单行控件，
 * 文字垂直居中、且会过滤掉换行符。正文需要换行，只能自己维护缓冲区并逐行绘制。
 *
 * <p>本版新增：
 * <ul>
 *   <li><b>正文光标</b>：左右移动 / Home / End / Delete / 上下跨行 / 鼠标点击定位，
 *       光标闪一下，输入不再只能在末尾追加（0.1.0 的老限制）。</li>
 *   <li><b>「新建投稿」按钮</b>：不再被一份草稿锁死一辈子。</li>
 *   <li><b>关界面暂存草稿</b>（0.2.0 加入）与<b>按钮贴底</b>（矮窗口不出屏）。</li>
 * </ul>
 */
public class SubmissionScreen extends Screen {

    private static final int PANEL_W = 300;
    private static final int TITLE_H = 18;
    private static final int LINE_H = 10;
    private static final int INNER_PAD = 4;
    private static final int MIN_BODY_H = 60;
    private static final int MAX_BODY_H = 150;
    /** 光标闪烁周期（毫秒，半个周期亮/灭） */
    private static final long CARET_BLINK_MS = 500L;

    private final OpenEditorPacket draft;
    private EditBox titleBox;

    /** 正文缓冲区（原文，含换行） */
    private final StringBuilder bodyText;

    /** 正文区是否获得焦点（点标题框会切回去） */
    private boolean bodyFocused = false;

    /** 正文滚动行数（内容超出可视区时） */
    private int bodyScroll = 0;

    /** 正文光标位置（bodyText 的字符下标，0..length） */
    private int caret = 0;

    /** 本次是否点了「提交审核」——提交后关闭时不该再发草稿暂存 */
    private boolean submitted = false;

    /** 当前草稿是否还能编辑（已通过的稿子只能看） */
    private final boolean editable;

    private int panelLeft;
    private int bodyTop;
    private int bodyH;

    public SubmissionScreen(OpenEditorPacket draft) {
        super(Component.translatable("gui.meow_machine.submission"));
        this.draft = draft;
        this.bodyText = new StringBuilder(draft.body() == null ? "" : draft.body());
        this.editable = !"approved".equalsIgnoreCase(draft.status());
        this.caret = this.bodyText.length();
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        // 顶部区域：标题/状态两行文字，再往下才是输入区
        int top = Math.max(44, Math.min(this.height / 4, 72));

        this.panelLeft = cx - PANEL_W / 2;

        this.titleBox = new EditBox(this.font, panelLeft, top, PANEL_W, TITLE_H,
                Component.translatable("gui.meow_machine.title_field"));
        this.titleBox.setMaxLength(Network.MAX_TITLE_LEN);
        this.titleBox.setValue(draft.title());
        this.titleBox.setHint(Component.translatable("gui.meow_machine.title_hint"));
        this.titleBox.setEditable(editable);
        this.addRenderableWidget(this.titleBox);

        this.bodyTop = top + TITLE_H + 8;

        // 按钮贴底（留 30px），正文吃剩下的高度——这样任何 GUI scale 下按钮都在屏内
        int btnY = this.height - 30;
        int available = btnY - 8 - bodyTop;
        this.bodyH = Math.max(MIN_BODY_H, Math.min(MAX_BODY_H, available));

        // 三颗按钮并排：新建 / 提交 / 关闭
        int bw = 96;
        int gap = 6;
        int x0 = cx - (bw * 3 + gap * 2) / 2;
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.meow_machine.new_submission"), b -> startNew())
                .bounds(x0, btnY, bw, 20).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.meow_machine.submit"), b -> submit())
                .bounds(x0 + bw + gap, btnY, bw, 20).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.meow_machine.cancel"), b -> this.onClose())
                .bounds(x0 + (bw + gap) * 2, btnY, bw, 20).build());
    }

    private void submit() {
        if (!editable) {
            return;
        }
        Network.CHANNEL.sendToServer(new SubmitPacket(titleBox.getValue(), bodyText.toString()));
        this.submitted = true;
        this.onClose();
    }

    /** 丢当前草稿开新稿：服务端会回一个空的 OpenEditorPacket，客户端借此换界面 */
    private void startNew() {
        Network.CHANNEL.sendToServer(new NewSubmissionPacket());
    }

    /**
     * 关闭时暂存草稿。
     *
     * <p>「提交」路径关闭时不发（服务端已经存过 SUBMITTED 版本）；
     * 其它情况（ESC / 点关闭 / 点标题栏的叉）都把当前内容存成草稿。
     */
    @Override
    public void onClose() {
        if (!submitted && editable) {
            Network.CHANNEL.sendToServer(
                    new SaveDraftPacket(titleBox.getValue(), bodyText.toString()));
        }
        super.onClose();
    }

    // ---------------- 文本布局 ----------------

    /** 一行显示文本 + 它在 bodyText 里的起始下标 */
    private record Line(String text, int start) {
    }

    /** 把缓冲区按换行 + 宽度折成显示行，并记下每行对应的原文下标 */
    private List<Line> layout() {
        List<Line> out = new ArrayList<>();
        int maxWidth = PANEL_W - INNER_PAD * 2;
        int lineStart = 0;
        StringBuilder cur = new StringBuilder();

        for (int i = 0; i < bodyText.length(); i++) {
            char ch = bodyText.charAt(i);
            if (ch == '\n') {
                out.add(new Line(cur.toString(), lineStart));
                cur.setLength(0);
                lineStart = i + 1;
                continue;
            }
            if (cur.length() > 0 && this.font.width(cur.toString() + ch) > maxWidth) {
                out.add(new Line(cur.toString(), lineStart));
                lineStart = i;
                cur.setLength(0);
            }
            cur.append(ch);
        }
        out.add(new Line(cur.toString(), lineStart));
        return out;
    }

    /** 光标在第几个显示行 */
    private int caretLine(List<Line> lines) {
        for (int i = lines.size() - 1; i >= 0; i--) {
            if (lines.get(i).start() <= caret) {
                return i;
            }
        }
        return 0;
    }

    /** 光标在行内的列号（限制在行文本长度内） */
    private int caretColumn(Line line) {
        return Math.max(0, Math.min(caret - line.start(), line.text().length()));
    }

    private int visibleLines() {
        return Math.max(1, (bodyH - INNER_PAD * 2) / LINE_H);
    }

    /** 保证光标所在行在可视区内 */
    private void scrollToCaret() {
        List<Line> lines = layout();
        int lineIndex = caretLine(lines);
        int visible = visibleLines();
        bodyScroll = Math.max(0, Math.min(bodyScroll, Math.max(0, lines.size() - visible)));
        if (lineIndex < bodyScroll) {
            bodyScroll = lineIndex;
        } else if (lineIndex >= bodyScroll + visible) {
            bodyScroll = lineIndex - visible + 1;
        }
    }

    // ---------------- 渲染 ----------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int cx = this.width / 2;

        // 标题与状态：放在输入区上方，不压住输入框
        int titleY = Math.max(6, this.bodyTop - TITLE_H - 26);
        g.drawCenteredString(this.font, this.title, cx, titleY, 0xFFFFFF);
        g.drawCenteredString(this.font,
                Component.translatable("gui.meow_machine.status", draft.status()),
                cx, titleY + 12, 0xAAAAAA);

        super.render(g, mouseX, mouseY, partialTick);

        // 正文区边框（获得焦点时描亮边）
        int border = bodyFocused ? 0xFFFFFFFF : 0xFF808080;
        g.fill(panelLeft - 1, bodyTop - 1, panelLeft + PANEL_W + 1, bodyTop + bodyH + 1, border);
        g.fill(panelLeft, bodyTop, panelLeft + PANEL_W, bodyTop + bodyH, 0xFF000000);

        // 正文文本：逐行画，超出可视区时按 bodyScroll 截断
        List<Line> lines = layout();
        int visible = visibleLines();
        int start = Math.min(bodyScroll, Math.max(0, lines.size() - visible));
        for (int i = 0; i < visible && start + i < lines.size(); i++) {
            g.drawString(this.font, Component.literal(lines.get(start + i).text()),
                    panelLeft + INNER_PAD, bodyTop + INNER_PAD + i * LINE_H,
                    bodyFocused ? 0xFFFFFF : 0xCCCCCC, false);
        }

        // 空正文时给个提示；不可编辑时给个说明
        if (lines.size() == 1 && lines.get(0).text().isEmpty()) {
            g.drawString(this.font, Component.translatable(
                            editable ? "gui.meow_machine.body_hint" : "gui.meow_machine.body_readonly"),
                    panelLeft + INNER_PAD, bodyTop + INNER_PAD, 0x707070, false);
        }

        // 光标：只画在可视区内，并且闪一下
        if (bodyFocused && editable && (System.currentTimeMillis() / CARET_BLINK_MS) % 2 == 0) {
            int lineIndex = caretLine(lines);
            if (lineIndex >= start && lineIndex < start + visible) {
                Line line = lines.get(lineIndex);
                int col = caretColumn(line);
                int caretX = panelLeft + INNER_PAD + this.font.width(line.text().substring(0, col));
                int caretY = bodyTop + INNER_PAD + (lineIndex - start) * LINE_H;
                g.fill(caretX, caretY - 1, caretX + 1, caretY + this.font.lineHeight, 0xFFFFFFFF);
            }
        }
    }

    // ---------------- 输入 ----------------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // 点正文区：获得焦点并把光标移到点击位置
        boolean inBody = mx >= panelLeft && mx <= panelLeft + PANEL_W
                && my >= bodyTop && my <= bodyTop + bodyH;
        if (inBody && editable) {
            bodyFocused = true;
            titleBox.setFocused(false);
            this.setFocused(null);
            moveCaretToClick(mx, my);
            return true;
        }
        if (mx >= panelLeft && mx <= panelLeft + PANEL_W
                && my >= titleBox.getY() && my <= titleBox.getY() + TITLE_H) {
            bodyFocused = false;
        }
        return super.mouseClicked(mx, my, button);
    }

    /** 鼠标点击 → 光标位置 */
    private void moveCaretToClick(double mx, double my) {
        List<Line> lines = layout();
        int visible = visibleLines();
        int start = Math.min(bodyScroll, Math.max(0, lines.size() - visible));
        int row = (int) ((my - bodyTop - INNER_PAD) / LINE_H);
        int lineIndex = Math.max(0, Math.min(lines.size() - 1, start + row));
        Line line = lines.get(lineIndex);

        int relX = (int) (mx - (panelLeft + INNER_PAD));
        int col = 0;
        while (col < line.text().length()
                && this.font.width(line.text().substring(0, col + 1)) <= Math.max(0, relX)) {
            col++;
        }
        caret = line.start() + col;
        scrollToCaret();
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (!editable) {
            return false;
        }
        if (bodyFocused && titleBox != null && !titleBox.isFocused()) {
            if (bodyText.length() < Network.MAX_BODY_LEN && !Character.isISOControl(c)) {
                bodyText.insert(caret, c);
                caret++;
                scrollToCaret();
            }
            return true;
        }
        return super.charTyped(c, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (bodyFocused && titleBox != null && !titleBox.isFocused() && editable) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    if (caret > 0) {
                        bodyText.deleteCharAt(caret - 1);
                        caret--;
                        scrollToCaret();
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_DELETE -> {
                    if (caret < bodyText.length()) {
                        bodyText.deleteCharAt(caret);
                        scrollToCaret();
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    if (bodyText.length() < Network.MAX_BODY_LEN) {
                        bodyText.insert(caret, '\n');
                        caret++;
                        scrollToCaret();
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_LEFT -> {
                    if (caret > 0) {
                        caret--;
                        scrollToCaret();
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_RIGHT -> {
                    if (caret < bodyText.length()) {
                        caret++;
                        scrollToCaret();
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_HOME -> {
                    List<Line> homeLines = layout();
                    caret = homeLines.get(caretLine(homeLines)).start();
                    scrollToCaret();
                    return true;
                }
                case GLFW.GLFW_KEY_END -> {
                    List<Line> lines = layout();
                    Line line = lines.get(caretLine(lines));
                    caret = line.start() + line.text().length();
                    scrollToCaret();
                    return true;
                }
                case GLFW.GLFW_KEY_UP -> {
                    moveCaretVertically(-1);
                    return true;
                }
                case GLFW.GLFW_KEY_DOWN -> {
                    moveCaretVertically(1);
                    return true;
                }
                case GLFW.GLFW_KEY_ESCAPE -> {
                    this.onClose();
                    return true;
                }
                default -> {
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** 上下跨显示行移动光标，尽量保持列号 */
    private void moveCaretVertically(int delta) {
        List<Line> lines = layout();
        int lineIndex = caretLine(lines);
        int col = caretColumn(lines.get(lineIndex));
        int target = Math.max(0, Math.min(lines.size() - 1, lineIndex + delta));
        Line targetLine = lines.get(target);
        caret = targetLine.start() + Math.min(col, targetLine.text().length());
        scrollToCaret();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
