package io.github.zgxhzhr.meowmachine.network;

import io.github.zgxhzhr.meowmachine.MeowMachine;
import io.github.zgxhzhr.meowmachine.ai.LlmConfig;
import io.github.zgxhzhr.meowmachine.review.DraftStore;
import io.github.zgxhzhr.meowmachine.review.ReviewResult;
import io.github.zgxhzhr.meowmachine.review.ReviewService;
import io.github.zgxhzhr.meowmachine.review.Submission;
import io.github.zgxhzhr.meowmachine.review.SubmissionQueue;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：提交一篇稿件。
 *
 * <p>服务端做三件事，缺一不可：
 * <ol>
 *   <li>再夹一次长度（客户端限制只是体验，不能当安全边界）</li>
 *   <li>校验当前草稿**还能不能编辑**（已通过的稿子不许靠伪造包重投）</li>
 *   <li>把审核结论**写回草稿状态**——0.1.0 只在人工裁决时写回，
 *       本地规则/AI/兜底三条路径的草稿永远停在 SUBMITTED</li>
 * </ol>
 */
public record SubmitPacket(String title, String body) {

    public static void encode(SubmitPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.title, Network.MAX_TITLE_LEN);
        buf.writeUtf(msg.body, Network.MAX_BODY_LEN);
    }

    public static SubmitPacket decode(FriendlyByteBuf buf) {
        return new SubmitPacket(buf.readUtf(Network.MAX_TITLE_LEN), buf.readUtf(Network.MAX_BODY_LEN));
    }

    public static void handle(SubmitPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        ServerPlayer player = c.getSender();
        if (player == null) {
            c.setPacketHandled(true);
            return;
        }
        // 服务端再夹一次长度：客户端限制只是体验，不能当安全边界
        String title = clamp(msg.title, Network.MAX_TITLE_LEN);
        String body = clamp(msg.body, Network.MAX_BODY_LEN);

        // 复用已有草稿的 id（这样「改完再投」还是同一篇，版本号继续累加）
        Submission existing = DraftStore.load(player);
        if (!existing.state().editableByAuthor()) {
            // 已通过：状态机里写着不可编辑，但 0.1.0 从来没有一行代码执行这条规则
            Network.CHANNEL.sendTo(NoticePacket.of("chat.meow_machine.cannot_edit"),
                    player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
            c.setPacketHandled(true);
            return;
        }
        // 编辑中的稿子不再留在人工队列里（否则审核员会审到半成品）
        if (existing.state() == io.github.zgxhzhr.meowmachine.review.SubmissionState.SUBMITTED) {
            SubmissionQueue.drop(existing.id());
        }

        Submission candidate = existing.withContent(title, body);
        if (candidate.isEmpty()) {
            Network.CHANNEL.sendTo(NoticePacket.of("chat.meow_machine.empty_submission"),
                    player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
            c.setPacketHandled(true);
            return;
        }
        Submission sub = candidate.submit();
        DraftStore.save(player, sub);

        // 走审核流水线：本地规则 → 人工 → AI → 兜底。可能异步完成（AI 路径）。
        // 配置走内存缓存：以前每条投稿都在主线程同步读一次磁盘。
        LlmConfig cfg = LlmConfig.cached(FMLPaths.CONFIGDIR.get());

        ReviewService.process(player.getServer(), player, sub, cfg)
                .thenAccept(result -> {
                    // AI 路径可能在别的线程完成，回包必须回到主线程
                    var server = player.getServer();
                    if (server == null) {
                        return;
                    }
                    server.execute(() -> {
                        MeowMachine.LOG.info("[喵呜机] 投稿处理完毕 player={} verdict={} by={} findings={}",
                                player.getName().getString(), result.verdict(),
                                result.reviewer(), result.findings().size());
                        // 结论写回草稿：PENDING 保持 SUBMITTED，其余落到 APPROVED/RETURNED
                        Submission updated = sub.apply(result);
                        if (updated.state() != sub.state()) {
                            DraftStore.save(player, updated);
                io.github.zgxhzhr.meowmachine.review.SubmissionHistory.record(updated);
                        }
                        Network.CHANNEL.sendTo(ReviewResultPacket.from(result),
                                player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
                    });
                });

        c.setPacketHandled(true);
    }

    private static String clamp(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
