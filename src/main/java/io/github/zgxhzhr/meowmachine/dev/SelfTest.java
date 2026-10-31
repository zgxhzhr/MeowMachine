package io.github.zgxhzhr.meowmachine.dev;

import io.github.zgxhzhr.meowmachine.review.LocalRules;
import io.github.zgxhzhr.meowmachine.review.ReviewFinding;
import io.github.zgxhzhr.meowmachine.review.ReviewResult;
import io.github.zgxhzhr.meowmachine.review.Submission;
import io.github.zgxhzhr.meowmachine.review.SubmissionState;
import io.github.zgxhzhr.meowmachine.review.Verdict;

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
        testRuleTables();
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

    /** AI_ONLY_RULES 必须和 AI prompt 覆盖的条款一致；本地规则不许重复判 */
    private static void testRuleTables() {
        List<Integer> aiOnly = new java.util.ArrayList<>();
        for (int r : LocalRules.AI_ONLY_RULES) {
            aiOnly.add(r);
        }
        check("AI_ONLY_RULES 与 prompt 覆盖的条款一致（实际 " + aiOnly + "）",
                aiOnly.equals(List.of(1, 6, 7, 18)));
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
