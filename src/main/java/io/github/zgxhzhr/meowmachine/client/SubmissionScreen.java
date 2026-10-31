package io.github.zgxhzhr.meowmachine.client;

import io.github.zgxhzhr.meowmachine.network.Network;
import io.github.zgxhzhr.meowmachine.network.OpenEditorPacket;
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
 * <p>第一版的多行输入刻意做得简单：光标固定在末尾，支持输入、退格、回车和向上滚动，
 * 暂不支持鼠标定位与左右移动光标。够写一段稿子，后续再补。
 */
public class SubmissionScreen extends Screen {

    private static final int PANEL_W = 300;
    private static final int TITLE_H = 18;
    private static final int BODY_H = 150;
    private static final int LINE_H = 10;
    private static final int INNER_PAD = 4;

    private final OpenEditorPacket draft;
    private EditBox titleBox;

    /** 正文缓冲区（按 \n 分行） */
    private final StringBuilder bodyText;

    /** 正文区是否获得焦点（点标题框会切回去） */
    private boolean bodyFocused = false;

    /** 正文滚动行数（内容超出可视区时） */
    private int bodyScroll = 0;

    private int panelLeft;
    private int bodyTop;

    public SubmissionScreen(OpenEditorPacket draft) {
        super(Component.translatable("gui.meow_machine.submission"));
        this.draft = draft;
        this.bodyText = new StringBuilder(draft.body() == null ? "" : draft.body());
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        // 整体下移，给标题/状态文字留出空间（之前压在输入框上）
        int top = this.height / 4 + 12;
        this.panelLeft = cx - PANEL_W / 2;

        this.titleBox = new EditBox(this.font, panelLeft, top, PANEL_W, TITLE_H,
                Component.translatable("gui.meow_machine.title_field"));
        this.titleBox.setMaxLength(Network.MAX_TITLE_LEN);
        this.titleBox.setValue(draft.title());
        this.titleBox.setHint(Component.translatable("gui.meow_machine.title_hint"));
        this.addRenderableWidget(this.titleBox);

        this.bodyTop = top + TITLE_H + 8;

        int btnY = bodyTop + BODY_H + 8;
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.meow_machine.submit"), b -> submit())
                .bounds(cx - 102, btnY, 100, 20).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.meow_machine.cancel"), b -> this.onClose())
                .bounds(cx + 2, btnY, 100, 20).build());
    }

    private void submit() {
        Network.CHANNEL.sendToServer(new SubmitPacket(titleBox.getValue(), bodyText.toString()));
        this.onClose();
    }

    // ---------------- 渲染 ----------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int cx = this.width / 2;

        // 标题与状态：放到输入区**上方**，不再压住输入框
        g.drawCenteredString(this.font, this.title, cx, panelLeft == 0 ? 20 : this.height / 4 - 22, 0xFFFFFF);
        g.drawCenteredString(this.font,
                Component.translatable("gui.meow_machine.status", draft.status()),
                cx, this.height / 4 - 10, 0xAAAAAA);

        super.render(g, mouseX, mouseY, partialTick);

        // 正文区边框（获得焦点时描亮边）
        int border = bodyFocused ? 0xFFFFFFFF : 0xFF808080;
        g.fill(panelLeft - 1, bodyTop - 1, panelLeft + PANEL_W + 1, bodyTop + BODY_H + 1, border);
        g.fill(panelLeft, bodyTop, panelLeft + PANEL_W, bodyTop + BODY_H, 0xFF000000);

        // 正文文本：逐行画，超出可视区时按 bodyScroll 截断
        List<String> lines = wrappedLines();
        int visible = (BODY_H - INNER_PAD * 2) / LINE_H;
        int start = Math.min(bodyScroll, Math.max(0, lines.size() - visible));
        for (int i = 0; i < visible && start + i < lines.size(); i++) {
            g.drawString(this.font, Component.literal(lines.get(start + i)),
                    panelLeft + INNER_PAD, bodyTop + INNER_PAD + i * LINE_H,
                    bodyFocused ? 0xFFFFFF : 0xCCCCCC, false);
        }

        // 空正文时给个提示
        if (lines.isEmpty() || (lines.size() == 1 && lines.get(0).isEmpty())) {
            g.drawString(this.font, Component.translatable("gui.meow_machine.body_hint"),
                    panelLeft + INNER_PAD, bodyTop + INNER_PAD, 0x707070, false);
        }
    }

    /** 把缓冲区按换行 + 宽度折成显示行 */
    private List<String> wrappedLines() {
        String[] raw = bodyText.toString().split("\n", -1);
        List<String> out = new ArrayList<>();
        int maxWidth = PANEL_W - INNER_PAD * 2;
        for (String line : raw) {
            if (this.font.width(line) <= maxWidth) {
                out.add(line);
                continue;
            }
            // 按字符宽度折行
            StringBuilder cur = new StringBuilder();
            for (int i = 0; i < line.length(); i++) {
                char ch = line.charAt(i);
                if (this.font.width(cur.toString() + ch) > maxWidth) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
                cur.append(ch);
            }
            out.add(cur.toString());
        }
        return out;
    }

    // ---------------- 输入 ----------------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // 点正文区：获得焦点；点标题框：交回 EditBox
        boolean inBody = mx >= panelLeft && mx <= panelLeft + PANEL_W
                && my >= bodyTop && my <= bodyTop + BODY_H;
        if (inBody) {
            bodyFocused = true;
            titleBox.setFocused(false);
            this.setFocused(null);
            return true;
        }
        if (mx >= panelLeft && mx <= panelLeft + PANEL_W
                && my >= titleBox.getY() && my <= titleBox.getY() + TITLE_H) {
            bodyFocused = false;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (bodyFocused && titleBox != null && !titleBox.isFocused()) {
            if (bodyText.length() < Network.MAX_BODY_LEN && !Character.isISOControl(c)) {
                bodyText.append(c);
                scrollToBottom();
            }
            return true;
        }
        return super.charTyped(c, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (bodyFocused && titleBox != null && !titleBox.isFocused()) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    if (bodyText.length() > 0) {
                        bodyText.deleteCharAt(bodyText.length() - 1);
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    if (bodyText.length() < Network.MAX_BODY_LEN) {
                        bodyText.append('\n');
                        scrollToBottom();
                    }
                    return true;
                }
                case GLFW.GLFW_KEY_UP -> {
                    bodyScroll = Math.max(0, bodyScroll - 1);
                    return true;
                }
                case GLFW.GLFW_KEY_DOWN -> {
                    bodyScroll = bodyScroll + 1;
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

    private void scrollToBottom() {
        int visible = (BODY_H - INNER_PAD * 2) / LINE_H;
        int total = wrappedLines().size();
        bodyScroll = Math.max(0, total - visible);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
