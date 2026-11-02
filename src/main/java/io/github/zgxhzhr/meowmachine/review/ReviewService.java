package io.github.zgxhzhr.meowmachine.review;

import io.github.zgxhzhr.meowmachine.MeowMachine;
import io.github.zgxhzhr.meowmachine.ai.AiReviewer;
import io.github.zgxhzhr.meowmachine.ai.LlmConfig;
import io.github.zgxhzhr.meowmachine.sticker.StickerRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 审核流程编排。**顺序按需求定的**：
 *
 * <pre>
 * 投稿
 *  ├─ ① 本地规则命中？  → 是：自动打回（附具体规则），梗图按规则挑
 *  └─ 否
 *      ├─ ② 有人能终审？（在线 OP / 名册里有人）
 *      │      且没有开「AI 接管」
 *      │    → 是：入队等人工裁决，返回 PENDING（不是 REVISE！）
 *      └─ 否
 *          ├─ ③ AI 可用？
 *          │    ├─ AI 拿到可信结论、无违规 → 通过
 *          │    ├─ AI 拿到可信结论、有违规 → 打回（附 AI 发现）
 *          │    └─ AI 不可用（网络/超时/解析失败）→ ④ 兜底打回
 *          └─ ④ 兜底打回，梗图用 fallback（「要不直接打回算了」）
 * </pre>
 *
 * <p>两条必须守住的线：
 * <ul>
 *   <li><b>AI 绝不阻塞</b>：AI 是异步的，超时/失败都会在有限时间内落到 ④，流程不会卡住。</li>
 *   <li><b>AI 失败 ≠ 通过</b>：0.1.0 曾把「AI 挂了」当成「没查出问题」直接放行，
 *       等于一次网络抖动让所有投稿自动过审。现在失败一律走兜底打回，
 *       并回报一个能看懂的说明（不是怪作者）。</li>
 * </ul>
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
        String plainBody = io.github.zgxhzhr.meowmachine.richtext.RichHtml.stripTags(sub.body());
        List<ReviewFinding> localFindings =
                new java.util.ArrayList<>(LocalRules.check(sub.title(), plainBody, sub.body()));
        String aiText = io.github.zgxhzhr.meowmachine.richtext.RichHtml.toAiText(sub.body());

        // ①.0 已通过条目的再编辑：先算改动对比（硬闸 + 交给 AI 判断改动是否有意义）
        var prevApproved = ApprovedIndex.find(sub.id());
        String diffNote = "";
        boolean trivialEdit = false;
        if (prevApproved.isPresent()) {
            var diff = EditDiffs.of(prevApproved.get().body(), sub.body());
            diffNote = String.join("\n", diff.summaryLines());
            trivialEdit = diff.trivial();
            if (trivialEdit) {
                localFindings.add(0, new ReviewFinding(10,
                        "只改动了 " + Math.abs(diff.newChars() - diff.oldChars()) + " 字（新增 "
                                + diff.added() + " 行 / 删除 " + diff.removed() + " 行）",
                        "与上一版几乎相同，实质未修改；请做出有意义的补充或修正后再提交"));
            }
            if (diff.regressed()) {
                localFindings.add(0, new ReviewFinding(29,
                        "删除 " + diff.removed() + " 行 / 新增 " + diff.added() + " 行",
                        "越改越倒退：这次删得比加得多，或总字数明显缩水"));
            }
        }
        if (trivialEdit) {
            String sticker = pickSticker(Verdict.RETURN, localFindings);
            MeowMachine.LOG.info("[喵呜机] 再编辑改动过小，直接打回 author={}",
                    author.getName().getString());
            return CompletableFuture.completedFuture(
                    ReviewResult.of(Verdict.RETURN, localFindings, "local-rules", sticker));
        }

        // AI 也算审核方：如果这是已通过条目的再编辑、且 AI 判它需要修改，就替 AI 记一条退回申请
        // （审核员在「已通过条目」列表里能看到「AI 申请退回」）
        final String aiRantReason = diffNote;

        // ② AI 接管：AI 判断优于本地规则（格式类 10/22/26/29 除外）
        boolean aiTakesOver = cfg.usable() && cfg.takeover();
        if (aiTakesOver) {
            return AiReviewer.review(cfg, sub.title(), aiText, localFindings, diffNote)
                    .thenApplyAsync(ai -> {
                        if (!ai.available()) {
                            if (!localFindings.isEmpty()) {
                                return ReviewResult.of(Verdict.RETURN, localFindings, "local-rules",
                                        pickSticker(Verdict.RETURN, localFindings));
                            }
                            return fallbackReturn("AI 审核暂时不可用，无法确认内容，先按打回处理，稍后可重新投稿");
                        }
                        List<ReviewFinding> hardFormat = localFindings.stream()
                                .filter(f -> f.ruleId() != null
                                        && (f.ruleId() == 10 || f.ruleId() == 22
                                                || f.ruleId() == 26 || f.ruleId() == 29))
                                .toList();
                        AiOpinions.putOutcome(sub.id(), ai.findings(), ai.note());
                        MeowMachine.LOG.info(
                                "[喵呜机] AI 接管裁决 author={} 本地线索 {} 条（其中确定性 {} 条）/ AI 违规 {} 条",
                                author.getName().getString(), localFindings.size(),
                                hardFormat.size(), ai.findings().size());
                        String aiSticker = StickerRegistry.exists(ai.stickerId()) ? ai.stickerId() : null;
                        boolean pass = ai.findings().isEmpty() && hardFormat.isEmpty();
                        if (aiSticker != null) {
                            boolean approvedSticker = "approved".equals(StickerRegistry.stateOf(aiSticker));
                            if (pass != approvedSticker) {
                                aiSticker = null;
                            }
                        }
                        if (pass) {
                            String sticker = aiSticker != null ? aiSticker
                                    : StickerRegistry.forState("approved")
                                            .map(s -> s.id().toString()).orElse("");
                            List<ReviewFinding> note = (ai.note() == null || ai.note().isBlank())
                                    ? List.of() : List.of(ReviewFinding.unexplained(ai.note().trim()));
                            return ReviewResult.of(Verdict.PASS, note, "ai", sticker);
                        }
                        List<ReviewFinding> findings = new java.util.ArrayList<>(ai.findings());
                        findings.addAll(hardFormat);
                        boolean aiSaidOk = ai.findings().isEmpty();
                        if (!aiSaidOk && ai.note() != null && !ai.note().isBlank()) {
                            findings.add(ReviewFinding.unexplained(ai.note().trim()));
                        }
                        String sticker = aiSticker != null ? aiSticker
                                : pickSticker(Verdict.RETURN, findings);
                        if (prevApproved.isPresent()) {
                            ReturnRequests.request(sub.id(), "AI",
                                    "AI 判定需要修改" + (aiRantReason.isBlank() ? ""
                                            : "：" + aiRantReason.replace("\n", "；")));
                        }
                        return ReviewResult.of(Verdict.RETURN, findings, "ai", sticker);
                    }, server).exceptionally(ex -> {
                        MeowMachine.LOG.error("[喵呜机] AI 接管裁决异常，退回本地规则", ex);
                        return localFindings.isEmpty()
                                ? fallbackReturn("AI 审核异常，无法确认内容，先按打回处理")
                                : ReviewResult.of(Verdict.RETURN, localFindings, "local-rules",
                                        pickSticker(Verdict.RETURN, localFindings));
                    });
        }

        // ③ 有人能终审 → 入人工队列（先入队，AI 意见异步补）
        if (hasHumanFinalizer(server)) {
            ReviewResult pending = ReviewResult.of(Verdict.PENDING, List.of(), "pending-human",
                    StickerRegistry.forState("submitted").map(s -> s.id().toString()).orElse(""));
            SubmissionQueue.enqueue(sub, System.currentTimeMillis());
            if (cfg.usable()) {
                AiReviewer.review(cfg, sub.title(), aiText, localFindings, diffNote)
                        .thenApplyAsync(ai -> {
                            if (ai.available()) {
                                AiOpinions.putOutcome(sub.id(), ai.findings(), ai.note());
                            }
                            return pending;
                        }, server)
                        .exceptionally(ex -> {
                            MeowMachine.LOG.warn("[喵呜机] 人工队列的 AI 预审失败（不影响入队）", ex);
                            return pending;
                        });
            }
            return CompletableFuture.completedFuture(pending);
        }

        // ④ 没人能终审 → 本地规则先判
        if (!localFindings.isEmpty()) {
            String sticker = pickSticker(Verdict.RETURN, localFindings);
            MeowMachine.LOG.info("[喵呜机] 无人工可审，本地规则命中 {} 条，自动打回 author={}",
                    localFindings.size(), author.getName().getString());
            return CompletableFuture.completedFuture(
                    ReviewResult.of(Verdict.RETURN, localFindings, "local-rules", sticker));
        }

        // ⑤ 没人审 → AI
        if (cfg.usable()) {
            return AiReviewer.review(cfg, sub.title(), aiText, java.util.List.of(), diffNote)
                    .thenApplyAsync(ai -> {
                        if (!ai.available()) {
                            return fallbackReturn("AI 审核暂时不可用，无法确认内容，先按打回处理，稍后可重新投稿");
                        }
                        AiOpinions.putOutcome(sub.id(), ai.findings(), ai.note());
                        String aiSticker = StickerRegistry.exists(ai.stickerId()) ? ai.stickerId() : null;
                        boolean pass = ai.findings().isEmpty();
                        if (aiSticker != null) {
                            boolean approvedSticker = "approved".equals(StickerRegistry.stateOf(aiSticker));
                            if (pass != approvedSticker) {
                                aiSticker = null;
                            }
                        }
                        if (pass) {
                            String sticker = aiSticker != null ? aiSticker
                                    : StickerRegistry.forState("approved")
                                            .map(s -> s.id().toString()).orElse("");
                            List<ReviewFinding> note = (ai.note() == null || ai.note().isBlank())
                                    ? List.of() : List.of(ReviewFinding.unexplained(ai.note().trim()));
                            return ReviewResult.of(Verdict.PASS, note, "ai", sticker);
                        }
                        List<ReviewFinding> findings = new java.util.ArrayList<>(ai.findings());
                        if (ai.note() != null && !ai.note().isBlank()) {
                            findings.add(ReviewFinding.unexplained(ai.note().trim()));
                        }
                        String sticker = aiSticker != null ? aiSticker
                                : pickSticker(Verdict.RETURN, ai.findings());
                        if (prevApproved.isPresent()) {
                            ReturnRequests.request(sub.id(), "AI",
                                    "AI 判定需要修改" + (aiRantReason.isBlank() ? ""
                                            : "：" + aiRantReason.replace("\n", "；")));
                        }
                        return ReviewResult.of(Verdict.RETURN, findings, "ai", sticker);
                    }, server).exceptionally(ex -> {
                        MeowMachine.LOG.error("[喵呜机] 自动审核异常，按兜底打回处理", ex);
                        return fallbackReturn("审核异常，无法确认内容，先按打回处理");
                    });
        }

        // ⑥ 既没人也没 AI → 兜底打回
        return CompletableFuture.completedFuture(fallbackReturn("没人审，也没查出具体问题"));
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
        // ①.5 打回但没有任何规则图可用 → 用「要不直接打回算了」那张
        if (verdict == Verdict.RETURN && StickerRegistry.exists("meow_machine:just_return_it")) {
            return "meow_machine:just_return_it";
        }
        // ② 按结论挑状态图
        String state = switch (verdict) {
            case PASS -> "approved";
            case REVISE -> "revising";
            case PENDING -> "submitted";
            case RETURN -> "returned";
        };
        var byState = StickerRegistry.forState(state);
        if (byState.isPresent()) {
            return byState.get().id().toString();
        }
        // ③ 兜底
        return StickerRegistry.fallback().map(s -> s.id().toString()).orElse("");
    }

    /**
     * 兜底打回：说不出依据（或 AI 不可用），用 fallback 梗图。
     *
     * @param why 给作者看的一句话（区分「没人审」和「AI 挂了」）
     */
    private static ReviewResult fallbackReturn(String why) {
        String sticker = StickerRegistry.fallback().map(s -> s.id().toString()).orElse("");
        return ReviewResult.of(Verdict.RETURN, List.of(ReviewFinding.unexplained(why)), "fallback", sticker);
    }

    /**
     * 当前是否有人能终审。
     *
     * <p>注意不能只看名册：**任何一个在线 OP 都天然能终审**，所以这里扫在线玩家；
     * 名册成员不必在线也算「有人能终审」（人工优先的语义：名册存在即代表这批人负责审核）。
     */
    private static boolean hasHumanFinalizer(MinecraftServer server) {
        if (server == null) {
            return false;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            // 在线 OP 或在线名册成员都算
            if (ReviewerRoster.isOp(p) || ReviewerRoster.canFinalize(p)) {
                return true;
            }
        }
        // 名册里有人但全离线：不再无限期挂在 PENDING（旧实现会让队列永远卡住），
        // 交给本地规则/AI 先出结论，作者之后还能重新投稿。
        return false;
    }
}
