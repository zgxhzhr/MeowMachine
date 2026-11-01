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
    /** 正文超过上限时只提示一次 */
    private boolean truncatedNotified = false;

    private int panelLeft;
    private int bodyTop;
    private int bodyH;
    private int contentHeight = 0;
    private int scrollY = 0;

    /** 排版缓存：字符盒 + 表格框（x, y, w, h, cols） */
    private final List<CharBox> boxes = new ArrayList<>();
    private final List<int[]> tableFrames = new ArrayList<>();
    /** 每个单元（段落/列表项/表格单元格）的屏幕矩形，用于点击命中 */
    private final List<CellRect> cellRects = new ArrayList<>();
    /** 列表前缀这类装饰文字（不参与光标/选区） */
    private final List<Deco> decorations = new ArrayList<>();

    /** 撤销栈（粗粒度：每次命令前存一份 HTML） */

    private record CharBox(int block, int cell, int offset, int x, int y, int w, char ch,
                           boolean bold, boolean italic) {
    }

    /** 单元矩形：x/y/w/h 以及正文起始 x（列表项要跳过前缀宽度） */
    private record CellRect(int block, int cell, int x, int y, int w, int h, int textX) {
    }

    /** 装饰文字（列表前缀） */
    private record Deco(int x, int y, String text) {
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

        String keepTitle = this.titleBox == null ? draft.title() : this.titleBox.getValue();
        this.titleBox = new EditBox(this.font, panelLeft, top, PANEL_W, TITLE_H,
                Component.translatable("gui.meow_machine.title_field"));
        this.titleBox.setMaxLength(Network.MAX_TITLE_LEN);
        // 窗口大小变化/从插入表格界面返回都会重新 init：保留玩家已经打的标题
        this.titleBox.setValue(keepTitle);
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
        if (idx != 4) {
            pushUndo();
        }
        switch (idx) {
            case 0 -> editor.toggleBold();
            case 1 -> editor.toggleItalic();
            case 2 -> editor.toggleList(false);
            case 3 -> editor.toggleList(true);
            case 4 -> {
                pushUndo();
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new TableInsertScreen(this, (r, c, h) -> {
                        editor.insertTable(r, c, h);
                        layout();
                    }));
                }
            }
            default -> {
            }
        }
        layout();
    }

    /** 撤销快照：连光标一起存，撤销后回到原位置而不是跳回文首 */
    private record Snapshot(String html, RichEditor.Pos caret) {
    }

    private final java.util.Deque<Snapshot> undoStack = new java.util.ArrayDeque<>();
    private long lastUndoAt = 0L;

    private void pushUndo() {
        pushUndo(false);
    }

    /** @param coalesce true = 连续打字，短时间内只入栈一次 */
    private void pushUndo(boolean coalesce) {
        long now = System.currentTimeMillis();
        if (coalesce && now - lastUndoAt < 700) {
            lastUndoAt = now;
            return;
        }
        lastUndoAt = now;
        undoStack.push(new Snapshot(RichHtml.toHtml(editor.doc()), editor.caret()));
        while (undoStack.size() > 100) {
            undoStack.removeLast();
        }
    }

    private void undo() {
        if (undoStack.isEmpty()) {
            return;
        }
        Snapshot snap = undoStack.pop();
        editor = new RichEditor(RichHtml.parse(snap.html()));
        editor.setCaret(snap.caret());
        layout();
    }

    // ---------------- 排版 ----------------

    private void layout() {
        boxes.clear();
        tableFrames.clear();
        cellRects.clear();
        decorations.clear();
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
                    int rectFrom = cellRects.size();
                    for (int c = 0; c < cols && r * cols + c < cells.size(); c++) {
                        int cellX = left + c * cellW;
                        int consumed = layoutCell(b, r * cols + c, cells.get(r * cols + c),
                                cellX + 3, rowTop + 2, cellW - 6, "");
                        rowH = Math.max(rowH, consumed - rowTop + 2);
                    }
                    // 一行里只要有单元格折行，整行都会变高；把本行各单元格的点击矩形补齐，
                    // 否则空单元格只有 10px 高，点下半截会落到别的格子（实测问题）
                    for (int i = rectFrom; i < cellRects.size(); i++) {
                        CellRect old = cellRects.get(i);
                        cellRects.set(i, new CellRect(old.block(), old.cell(), old.x(), old.y(),
                                old.w(), Math.max(old.h(), rowH - 2), old.textX()));
                    }
                    tableFrames.add(new int[]{left, rowTop, maxW, rowH, cols});
                    y = rowTop + rowH;
                }
            }
        }
        this.contentHeight = y - bodyTop + PAD;
        clampScroll();
    }

    /**
     * 排一个单元（段落 / 列表项 / 表格单元格），返回下一行 y。
     *
     * <p>列表前缀画成装饰，不进入字符盒——否则光标/选区会被前缀错位一格。
     * 每个单元都登记一个矩形，空单元也能被点中。
     */
    private int layoutCell(int block, int cell, List<RichDoc.Run> runs,
                           int x0, int y0, int maxW, String prefix) {
        int prefixW = prefix.isEmpty() ? 0 : this.font.width(prefix);
        if (!prefix.isEmpty()) {
            decorations.add(new Deco(x0, y0, prefix));
        }
        int textX = x0 + prefixW;
        int x = textX;
        int y = y0;
        int right = x0 + maxW;
        int cursor = 0;
        int lines = 1;

        boolean firstChar = true;
        for (RichDoc.Run run : runs) {
            for (int i = 0; i < run.text().length(); i++) {
                char ch = run.text().charAt(i);
                if (ch == '\n') {
                    if (firstChar) {
                        continue; // 开头的换行不该把正文挤到第二行
                    }
                    x = textX;
                    y += LINE_H;
                    lines++;
                    continue;
                }
                firstChar = false;
                int w = this.font.width(String.valueOf(ch));
                if (x + w > right && x > textX) {
                    x = textX;
                    y += LINE_H;
                    lines++;
                }
                boxes.add(new CharBox(block, cell, cursor, x, y, w, ch, run.bold(), run.italic()));
                x += w;
                cursor++;
            }
        }
        int height = Math.max(LINE_H, lines * LINE_H);
        cellRects.add(new CellRect(block, cell, x0, y0, maxW, height, textX));
        return y + LINE_H;
    }

    private void clampScroll() {
        int maxScroll = Math.max(0, contentHeight - bodyH);
        scrollY = Math.max(0, Math.min(scrollY, maxScroll));
    }

    private void scrollToCaret() {
        int[] caret = caretPoint();
        if (caret == null) {
            return;
        }
        int y = caret[1] - bodyTop;
        if (y < 0) {
            scrollY += y - PAD;
        } else if (y > bodyH - LINE_H) {
            scrollY += y - (bodyH - LINE_H) + PAD;
        }
        clampScroll();
    }

    // ---------------- 光标 / 命中 ----------------

    /** 光标屏幕坐标（解析式：不依赖“恰好有那个字符盒”） */
    private int[] caretPoint() {
        RichEditor.Pos caret = editor.caret();
        CellRect rect = null;
        int lastX = -1;
        int lastY = -1;
        int lastOffset = -1;
        for (CellRect r : cellRects) {
            if (r.block() == caret.block() && r.cell() == caret.cell()) {
                rect = r;
                break;
            }
        }
        if (rect == null) {
            return null;
        }
        for (CharBox b : boxes) {
            if (b.block() != caret.block() || b.cell() != caret.cell() || b.offset() < 0) {
                continue;
            }
            if (b.offset() >= caret.offset()) {
                return new int[]{b.x(), b.y()};
            }
            if (b.offset() > lastOffset) {
                lastX = b.x() + b.w();
                lastY = b.y();
                lastOffset = b.offset();
            }
        }
        if (lastX >= 0) {
            return new int[]{lastX, lastY};
        }
        return new int[]{rect.textX(), rect.y()};
    }

    private RichEditor.Pos posAt(double mx, double my) {
        int py = (int) my + scrollY;
        // 先看落在哪个单元矩形里（空单元格、空段落也能点中）
        for (CellRect r : cellRects) {
            if (mx < r.x() || mx > r.x() + r.w() || py < r.y() || py >= r.y() + r.h()) {
                continue;
            }
            // 关键：只在**被点中的那一行**里算偏移，否则点下面一行会按上一行的字算
            int lineY = r.y() + Math.max(0, (py - r.y()) / LINE_H) * LINE_H;
            int offset = 0;
            boolean any = false;
            for (CharBox b : boxes) {
                if (b.block() != r.block() || b.cell() != r.cell() || b.offset() < 0) {
                    continue;
                }
                if (b.y() != lineY) {
                    continue;
                }
                any = true;
                if (mx < b.x() + b.w() / 2.0) {
                    return new RichEditor.Pos(r.block(), r.cell(), b.offset());
                }
                offset = b.offset() + 1;
            }
            return new RichEditor.Pos(r.block(), r.cell(), any ? offset : 0);
        }
        // 兜底：就近吸附
        CharBox best = null;
        int bestDist = Integer.MAX_VALUE;
        for (CharBox b : boxes) {
            if (b.offset() < 0) {
                continue;
            }
            int dx = (int) Math.abs(mx - b.x());
            int dy = Math.abs(py - b.y());
            int dist = dy * 400 + dx;
            if (dist < bestDist) {
                bestDist = dist;
                best = b;
            }
        }
        if (best == null) {
            return new RichEditor.Pos(0, 0, 0);
        }
        return new RichEditor.Pos(best.block(), best.cell(),
                mx > best.x() + best.w() / 2.0 ? best.offset() + 1 : best.offset());
    }

    // ---------------- 渲染 ----------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int cx = this.width / 2;

        int titleY = Math.max(6, this.bodyTop - TITLE_H - TOOLBAR_H - 30);
        g.drawCenteredString(this.font, this.title, cx, titleY, 0xFFFFFF);
        g.drawCenteredString(this.font,
                Component.translatable("gui.meow_machine.status",
                        Component.translatable("gui.meow_machine.state."
                                + (draft.status() == null ? "draft" : draft.status().toLowerCase()))),
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
                    || first.offset() < 0 || first.ch() == ' ' || first.ch() == '\n') {
                i++;
                continue;
            }
            StringBuilder sb = new StringBuilder().append(first.ch());
            int j = i + 1;
            while (j < boxes.size()) {
                CharBox prev = boxes.get(j - 1);
                CharBox cur = boxes.get(j);
                if (cur.y() != first.y() || cur.bold() != first.bold()
                        || cur.italic() != first.italic() || cur.offset() < 0
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

        // 列表前缀
        for (Deco d : decorations) {
            int py = d.y() - scrollY;
            if (py >= bodyTop && py < bodyTop + bodyH) {
                g.drawString(this.font, Component.literal(d.text()), d.x(), py, 0xFFAAAAAA, false);
            }
        }

        // 光标
        int[] caret = caretPoint();
        if (caret != null && (System.currentTimeMillis() / 500L) % 2 == 0) {
            int py = caret[1] - scrollY;
            if (py >= bodyTop && py < bodyTop + bodyH) {
                g.fill(caret[0], py, caret[0] + 1, py + LINE_H, 0xFFFFFFFF);
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

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (inBody(mx, my) && contentHeight > bodyH) {
            scrollY -= (int) Math.signum(delta) * LINE_H * 2;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
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
        pushUndo(true);
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
                        editor.insertPlainText(RichHtml.stripTags(clip));
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
                RichEditor.Pos anchor = editor.anchor();
                editor.moveLeft();
                if (shift) {
                    // 已有选区时锚点保持不动；只有第一次按 Shift 才把锚点落在起点
                    editor.select(editor.hasSelection() ? anchor : from, editor.caret());
                }
                layout();
                scrollToCaret();
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                RichEditor.Pos from = editor.caret();
                RichEditor.Pos anchor = editor.anchor();
                editor.moveRight();
                if (shift) {
                    editor.select(editor.hasSelection() ? anchor : from, editor.caret());
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
        if (html.length() <= Network.MAX_BODY_LEN) {
            return html;
        }
        // 硬切会把标签切一半（`<td` 只剩开头），重开编辑器整张表消失（审查 P0-3）。
        // 这里回退到最后一个 '>'，保证切点落在标签边界，并提示一次。
        int cut = html.lastIndexOf('>', Network.MAX_BODY_LEN);
        if (cut < Network.MAX_BODY_LEN / 2) {
            cut = Network.MAX_BODY_LEN;
        }
        if (!truncatedNotified) {
            truncatedNotified = true;
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable(
                                "chat.meow_machine.body_truncated"), false);
            }
        }
        return html.substring(0, cut);
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
