package io.github.zgxhzhr.meowmachine.client;

import io.github.zgxhzhr.meowmachine.network.MySubmissionsRequestPacket;
import io.github.zgxhzhr.meowmachine.network.Network;
import io.github.zgxhzhr.meowmachine.network.NewSubmissionPacket;
import io.github.zgxhzhr.meowmachine.network.OpenEditorPacket;
import io.github.zgxhzhr.meowmachine.network.SaveDraftPacket;
import io.github.zgxhzhr.meowmachine.network.SubmitPacket;
import io.github.zgxhzhr.meowmachine.richtext.RichDoc;
import io.github.zgxhzhr.meowmachine.richtext.RichEditor;
import io.github.zgxhzhr.meowmachine.richtext.RichHtml;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 投稿编辑界面：标题（原版 EditBox）+ **富文本正文**。
 *
 * <p>正文按原生 MC百科编辑器的输入层来：编辑者能做的只有五种操作——
 * 粗体、斜体、无序列表、有序列表、表格；标题是手打的 {@code [hN=…]} 文字，不是按钮。
 *
 * <p>渲染方式：把文档排成「字符盒」（每个字记录所属块/单元/偏移与屏幕坐标），
 * 用它做点选、拖选、选区高亮、光标与折行；粗斜体直接用 Minecraft 的
 * {@code Style.bold/italic} 渲染（原版字体本来就会画斜体，不用自己剪切）。
 */
public class SubmissionScreen extends Screen {

    private static final int PANEL_W = 320;
    private static final int TITLE_H = 18;
    private static final int LINE_H = 10;
    private static final int PAD = 4;
    private static final int TOOLBAR_H = 18;
    private static final int MIN_BODY_H = 60;

    private final OpenEditorPacket draft;
    private final boolean editable;
    private RichEditor editor;
    private EditBox titleBox;
    private boolean submitted = false;

    private int panelLeft;
    private int bodyTop;
    private int bodyH;
    private int contentHeight = 0;
    private int scrollY = 0;

    /** 排版缓存：字符盒 + 表格框（x, y, w, h, cols） */
    private final List<CharBox> boxes = new ArrayList<>();
    private final List<int[]> tableFrames = new ArrayList<>();

    /** 撤销栈（粗粒度：每次命令前存一份 HTML） */
    private final Deque<String> undoStack = new ArrayDeque<>();

    private record CharBox(int block, int cell, int offset, int x, int y, int w, char ch,
                           boolean bold, boolean italic) {
    }

    public SubmissionScreen(OpenEditorPacket draft) {
        super(Component.translatable("gui.meow_machine.submission"));
        this.draft = draft;
        this.editable = !"approved".equalsIgnoreCase(draft.status());
        this.editor = new RichEditor(RichHtml.parseLoose(draft.body()));
    }

    @Override
    protected void init() {
        ImeCompat.onTextScreenOpened();
        int cx = this.width / 2;
        int top = Math.max(44, Math.min(this.height / 4, 72));
        this.panelLeft = cx - PANEL_W / 2;

        this.titleBox = new EditBox(this.font, panelLeft, top, PANEL_W, TITLE_H,
                Component.translatable("gui.meow_machine.title_field"));
        this.titleBox.setMaxLength(Network.MAX_TITLE_LEN);
        this.titleBox.setValue(draft.title());
        this.titleBox.setHint(Component.translatable("gui.meow_machine.title_hint"));
        this.titleBox.setEditable(editable);
        this.addRenderableWidget(this.titleBox);

        // ---------- 工具栏：粗体 / 斜体 / 无序 / 有序 / 表格 ----------
        int toolbarY = top + TITLE_H + 4;
        int bw = 34;
        int gap = 2;
        String[] labels = {"B", "I", "•", "1.", "⊞"};
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            addRenderableWidget(Button.builder(Component.literal(labels[i]), b -> onToolbar(idx))
                    .bounds(panelLeft + i * (bw + gap), toolbarY, bw, TOOLBAR_H).build());
        }

        this.bodyTop = toolbarY + TOOLBAR_H + 4;

