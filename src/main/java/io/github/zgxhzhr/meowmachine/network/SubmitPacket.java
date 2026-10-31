package io.github.zgxhzhr.meowmachine.network;

import io.github.zgxhzhr.meowmachine.MeowMachine;
import io.github.zgxhzhr.meowmachine.review.LocalRules;
import io.github.zgxhzhr.meowmachine.review.ReviewFinding;
import io.github.zgxhzhr.meowmachine.review.ReviewResult;
import io.github.zgxhzhr.meowmachine.ai.LlmConfig;
import io.github.zgxhzhr.meowmachine.review.DraftStore;
import io.github.zgxhzhr.meowmachine.review.ReviewService;
import io.github.zgxhzhr.meowmachine.review.Submission;
import io.github.zgxhzhr.meowmachine.review.SubmissionState;
import io.github.zgxhzhr.meowmachine.review.Verdict;
import io.github.zgxhzhr.meowmachine.sticker.StickerRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/** 客户端 → 服务端：提交一篇稿件。 */
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
        Submission sub = existing.withContent(title, body).submit();
        DraftStore.save(player, sub);

        // 走审核流水线：本地规则 → 人工 → AI → 兜底。可能异步完成（AI 路径）。
        LlmConfig cfg = LlmConfig.loadOrCreate(
                net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get());

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
                        List<String> ruleIds = result.findings().stream()
                                .map(f -> f.ruleId() == null ? "?" : String.valueOf(f.ruleId()))
                                .toList();
                        Network.CHANNEL.sendTo(new ReviewResultPacket(
                                        result.verdict().name(), ruleIds,
                                        result.stickerId(), summarize(result.findings())),
                                player.connection.connection,
                                net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
                    });
                });

        c.setPacketHandled(true);
    }

    private static String summarize(List<ReviewFinding> findings) {
        if (findings.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(3, findings.size()); i++) {
            if (i > 0) {
                sb.append(" / ");
            }
            sb.append(findings.get(i).reason());
        }
        return sb.toString();
    }

    private static String clamp(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
