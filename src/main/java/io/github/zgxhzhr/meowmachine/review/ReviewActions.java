package io.github.zgxhzhr.meowmachine.review;

import io.github.zgxhzhr.meowmachine.network.Network;
import io.github.zgxhzhr.meowmachine.network.ReviewResultPacket;
import io.github.zgxhzhr.meowmachine.sticker.StickerRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;

import java.util.List;
import java.util.Optional;

/**
 * 审核裁决的公共实现。命令（{@code /meow review decide}）与审核界面（按钮）都走这里，
 * 避免两处各写一份判定与通知逻辑。
 */
public final class ReviewActions {

    private ReviewActions() {
    }

    /** 裁决结果 */
    public enum Outcome {
        OK,
        NO_PERMISSION,
        NO_SUCH_ID,
        CANNOT_FINALIZE
    }

    /**
     * 对一份待审稿件给出结论。
     *
     * @param reviewer 裁决人（可为 null 表示控制台）
     * @param idPrefix 稿件 id 前缀（至少 4 个字符，便于在界面上显示短 id）
     * @param verdict  结论
     */
    public static Outcome decide(ServerPlayer reviewer, String idPrefix, Verdict verdict) {
        Optional<SubmissionQueue.Entry> found = SubmissionQueue.snapshot().stream()
                .filter(e -> e.submission().id().toString().startsWith(idPrefix))
                .findFirst();
        if (found.isEmpty()) {
            return Outcome.NO_SUCH_ID;
        }
        Submission sub = found.get().submission();

        if (!ReviewerRoster.canFinalize(reviewer, sub.authorUuid())) {
            return Outcome.CANNOT_FINALIZE;
        }

        SubmissionQueue.claim(sub.id());

        String sticker = switch (verdict) {
            case PASS -> StickerRegistry.forState("approved").map(s -> s.id().toString()).orElse("");
            case REVISE -> StickerRegistry.forState("revising").map(s -> s.id().toString()).orElse("");
            case RETURN -> StickerRegistry.forState("returned").map(s -> s.id().toString()).orElse("");
        };
        ReviewResult result = ReviewResult.of(verdict, List.of(),
                reviewer == null ? "console" : reviewer.getName().getString(), sticker);

        // 通知作者 + 更新草稿状态
        if (reviewer != null && reviewer.getServer() != null) {
            ServerPlayer author = reviewer.getServer().getPlayerList().getPlayer(sub.authorUuid());
            if (author != null) {
                DraftStore.save(author, sub.apply(result));
                Network.CHANNEL.sendTo(
                        new ReviewResultPacket(result.verdict().name(), List.of(),
                                result.stickerId(), ""),
                        author.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
            }
        }
        return Outcome.OK;
    }

    /** 把 Outcome 转成给玩家看的文案 key */
    public static String messageKey(Outcome outcome) {
        return switch (outcome) {
            case OK -> "command.meow_machine.decided_ok";
            case NO_PERMISSION -> "command.meow_machine.no_permission";
            case NO_SUCH_ID -> "command.meow_machine.no_such_id";
            case CANNOT_FINALIZE -> "command.meow_machine.cannot_finalize";
        };
    }
}
