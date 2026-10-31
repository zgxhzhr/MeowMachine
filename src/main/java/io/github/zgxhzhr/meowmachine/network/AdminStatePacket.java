package io.github.zgxhzhr.meowmachine.network;

import io.github.zgxhzhr.meowmachine.ai.LlmConfig;
import io.github.zgxhzhr.meowmachine.client.AdminScreenOpener;
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
 * <p>带上 endpoint / apiKey / model 是为了**在游戏内配置**（照 TLM 的做法）：
 * 只有 OP 能打开这个界面，所以回传 key 是可接受的。
 *
 * @param aiEnabled  AI 审核是否启用
 * @param aiTakeover AI 是否接管全部审核
 * @param selfReview 单人自审是否开启
 * @param pending    待审稿件数
 * @param hasKey     三项是否都填了（没填时开着也没用）
 * @param endpoint   AI 域名
 * @param apiKey     API 密钥
 * @param model      模型名
 */
public record AdminStatePacket(boolean aiEnabled, boolean aiTakeover, boolean selfReview,
                               int pending, boolean hasKey,
                               String endpoint, String apiKey, String model) {

    public static AdminStatePacket collect() {
        LlmConfig cfg = LlmConfig.loadOrCreate(FMLPaths.CONFIGDIR.get());
        return new AdminStatePacket(cfg.enabled(), cfg.takeover(),
                ReviewerRoster.isSelfReviewEnabled(), SubmissionQueue.size(), cfg.usable(),
                cfg.endpoint(), cfg.apiKey(), cfg.model());
    }

    public static void encode(AdminStatePacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.aiEnabled);
        buf.writeBoolean(msg.aiTakeover);
        buf.writeBoolean(msg.selfReview);
        buf.writeVarInt(msg.pending);
        buf.writeBoolean(msg.hasKey);
        buf.writeUtf(msg.endpoint, 256);
        buf.writeUtf(msg.apiKey, 256);
        buf.writeUtf(msg.model, 128);
    }

    public static AdminStatePacket decode(FriendlyByteBuf buf) {
        return new AdminStatePacket(buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                buf.readVarInt(), buf.readBoolean(),
                buf.readUtf(256), buf.readUtf(256), buf.readUtf(128));
    }

    public static void handle(AdminStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> AdminScreenOpener.open(msg));
        c.setPacketHandled(true);
    }
}
