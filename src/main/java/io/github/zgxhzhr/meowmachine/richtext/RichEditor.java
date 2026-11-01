package io.github.zgxhzhr.meowmachine.richtext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 富文本编辑器的**操作内核**（不含渲染）：按原生 MC百科编辑器的行为实现。
 *
 * <p>行为对齐点：
 * <ul>
 *   <li>粗体/斜体作用于**选区**；选区为空时切换「悬挂样式」，之后输入的字带上该样式；</li>
 *   <li>列表按钮把光标所在段落与列表项互转，多段选中时整段切换；</li>
 *   <li>列表里回车新建同级项，**空项回车退出列表**；</li>
 *   <li>表格插入后光标进第一格；单元格内回车是换行，{@code Tab} 由界面层处理；</li>
 *   <li>段首退格：与前一段合并（列表项在首项时退回段落）。</li>
 * </ul>
 *
 * <p>实现上把文档摊平成「单元」（段落一个单元、列表每项一个单元、表格每格一个单元），
 * 选区就是 (单元序号, 字符偏移) 的一段，所有命令都在这个线性空间里做，再映射回块结构。
 * 纯 Java、可脱机测试。
 */
public final class RichEditor {

    /** 单元在块里的种类 */
    private enum Kind { PARA, UL, OL, TABLE }

    /** 块的工作副本 */
    private static final class Block2 {
        Kind kind;
        /** 每个单元的行内内容；表格按行优先排列 */
        List<List<RichDoc.Run>> cells = new ArrayList<>();
        /** 仅表格使用 */
        int cols;
        boolean header;

        Block2(Kind kind) {
            this.kind = kind;
        }
    }

    /** 光标/选区端点 */
    public record Pos(int block, int cell, int offset) implements Comparable<Pos> {
        @Override
        public int compareTo(Pos o) {
            if (block != o.block) {
                return Integer.compare(block, o.block);
            }
            if (cell != o.cell) {
                return Integer.compare(cell, o.cell);
            }
            return Integer.compare(offset, o.offset);
        }
    }

    private final List<Block2> blocks = new ArrayList<>();
    private Pos caret;
    private Pos anchor;
    private boolean pendingBold;
    private boolean pendingItalic;

    public RichEditor(RichDoc doc) {
        load(doc);
    }

    public static RichEditor empty() {
        return new RichEditor(RichDoc.empty());
    }

    // ---------------- 载入 / 导出 ----------------

    private void load(RichDoc doc) {
        for (RichDoc.Block b : doc.blocks()) {
            if (b instanceof RichDoc.Para p) {
                // 兼容旧草稿：段落里如果夹着换行（以前粘贴/输入留下的），拆成多个段落
                for (List<RichDoc.Run> seg : splitOnNewlines(p.runs())) {
                    Block2 mb = new Block2(Kind.PARA);
                    mb.cells.add(new ArrayList<>(seg));
                    blocks.add(mb);
                }
            } else if (b instanceof RichDoc.BulletList l) {
                Block2 mb = new Block2(Kind.UL);
                for (List<RichDoc.Run> item : l.items()) {
                    mb.cells.add(new ArrayList<>(item));
                }
                blocks.add(mb);
            } else if (b instanceof RichDoc.OrderedList l) {
                Block2 mb = new Block2(Kind.OL);
                for (List<RichDoc.Run> item : l.items()) {
                    mb.cells.add(new ArrayList<>(item));
                }
                blocks.add(mb);
            } else if (b instanceof RichDoc.Table t) {
                Block2 mb = new Block2(Kind.TABLE);
                mb.cols = t.columns();
                mb.header = t.headerRow();
                for (List<List<RichDoc.Run>> row : t.rows()) {
                    for (List<RichDoc.Run> cell : row) {
                        mb.cells.add(new ArrayList<>(cell));
                    }
                }
                blocks.add(mb);
            }
        }
        if (blocks.isEmpty()) {
            Block2 p = new Block2(Kind.PARA);
            p.cells.add(new ArrayList<>(List.of(RichDoc.Run.plain(""))));
            blocks.add(p);
        }
        caret = new Pos(0, 0, 0);
        anchor = caret;
    }

