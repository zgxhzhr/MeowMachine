package io.github.zgxhzhr.meowmachine.richtext;

import java.util.ArrayList;
import java.util.List;

/**
 * 富文本正文的文档模型。
 *
 * <p><b>只覆盖编辑者能用工具栏做出来的东西</b>：段落、粗体、斜体、无序列表、有序列表、表格。
 *
 * <p>标题不在模型里：原生 MC百科编辑器没有标题按钮，编辑者是在正文里**手打**
 * {@code [h2=标题]} 这种标记文字，站点渲染时才转成标题块。所以 {@code [hN=…]}
 * 在这里就是一个普通段落里的普通文字，不能被当成块级元素拆开——拆了就会改坏作者输入。
 *
 * <p>纯 Java、不依赖 Minecraft，方便用 {@code SelfTest} 直接跑解析/序列化往返。
 */
public final class RichDoc {

    /** 行内片段：一段文字 + 是否粗体/斜体 */
    public record Run(String text, boolean bold, boolean italic) {

        public static Run plain(String text) {
            return new Run(text == null ? "" : text, false, false);
        }
    }

    /** 块级元素（只有编辑器能产出的容器） */
    public sealed interface Block permits Para, BulletList, OrderedList, Table {
    }

    /** 普通段落（标题标记 {@code [hN=]} 也在这里，作为文字） */
    public record Para(List<Run> runs) implements Block {
        public Para {
            runs = List.copyOf(runs);
        }

        public String text() {
            return RichHtml.join(runs);
        }
    }

    /** 无序列表：每项是一组行内片段 */
    public record BulletList(List<List<Run>> items) implements Block {
        public BulletList {
            items = List.copyOf(items);
        }
    }

    /** 有序列表 */
    public record OrderedList(List<List<Run>> items) implements Block {
        public OrderedList {
            items = List.copyOf(items);
        }
    }

    /**
     * 表格。
     *
     * @param rows      行 × 列 × 行内片段；第一行按表头（{@code <th>}）输出
     * @param headerRow 第一行是否为表头
     */
    public record Table(List<List<List<Run>>> rows, boolean headerRow) implements Block {
        public Table {
            rows = List.copyOf(rows);
        }

        public int columns() {
            return rows.isEmpty() ? 0 : rows.get(0).size();
        }
    }

    private final List<Block> blocks;

    public RichDoc(List<Block> blocks) {
        this.blocks = List.copyOf(blocks);
    }

    public List<Block> blocks() {
        return blocks;
    }

    public boolean isEmpty() {
        return blocks.stream().allMatch(b -> b instanceof Para p && p.text().isEmpty());
    }

    /** 新建一份只有一个空段落的文档 */
    public static RichDoc empty() {
        List<Block> b = new ArrayList<>();
        b.add(new Para(List.of(Run.plain(""))));
        return new RichDoc(b);
    }

    /**
     * 纯文本（审核规则用：剥掉结构、按块断行）。
     *
     * <p>标题标记保持原样——{@code LocalRules} 的第 26 条就是靠它判标题结构的。
     */
    public String toPlainText() {
        StringBuilder sb = new StringBuilder();
        for (Block block : blocks) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            if (block instanceof Para p) {
                sb.append(p.text());
            } else if (block instanceof BulletList l) {
                for (List<Run> item : l.items()) {
                    sb.append('·').append(RichHtml.join(item)).append('\n');
                }
            } else if (block instanceof OrderedList l) {
                for (List<Run> item : l.items()) {
                    sb.append("1) ").append(RichHtml.join(item)).append('\n');
                }
            } else if (block instanceof Table t) {
                for (List<List<Run>> row : t.rows()) {
                    for (int i = 0; i < row.size(); i++) {
                        if (i > 0) {
                            sb.append(" | ");
                        }
                        sb.append(RichHtml.join(row.get(i)));
                    }
                    sb.append('\n');
                }
            }
        }
        return sb.toString();
    }
}
