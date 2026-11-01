package io.github.zgxhzhr.meowmachine.richtext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 富文本与站点正文 HTML 之间的解析 / 序列化。
 *
 * <p><b>只处理编辑者能用工具栏做出来的结构</b>：
 * <pre>
 * 段落    &lt;p&gt;…&lt;/p&gt;              （标题标记 [hN=] 只是段落里的文字）
 * 粗体    &lt;strong&gt;…&lt;/strong&gt;
 * 斜体    &lt;em&gt;…&lt;/em&gt;
 * 无序表  &lt;ul&gt;&lt;li&gt;&lt;p&gt;…&lt;/p&gt;&lt;/li&gt;&lt;/ul&gt;
 * 有序表  &lt;ol&gt;&lt;li&gt;&lt;p&gt;…&lt;/p&gt;&lt;/li&gt;&lt;/ol&gt;
 * 表格    &lt;table&gt;&lt;tbody&gt;&lt;tr&gt;&lt;th&gt;…&lt;/th&gt;&lt;/tr&gt;&lt;tr&gt;&lt;td valign="top"&gt;&lt;p&gt;…&lt;/p&gt;&lt;/td&gt;&lt;/tr&gt;&lt;/tbody&gt;&lt;/table&gt;
 * </pre>
 *
 * <p>不认识的标签一律剥掉、保留文字（粘贴外部内容时的清洗），{@code <br>} 变成换行。
 */
public final class RichHtml {

    private RichHtml() {
    }

    /**
     * 宽松解析：新稿是 HTML；0.8.x 及更早的旧草稿是纯文本。
     *
     * <p>纯文本按行拆成段落（标题标记照原样留在文字里），这样旧草稿打开不会变成一坨。
     */
    public static RichDoc parseLoose(String body) {
        if (body == null || body.isBlank()) {
            return RichDoc.empty();
        }
        // 必须带闭合的 '>'：像「表达式 a<p 时怎么办」这种正文里的字面量不能被当成 HTML，
        // 否则 parse 会从那里开始啃，把后半篇吃掉（审查 P0-9）
        boolean looksHtml = HAS_BLOCK_TAG.matcher(body).find();
        if (looksHtml) {
            return parse(body);
        }
        List<RichDoc.Block> blocks = new ArrayList<>();
        for (String line : body.split("\n", -1)) {
            blocks.add(new RichDoc.Para(List.of(RichDoc.Run.plain(line))));
        }
        return blocks.isEmpty() ? RichDoc.empty() : new RichDoc(blocks);
    }

    // ---------------- 行内 ----------------

    /** 真正的块级标签（带 '>'）才算 HTML */
    private static final java.util.regex.Pattern HAS_BLOCK_TAG = java.util.regex.Pattern.compile(
            "(?i)<(p|table|tbody|thead|tr|td|th|ul|ol|li|h[1-6]|div|br)\\s*/?>");

    /** 正文里是否有真正的列表/表格结构（用于区分「编辑器生成」与「粘贴压平」） */
    public static boolean hasStructure(String html) {
        return html != null && java.util.regex.Pattern
                .compile("(?i)<(table|ul|ol)\\b").matcher(html).find();
    }

    /**
     * 只取**段落**文字（不含列表项与表格单元格）。
     *
     * <p>本地规则第 12 条（句末标点）拿它判断：表格单元格与列表项本来就不以句号结尾，
     * 用全文判会 100% 误杀编辑器自己生成的正确内容（审查 P0-1）。
     */
    public static String proseText(String html) {
        StringBuilder sb = new StringBuilder();
        for (RichDoc.Block block : parseLoose(html).blocks()) {
            if (block instanceof RichDoc.Para p) {
                if (!p.text().isBlank()) {
                    sb.append(p.text()).append('\n');
                }
            }
        }
        return sb.toString().strip();
    }

