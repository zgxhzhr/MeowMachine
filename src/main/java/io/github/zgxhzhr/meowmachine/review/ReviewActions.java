package io.github.zgxhzhr.meowmachine.review;

import io.github.zgxhzhr.meowmachine.MeowMachine;
import io.github.zgxhzhr.meowmachine.network.Network;
import io.github.zgxhzhr.meowmachine.network.ReviewResultPacket;
import io.github.zgxhzhr.meowmachine.sticker.StickerRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.List;
import java.util.Optional;

/**
 * 审核裁决的公共实现。命令（{@code /meow review decide}）与审核界面（按钮）都走这里，
 * 避免两处各写一份判定与通知逻辑。
 *
 * <p>三条守住的行为：
 * <ul>
 *   <li><b>服务端二次校验权限</b>：客户端能伪造任何包，这里再查一次 {@code canFinalize}。</li>
 *   <li><b>作者离线也不丢结论</b>：不再静默 return，而是进 {@link ResultInbox}，登录时补发。</li>
 *   <li><b>控制台可裁决</b>：{@code reviewer == null} 表示服务器控制台，
 *       以前会直接判 CANNOT_FINALIZE，与自己的注释自相矛盾。</li>
 * </ul>
 */
public final class ReviewActions {

    private ReviewActions() {
    }

