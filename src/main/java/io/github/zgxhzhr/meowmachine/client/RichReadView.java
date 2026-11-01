package io.github.zgxhzhr.meowmachine.client;

import io.github.zgxhzhr.meowmachine.richtext.RichDoc;
import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.List;

/**
 * 只读富文本排版：把 {@link RichDoc} 摊成「文字 + 边框线」两类绘制指令。
 *
 * <p>投稿端看到的是真实表格与列表，审核端也应当一致。以前审核详情只拿得到
 * 剥掉标签的纯文本，表格会被压成一串，审核员看不出排版。
 */
public final class RichReadView {

    private static final int LINE = 10;
    private static final int PAD = 4;
    private static final int TEXT = 0xE0E0E0;
    private static final int BORDER = 0xFF808080;
    private static final int HEADER = 0xFFD0C890;

    public record TextOp(int x, int y, String text, int color) {
    }

    public record BorderOp(int x1, int y1, int x2, int y2, int color) {
    }

    private final List<TextOp> texts = new ArrayList<>();
    private final List<BorderOp> borders = new ArrayList<>();
    private int height = 0;

    public List<TextOp> texts() {
        return texts;
    }

    public List<BorderOp> borders() {
        return borders;
    }

    public int height() {
        return height;
    }

    public static RichReadView layout(Font font, RichDoc doc, int left, int width) {
        RichReadView v = new RichReadView();
        int[] cursor = {0};
        for (RichDoc.Block block : doc.blocks()) {
            if (block instanceof RichDoc.Para p) {
                v.paragraph(font, left, width, p.text(), cursor);
            } else if (block instanceof RichDoc.BulletList l) {
                v.list(font, left, width, l.items(), false, cursor);
            } else if (block instanceof RichDoc.OrderedList l) {
                v.list(font, left, width, l.items(), true, cursor);
            } else if (block instanceof RichDoc.Table t) {
                v.table(font, left, width, t, cursor);
            }
            cursor[0] += 3;
        }
        v.height = cursor[0];
        return v;
    }

    private void paragraph(Font font, int left, int width, String text, int[] cursor) {
        if (text == null || text.isBlank()) {
            cursor[0] += LINE / 2;
            return;
        }
        for (String line : wrap(font, text, width)) {
            texts.add(new TextOp(left, cursor[0], line, TEXT));
            cursor[0] += LINE;
        }
    }

    private void list(Font font, int left, int width, List<List<RichDoc.Run>> items,
                      boolean ordered, int[] cursor) {
        int idx = 1;
        for (List<RichDoc.Run> runs : items) {
            String prefix = ordered ? (idx++) + ". " : "\u2022 ";
            int indent = font.width(prefix) + 2;
            List<String> lines = wrap(font, join(runs), width - indent - 4);
            if (lines.isEmpty()) {
                lines.add("");
            }
            texts.add(new TextOp(left + 4, cursor[0], prefix, HEADER));
            for (int i = 0; i < lines.size(); i++) {
                texts.add(new TextOp(left + indent + 6, cursor[0] + i * LINE, lines.get(i), TEXT));
            }
            cursor[0] += lines.size() * LINE + 1;
        }
    }

    private void table(Font font, int left, int width, RichDoc.Table table, int[] cursor) {
        if (table.rows().isEmpty()) {
            return;
        }
        int cols = 0;
        for (List<List<RichDoc.Run>> row : table.rows()) {
            cols = Math.max(cols, row.size());
        }
        if (cols == 0) {
            return;
        }
        int tableLeft = left + 4;
        int avail = width - 8;
        int[] natural = new int[cols];
        for (List<List<RichDoc.Run>> row : table.rows()) {
            for (int c = 0; c < row.size(); c++) {
                natural[c] = Math.max(natural[c], font.width(join(row.get(c))) + PAD * 2);
            }
        }
        int sum = 0;
        for (int n : natural) {
            sum += Math.max(n, 20);
        }
        int[] colW = new int[cols];
        for (int c = 0; c < cols; c++) {
            colW[c] = Math.max(24, avail * Math.max(natural[c], 20) / Math.max(1, sum));
        }
        int[] colX = new int[cols];
        int x = tableLeft;
        for (int c = 0; c < cols; c++) {
            colX[c] = x;
            x += colW[c];
        }
        int right = x;

        int rowTop = cursor[0];
        for (int r = 0; r < table.rows().size(); r++) {
            List<List<RichDoc.Run>> row = table.rows().get(r);
            boolean header = table.headerRow() && r == 0;
            List<List<String>> cellLines = new ArrayList<>();
            int rowH = LINE + PAD;
            for (int c = 0; c < cols; c++) {
                String text = c < row.size() ? join(row.get(c)) : "";
                List<String> ls = wrap(font, text, colW[c] - PAD * 2);
                cellLines.add(ls);
                rowH = Math.max(rowH, ls.size() * LINE + PAD);
            }
            borders.add(new BorderOp(tableLeft, rowTop, right, rowTop, BORDER));
            for (int c = 0; c < cols; c++) {
                List<String> ls = cellLines.get(c);
                for (int i = 0; i < ls.size(); i++) {
                    texts.add(new TextOp(colX[c] + PAD, rowTop + PAD / 2 + i * LINE, ls.get(i),
                            header ? HEADER : TEXT));
                }
            }
            rowTop += rowH;
        }
        borders.add(new BorderOp(tableLeft, rowTop, right, rowTop, BORDER));
        for (int c = 0; c < cols; c++) {
            borders.add(new BorderOp(colX[c], cursor[0], colX[c], rowTop, BORDER));
        }
        borders.add(new BorderOp(right, cursor[0], right, rowTop, BORDER));
        cursor[0] = rowTop + 4;
    }

    private static String join(List<RichDoc.Run> runs) {
        StringBuilder sb = new StringBuilder();
        for (RichDoc.Run r : runs) {
            sb.append(r.text());
        }
        return sb.toString();
    }

    private static List<String> wrap(Font font, String text, int width) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        int safe = Math.max(20, width);
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\n') {
                out.add(cur.toString());
                cur.setLength(0);
                continue;
            }
            if (font.width(cur.toString() + ch) > safe && cur.length() > 0) {
                out.add(cur.toString());
                cur.setLength(0);
            }
            cur.append(ch);
        }
        out.add(cur.toString());
        return out;
    }
}
