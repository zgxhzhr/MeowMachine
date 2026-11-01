package io.github.zgxhzhr.meowmachine.dev;

import io.github.zgxhzhr.meowmachine.review.LocalRules;
import io.github.zgxhzhr.meowmachine.review.ReviewFinding;
import io.github.zgxhzhr.meowmachine.review.ReviewResult;
import io.github.zgxhzhr.meowmachine.review.Submission;
import io.github.zgxhzhr.meowmachine.review.SubmissionState;
import io.github.zgxhzhr.meowmachine.review.Verdict;
import io.github.zgxhzhr.meowmachine.richtext.RichDoc;
import io.github.zgxhzhr.meowmachine.richtext.RichEditor;
import io.github.zgxhzhr.meowmachine.richtext.RichHtml;

import java.util.List;
import java.util.UUID;

/**
 * 手写断言器：验证**纯逻辑**部分（规则判定与状态流转）。
 *
 * <p>这些类只依赖 JDK，所以能脱离游戏直接跑：
 * <pre>java -cp build/classes/java/main io.github.zgxhzhr.meowmachine.dev.SelfTest</pre>
 *
 * <p>构建时 {@code dev/} 整个包会被排除出 jar（见 build.gradle），所以它不会进正式产物。
 *
 * <p>本次接管补的断言，针对的都是「跑起来才发现」的那类 bug：
 * <ul>
 *   <li>PENDING 不能被映射成「已打回」</li>
 *   <li>颜文字正则不能把「（Open）」这种英文括号内容判成颜文字</li>
 *   <li>AI_ONLY_RULES 与实际 prompt 覆盖的条款保持一致（18 归本地规则）</li>
 * </ul>
 */
public final class SelfTest {

    private static int passed = 0;
    private static int failed = 0;

    private SelfTest() {
    }

    public static void main(String[] args) {
        run();
        System.out.println();
        System.out.println("结果: " + passed + " 通过, " + failed + " 失败");
        System.exit(failed == 0 ? 0 : 1);
    }

    public static void run() {
        testEmoji();
        testKaomoji();
        testSentenceEnd();
        testDivider();
        testWater();
        testRepeatedPunct();
        testPsPrefix();
        testStrikethrough();
        testVagueVersion();
        testCleanText();
        testSubmissionFlow();
        testVerdictMapping();
        testNewSubmissionIds();
        testTitleStructure();
        testDuplicates();
        testRichText();
        testRichEditor();
        testRuleTables();
    }

    /**
     * 第 10 条「重复内容」：必须逐字相同才报。
     *
     * <p>这条是本地兜的：AI 曾把一篇正常百科正文（带 [h1=] 标记）臆断成「整段重复」，
     * 所以重复判定要有确定性依据。
     */
    private static void testDuplicates() {
        String twice = "这是一段足够长的正常介绍文字。这是另一句不同的内容。这是一段足够长的正常介绍文字。";
        check("同一句出现两次判第 10 条", hasRule("", twice, 10));

        String normal = "星尘熔炉是一个围绕矿物的加工类模组。模组只新增一种矿物和两类机器，其余配方由数据包定义。";
        check("正常介绍不判第 10 条", !hasRule("", normal, 10));

        String markers = "[h1=概述]\n[h2=核心玩法]\n[h2=版本与兼容]\n正文内容写在这里，足够十来个字。";
        check("结构标记不算重复", !hasRule("", markers, 10));
    }

