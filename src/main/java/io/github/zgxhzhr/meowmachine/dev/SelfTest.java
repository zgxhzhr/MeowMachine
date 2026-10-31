package io.github.zgxhzhr.meowmachine.dev;

import io.github.zgxhzhr.meowmachine.review.LocalRules;
import io.github.zgxhzhr.meowmachine.review.ReviewFinding;
import io.github.zgxhzhr.meowmachine.review.Submission;
import io.github.zgxhzhr.meowmachine.review.SubmissionState;
import io.github.zgxhzhr.meowmachine.review.Verdict;
import io.github.zgxhzhr.meowmachine.review.ReviewResult;

import java.util.List;
import java.util.UUID;

/**
 * 手写断言器：验证**纯逻辑**部分（规则判定与状态流转）。
 *
 * <p>这些类只依赖 JDK，所以能脱离游戏直接跑：
 * <pre>java -cp build/classes/java/main io.github.zgxhzhr.meowmachine.dev.SelfTest</pre>
 *
 * <p>构建时 {@code dev/} 整个包会被排除出 jar（见 build.gradle），
 * 所以它不会进正式产物。
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
        testSentenceEnd();
        testDivider();
        testWater();
        testRepeatedPunct();
        testPsPrefix();
        testStrikethrough();
        testVagueVersion();
        testCleanText();
        testSubmissionFlow();
        testFindingSummary();
    }

    // ---------------- 规则 ----------------

    private static void testEmoji() {
        check("emoji 命中第 3 条", hasRule("", "哈哈哈😀", 3));
        check("颜文字命中第 3 条", hasRule("", "看这个 (╯°□°)", 3));
        check("普通括号不误报", !hasRule("", "这是（正常的）括号。", 3));
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

        Submission approved = submitted.apply(ReviewResult.pass("op", ""));
        check("通过后 APPROVED", approved.state() == SubmissionState.APPROVED);

        Submission returned = submitted.apply(
                ReviewResult.of(Verdict.RETURN, List.of(), "op", ""));
        check("打回后 RETURNED", returned.state() == SubmissionState.RETURNED);

        check("三种状态都可编辑", SubmissionState.DRAFT.editableByAuthor()
                && SubmissionState.SUBMITTED.editableByAuthor()
                && SubmissionState.RETURNED.editableByAuthor());
        check("APPROVED 不可编辑", !SubmissionState.APPROVED.editableByAuthor());
    }

    private static void testFindingSummary() {
        List<ReviewFinding> many = List.of(
                new ReviewFinding(3, "x", "a"),
                new ReviewFinding(12, "y", "b"),
                new ReviewFinding(22, "z", "c"),
                new ReviewFinding(31, "w", "d"));
        String s = ReviewFinding.summarizeRules(many);
        check("摘要只列前 3 条（实际: " + s + "）", s.equals("第 3 条、第 12 条、第 22 条"));

        ReviewFinding unexplained = ReviewFinding.unexplained("说不出");
        check("unexplained 的 ruleId 为空", unexplained.isUnexplained());
        check("unexplained 不进摘要",
                ReviewFinding.summarizeRules(List.of(unexplained)).isEmpty());
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
