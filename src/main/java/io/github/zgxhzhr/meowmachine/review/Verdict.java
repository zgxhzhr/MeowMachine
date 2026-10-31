package io.github.zgxhzhr.meowmachine.review;

/**
 * 一次审核的结论。
 *
 * <p>{@link #REVISE}（协改）与 {@link #RETURN}（打回）的区别：协改意味着「改完还能过」，
 * 打回意味着「问题较大，重写」。两者都允许作者继续编辑。
 *
 * <p>{@link #PENDING} 不是「裁决」，而是「已提交、正在等人工审」的中间态。
 * 早先这条路径硬套 {@code REVISE}，结果玩家刚投稿就看到弹窗写「需要协改」，
 * 却一个字都没让他改——所以单独列出，客户端标题与状态映射都按它区分。
 */
public enum Verdict {
    /** 通过 */
    PASS,
    /** 要求协改 */
    REVISE,
    /** 打回 */
    RETURN,
    /** 已提交，等待人工审核（非终局结论） */
    PENDING;

    /** 是否是可以由审核员拍板的终局结论（客户端/命令只接受这三种） */
    public boolean isDecidable() {
        return this != PENDING;
    }
}