    /**
     * 富文本：只处理编辑者能用工具栏做出来的结构。
     *
     * <p>关键一条：**标题是手打的文字**（{@code [h2=…]}），不是块级元素——
     * 模型/序列化都不能把它拆出来或丢掉。
     */
    private static void testRichText() {
        List<RichDoc.Run> runs = List.of(
                RichDoc.Run.plain("[h2=迁移边界]"),
                RichDoc.Run.plain("普通文字，"),
                new RichDoc.Run("加粗", true, false),
                new RichDoc.Run("斜体", false, true));
        RichDoc doc = new RichDoc(List.of(
                new RichDoc.Para(runs),
                new RichDoc.BulletList(List.of(
                        List.of(RichDoc.Run.plain("第一项")),
                        List.of(RichDoc.Run.plain("第二项")))),
                new RichDoc.OrderedList(List.of(List.of(RichDoc.Run.plain("第一步")))),
                new RichDoc.Table(List.of(
                        List.of(List.of(RichDoc.Run.plain("表头甲")), List.of(RichDoc.Run.plain("表头乙"))),
                        List.of(List.of(RichDoc.Run.plain("值一")), List.of(RichDoc.Run.plain("值二")))),
                        true)));

        String html = RichHtml.toHtml(doc);
        check("手打的标题标记保持为文字", html.contains("<p>[h2=迁移边界]普通文字，"));
        check("粗体序列化为 <strong>", html.contains("<strong>加粗</strong>"));
        check("斜体序列化为 <em>", html.contains("<em>斜体</em>"));
        check("无序列表 li>p", html.contains("<ul><li><p>第一项</p></li>"));
        check("有序列表 ol>li>p", html.contains("<ol><li><p>第一步</p></li></ol>"));
        check("表格表头用 th", html.contains("<th>表头甲</th>"));
        check("表格单元格 td valign", html.contains("<td valign=\"top\"><p>值一</p></td>"));

        check("往返一次 HTML 不变", RichHtml.toHtml(RichHtml.parse(html)).equals(html));
        check("纯文本保留标题标记", doc.toPlainText().contains("[h2=迁移边界]"));

        RichDoc pasted = RichHtml.parse(
                "<p><span class=\"common-text-title common-text-title-2\">迁移边界</span></p>");
        check("粘贴站点渲染形态时标签被剥掉、文字保留",
                pasted.blocks().size() == 1
                        && pasted.blocks().get(0) instanceof RichDoc.Para p
                        && p.text().equals("迁移边界"));
    }

    /**
     * 百科标题结构 + 方括号标记不会误报。
     *
     * <p>这是实测反馈里最容易被误解的一块：方括号是站点语法，
     * 不该被当成乱码；但「标题从 h2 开始 / 跨级」是真的结构问题，要能识别。
     */
    private static void testTitleStructure() {
        String body = "\n正文内容写在这里，足够十来个字。";
        check("标题从 [h2=] 开始命中第 26 条", hasRule("", "[h2=概述]" + body, 26));
        check("从 [h1=] 开始不命中第 26 条", !hasRule("", "[h1=页面]\n[h2=分节]" + body, 26));
        check("[h1=] 直接跳到 [h3=] 命中第 26 条", hasRule("", "[h1=页面]\n[h3=小节]" + body, 26));
        check("正文以方括号标记结尾不误判第 12 条", !hasRule("", "正文内容写在这里。[h2=机器]", 12));
        check("[ban:title_menu] 不触发任何本地规则", LocalRules.check("", "[ban:title_menu]" + body).isEmpty());
    }

    // ---------------- 规则 ----------------

    private static void testEmoji() {
        check("emoji 命中第 3 条", hasRule("", "哈哈哈😀", 3));
    }

    private static void testKaomoji() {
        check("符号颜文字命中第 3 条", hasRule("", "看这个 (╯°□°)", 3));
        check("T_T 命中第 3 条", hasRule("", "无语 (T_T)", 3));
        check("ToT 命中第 3 条", hasRule("", "哭了 (ToT)", 3));
        check("普通中文括号不误报", !hasRule("", "这是（正常的）括号。", 3));
        // 回归：0.1.0 的正则把含 o/O/T 的英文括号内容全判成颜文字
        check("英文括号（Open）不误报第 3 条", !hasRule("", "这个功能（Open）已经开放。", 3));
        check("英文括号（API）不误报第 3 条", !hasRule("", "接口（API）文档在下面。", 3));
    }

    private static void testSentenceEnd() {
        check("缺句末标点命中第 12 条", hasRule("", "这是一句话", 12));
        check("有句号不命中", !hasRule("", "这是一句话。", 12));
        check("有问号不命中", !hasRule("", "这是一句话？", 12));
    }

    private static void testDivider() {
        check("短横线分割命中第 22 条", hasRule("", "abc\n---\ndef。", 22));
        check("等号分割命中第 22 条", hasRule("", "abc\n===\ndef。", 22));
    }

    private static void testWater() {
        check("正文太短命中第 18 条", hasRule("", "短", 18));
        check("空内容命中第 18 条", hasRule("", "", 18));
    }

