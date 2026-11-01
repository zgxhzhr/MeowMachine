package io.github.zgxhzhr.meowmachine.network;

import io.github.zgxhzhr.meowmachine.ai.LlmConfig;
import io.github.zgxhzhr.meowmachine.client.AdminScreenOpener;
import io.github.zgxhzhr.meowmachine.review.ResultInbox;
import io.github.zgxhzhr.meowmachine.review.ReviewerRoster;
import io.github.zgxhzhr.meowmachine.review.SubmissionQueue;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 → 客户端：管理后台的当前状态。
 *
 * <p><b>不含 API 密钥</b>：早先把 key 原样发给客户端、还在输入框里明文显示，
 * 等于任何能截到包/看到屏幕的人都拿到密钥。现在只发「是否已配置」（{@code hasKey}），
 * 客户端输入框留空表示「不修改」，服务端只在收到非空值时覆盖。
 *
 * @param aiEnabled  AI 审核是否启用
 * @param aiTakeover AI 是否接管全部审核
 * @param pending    待审稿件数
 * @param inbox      待补发的离线结论数
 * @param reviewers  名册人数
 * @param hasKey     是否已配置 API 密钥
 * @param endpoint   AI 域名
 * @param model      模型名
 */
public record AdminStatePacket(boolean aiEnabled, boolean aiTakeover,
                               int pending, int inbox, int reviewers, boolean hasKey,
                               String endpoint, String model) {

    public static AdminStatePacket collect() {
        LlmConfig cfg = LlmConfig.cached(FMLPaths.CONFIGDIR.get());
        return new AdminStatePacket(cfg.enabled(), cfg.takeover(),
                SubmissionQueue.size(),
                ResultInbox.size(), ReviewerRoster.members().size(), !cfg.apiKey().isBlank(),
                cfg.endpoint(), cfg.model());
    }

    public static void encode(AdminStatePacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.aiEnabled);
        buf.writeBoolean(msg.aiTakeover);
        buf.writeVarInt(msg.pending);
        buf.writeVarInt(msg.inbox);
        buf.writeVarInt(msg.reviewers);
        buf.writeBoolean(msg.hasKey);
        buf.writeUtf(msg.endpoint, 256);
        buf.writeUtf(msg.model, 128);
    }

    public static AdminStatePacket decode(FriendlyByteBuf buf) {
        return new AdminStatePacket(buf.readBoolean(), buf.readBoolean(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(),
                buf.readUtf(256), buf.readUtf(128));
    }

    public static void handle(AdminStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> AdminScreenOpener.open(msg));
        c.setPacketHandled(true);
    }
}