    /** 把一行内片段按换行切成多段（保留样式） */
    private static List<List<RichDoc.Run>> splitOnNewlines(List<RichDoc.Run> runs) {
        List<List<RichDoc.Run>> out = new ArrayList<>();
        List<RichDoc.Run> cur = new ArrayList<>();
        for (RichDoc.Run r : runs) {
            StringBuilder buf = new StringBuilder();
            for (int i = 0; i < r.text().length(); i++) {
                char c = r.text().charAt(i);
                if (c == '\n') {
                    if (buf.length() > 0) {
                        cur.add(new RichDoc.Run(buf.toString(), r.bold(), r.italic()));
                        buf.setLength(0);
                    }
                    out.add(cur);
                    cur = new ArrayList<>();
                } else {
                    buf.append(c);
                }
            }
            if (buf.length() > 0) {
                cur.add(new RichDoc.Run(buf.toString(), r.bold(), r.italic()));
            }
        }
        out.add(cur);
        return out;
    }

    public RichDoc doc() {
        List<RichDoc.Block> out = new ArrayList<>();
        for (Block2 b : blocks) {
            switch (b.kind) {
                case PARA -> out.add(new RichDoc.Para(b.cells.get(0)));
                case UL -> out.add(new RichDoc.BulletList(b.cells));
                case OL -> out.add(new RichDoc.OrderedList(b.cells));
                case TABLE -> {
                    List<List<List<RichDoc.Run>>> rows = new ArrayList<>();
                    for (int i = 0; i < b.cells.size(); i += b.cols) {
                        rows.add(new ArrayList<>(b.cells.subList(i, Math.min(i + b.cols, b.cells.size()))));
                    }
                    out.add(new RichDoc.Table(rows, b.header));
                }
            }
        }
        return new RichDoc(out);
    }

    // ---------------- 光标 / 选区 ----------------

    public Pos caret() {
        return caret;
    }

    public Pos anchor() {
        return anchor;
    }

    public boolean hasSelection() {
        return !caret.equals(anchor);
    }

    public void setCaret(Pos p) {
        Pos c = clamp(p);
        this.caret = c;
        this.anchor = c;
    }

    public void select(Pos a, Pos b) {
        this.anchor = clamp(a);
        this.caret = clamp(b);
    }

    public void selectAll() {
        Pos last = lastPos();
        anchor = new Pos(0, 0, 0);
        caret = last;
    }

    public boolean pendingBold() {
        return pendingBold;
    }

    public boolean pendingItalic() {
        return pendingItalic;
    }

    private Pos clamp(Pos p) {
        int b = Math.max(0, Math.min(blocks.size() - 1, p.block()));
        Block2 blk = blocks.get(b);
        int c = Math.max(0, Math.min(blk.cells.size() - 1, p.cell()));
        int o = Math.max(0, Math.min(textOf(blk.cells.get(c)).length(), p.offset()));
        return new Pos(b, c, o);
    }

    private Pos lastPos() {
        int b = blocks.size() - 1;
        int c = blocks.get(b).cells.size() - 1;
        return new Pos(b, c, textOf(blocks.get(b).cells.get(c)).length());
    }

    private static String textOf(List<RichDoc.Run> runs) {
        return RichHtml.join(runs);
    }

    // ---------------- 行内样式 ----------------

    /** 粗体按钮：有选区就作用于选区，否则切换悬挂样式 */
    public void toggleBold() {
        if (!hasSelection()) {
            pendingBold = !pendingBold;
            return;
        }
        boolean allBold = allStyled(true);
        applyStyle(true, !allBold);
    }

    /** 斜体按钮 */
    public void toggleItalic() {
        if (!hasSelection()) {
            pendingItalic = !pendingItalic;
            return;
        }
        boolean allItalic = allStyled(false);
        applyStyle(false, !allItalic);
    }

    private boolean allStyled(boolean bold) {
        Pos a = minPos();
        Pos b = maxPos();
        for (Pos p = a; p.compareTo(b) < 0; p = next(p)) {
            Pos end = nextCharEnd(p);
            List<RichDoc.Run> runs = cellRuns(p);
            int from = Math.min(p.offset(), textOf(runs).length());
            String slice = textOf(runs).substring(from, Math.min(end.offset(), textOf(runs).length()));
            if (slice.isEmpty()) {
                continue;
            }
            boolean styled = sliceHasStyle(runs, from, slice.length(), bold);
            if (!styled) {
                return false;
            }
        }
        return true;
    }