    /** 拼接行内片段的纯文本 */
    public static String join(List<RichDoc.Run> runs) {
        StringBuilder sb = new StringBuilder();
        for (RichDoc.Run r : runs) {
            sb.append(r.text());
        }
        return sb.toString();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String inlineHtml(List<RichDoc.Run> runs) {
        StringBuilder sb = new StringBuilder();
        for (RichDoc.Run r : runs) {
            String text = r.text();
            if (text.isEmpty()) {
                continue;
            }
            String html = escape(text).replace("\n", "<br>");
            if (!text.isBlank()) {
                if (r.bold()) {
                    html = "<strong>" + html + "</strong>";
                }
                if (r.italic()) {
                    html = "<em>" + html + "</em>";
                }
            }
            sb.append(html);
        }
        return sb.toString();
    }

    // ---------------- 序列化 ----------------

    public static String toHtml(RichDoc doc) {
        StringBuilder sb = new StringBuilder();
        for (RichDoc.Block block : doc.blocks()) {
            if (block instanceof RichDoc.Para p) {
                sb.append("<p>").append(inlineHtml(p.runs())).append("</p>");
            } else if (block instanceof RichDoc.BulletList l) {
                sb.append("<ul>");
                for (List<RichDoc.Run> item : l.items()) {
                    sb.append("<li><p>").append(inlineHtml(item)).append("</p></li>");
                }
                sb.append("</ul>");
            } else if (block instanceof RichDoc.OrderedList l) {
                sb.append("<ol>");
                for (List<RichDoc.Run> item : l.items()) {
                    sb.append("<li><p>").append(inlineHtml(item)).append("</p></li>");
                }
                sb.append("</ol>");
            } else if (block instanceof RichDoc.Table t) {
                sb.append("<table><tbody>");
                for (int r = 0; r < t.rows().size(); r++) {
                    sb.append("<tr>");
                    boolean header = t.headerRow() && r == 0;
                    for (List<RichDoc.Run> cell : t.rows().get(r)) {
                        if (header) {
                            sb.append("<th>").append(inlineHtml(cell)).append("</th>");
                        } else {
                            sb.append("<td valign=\"top\"><p>").append(inlineHtml(cell)).append("</p></td>");
                        }
                    }
                    sb.append("</tr>");
                }
                sb.append("</tbody></table>");
            }
        }
        return sb.toString();
    }

    // ---------------- 解析 ----------------

    /** 解析正文 HTML；不认识的标签剥掉但保留文字 */
    public static RichDoc parse(String html) {        if (html == null || html.isBlank()) {
            return RichDoc.empty();
        }
        String s = html.trim();
        List<RichDoc.Block> blocks = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            int pStart = indexOfIgnoreCase(s, "<p", i);
            int ulStart = indexOfIgnoreCase(s, "<ul", i);
            int olStart = indexOfIgnoreCase(s, "<ol", i);
            int tableStart = indexOfIgnoreCase(s, "<table", i);
            int next = minPositive(pStart, ulStart, olStart, tableStart);

            if (next < 0) {
                String rest = stripTags(s.substring(i)).trim();
                if (!rest.isEmpty()) {
                    blocks.add(new RichDoc.Para(List.of(RichDoc.Run.plain(rest))));
                }
                break;
            }
            if (next > i) {
                String between = stripTags(s.substring(i, next)).trim();
                if (!between.isEmpty()) {
                    blocks.add(new RichDoc.Para(List.of(RichDoc.Run.plain(between))));
                }
            }
            if (next == pStart) {
                int end = closeTag(s, next, "p");
                if (end < 0) {
                    appendTail(blocks, s, next);
                    break;
                }
                blocks.add(new RichDoc.Para(parseInline(s.substring(s.indexOf('>', next) + 1, end))));
                i = end + 4;
            } else if (next == ulStart || next == olStart) {
                String tag = next == ulStart ? "ul" : "ol";
                int end = closeTag(s, next, tag);
                if (end < 0) {
                    appendTail(blocks, s, next);
                    break;
                }
                blocks.add(parseList(s.substring(s.indexOf('>', next) + 1, end), tag.equals("ol")));
                i = end + 3 + tag.length();
            } else {
                int end = closeTag(s, next, "table");
                if (end < 0) {
                    appendTail(blocks, s, next);
                    break;
                }
                blocks.add(parseTable(s.substring(s.indexOf('>', next) + 1, end)));
                i = end + 8;
            }
        }
        return blocks.isEmpty() ? RichDoc.empty() : new RichDoc(blocks);
    }

    /** 未闭合块：把从这里到结尾的文字保下来，宁可结构不完美也不能吞正文 */
    private static void appendTail(List<RichDoc.Block> blocks, String s, int from) {
        int gt = s.indexOf('>', from);
        String rest = stripTags(gt < 0 ? s.substring(from) : s.substring(gt + 1)).trim();
        if (!rest.isEmpty()) {
            blocks.add(new RichDoc.Para(List.of(RichDoc.Run.plain(rest))));
        }
    }

    private static RichDoc.Block parseList(String inner, boolean ordered) {
        List<List<RichDoc.Run>> items = new ArrayList<>();
        for (String li : allInner(inner, "li")) {
            items.add(parseInline(li));
        }
        if (items.isEmpty()) {
            items.add(List.of(RichDoc.Run.plain("")));
        }
        return ordered ? new RichDoc.OrderedList(items) : new RichDoc.BulletList(items);
    }

    private static RichDoc.Table parseTable(String inner) {
        List<List<List<RichDoc.Run>>> rows = new ArrayList<>();
        boolean header = false;
        for (String tr : allInner(inner, "tr")) {
            List<List<RichDoc.Run>> cells = new ArrayList<>();
            for (String th : allInner(tr, "th")) {
                header = true;
                cells.add(parseInline(th));
            }
            for (String td : allInner(tr, "td")) {
                cells.add(parseInline(td));
            }
            if (!cells.isEmpty()) {
                rows.add(cells);
            }
        }
        if (rows.isEmpty()) {
            rows.add(List.of(List.of(RichDoc.Run.plain(""))));
        }
        return new RichDoc.Table(rows, header);
    }