    private static void testRepeatedPunct() {
        check("标点堆叠命中第 19 条", hasRule("", "真的吗？？？", 19));
        check("单个问号不命中", !hasRule("", "真的吗？", 19));
    }

    private static void testPsPrefix() {
        check("PS 前缀命中第 24 条", hasRule("", "正文在这里。\nPS：补充一句", 24));
        check("注前缀命中第 24 条", hasRule("", "正文在这里。\n注：补充一句", 24));
    }

    private static void testStrikethrough() {
        check("删除线命中第 31 条", hasRule("", "这是~~错误~~的表达。", 31));
    }

    private static void testVagueVersion() {
        check("模糊版本号命中第 23 条", hasRule("", "新版本里这个功能可以用了。", 23));
        check("具体版本号不命中", !hasRule("", "1.20.1 里这个功能可以用了。", 23));
    }

    private static void testCleanText() {
        List<ReviewFinding> f = LocalRules.check("一个标题",
                "这是一个完全正常的段落，标点齐全，没有任何禁用的记号。");
        check("正常文本无发现（实际 " + f.size() + " 条: " + reasons(f) + "）", f.isEmpty());
    }

    // ---------------- 状态流转 ----------------

    private static void testSubmissionFlow() {
        UUID author = UUID.randomUUID();
        Submission draft = Submission.draft(author, "tester");
        check("新建是 DRAFT", draft.state() == SubmissionState.DRAFT);
        check("新建版本号 0", draft.revision() == 0);

        Submission edited = draft.withContent("标题", "正文内容。");
        check("编辑后仍是 DRAFT", edited.state() == SubmissionState.DRAFT);
        check("编辑后版本号 1", edited.revision() == 1);
        check("编辑保留同一 id", edited.id().equals(draft.id()));

        Submission submitted = edited.submit();
        check("提交后 SUBMITTED", submitted.state() == SubmissionState.SUBMITTED);
        check("提交不改版本号", submitted.revision() == edited.revision());

        check("三种状态都可编辑", SubmissionState.DRAFT.editableByAuthor()
                && SubmissionState.SUBMITTED.editableByAuthor()
                && SubmissionState.RETURNED.editableByAuthor());
        check("APPROVED 不可编辑", !SubmissionState.APPROVED.editableByAuthor());
    }

    /** PENDING 是「在审」，不是「被打回」——0.1.0 把非 PASS 一律映射成 RETURNED */
    private static void testVerdictMapping() {
        UUID author = UUID.randomUUID();
        Submission submitted = Submission.draft(author, "tester")
                .withContent("标题", "正文内容。").submit();

        Submission pending = submitted.apply(
                ReviewResult.of(Verdict.PENDING, List.of(), "pending-human", "under_review"));
        check("PENDING 之后仍是 SUBMITTED（不是被打回）",
                pending.state() == SubmissionState.SUBMITTED);

        Submission approved = submitted.apply(ReviewResult.pass("op", "approved_plain"));
        check("PASS 之后是 APPROVED", approved.state() == SubmissionState.APPROVED);

        Submission revised = submitted.apply(
                ReviewResult.of(Verdict.REVISE, List.of(), "op", "revising"));
        check("REVISE 之后是 RETURNED", revised.state() == SubmissionState.RETURNED);

        Submission returned = submitted.apply(
                ReviewResult.of(Verdict.RETURN, List.of(), "op", "returned"));
        check("RETURN 之后是 RETURNED", returned.state() == SubmissionState.RETURNED);

        check("PENDING 不是终局结论", !Verdict.PENDING.isDecidable());
        check("PASS/REVISE/RETURN 都是终局结论",
                Verdict.PASS.isDecidable() && Verdict.REVISE.isDecidable() && Verdict.RETURN.isDecidable());
    }

    /** 「新建投稿」必须是一份真正独立的稿子，否则玩家永远只投得了一篇 */
    private static void testNewSubmissionIds() {
        UUID author = UUID.randomUUID();
        Submission first = Submission.draft(author, "tester");
        Submission second = Submission.draft(author, "tester");
        check("两次新投稿 id 不同", !first.id().equals(second.id()));
        check("新投稿都从第 0 版开始", first.revision() == 0 && second.revision() == 0);
        check("新投稿都是 DRAFT", first.state() == SubmissionState.DRAFT
                && second.state() == SubmissionState.DRAFT);
    }

