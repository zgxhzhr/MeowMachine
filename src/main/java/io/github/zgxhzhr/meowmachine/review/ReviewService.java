package io.github.zgxhzhr.meowmachine.review;

import io.github.zgxhzhr.meowmachine.MeowMachine;
import io.github.zgxhzhr.meowmachine.ai.AiReviewer;
import io.github.zgxhzhr.meowmachine.ai.LlmConfig;
import io.github.zgxhzhr.meowmachine.sticker.StickerRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * 审核流程编排。**顺序按需求定的**：
 *
 * <pre>
 * 投稿
 *  ├─ 本地规则命中？  → 是：自动打回（附具体规则），梗图按规则挑
 *  └─ 否
 *      ├─ 有能终审的人？（在线 OP / 名册审核员 / 单人自审）
 *      │    → 是：进人工队列，等审核员裁决
 *      └─ 否
 *          ├─ AI 可用？  → 是：AI 审（拿 AI 的发现决定打回/通过）
 *          └─ 否：**兜底打回**，梗图用 fallback（「要不直接打回算了」）
 * </pre>
 *
 * <p>这条链路刻意**不让 AI 阻塞**：AI 是异步的，失败就当作「没查出问题」，
 * 于是继续沿「无人工 + AI 无发现」的分支走兜底打回——结果确定、不会卡住。
 */
public final class ReviewService {

    private ReviewService() {
    }

    /**
     * 处理一份投稿。
     *
     * @param server 服务端（用于判断是否有人工审核员在线）
     * @param author 作者
     * @param sub    已提交的稿件
     * @param cfg    AI 配置
     * @return 审核结果；**可能异步完成**（AI 路径）
     */
    public static CompletableFuture<ReviewResult> process(MinecraftServer server, ServerPlayer author,
                                                          Submission sub, LlmConfig cfg) {
        // ① 本地规则先判（不花 token，判得准的部分直接出结论）
        List<ReviewFinding> localFindings = LocalRules.check(sub.title(), sub.body());
        if (!localFindings.isEmpty()) {
            String sticker = pickSticker(Verdict.RETURN, localFindings);
            MeowMachine.LOG.info("[喵呜机] 本地规则命中 {} 条，自动打回 author={}",
                    localFindings.size(), author.getName().getString());
            return CompletableFuture.completedFuture(
                    ReviewResult.of(Verdict.RETURN, localFindings, "local-rules", sticker));
        }

        // ② 有能终审的人 → 入队等人工裁决（/meow review list 能查到）
        if (hasHumanFinalizer(server)) {
            SubmissionQueue.enqueue(sub, System.currentTimeMillis());
            return CompletableFuture.completedFuture(
                    ReviewResult.of(Verdict.REVISE, List.of(), "pending-human",
                            StickerRegistry.forState("submitted").map(s -> s.id().toString()).orElse("")));
        }

        // ③ 没有人工 → AI（可选）
        if (cfg.usable()) {
            return AiReviewer.review(cfg, sub.title(), sub.body()).thenApply(aiFindings -> {
                if (aiFindings.isEmpty()) {
                    // AI 判没问题 → **通过**（用「通过」系列，同状态多张时随机）
                    String sticker = StickerRegistry.forState("approved")
                            .map(s -> s.id().toString()).orElse("");
                    return ReviewResult.pass("ai", sticker);
                }
                String sticker = pickSticker(Verdict.RETURN, aiFindings);
                return ReviewResult.of(Verdict.RETURN, aiFindings, "ai", sticker);
            });
        }

        // ④ 既没人也没 AI → 兜底打回
        return CompletableFuture.completedFuture(fallbackReturn());
    }

    /**
     * 挑一张梗图。
     *
     * <p>优先级：**语义精确的规则图** → **结论状态图** → **兜底图**。
     *
     * <p>「语义精确」指规则图本身讲的就是这个问题（灌水→「又是灌水」）。
     * 状态图（协改中/通过/打回）不该被某条规则抢占——之前把「协改中」挂在第 12 条
     * （句末标点）上，就出现了「被打回却显示协改中」的错位。
     */
    private static String pickSticker(Verdict verdict, List<ReviewFinding> findings) {
        // ① 规则专属图
        for (ReviewFinding f : findings) {
            if (f.ruleId() == null) {
                continue;
            }
            var byRule = StickerRegistry.forRule(f.ruleId());
            if (byRule.isPresent()) {
                return byRule.get().id().toString();
            }
        }
        // ② 按结论挑状态图
        String state = switch (verdict) {
            case PASS -> "approved";
            case REVISE -> "revising";
            case RETURN -> "returned";
        };
        var byState = StickerRegistry.forState(state);
        if (byState.isPresent()) {
            return byState.get().id().toString();
        }
        // ③ 兜底
        return StickerRegistry.fallback().map(s -> s.id().toString()).orElse("");
    }

    /** 兜底打回：说不出依据，用 fallback 梗图 */
    private static ReviewResult fallbackReturn() {
        String sticker = StickerRegistry.fallback().map(s -> s.id().toString()).orElse("");
        return ReviewResult.of(Verdict.RETURN,
                List.of(ReviewFinding.unexplained("没人审，也没查出具体问题")),
                "fallback", sticker);
    }

    /**
     * 当前是否有人能终审。
     *
     * <p>注意不能只看名册：**任何一个在线 OP 都天然能终审**，所以这里扫在线玩家。
     * 单人世界开了 {@code selfReview} 时，作者自己也算（由调用方在
     * {@code ReviewerRoster#canFinalize} 里判定，这里只保证「有人在」）。
     */
    private static boolean hasHumanFinalizer(MinecraftServer server) {
        if (server == null) {
            return false;
        }
        if (ReviewerRoster.isSelfReviewEnabled()) {
            return true;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (ReviewerRoster.isOp(p)) {
                return true;
            }
        }
        return ReviewerRoster.hasAnyFinalizer();
    }
}