    private static boolean sliceHasStyle(List<RichDoc.Run> runs, int from, int len, boolean bold) {
        int pos = 0;
        int end = from + len;
        for (RichDoc.Run r : runs) {
            int s = pos;
            int e = pos + r.text().length();
            pos = e;
            if (e <= from || s >= end) {
                continue;
            }
            boolean flag = bold ? r.bold() : r.italic();
            if (!flag) {
                return false;
            }
        }
        return true;
    }

    private void applyStyle(boolean bold, boolean value) {
        Pos a = minPos();
        Pos b = maxPos();
        for (Pos p = a; p.compareTo(b) < 0; p = next(p)) {
            Pos end = nextCharEnd(p);
            applyStyleInCell(p.block(), p.cell(), p.offset(), end.offset(), bold, value);
        }
        normalize();
    }

    private Pos next(Pos p) {
        return nextCharEnd(p);
    }

    /** 从 p 开始的一个字符之后的位置（跨单元时跳到下一单元起点） */
    private Pos nextCharEnd(Pos p) {
        Block2 blk = blocks.get(p.block());
        int len = textOf(blk.cells.get(p.cell())).length();
        if (p.offset() < len) {
            return new Pos(p.block(), p.cell(), p.offset() + 1);
        }
        if (p.cell() + 1 < blk.cells.size()) {
            return new Pos(p.block(), p.cell() + 1, 0);
        }
        if (p.block() + 1 < blocks.size()) {
            return new Pos(p.block() + 1, 0, 0);
        }
        return p;
    }

    /** 在某个单元的 [from,to) 上改样式 */
    private void applyStyleInCell(int block, int cell, int from, int to, boolean bold, boolean value) {
        Block2 blk = blocks.get(block);
        List<RichDoc.Run> runs = blk.cells.get(cell);
        String text = textOf(runs);
        from = Math.max(0, Math.min(from, text.length()));
        to = Math.max(from, Math.min(to, text.length()));
        if (from == to) {
            return;
        }
        List<RichDoc.Run> out = new ArrayList<>();
        int pos = 0;
        for (RichDoc.Run r : runs) {
            int s = pos;
            int e = pos + r.text().length();
            pos = e;
            if (e <= from || s >= to) {
                out.add(r);
                continue;
            }
            // 前缀
            if (s < from) {
                out.add(new RichDoc.Run(r.text().substring(0, from - s), r.bold(), r.italic()));
            }
            int os = Math.max(s, from);
            int oe = Math.min(e, to);
            String mid = r.text().substring(os - s, oe - s);
            out.add(new RichDoc.Run(mid,
                    bold ? value : r.bold(),
                    bold ? r.italic() : value));
            // 后缀
            if (e > to) {
                out.add(new RichDoc.Run(r.text().substring(to - s), r.bold(), r.italic()));
            }
        }
        blk.cells.set(cell, merge(out));
    }

    private static List<RichDoc.Run> merge(List<RichDoc.Run> runs) {
        List<RichDoc.Run> out = new ArrayList<>();
        for (RichDoc.Run r : runs) {
            if (r.text().isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                RichDoc.Run last = out.get(out.size() - 1);
                if (last.bold() == r.bold() && last.italic() == r.italic()) {
                    out.set(out.size() - 1,
                            new RichDoc.Run(last.text() + r.text(), r.bold(), r.italic()));
                    continue;
                }
            }
            out.add(r);
        }
        if (out.isEmpty()) {
            out.add(RichDoc.Run.plain(""));
        }
        return out;
    }

    private void normalize() {
        for (Block2 b : blocks) {
            for (int i = 0; i < b.cells.size(); i++) {
                b.cells.set(i, merge(b.cells.get(i)));
            }
        }
    }

    private List<RichDoc.Run> cellRuns(Pos p) {
        return blocks.get(p.block()).cells.get(p.cell());
    }

