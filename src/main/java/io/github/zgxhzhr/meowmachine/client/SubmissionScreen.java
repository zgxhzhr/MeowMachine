package io.github.zgxhzhr.meowmachine.client;

import io.github.zgxhzhr.meowmachine.network.Network;
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
 * <p>本次修的两个坑：
 * <ul>
 *   <li><b>关界面不再丢稿</b>：以前只有「提交审核」会写草稿，写一半按 ESC 全没。
 *       现在关闭时发一次 {@link SaveDraftPacket} 暂存。</li>
 *   <li><b>按钮不再出屏</b>：以前正文区固定 150px，1080p 全屏（GUI scale 4，逻辑高 270）时
 *       提交按钮底边到 283px，被裁在屏幕外。现在正文高度按可用空间收缩，按钮贴底。</li>
 * </ul>
 *
 * <p>正文光标仍然固定在末尾（第一版限制，未在本次改动范围内）。
 */
public class SubmissionScreen extends Screen {

    private static final int PANEL_W = 300;
    private static final int TITLE_H = 18;
    private static final int LINE_H = 10;
    private static final int INNER_PAD = 4;
    private static final int MIN_BODY_H = 60;
    private static final int MAX_BODY_H = 150;

    private final OpenEditorPacket draft;
    private EditBox titleBox;

    /** 正文缓冲区（按 \n 分行） */
    private final StringBuilder bodyText;

    /** 正文区是否获得焦点（点标题框会切回去） */
    private boolean bodyFocused = false;

    /** 正文滚动行数（内容超出可视区时） */
    private int bodyScroll = 0;

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

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.meow_machine.submit"), b -> submit())
                .bounds(cx - 102, btnY, 100, 20).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.meow_machine.cancel"), b -> this.onClose())
                .bounds(cx + 2, btnY, 100, 20).build());
    }

    private void submit() {
        if (!editable) {
            return;
        }
        Network.CHANNEL.sendToServer(new SubmitPacket(titleBox.getValue(), bodyText.toString()));
        this.submitted = true;
        this.onClose();
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
        List<String> lines = wrappedLines();
        int visible = (bodyH - INNER_PAD * 2) / LINE_H;
        int start = Math.min(bodyScroll, Math.max(0, lines.size() - visible));
        for (int i = 0; i < visible && start + i < lines.size(); i++) {
            g.drawString(this.font, Component.literal(lines.get(start + i)),
                    panelLeft + INNER_PAD, bodyTop + INNER_PAD + i * LINE_H,
                    bodyFocused ? 0xFFFFFF : 0xCCCCCC, false);
        }

        // 空正文时给个提示；不可编辑时给个说明
        if (lines.isEmpty() || (lines.size() == 1 && lines.get(0).isEmpty())) {
            g.drawString(this.font, Component.translatable(
                            editable ? "gui.meow_machine.body_hint" : "gui.meow_machine.body_readonly"),
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
                && my >= bodyTop && my <= bodyTop + bodyH;
        if (inBody && editable) {
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
        if (!editable) {
            return false;
        }
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
        if (bodyFocused && titleBox != null && !titleBox.isFocused() && editable) {
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
        int visible = (bodyH - INNER_PAD * 2) / LINE_H;
        int total = wrappedLines().size();
        bodyScroll = Math.max(0, total - visible);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