    /** 行内解析：识别 strong/b 与 em/i，其余标签剥掉但保留文字 */
    static List<RichDoc.Run> parseInline(String html) {
        List<RichDoc.Run> runs = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        boolean bold = false;
        boolean italic = false;

        int i = 0;
        while (i < html.length()) {
            char c = html.charAt(i);
            if (c == '<') {
                int end = html.indexOf('>', i);
                if (end < 0) {
                    break;
                }
                String tag = html.substring(i + 1, end).trim().toLowerCase(Locale.ROOT);
                boolean closing = tag.startsWith("/");
                String name = (closing ? tag.substring(1) : tag).split("\\s")[0];
                if (!buf.isEmpty()) {
                    runs.add(new RichDoc.Run(unescape(buf.toString()), bold, italic));
                    buf.setLength(0);
                }
                switch (name) {
                    case "strong", "b" -> bold = !closing;
                    case "em", "i" -> italic = !closing;
                    case "br" -> buf.append('\n');
                    default -> {
                        // 其它标签（a/span/p/…）剥掉，文字保留
                    }
                }
                i = end + 1;
                continue;
            }
            buf.append(c);
            i++;
        }
        if (!buf.isEmpty()) {
            runs.add(new RichDoc.Run(unescape(buf.toString()), bold, italic));
        }
        if (runs.isEmpty()) {
            runs.add(RichDoc.Run.plain(""));
        }
        return runs;
    }

    // ---------------- 工具 ----------------

    private static int minPositive(int... v) {
        int best = -1;
        for (int x : v) {
            if (x >= 0 && (best < 0 || x < best)) {
                best = x;
            }
        }
        return best;
    }

    private static int indexOfIgnoreCase(String haystack, String needle, int from) {
        return haystack.toLowerCase(Locale.ROOT).indexOf(needle, from);
    }

    /** 找到 {@code <tag …>} 对应闭合标签的起始下标（-1 表示没有） */
    private static int closeTag(String s, int openIdx, String tag) {
        String open = "<" + tag;
        String close = "</" + tag + ">";
        int depth = 0;
        int i = openIdx;
        String lower = s.toLowerCase(Locale.ROOT);
        while (i < s.length()) {
            int nextOpen = lower.indexOf(open, i);
            int nextClose = lower.indexOf(close, i);
            if (nextClose < 0) {
                return -1;
            }
            if (nextOpen >= 0 && nextOpen < nextClose) {
                depth++;
                i = nextOpen + open.length();
            } else {
                depth--;
                if (depth == 0) {
                    return nextClose;
                }
                i = nextClose + close.length();
            }
        }
        return -1;
    }

    /** 取出所有同名标签的内部 HTML */
    private static List<String> allInner(String s, String tag) {
        List<String> out = new ArrayList<>();
        int i = 0;
        String lower = s.toLowerCase(Locale.ROOT);
        while (true) {
            int open = lower.indexOf("<" + tag, i);
            if (open < 0) {
                break;
            }
            int end = closeTag(s, open, tag);
            if (end < 0) {
                break;
            }
            out.add(s.substring(s.indexOf('>', open) + 1, end));
            i = end + tag.length() + 3;
        }
        return out;
    }

    /**
     * 给 AI 看的结构化纯文本：列表和表格**保留结构**。
     *
     * <p>{@link #stripTags} 会把列表/表格压成一行行文字，AI 就以为作者没建列表、
     * 甚至反过来建议「用编辑器列表重排」。这里用不会被 Markdown 检查误伤的符号：
     * 无序列表 {@code •}、有序列表 {@code 1、}、表格 {@code | 甲 | 乙 |}。
     */
    public static String toAiText(String html) {
        RichDoc doc = parse(html);
        StringBuilder sb = new StringBuilder();
        for (RichDoc.Block block : doc.blocks()) {
            if (block instanceof RichDoc.Para p) {
                sb.append(p.text()).append('\n');
            } else if (block instanceof RichDoc.BulletList l) {
                for (List<RichDoc.Run> item : l.items()) {
                    sb.append("• ").append(join(item)).append('\n');
                }
            } else if (block instanceof RichDoc.OrderedList l) {
                int i = 1;
                for (List<RichDoc.Run> item : l.items()) {
                    sb.append(i++).append("、").append(join(item)).append('\n');
                }
            } else if (block instanceof RichDoc.Table t) {
                for (List<List<RichDoc.Run>> row : t.rows()) {
                    sb.append("| ");
                    for (int i = 0; i < row.size(); i++) {
                        if (i > 0) {
                            sb.append(" | ");
                        }
                        sb.append(join(row.get(i)));
                    }
                    sb.append(" |\n");
                }
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** 去掉所有标签（保留 {@code <br>} 为换行） */
    public static String stripTags(String html) {
        if (html == null) {
            return "";
        }
        String withBreaks = html
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p\\s*>", "\n")
                .replaceAll("(?i)</li\\s*>", "\n")
                .replaceAll("(?i)</tr\\s*>", "\n")
                .replaceAll("(?i)</h[1-6]\\s*>", "\n");
        return unescape(withBreaks.replaceAll("<[^>]+>", ""));
    }

    private static String unescape(String s) {
        return s.replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&nbsp;", " ").replace("&amp;", "&");
    }
}