    private Pos minPos() {
        return caret.compareTo(anchor) <= 0 ? caret : anchor;
    }

    private Pos maxPos() {
        return caret.compareTo(anchor) <= 0 ? anchor : caret;
    }

    // ---------------- 输入 ----------------

    /** 输入文字（带上悬挂样式；有选区时先替换选区） */
    public void typeText(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        deleteSelection();
        Pos p = caret;
        Block2 blk = blocks.get(p.block());
        List<RichDoc.Run> runs = new ArrayList<>(blk.cells.get(p.cell()));
        List<RichDoc.Run> out = new ArrayList<>();
        int pos = 0;
        boolean inserted = false;
        for (RichDoc.Run r : runs) {
            int s = pos;
            int e = pos + r.text().length();
            pos = e;
            if (!inserted && p.offset() >= s && p.offset() <= e) {
                int cut = p.offset() - s;
                if (cut > 0) {
                    out.add(new RichDoc.Run(r.text().substring(0, cut), r.bold(), r.italic()));
                }
                out.add(new RichDoc.Run(text, pendingBold, pendingItalic));
                if (cut < r.text().length()) {
                    out.add(new RichDoc.Run(r.text().substring(cut), r.bold(), r.italic()));
                }
                inserted = true;
            } else {
                out.add(r);
            }
        }
        if (!inserted) {
            out.add(new RichDoc.Run(text, pendingBold, pendingItalic));
        }
        blk.cells.set(p.cell(), merge(out));
        caret = new Pos(p.block(), p.cell(), p.offset() + text.length());
        anchor = caret;
    }

