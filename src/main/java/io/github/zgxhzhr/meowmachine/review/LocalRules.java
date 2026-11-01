package io.github.zgxhzhr.meowmachine.review;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 本地规则：**不需要 AI，纯代码就能判的条款**。
 *
 * <p>规则编号与措辞沿用 MC百科的公共编辑规范（1~35 条），这样审核意见可以直接引条号。
 * 这里只实现「机器能判」的那部分：语义类（主观词、机翻、AIGC 痕迹）交给 AI，
 * 见 {@link #AI_ONLY_RULES}。
 *
 * <p>设计原则：**宁可漏报也不误报**。误报会让作者困惑，比漏报更伤体验；
 * 所以正则都写得很窄，判不准的一律不报。
 */
public final class LocalRules {

    private LocalRules() {
    }

    /**
     * 需要 AI 参与判定的条款（{@code AiReviewer} 的 prompt 里始终列出全部 35 条，
     * 这里是其中有语义成分、本地判不可靠的那部分）。
     *
     * <p>反过来，{@link #LOCAL_ONLY_RULES} 是本地已经能可靠判定的纯格式条款：
     * 那几条 AI 不用重复报，避免一次投稿收两条一样的意见。
     *
     * <p>0.1.0 曾把这里写成 11 条而 prompt 只覆盖 3 条，等于 8 条规则无人判；
     * 现在清单与 prompt 对齐，且 AI 拿到的是完整 35 条。
     */
    public static final int[] AI_ONLY_RULES = {
            1, 2, 4, 5, 6, 7, 8, 9, 10, 11, 13, 14, 15, 16, 17, 18, 20, 21,
            25, 26, 27, 28, 30, 32, 33, 34, 35
    };

    /** 本地规则已能可靠判定的纯格式条款（AI 不必重复报） */
    public static final int[] LOCAL_ONLY_RULES = {3, 12, 19, 22, 23, 24, 29, 31};

    // 第 3 条：emoji（只认明确的 emoji 区段，避免误伤中文标点）
    private static final Pattern EMOJI = Pattern.compile(
            "[\\x{1F300}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{FE0F}]");
    // 第 3 条：颜文字。分成两条正则是有原因的：
    //  - 符号类（ω^╯╰ノ°□▽Σ△…）不会出现在正常中文里，可以放心匹配；
    //  - ASCII 类只认 T_T / T.T / ToT 这种明确写法。
    // 早先两条混在一起，把「（Open）」「（API）」这种英文括号内容全判成了颜文字——
    // 与「宁可漏报」的原则相反。
    private static final Pattern KAOMOJI_SYMBOL = Pattern.compile(
            "[\\(（][^\\)）]{0,10}[ω^╯╰╭ノヽﾉ°□▽∀Σ△▲▼・｡•ﾟ][^\\)）]{0,10}[\\)）]");
    private static final Pattern KAOMOJI_ASCII = Pattern.compile(
            "(?i)[\\(（]\\s*[tT]\\s*[._\\-]\\s*[tT]\\s*[\\)）]|[\\(（]\\s*[tT]\\s*[oO]\\s*[tT]\\s*[\\)）]");

    // 第 12 条：句末缺标点（整段末尾不是标点）
    private static final Pattern ENDS_WITH_PUNCT = Pattern.compile(
            "[。！？；：…、,.!?;:\\-—\\)）\\]】」』”’\"']$");

    // 第 19 条：情绪化堆叠标点
    private static final Pattern REPEATED_PUNCT = Pattern.compile(
            "[。！？!?]{3,}");

    // 第 22 条：字符串/符号拼成的分割线
    private static final Pattern DIVIDER = Pattern.compile(
            "(?m)^\\s*([-=_*~#])\\1{2,}\\s*$");

    // 第 24 条：PS./PSS./注 这类前后缀（注解应融入正文）
    private static final Pattern PS_PREFIX = Pattern.compile(
            "(?i)(^|\\n)\\s*(PS|PSS|P\\.S\\.|注|注意)\\s*[：:.]");

    // 第 29 条：冗余句
    private static final Pattern REDUNDANT = Pattern.compile(
            "欢迎各位补充|介绍就到这里|未完待续|希望大家喜欢|敬请期待");

    // 第 31 条：打补丁式批改（删除线）
    private static final Pattern STRIKETHROUGH = Pattern.compile(
            "~~[^~]+~~|~~.+?~~");

    // 第 23 条：提到版本却只有「新版/旧版/老版本」，没给具体版本号
    private static final Pattern VAGUE_VERSION = Pattern.compile(
            "新版本?|旧版本?|老版本?|最新版");
    private static final Pattern CONCRETE_VERSION = Pattern.compile(
            "\\d+\\.\\d+|1\\.20|1\\.21|26\\.\\d");

    /**
     * 第 22 条里的「全文样式异常」：正文混进 Markdown 语法。
     *
     * <p>百科正文要用编辑器生成的结构（真列表、真表格）和 {@code [hN=]} 标题标记；
     * 手打 {@code ##}、{@code **粗体**}、{@code - 列表}、{@code |表格|} 在页面里会原样显示成字符，
     * 属于明确的格式错误，不用 AI 猜。
     */
    private static final Pattern MD_HEADING = Pattern.compile("(?m)^\\s*#{1,6}\\s+\\S");
    private static final Pattern MD_BOLD = Pattern.compile("\\*\\*[^*\\n]+\\*\\*");
    private static final Pattern MD_BULLET = Pattern.compile("(?m)^\\s*[-*+]\\s+\\S");
    private static final Pattern MD_ORDERED = Pattern.compile("(?m)^\\s*\\d+[.)]\\s+\\S");
    private static final Pattern MD_TABLE = Pattern.compile("(?m)^\\s*\\|.*\\|\\s*$");
    private static final Pattern MD_FENCE = Pattern.compile("(?m)^\\s*```");

    // 第 18 条：灌水（正文太短；标题不算）
    private static final int MIN_BODY_CHARS = 10;

    /**
     * 第 26 条（排版）里的「标题结构」：正文用了百科标题标记时，
     * 必须从 {@code [h1=]} 开始、且不许跨级。
     *
     * <p>这是站点审核实测会拦的一类问题，而且**AI 判不了**（它只判语义），
     * 所以放在本地规则里：方括号标记本身不该被误判成乱码，但结构错了要能识别出来。
     */
    private static final Pattern TITLE_TAG = Pattern.compile(
            "\\[h([1-6])=([^\\]]*)\\]");

    /**
     * 跑一遍本地规则。
     *
     * @param title 标题（可为空）
     * @param body  正文（可为空）
     * @return 发现列表；空列表表示本地规则没查出错
     */
    public static List<ReviewFinding> check(String title, String body) {
        List<ReviewFinding> out = new ArrayList<>();
        String t = title == null ? "" : title;
        String b = body == null ? "" : body;
        String all = t + "\n" + b;

        if (all.isBlank()) {
            out.add(new ReviewFinding(18, null, "内容是空的"));
            return out;
        }

        // 第 3 条 emoji / 颜文字
        Matcher m = EMOJI.matcher(all);
        if (m.find()) {
            out.add(new ReviewFinding(3, m.group(), "含 emoji，正式条目里不该出现"));
        }
        m = KAOMOJI_SYMBOL.matcher(all);
        if (m.find()) {
            out.add(new ReviewFinding(3, m.group(), "含颜文字，读者读起来会分神"));
        } else {
            m = KAOMOJI_ASCII.matcher(all);
            if (m.find()) {
                out.add(new ReviewFinding(3, m.group(), "含颜文字，读者读起来会分神"));
            }
        }

        // 第 12 条 句末标点
        String trimmedBody = b.strip();
        if (!trimmedBody.isEmpty() && !ENDS_WITH_PUNCT.matcher(trimmedBody).find()) {
            out.add(new ReviewFinding(12, tail(trimmedBody),
                    "最后一句没有句末标点"));
        }

        // 第 19 条 情绪化标点
        m = REPEATED_PUNCT.matcher(all);
        if (m.find()) {
            out.add(new ReviewFinding(19, m.group(), "标点堆叠，情绪化表达"));
        }

        // 第 22 条 分割线
        m = DIVIDER.matcher(all);
        if (m.find()) {
            out.add(new ReviewFinding(22, m.group().strip(), "用字符拼的分割线，正文里不允许"));
        }

        // 第 24 条 PS./注
        m = PS_PREFIX.matcher(all);
        if (m.find()) {
            out.add(new ReviewFinding(24, m.group().strip(),
                    "「注/PS」这类前缀，注解应直接写进正文"));
        }

        // 第 29 条 冗余句
        m = REDUNDANT.matcher(all);
        if (m.find()) {
            out.add(new ReviewFinding(29, m.group(), "冗余句可以删掉"));
        }

        // 第 31 条 删除线
        m = STRIKETHROUGH.matcher(all);
        if (m.find()) {
            out.add(new ReviewFinding(31, m.group(), "删除线属于「划掉再批改」，有误直接删"));
        }

        // 第 23 条 版本号
        if (VAGUE_VERSION.matcher(all).find() && !CONCRETE_VERSION.matcher(all).find()) {
            out.add(new ReviewFinding(23, VAGUE_VERSION.matcher(all).results()
                            .map(r -> r.group()).findFirst().orElse(""),
                    "提了版本却没说具体版本号"));
        }

        // 第 18 条 灌水
        if (removedWhitespace(b).length() < MIN_BODY_CHARS && !b.isBlank()) {
            out.add(new ReviewFinding(18, b.strip(), "正文太短，像在凑字数"));
        }

        // 第 22 条 Markdown 残留 / 粘贴压平（全文样式异常）
        checkMarkdownResidue(all, out);
        if (out.stream().noneMatch(f -> f.ruleId() != null && f.ruleId() == 22)) {
            checkFlattenedTable(b, out);
        }

        // 第 10 条 重复内容（逐句比对；AI 容易把「结构相似」臆断成整段重复，所以本地兜一条确定性的）
        checkDuplicates(b, out);

        // 第 26 条 标题结构（用了标记就查层级；长稿连标记都没有也算缺结构）
        checkTitleStructure(all, b, out);

        return out;
    }

    /** 短行判定：多少字以内算「表格/列表被压平后的碎片」 */
    private static final int FLAT_LINE_CHARS = 12;
    /** 至少多少条这种碎片行才判（正常散文不会连续出现这么多短行） */
    private static final int FLAT_LINE_MIN = 6;

    /**
     * 从网页复制粘贴会把表格压成一行行词组（「渠道 / 地址 / 源码」这种），列表也会变成普通行。
     *
     * <p>这类稿子读起来断断续续，但根因是格式：应使用编辑器自带的表格与列表重新排版。
     * 判定放在本地：连续出现足够多的「短、无标点」行才算，避免误伤正常文章里的短句。
     */
    private static void checkFlattenedTable(String body, List<ReviewFinding> out) {
        int flat = 0;
        String sample = null;
        for (String raw : body.split("\n")) {
            String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            boolean bareUrl = line.startsWith("http://") || line.startsWith("https://");
            boolean shortToken = line.length() <= FLAT_LINE_CHARS
                    && !ENDS_WITH_PUNCT.matcher(line).find()
                    && line.indexOf('。') < 0;
            if (bareUrl || shortToken) {
                flat++;
                if (sample == null) {
                    sample = line;
                }
            }
        }
        if (flat >= FLAT_LINE_MIN) {
            out.add(new ReviewFinding(22, sample,
                    "正文里有一串被压平的短词组行，像是表格/列表直接粘贴丢了格式；"
                            + "请用编辑器自带的表格与无序/有序列表重新排版"));
        }
    }

    /** Markdown 语法残留检查（第 22 条） */
    private static void checkMarkdownResidue(String text, List<ReviewFinding> out) {
        Pattern[] patterns = {MD_HEADING, MD_FENCE, MD_BOLD, MD_BULLET, MD_ORDERED, MD_TABLE};
        for (Pattern p : patterns) {
            Matcher m = p.matcher(text);
            if (m.find()) {
                String sample = m.group().strip();
                out.add(new ReviewFinding(22, sample,
                        "正文混进了 Markdown 语法，百科要用编辑器生成的结构（列表/表格）和 [hN=] 标题标记"));
                return;
            }
        }
    }

    /** 重复内容的最小段落长度（太短的段落重复很常见，不判） */
    private static final int MIN_DUP_PARA = 20;
    /** 重复段落里至少要有多少个汉字（挡住标记行、符号行） */
    private static final int MIN_DUP_CJK = 6;

    /**
     * 第 10 条里的「重复内容」：**整段**逐字相同时才报。
     *
     * <p>踩过的坑：早先按句切分，而 {@code stripTags} 又把段落边界删掉了，
     * 结果跨段拼接出来的片段被误判成重复——一篇正常文章被打回。
     * 现在要求整段相同、长度 ≥ 20 字、且含 ≥ 6 个汉字。
     */
    private static void checkDuplicates(String body, List<ReviewFinding> out) {
        Map<String, Integer> seen = new HashMap<>();
        for (String raw : body.split("\n")) {
            String norm = raw.replaceAll("\\s", "").trim();
            if (norm.length() < MIN_DUP_PARA || countCjk(norm) < MIN_DUP_CJK) {
                continue;
            }
            int n = seen.merge(norm, 1, Integer::sum);
            if (n >= 2) {
                out.add(new ReviewFinding(10, head(norm),
                        "同一段内容在正文里出现了两次以上"));
                return;
            }
        }
    }

    private static int countCjk(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) {
                n++;
            }
        }
        return n;
    }

    /** 取开头一小段，用于审核意见引用 */
    private static String head(String s) {
        int n = Math.min(24, s.length());
        return s.substring(0, n);
    }

    /**
     * 检查百科标题结构：第一个标题必须是 {@code [h1=]}，层级不许跨级（如 h1 直接跳到 h3）。
     *
     * <p>方括号标记是站点语法，本身不算问题；错的是结构。
     */
    /**
     * 标题结构（第 26 条）。
     *
     * @param text 标题+正文（查层级用）
     * @param body 正文（判断「长稿却完全没标题结构」用）
     */
    private static void checkTitleStructure(String text, String body, List<ReviewFinding> out) {
        if (!TITLE_TAG.matcher(text).find()) {
            // 长稿没有 [h1=] 之类的结构：百科正文要求分节，整篇流水账过不了排版
            if (body.replaceAll("\s", "").length() >= MIN_STRUCTURED_CHARS) {
                out.add(new ReviewFinding(26, head(body.strip()),
                        "正文较长但没有 [h1=] 等标题结构，百科正文要分节"));
            }
            return;
        }
        checkTitleLevels(text, out);
    }

    /** 正文超过这个字数就要求有标题结构 */
    private static final int MIN_STRUCTURED_CHARS = 200;

    private static void checkTitleLevels(String text, List<ReviewFinding> out) {
        Matcher m = TITLE_TAG.matcher(text);
        int prevLevel = -1;
        int firstLevel = -1;
        String firstTag = null;
        while (m.find()) {
            int level = Integer.parseInt(m.group(1));
            if (firstLevel < 0) {
                firstLevel = level;
                firstTag = m.group();
            }
            if (prevLevel > 0 && level > prevLevel + 1) {
                out.add(new ReviewFinding(26, m.group(),
                        "标题跨级：第 " + prevLevel + " 级后面直接出现第 " + level + " 级"));
                return;
            }
            prevLevel = level;
        }
        if (firstLevel > 0 && firstLevel != 1) {
            out.add(new ReviewFinding(26, firstTag,
                    "正文第一个标题不是 [h1=]，标题结构不完整"));
        }
    }

    private static String removedWhitespace(String s) {
        return s.replaceAll("\\s", "");
    }

    /** 取正文末尾一小段，用于在审核意见里引用 */
    private static String tail(String s) {
        int n = Math.min(20, s.length());
        return s.substring(s.length() - n).strip();
    }
}