    /** 在线玩家（作者离线时返回 null，成就等依赖在线状态的操作跳过） */
    private static ServerPlayer serverPlayerOf(java.util.UUID uuid) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null || uuid == null ? null : server.getPlayerList().getPlayer(uuid);
    }

    /** 裁决结果 */
    public enum Outcome {
        OK,
        NO_PERMISSION,
        NO_SUCH_ID,
        CANNOT_FINALIZE,
        BAD_VERDICT,
        /** id 前缀太短（容易误伤别人的稿）或匹配到多份 */
        AMBIGUOUS_ID
    }

    /**
     * 对一份待审稿件给出结论（命令用，不带原因）。
     *
     * @param reviewer 裁决人（null 表示服务器控制台）
     * @param idPrefix 稿件 id 前缀（至少 4 个字符，便于在界面上显示短 id）
     * @param verdict  结论（只能是终局结论，不能是 PENDING）
     */
    public static Outcome decide(ServerPlayer reviewer, String idPrefix, Verdict verdict) {
        return decide(reviewer, idPrefix, verdict, -1, "");
    }

    /**
     * 对一份待审稿件给出结论（界面用，可带原因）。
     *
     * @param ruleId 规则号；-1 表示「说不出具体条款」（梗图走状态图/兜底图）
     * @param note   一句话说明，可空
     */
    public static Outcome decide(ServerPlayer reviewer, String idPrefix, Verdict verdict,
                                 int ruleId, String note) {
        return decide(reviewer, idPrefix, verdict, ruleId, note, "");
    }

    /**
     * 带梗图选择的裁决（审核员通过时可手动挑图）。
     *
     * @param stickerId 指定的梗图 id；空/非法/与结论不匹配则按原规则自动挑
     */
    public static Outcome decide(ServerPlayer reviewer, String idPrefix, Verdict verdict,
                                 int ruleId, String note, String stickerId) {
        if (verdict == null || !verdict.isDecidable()) {
            return Outcome.BAD_VERDICT;
        }
        if (idPrefix == null || idPrefix.isBlank()) {
            return Outcome.NO_SUCH_ID;
        }
        // 列表只显示 8 位短 id，前缀太短会撞到别人的稿；且必须唯一
        if (idPrefix.strip().length() < 4) {
            return Outcome.AMBIGUOUS_ID;
        }
        java.util.List<SubmissionQueue.Entry> matches = SubmissionQueue.snapshot().stream()
                .filter(e -> e.submission().id().toString().startsWith(idPrefix.strip()))
                .toList();
        if (matches.isEmpty()) {
            return Outcome.NO_SUCH_ID;
        }
        if (matches.size() > 1) {
            return Outcome.AMBIGUOUS_ID;
        }
        Optional<SubmissionQueue.Entry> found = Optional.of(matches.get(0));
        Submission sub = found.get().submission();

        // 控制台（reviewer == null）天然有权；玩家要过 canFinalize
        if (reviewer != null && !ReviewerRoster.canFinalize(reviewer)) {
            return Outcome.CANNOT_FINALIZE;
        }

        SubmissionQueue.claim(sub.id());

        // 原因：审核员可以选规则号（决定用哪张梗图）+ 补一句话
        // 规则号来自客户端，越界值直接当「说不出依据」处理，避免后续 writeUtf 崩包
        if (ruleId < -1 || ruleId > 35) {
            ruleId = -1;
        }
        List<ReviewFinding> findings = List.of();
        if (ruleId >= 0) {
            String reason = (note == null || note.isBlank())
                    ? "不符合公共编辑规范第 " + ruleId + " 条"
                    : note.trim();
            findings = List.of(new ReviewFinding(ruleId, null, reason));
        } else if (note != null && !note.isBlank()) {
            findings = List.of(ReviewFinding.unexplained(note.trim()));
        }

        // 助理/审核员留的意见：并进给作者的结果里（作为附言式说明），然后清空
        List<ReviewFinding> withNotes = new java.util.ArrayList<>(findings);
        for (String opinion : ReviewNotes.notesOnly(sub.id())) {
            withNotes.add(ReviewFinding.unexplained("审核意见：" + opinion));
        }
        findings = List.copyOf(withNotes);
        ReviewNotes.clear(sub.id());
        AiOpinions.clear(sub.id());

        // 「说不出依据」时用「要不直接打回算了」那张图；其它情况按规则图 → 状态图
        // 审核员手动挑的图优先（通过时必须 approved 系列，其它结论不能是 approved）
        String chosen = (stickerId == null || stickerId.isBlank()) ? null : stickerId.trim();
        if (chosen != null && !StickerRegistry.exists(chosen)) {
            chosen = null;
        }
        if (chosen != null) {
            boolean approvedSticker = "approved".equals(StickerRegistry.stateOf(chosen));
            if ((verdict == Verdict.PASS) != approvedSticker) {
                chosen = null;
            }
        }
        String sticker = chosen != null ? chosen
                : (ruleId >= 0
                        ? StickerRegistry.forRule(ruleId).map(s -> s.id().toString())
                                .orElse(stateSticker(verdict))
                        : unexplainedSticker(verdict));
        ReviewResult result = ReviewResult.of(verdict, findings,
                reviewer == null ? "console" : reviewer.getName().getString(), sticker);

        if (reviewer != null) {
            io.github.zgxhzhr.meowmachine.progress.StatsAchievements.onReview(
                    reviewer, verdict == Verdict.RETURN || verdict == Verdict.REVISE);
        }
        Submission app = sub.apply(result);
        // 人工裁决路径也要发作者侧成就（以前只在自动审核路径发，有审核员时作者成就永远停在 first_submit）
        if (app.authorUuid() != null && serverPlayerOf(app.authorUuid()) != null) {
            var author = serverPlayerOf(app.authorUuid());
            if (verdict == Verdict.PASS) {
                if (app.reEdit()) {
                    io.github.zgxhzhr.meowmachine.progress.StatsAchievements.onEditApproved(author);
                }
                io.github.zgxhzhr.meowmachine.advancement.MeowAdvancements.award(author, "first_approved");
                if (sub.state() == SubmissionState.RETURNED
                    || sub.state() == SubmissionState.REVISING) {
                    io.github.zgxhzhr.meowmachine.advancement.MeowAdvancements.award(author, "revised_approved");
                }
            } else if (verdict == Verdict.RETURN) {
                // 只有真正「打回」才发；「协改」不算
                io.github.zgxhzhr.meowmachine.advancement.MeowAdvancements.award(author, "first_returned");
            }
        }
        SubmissionHistory.record(app);
        ApprovedIndex.record(app);
        if (reviewer != null) {
            io.github.zgxhzhr.meowmachine.advancement.MeowAdvancements.award(
                    reviewer,
                    io.github.zgxhzhr.meowmachine.advancement.MeowAdvancements.FIRST_DECISION);
        }
        deliver(serverOf(reviewer), sub, result);
        return Outcome.OK;
    }

    /**
     * 「说不出依据」用哪张图。
     *
     * <p>打回时要的是「要不直接打回算了」那张（just_return_it），而不是状态图；
     * 协改仍用协改中，通过仍用通过系列。
     */
    private static String unexplainedSticker(Verdict verdict) {
        if (verdict == Verdict.RETURN) {
            String fallback = "meow_machine:just_return_it";
            if (StickerRegistry.exists(fallback)) {
                return fallback;
            }
        }
        return stateSticker(verdict);
    }

    /** 按结论挑状态梗图（规则图没有命中时的退路） */
    private static String stateSticker(Verdict verdict) {
        return switch (verdict) {
            case PASS -> StickerRegistry.forState("approved").map(s -> s.id().toString()).orElse("");
            case REVISE -> StickerRegistry.forState("revising").map(s -> s.id().toString()).orElse("");
            case RETURN -> StickerRegistry.forState("returned").map(s -> s.id().toString()).orElse("");
            case PENDING -> StickerRegistry.forState("submitted").map(s -> s.id().toString()).orElse("");
        };
    }

    /** 把结论送到作者手里：在线就立刻发，离线就进信箱等登录补发 */
    public static void deliver(MinecraftServer server, Submission sub, ReviewResult result) {
        ServerPlayer author = server == null ? null : server.getPlayerList().getPlayer(sub.authorUuid());
        if (author == null) {
            ResultInbox.offer(sub.authorUuid(), sub.id(), result);
            return;
        }
        deliverResult(author, sub.id(), result);
    }

    /**
     * 直接把结论发给某个在线玩家（登录补发也走这里）。
     *
     * <p>草稿状态只在「当前草稿就是被裁决的那份」时才写回，
     * 避免拿一份旧结论覆盖作者正在写的新稿。
     */
    public static void deliverResult(ServerPlayer author, java.util.UUID submissionId, ReviewResult result) {
        if (author == null || result == null) {
            return;
        }
        Submission draft = DraftStore.load(author);
        if (submissionId != null && draft.id().equals(submissionId)) {
            DraftStore.save(author, draft.apply(result));
        } else {
            MeowMachine.LOG.info("[喵呜机] 作者当前草稿与被裁决稿件不一致，只通知不改草稿 author={}",
                    author.getName().getString());
        }
        io.github.zgxhzhr.meowmachine.progress.StickerAlbum.grant(author, result.stickerId());
        Network.CHANNEL.sendTo(ReviewResultPacket.from(result),
                author.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }

    private static MinecraftServer serverOf(ServerPlayer reviewer) {
        if (reviewer != null) {
            return reviewer.getServer();
        }
        return ServerLifecycleHooks.getCurrentServer();
    }

    /** 把 Outcome 转成给玩家看的文案 key */
    public static String messageKey(Outcome outcome) {
        return switch (outcome) {
            case OK -> "command.meow_machine.decided_ok";
            case NO_PERMISSION -> "command.meow_machine.no_permission";
            case NO_SUCH_ID -> "command.meow_machine.no_such_id";
        case AMBIGUOUS_ID -> "command.meow_machine.ambiguous_id";
            case CANNOT_FINALIZE -> "command.meow_machine.cannot_finalize";
            case BAD_VERDICT -> "command.meow_machine.bad_verdict";
        };
    }
}