    /**
     * 粘贴纯文本：**按行拆成段落**。
     *
     * <p>与原生编辑器一致：粘贴多行文本是一堆段落，不是「一段里塞换行」。
     * 不拆的话，整篇会变成同一个块——点列表按钮就只有一条，正是实测踩到的坑。
     */
    public void insertPlainText(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                enter();
            }
            if (!lines[i].isEmpty()) {
                typeText(lines[i]);
            }
        }
    }

    /** 退格：删一个字符；段首则与上一单元合并（列表首项退回段落） */
    public void backspace() {
        if (hasSelection()) {
            deleteSelection();
            return;
        }
        Pos p = caret;
        if (p.offset() > 0) {
            deleteRange(new Pos(p.block(), p.cell(), p.offset() - 1), p);
            caret = new Pos(p.block(), p.cell(), p.offset() - 1);
            anchor = caret;
            return;
        }
        // 段首
        Block2 blk = blocks.get(p.block());
        if (blk.kind == Kind.UL || blk.kind == Kind.OL) {
            if (p.cell() > 0) {
                // 合并到上一项
                mergeIntoPrevious(blk, p.cell());
            } else {
                // 首项退格 → 退出列表
                blocks.set(p.block(), paraFrom(blk.cells.get(0)));
                caret = new Pos(p.block(), 0, 0);
                anchor = caret;
            }
            return;
        }
        if (p.cell() > 0) {
            mergeIntoPrevious(blk, p.cell());
            return;
        }
        if (p.block() > 0) {
            Block2 prev = blocks.get(p.block() - 1);
            if (prev.kind == Kind.PARA) {
                int prevLen = textOf(prev.cells.get(0)).length();
                List<RichDoc.Run> joined = new ArrayList<>(prev.cells.get(0));
                joined.addAll(blk.cells.get(p.cell()));
                prev.cells.set(0, merge(joined));
                blocks.remove(p.block());
                caret = new Pos(p.block() - 1, 0, prevLen);
                anchor = caret;
            }
        }
    }

    private Block2 paraFrom(List<RichDoc.Run> runs) {
        Block2 mb = new Block2(Kind.PARA);
        mb.cells.add(new ArrayList<>(runs));
        return mb;
    }

    private void mergeIntoPrevious(Block2 blk, int cell) {
        int prevLen = textOf(blk.cells.get(cell - 1)).length();
        List<RichDoc.Run> joined = new ArrayList<>(blk.cells.get(cell - 1));
        joined.addAll(blk.cells.get(cell));
        blk.cells.set(cell - 1, merge(joined));
        blk.cells.remove(cell);
        caret = new Pos(blocks.indexOf(blk), cell - 1, prevLen);
        anchor = caret;
    }

    /** 删除光标后的字符（段尾则吞并下一单元/下一段） */
    public void deleteForward() {
        if (hasSelection()) {
            deleteSelection();
            return;
        }
        Pos p = caret;
        Block2 blk = blocks.get(p.block());
        String text = textOf(blk.cells.get(p.cell()));
        if (p.offset() < text.length()) {
            deleteRange(p, new Pos(p.block(), p.cell(), p.offset() + 1));
            return;
        }
        if (p.cell() + 1 < blk.cells.size()) {
            List<RichDoc.Run> joined = new ArrayList<>(blk.cells.get(p.cell()));
            joined.addAll(blk.cells.get(p.cell() + 1));
            blk.cells.set(p.cell(), merge(joined));
            blk.cells.remove(p.cell() + 1);
            return;
        }
        if (p.block() + 1 < blocks.size()) {
            Block2 next = blocks.get(p.block() + 1);
            if (next.kind == Kind.PARA && blk.kind == Kind.PARA) {
                List<RichDoc.Run> joined = new ArrayList<>(blk.cells.get(p.cell()));
                joined.addAll(next.cells.get(0));
                blk.cells.set(p.cell(), merge(joined));
                blocks.remove(p.block() + 1);
            }
        }
    }

    /** 回车：段落拆成两段；列表新建同级项（空项退出列表）；表格内换行 */
    public void enter() {
        if (hasSelection()) {
            deleteSelection();
        }
        Pos p = caret;
        Block2 blk = blocks.get(p.block());
        List<RichDoc.Run> runs = blk.cells.get(p.cell());

        if (blk.kind == Kind.TABLE) {
            // 单元格内换行
            List<RichDoc.Run> out = new ArrayList<>();
            int pos = 0;
            for (RichDoc.Run r : runs) {
                int s = pos;
                int e = pos + r.text().length();
                pos = e;
                if (p.offset() >= s && p.offset() <= e) {
                    int cut = p.offset() - s;
                    if (cut > 0) {
                        out.add(new RichDoc.Run(r.text().substring(0, cut), r.bold(), r.italic()));
                    }
                    out.add(new RichDoc.Run("\n", r.bold(), r.italic()));
                    if (cut < r.text().length()) {
                        out.add(new RichDoc.Run(r.text().substring(cut), r.bold(), r.italic()));
                    }
                } else {
                    out.add(r);
                }
            }
            blk.cells.set(p.cell(), merge(out));
            caret = new Pos(p.block(), p.cell(), p.offset() + 1);
            anchor = caret;
            return;
        }

        String flat = textOf(runs);
        boolean emptyItem = (blk.kind == Kind.UL || blk.kind == Kind.OL)
                && flat.trim().isEmpty();

        if (emptyItem) {
            // 空列表项回车 → 退出列表
            blocks.set(p.block(), paraFrom(List.of()));
            caret = new Pos(p.block(), 0, 0);
            anchor = caret;
            return;
        }

        // 在光标处拆开
        List<RichDoc.Run> before = new ArrayList<>();
        List<RichDoc.Run> after = new ArrayList<>();
        splitRuns(runs, p.offset(), before, after);

        if (blk.kind == Kind.PARA) {
            blk.cells.set(0, merge(before));
            Block2 next = new Block2(Kind.PARA);
            next.cells.add(new ArrayList<>(merge(after)));
            blocks.add(p.block() + 1, next);
            caret = new Pos(p.block() + 1, 0, 0);
        } else {
            blk.cells.set(p.cell(), merge(before));
            blk.cells.add(p.cell() + 1, new ArrayList<>(merge(after)));
            caret = new Pos(p.block(), p.cell() + 1, 0);
        }
        anchor = caret;
    }

    private static void splitRuns(List<RichDoc.Run> runs, int offset,
                                  List<RichDoc.Run> before, List<RichDoc.Run> after) {
        int pos = 0;
        for (RichDoc.Run r : runs) {
            int s = pos;
            int e = pos + r.text().length();
            pos = e;
            if (offset <= s) {
                after.add(r);
            } else if (offset >= e) {
                before.add(r);
            } else {
                int cut = offset - s;
                before.add(new RichDoc.Run(r.text().substring(0, cut), r.bold(), r.italic()));
                after.add(new RichDoc.Run(r.text().substring(cut), r.bold(), r.italic()));
            }
        }
        if (before.isEmpty()) {
            before.add(RichDoc.Run.plain(""));
        }
        if (after.isEmpty()) {
            after.add(RichDoc.Run.plain(""));
        }
    }

    private void deleteSelection() {
        if (!hasSelection()) {
            return;
        }
        Pos a = minPos();
        Pos b = maxPos();
        deleteRange(a, b);
        caret = a;
        anchor = a;
    }

    private void deleteRange(Pos a, Pos b) {
        int startBlock = a.block();
        int endBlock = b.block();
        if (startBlock == endBlock) {
            deleteInBlock(a, b);
            return;
        }
        // 跨块：先把首块尾与末块头接起来
        Block2 first = blocks.get(startBlock);
        Block2 last = blocks.get(endBlock);
        List<RichDoc.Run> head = new ArrayList<>();
        splitRuns(first.cells.get(a.cell()), a.offset(), head, new ArrayList<>());
        List<RichDoc.Run> tail = new ArrayList<>();
        splitRuns(last.cells.get(b.cell()), b.offset(), new ArrayList<>(), tail);
        first.cells.set(a.cell(), merge(head));
        last.cells.set(b.cell(), merge(tail));
        first.cells.get(a.cell()).addAll(last.cells.get(b.cell()));
        first.cells.set(a.cell(), merge(first.cells.get(a.cell())));
        for (int i = endBlock; i > startBlock; i--) {
            blocks.remove(i);
        }
        if (first.kind == Kind.PARA) {
            first.cells.set(0, merge(first.cells.get(0)));
        }
        caret = a;
        anchor = a;
    }

    private void deleteInBlock(Pos a, Pos b) {
        Block2 blk = blocks.get(a.block());
        if (a.cell() == b.cell()) {
            List<RichDoc.Run> runs = blk.cells.get(a.cell());
            List<RichDoc.Run> head = new ArrayList<>();
            List<RichDoc.Run> tail = new ArrayList<>();
            splitRuns(runs, a.offset(), head, new ArrayList<>());
            splitRuns(runs, b.offset(), new ArrayList<>(), tail);
            List<RichDoc.Run> joined = new ArrayList<>(head);
            joined.addAll(tail);
            blk.cells.set(a.cell(), merge(joined));
        }
    }

    // ---------------- 块操作 ----------------

    /** 列表按钮：把选区覆盖的段落 ↔ 列表互转 */
    public void toggleList(boolean ordered) {
        Kind target = ordered ? Kind.OL : Kind.UL;
        Pos a = minPos();
        Pos b = maxPos();
        int fromBlock = a.block();
        int toBlock = Math.min(blocks.size() - 1, b.block());

        boolean allTarget = true;
        for (int i = fromBlock; i <= toBlock; i++) {
            if (blocks.get(i).kind != target) {
                allTarget = false;
                break;
            }
        }

        if (allTarget) {
            // 撤销列表：每个列表块拆回段落
            for (int i = toBlock; i >= fromBlock; i--) {
                Block2 blk = blocks.get(i);
                if (blk.kind != target) {
                    continue;
                }
                List<Block2> paras = new ArrayList<>();
                for (List<RichDoc.Run> item : blk.cells) {
                    paras.add(paraFrom(item));
                }
                blocks.remove(i);
                blocks.addAll(i, paras);
            }
        } else {
            for (int i = fromBlock; i <= toBlock; i++) {
                Block2 blk = blocks.get(i);
                if (blk.kind == Kind.TABLE) {
                    continue;
                }
                if (blk.kind == Kind.PARA) {
                    blk.kind = target;
                } else {
                    blk.kind = target;
                }
            }
        }
        normalize();
        caret = clamp(caret);
        anchor = clamp(anchor);
    }

    /** 插入表格：光标所在块之后插入 rows×cols，光标进第一格 */
    public void insertTable(int rows, int cols) {
        int r = Math.max(1, rows);
        int c = Math.max(1, cols);
        Block2 table = new Block2(Kind.TABLE);
        table.cols = c;
        table.header = true;
        for (int i = 0; i < r * c; i++) {
            table.cells.add(new ArrayList<>(List.of(RichDoc.Run.plain(""))));
        }
        int at = Math.min(blocks.size(), caret.block() + 1);
        blocks.add(at, table);
        caret = new Pos(at, 0, 0);
        anchor = caret;
    }

    /** 表格内跳格（界面层 Tab 调用）：返回 false 表示已经是最后一格 */
    public boolean moveToNextCell() {
        Pos p = caret;
        Block2 blk = blocks.get(p.block());
        if (blk.kind != Kind.TABLE) {
            return false;
        }
        int nextCell = p.cell() + 1;
        if (nextCell >= blk.cells.size()) {
            return false;
        }
        caret = new Pos(p.block(), nextCell, 0);
        anchor = caret;
        return true;
    }

    /** 表格内上一格（Shift+Tab） */
    public boolean moveToPrevCell() {
        Pos p = caret;
        Block2 blk = blocks.get(p.block());
        if (blk.kind != Kind.TABLE || p.cell() == 0) {
            return false;
        }
        caret = new Pos(p.block(), p.cell() - 1, 0);
        anchor = caret;
        return true;
    }

    /** 光标左移一格（界面层方向键用） */
    public void moveLeft() {
        Pos p = caret;
        if (p.offset() > 0) {
            caret = new Pos(p.block(), p.cell(), p.offset() - 1);
        } else if (p.cell() > 0) {
            caret = new Pos(p.block(), p.cell() - 1,
                    textOf(blocks.get(p.block()).cells.get(p.cell() - 1)).length());
        } else if (p.block() > 0) {
            Block2 prev = blocks.get(p.block() - 1);
            caret = new Pos(p.block() - 1, prev.cells.size() - 1,
                    textOf(prev.cells.get(prev.cells.size() - 1)).length());
        }
        anchor = caret;
    }

    /** 光标右移一格 */
    public void moveRight() {
        Pos p = caret;
        Block2 blk = blocks.get(p.block());
        int len = textOf(blk.cells.get(p.cell())).length();
        if (p.offset() < len) {
            caret = new Pos(p.block(), p.cell(), p.offset() + 1);
        } else if (p.cell() + 1 < blk.cells.size()) {
            caret = new Pos(p.block(), p.cell() + 1, 0);
        } else if (p.block() + 1 < blocks.size()) {
            caret = new Pos(p.block() + 1, 0, 0);
        }
        anchor = caret;
    }

    /** 供界面层取某一单元（块、单元）的行内内容 */
    public List<RichDoc.Run> runsAt(int block, int cell) {
        if (block < 0 || block >= blocks.size()) {
            return Collections.singletonList(RichDoc.Run.plain(""));
        }
        Block2 blk = blocks.get(block);
        if (cell < 0 || cell >= blk.cells.size()) {
            return Collections.singletonList(RichDoc.Run.plain(""));
        }
        return Collections.unmodifiableList(blk.cells.get(cell));
    }

    /** 单元数量（行内渲染用） */
    public int blockCount() {
        return blocks.size();
    }

    /** 某块的种类名（渲染用：para/ul/ol/table） */
    public String kindAt(int block) {
        if (block < 0 || block >= blocks.size()) {
            return "para";
        }
        return switch (blocks.get(block).kind) {
            case PARA -> "para";
            case UL -> "ul";
            case OL -> "ol";
            case TABLE -> "table";
        };
    }

    /** 某块的列数（表格渲染用） */
    public int colsAt(int block) {
        return block >= 0 && block < blocks.size() ? blocks.get(block).cols : 0;
    }

    /** 某块的单元数 */
    public int cellCountAt(int block) {
        return block >= 0 && block < blocks.size() ? blocks.get(block).cells.size() : 0;
    }

    /** 块内行内内容（供渲染，按单元顺序） */
    public List<List<RichDoc.Run>> cellsAt(int block) {
        if (block < 0 || block >= blocks.size()) {
            return List.of();
        }
        return Collections.unmodifiableList(blocks.get(block).cells);
    }
}
