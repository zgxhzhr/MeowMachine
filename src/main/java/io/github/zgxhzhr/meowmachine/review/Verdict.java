package io.github.zgxhzhr.meowmachine.review;

/**
 * 一次审核的结论。
 *
 * <p>{@link #REVISE}（协改）与 {@link #RETURN}（打回）的区别：协改意味着「改完还能过」，
 * 打回意味着「问题较大，重写」。两者都允许作者继续编辑。
 */
public enum Verdict {
    /** 通过 */
    PASS,
    /** 要求协改 */
    REVISE,
    /** 打回 */
    RETURN;

    /** 是否允许作者继续编辑重投 */
    public boolean reopensEditing() {
        return this != PASS;
    }
}