        // ---------- 底部四颗功能键 ----------
        int btnY = this.height - 30;
        this.bodyH = Math.max(MIN_BODY_H, btnY - 8 - bodyTop);
        int fw = 72;
        int fg = 4;
        int x0 = cx - (fw * 4 + fg * 3) / 2;
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.meow_machine.new_submission"), b -> startNew())
                .bounds(x0, btnY, fw, 20).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.meow_machine.submit"), b -> submit())
                .bounds(x0 + fw + fg, btnY, fw, 20).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.meow_machine.my_button"),
                        b -> Network.CHANNEL.sendToServer(new MySubmissionsRequestPacket()))
                .bounds(x0 + (fw + fg) * 2, btnY, fw, 20).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.meow_machine.cancel"), b -> this.onClose())
                .bounds(x0 + (fw + fg) * 3, btnY, fw, 20).build());

        this.layout();
    }

    // ---------------- 工具栏 ----------------

    private void onToolbar(int idx) {
        if (!editable) {
            return;
        }
        pushUndo();
        switch (idx) {
            case 0 -> editor.toggleBold();
            case 1 -> editor.toggleItalic();
            case 2 -> editor.toggleList(false);
            case 3 -> editor.toggleList(true);
            case 4 -> editor.insertTable(2, 3);
            default -> {
            }
        }
        layout();
    }

    private void pushUndo() {
        undoStack.push(RichHtml.toHtml(editor.doc()));
        while (undoStack.size() > 50) {
            undoStack.removeLast();
        }
    }

    private void undo() {
        if (undoStack.isEmpty()) {
            return;
        }
        editor = new RichEditor(RichHtml.parse(undoStack.pop()));
        layout();
    }

    // ---------------- 排版 ----------------

    private void layout() {
        boxes.clear();
        tableFrames.clear();
        int left = panelLeft + PAD;
        int right = panelLeft + PANEL_W - PAD;
        int maxW = right - left;
        int y = bodyTop + PAD;

        for (int b = 0; b < editor.blockCount(); b++) {
            String kind = editor.kindAt(b);
            List<List<RichDoc.Run>> cells = editor.cellsAt(b);
            if (kind.equals("para") || kind.equals("ul") || kind.equals("ol")) {
                for (int c = 0; c < cells.size(); c++) {
                    String prefix = kind.equals("ul") ? "• "
                            : kind.equals("ol") ? (c + 1) + ". " : "";
                    int indent = kind.equals("para") ? 0 : 12;
                    y = layoutCell(b, c, cells.get(c), left + indent, y, maxW - indent, prefix);
                }
            } else {
                int cols = Math.max(1, editor.colsAt(b));
                int cellW = maxW / cols;
                for (int r = 0; r * cols < cells.size(); r++) {
                    int rowTop = y;
                    int rowH = LINE_H + PAD;
                    for (int c = 0; c < cols && r * cols + c < cells.size(); c++) {
                        int cellX = left + c * cellW;
                        int consumed = layoutCell(b, r * cols + c, cells.get(r * cols + c),
                                cellX + 3, rowTop + 2, cellW - 6, "");
                        rowH = Math.max(rowH, consumed - rowTop + 2);
                    }
                    tableFrames.add(new int[]{left, rowTop, maxW, rowH, cols});
                    y = rowTop + rowH;
                }
            }
        }
        this.contentHeight = y - bodyTop + PAD;
        clampScroll();
    }

    /** 排一个单元（段落 / 列表项 / 表格单元格），返回下一行 y */
    private int layoutCell(int block, int cell, List<RichDoc.Run> runs,
                           int x0, int y0, int maxW, String prefix) {
        int x = x0;
        int y = y0;
        int right = x0 + maxW;
        int offsetBase = -prefix.length();
        int cursor = 0;

        if (!prefix.isEmpty()) {
            for (char ch : prefix.toCharArray()) {
                int w = this.font.width(String.valueOf(ch));
                boxes.add(new CharBox(block, cell, 0, x, y, w, ch, false, false));
                x += w;
            }
        }
        for (RichDoc.Run run : runs) {
            for (int i = 0; i < run.text().length(); i++) {
                char ch = run.text().charAt(i);
                if (ch == '\n') {
                    x = x0;
                    y += LINE_H;
                    continue;
                }
                String s = String.valueOf(ch);
                int w = this.font.width(s);
                if (x + w > right && x > x0) {
                    x = x0;
                    y += LINE_H;
                }
                boxes.add(new CharBox(block, cell, Math.max(0, cursor + offsetBase),
                        x, y, w, ch, run.bold(), run.italic()));
                x += w;
                cursor++;
            }
        }
        return y + LINE_H;
    }

    private void clampScroll() {
        int maxScroll = Math.max(0, contentHeight - bodyH);
        scrollY = Math.max(0, Math.min(scrollY, maxScroll));
    }

    private void scrollToCaret() {
        CharBox caretBox = caretBox();
        if (caretBox == null) {
            return;
        }
        int y = caretBox.y() - bodyTop;
        if (y < 0) {
            scrollY += y - PAD;
        } else if (y > bodyH - LINE_H) {
            scrollY += y - (bodyH - LINE_H) + PAD;
        }
        clampScroll();
    }

    // ---------------- 光标 / 命中 ----------------

    private CharBox caretBox() {
        RichEditor.Pos caret = editor.caret();
        CharBox last = null;
        for (CharBox b : boxes) {
            if (b.block() == caret.block() && b.cell() == caret.cell()) {
                if (b.offset() == caret.offset()) {
                    return b;
                }
                last = b;
                if (b.offset() > caret.offset()) {
                    return b;
                }
            }
        }
        return last;
    }

    private RichEditor.Pos posAt(double mx, double my) {
        int py = (int) my + scrollY;
        CharBox best = null;
        int bestDist = Integer.MAX_VALUE;
        for (CharBox b : boxes) {
            int dx = (int) Math.abs(mx - (b.x() + b.w() / 2.0));
            int dy = Math.abs(py - (b.y() + LINE_H / 2));
            int dist = dy * 400 + dx;
            if (dist < bestDist) {
                bestDist = dist;
                best = b;
            }
        }
        if (best == null) {
            return new RichEditor.Pos(0, 0, 0);
        }
        int off = mx > best.x() + best.w() / 2.0 ? best.offset() + 1 : best.offset();
        return new RichEditor.Pos(best.block(), best.cell(), off);
    }

    // ---------------- 渲染 ----------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int cx = this.width / 2;

        int titleY = Math.max(6, this.bodyTop - TITLE_H - TOOLBAR_H - 30);
        g.drawCenteredString(this.font, this.title, cx, titleY, 0xFFFFFF);
        g.drawCenteredString(this.font,
                Component.translatable("gui.meow_machine.status", draft.status()),
                cx, titleY + 12, 0xAAAAAA);

        super.render(g, mouseX, mouseY, partialTick);

        g.fill(panelLeft - 1, bodyTop - 1, panelLeft + PANEL_W + 1, bodyTop + bodyH + 1, 0xFF808080);
        g.fill(panelLeft, bodyTop, panelLeft + PANEL_W, bodyTop + bodyH, 0xFF000000);

        // 表格框
        for (int[] f : tableFrames) {
            int x = f[0];
            int y = f[1] - scrollY;
            int w = f[2];
            int h = f[3];
            int cols = f[4];
            g.fill(x, y, x + w, y + 1, 0xFF707070);
            g.fill(x, y + h - 1, x + w, y + h, 0xFF707070);
            g.fill(x, y, x + 1, y + h, 0xFF707070);
            g.fill(x + w - 1, y, x + w, y + h, 0xFF707070);
            for (int c = 1; c < cols; c++) {
                int vx = x + c * (w / cols);
                g.fill(vx, y, vx + 1, y + h, 0xFF707070);
            }
        }

        // 选区高亮
        RichEditor.Pos a = editor.anchor();
        RichEditor.Pos b = editor.caret();
        RichEditor.Pos lo = a.compareTo(b) <= 0 ? a : b;
        RichEditor.Pos hi = a.compareTo(b) <= 0 ? b : a;
        if (!lo.equals(hi)) {
            for (CharBox box : boxes) {
                RichEditor.Pos p = new RichEditor.Pos(box.block(), box.cell(), box.offset());
                if (p.compareTo(lo) >= 0 && p.compareTo(hi) < 0) {
                    int py = box.y() - scrollY;
                    if (py >= bodyTop && py < bodyTop + bodyH) {
                        g.fill(box.x(), py, box.x() + box.w(), py + LINE_H, 0x66FFFFFF);
                    }
                }
            }
        }

        // 绘制文本：同一行、同一样式、位置连续的字符合成一次绘制（省 draw call）
        int i = 0;
        while (i < boxes.size()) {
            CharBox first = boxes.get(i);
            int py = first.y() - scrollY;
            if (py < bodyTop - LINE_H || py > bodyTop + bodyH
                    || first.ch() == ' ' || first.ch() == '\n') {
                i++;
                continue;
            }
            StringBuilder sb = new StringBuilder().append(first.ch());
            int j = i + 1;
            while (j < boxes.size()) {
                CharBox prev = boxes.get(j - 1);
                CharBox cur = boxes.get(j);
                if (cur.y() != first.y() || cur.bold() != first.bold()
                        || cur.italic() != first.italic()
                        || cur.ch() == ' ' || cur.ch() == '\n'
                        || cur.x() != prev.x() + prev.w()) {
                    break;
                }
                sb.append(cur.ch());
                j++;
            }
            Style style = Style.EMPTY.withBold(first.bold()).withItalic(first.italic());
            g.drawString(this.font, Component.literal(sb.toString()).withStyle(style),
                    first.x(), py, 0xFFFFFF, false);
            i = j;
        }

        // 光标
        CharBox caret = caretBox();
        if (caret != null && (System.currentTimeMillis() / 500L) % 2 == 0) {
            int py = caret.y() - scrollY;
            if (py >= bodyTop && py < bodyTop + bodyH) {
                g.fill(caret.x(), py, caret.x() + 1, py + LINE_H, 0xFFFFFFFF);
            }
        }
    }

    // ---------------- 输入 ----------------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (editable && inBody(mx, my)) {
            titleBox.setFocused(false);
            this.setFocused(null);
            ImeCompat.wantIme(true);
            editor.setCaret(posAt(mx, my));
            layout();
            scrollToCaret();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (editable && inBody(mx, my)) {
            editor.select(editor.anchor(), posAt(mx, my));
            layout();
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    private boolean inBody(double mx, double my) {
        return mx >= panelLeft && mx <= panelLeft + PANEL_W
                && my >= bodyTop && my <= bodyTop + bodyH;
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (!editable) {
            return false;
        }
        if (titleBox != null && titleBox.isFocused()) {
            return super.charTyped(c, modifiers);
        }
        if (Character.isISOControl(c)) {
            return true;
        }
        pushUndo();
        editor.typeText(String.valueOf(c));
        layout();
        scrollToCaret();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (titleBox != null && titleBox.isFocused()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (!editable) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        boolean shift = Screen.hasShiftDown();

        if (Screen.hasControlDown()) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_B -> {
                    pushUndo();
                    editor.toggleBold();
                    layout();
                    return true;
                }
                case GLFW.GLFW_KEY_I -> {
                    pushUndo();
                    editor.toggleItalic();
                    layout();
                    return true;
                }
                case GLFW.GLFW_KEY_V -> {
                    pushUndo();
                    String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
                    if (clip != null && !clip.isEmpty()) {
                        editor.typeText(RichHtml.stripTags(clip)
                                .replace("\r\n", "\n").replace('\r', '\n'));
                    }
                    layout();
                    scrollToCaret();
                    return true;
                }
                case GLFW.GLFW_KEY_C -> {
                    Minecraft.getInstance().keyboardHandler.setClipboard(editor.doc().toPlainText());
                    return true;
                }
                case GLFW.GLFW_KEY_A -> {
                    editor.selectAll();
                    layout();
                    return true;
                }
                case GLFW.GLFW_KEY_Z -> {
                    undo();
                    return true;
                }
                default -> {
                }
            }
        }

        switch (keyCode) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                pushUndo();
                editor.enter();
                layout();
                scrollToCaret();
                return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                pushUndo();
                editor.backspace();
                layout();
                scrollToCaret();
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                pushUndo();
                editor.deleteForward();
                layout();
                scrollToCaret();
                return true;
            }
            case GLFW.GLFW_KEY_TAB -> {
                boolean moved = shift ? editor.moveToPrevCell() : editor.moveToNextCell();
                if (moved) {
                    layout();
                    scrollToCaret();
                }
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                RichEditor.Pos from = editor.caret();
                editor.moveLeft();
                if (shift) {
                    editor.select(from, editor.caret());
                }
                layout();
                scrollToCaret();
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                RichEditor.Pos from = editor.caret();
                editor.moveRight();
                if (shift) {
                    editor.select(from, editor.caret());
                }
                layout();
                scrollToCaret();
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                RichEditor.Pos c = editor.caret();
                editor.setCaret(new RichEditor.Pos(c.block(), c.cell(), 0));
                layout();
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                RichEditor.Pos c = editor.caret();
                int len = RichHtml.join(editor.runsAt(c.block(), c.cell())).length();
                editor.setCaret(new RichEditor.Pos(c.block(), c.cell(), len));
                layout();
                return true;
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                this.onClose();
                return true;
            }
            default -> {
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ---------------- 提交 / 关闭 ----------------

    private void submit() {
        if (!editable) {
            return;
        }
        Network.CHANNEL.sendToServer(new SubmitPacket(titleBox.getValue(), bodyHtml()));
        this.submitted = true;
        this.onClose();
    }

    private void startNew() {
        Network.CHANNEL.sendToServer(new NewSubmissionPacket());
    }

    private String bodyHtml() {
        String html = RichHtml.toHtml(editor.doc());
        return html.length() <= Network.MAX_BODY_LEN ? html : html.substring(0, Network.MAX_BODY_LEN);
    }

    @Override
    public void onClose() {
        if (!submitted && editable) {
            Network.CHANNEL.sendToServer(new SaveDraftPacket(titleBox.getValue(), bodyHtml()));
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        ImeCompat.onTextScreenClosed();
        super.removed();
    }
}