    /** 编辑器内核：悬挂样式、选区样式、列表、表格、回车/退格 */
    private static void testRichEditor() {
        RichEditor e = RichEditor.empty();
        e.toggleBold();
        e.typeText("粗");
        e.toggleBold();
        e.typeText("普");
        check("悬挂粗体只作用于之后的输入", RichHtml.toHtml(e.doc()).contains("<strong>粗</strong>普"));

        RichEditor e2 = RichEditor.empty();
        e2.typeText("abcd");
        e2.select(new RichEditor.Pos(0, 0, 1), new RichEditor.Pos(0, 0, 3));
        e2.toggleBold();
        check("选区加粗只影响选中部分",
                RichHtml.toHtml(e2.doc()).contains("a<strong>bc</strong>d"));

        RichEditor e3 = RichEditor.empty();
        e3.typeText("第一段");
        e3.toggleList(true);
        check("段落转有序列表", RichHtml.toHtml(e3.doc()).startsWith("<ol><li><p>第一段"));

        RichEditor e4 = RichEditor.empty();
        e4.toggleList(true);
        e4.typeText("甲");
        e4.enter();
        e4.typeText("乙");
        String h4 = RichHtml.toHtml(e4.doc());
        check("列表回车新建同级项", h4.contains("<p>甲</p>") && h4.contains("<p>乙</p>"));

        RichEditor e5 = RichEditor.empty();
        e5.toggleList(true);
        e5.enter();
        check("空列表项回车退出列表", RichHtml.toHtml(e5.doc()).startsWith("<p>"));

        RichEditor e6 = RichEditor.empty();
        e6.insertTable(2, 2);
        check("插入 2×2 表格", e6.cellCountAt(1) == 4);
        e6.typeText("格1");
        check("表格内可输入", RichHtml.toHtml(e6.doc()).contains("格1"));
        check("Tab 跳到下一格", e6.moveToNextCell());

        RichEditor e7 = RichEditor.empty();
        e7.typeText("甲乙");
        e7.setCaret(new RichEditor.Pos(0, 0, 1));
        e7.enter();
        check("段落回车拆成两段", RichHtml.toHtml(e7.doc()).equals("<p>甲</p><p>乙</p>"));

        RichEditor e8 = RichEditor.empty();
        e8.typeText("甲");
        e8.enter();
        e8.typeText("乙");
        e8.setCaret(new RichEditor.Pos(1, 0, 0));
        e8.backspace();
        check("段首退格与前一段合并", RichHtml.toHtml(e8.doc()).equals("<p>甲乙</p>"));
    }

    /** 规则表口径：AI 拿到全部 35 条，本地只独占 8 条纯格式 */
    private static void testRuleTables() {
        List<Integer> aiOnly = new java.util.ArrayList<>();
        List<Integer> localOnly = new java.util.ArrayList<>();
        for (int r : LocalRules.AI_ONLY_RULES) {
            aiOnly.add(r);
        }
        for (int r : LocalRules.LOCAL_ONLY_RULES) {
            localOnly.add(r);
        }
        check("AI 参与判定的条款数 = 35 − 本地独占 8（实际 " + aiOnly.size() + "）",
                aiOnly.size() == 27);
        check("本地独占的 8 条不在 AI 清单里",
                aiOnly.stream().noneMatch(localOnly::contains));
        check("AI 清单覆盖 1~35 里所有非本地独占条款",
                aiOnly.size() + localOnly.size() == 35);
        check("AI 清单包含机翻/AIGC/内容不实（6/7/18）",
                aiOnly.contains(6) && aiOnly.contains(7) && aiOnly.contains(18));
        check("第 18 条也在本地有可量化检查（正文太短）", hasRule("", "短", 18));
    }

    // ---------------- 断言器 ----------------

    private static boolean hasRule(String title, String body, int rule) {
        return LocalRules.check(title, body).stream()
                .anyMatch(f -> f.ruleId() != null && f.ruleId() == rule);
    }

    private static String reasons(List<ReviewFinding> f) {
        return f.stream().map(x -> String.valueOf(x.ruleId())).reduce((a, b) -> a + "," + b).orElse("");
    }

    private static void check(String name, boolean cond) {
        if (cond) {
            passed++;
            System.out.println("  [PASS] " + name);
        } else {
            failed++;
            System.out.println("  [FAIL] " + name);
        }
    }
}
