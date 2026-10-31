package io.github.zgxhzhr.meowmachine.review;

import java.util.ArrayList;
import java.util.List;
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

        // 第 26 条 标题结构（只在真的用了标题标记时才判，纯文本投稿不涉及）
        checkTitleStructure(all, out);

        return out;
    }

    /**
     * 检查百科标题结构：第一个标题必须是 {@code [h1=]}，层级不许跨级（如 h1 直接跳到 h3）。
     *
     * <p>方括号标记是站点语法，本身不算问题；错的是结构。
     */
    private static void checkTitleStructure(String text, List<ReviewFinding> out) {
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
