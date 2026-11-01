package io.github.zgxhzhr.meowmachine.network;

import io.github.zgxhzhr.meowmachine.MeowMachine;
import io.github.zgxhzhr.meowmachine.ai.LlmConfig;
import io.github.zgxhzhr.meowmachine.review.ReviewerRoster;
import io.github.zgxhzhr.meowmachine.review.SubmissionQueue;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：管理后台上的操作。
 *
 * <p>**服务端会再校验一次权限**（{@code hasPermission(2)}）——
 * 客户端能伪造任何包，不能只靠界面上「只有 OP 能打开」这一点。
 */
public record AdminActionPacket(Action action, String endpoint, String apiKey, String model) {

    public enum Action {
        TOGGLE_AI,
        TOGGLE_TAKEOVER,
        OPEN_REVIEW,
        SAVE_AI_CONFIG
    }

    public static AdminActionPacket of(Action action) {
        return new AdminActionPacket(action, "", "", "");
    }

    public static AdminActionPacket saveAiConfig(String endpoint, String apiKey, String model) {
        return new AdminActionPacket(Action.SAVE_AI_CONFIG,
                endpoint == null ? "" : endpoint,
                apiKey == null ? "" : apiKey,
                model == null ? "" : model);
    }

    public static void encode(AdminActionPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.action);
        buf.writeUtf(msg.endpoint, 256);
        buf.writeUtf(msg.apiKey, 256);
        buf.writeUtf(msg.model, 128);
    }

    public static AdminActionPacket decode(FriendlyByteBuf buf) {
        return new AdminActionPacket(buf.readEnum(Action.class),
                buf.readUtf(256), buf.readUtf(256), buf.readUtf(128));
    }

    public static void handle(AdminActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        ServerPlayer player = c.getSender();
        if (player == null) {
            c.setPacketHandled(true);
            return;
        }
        if (!player.hasPermissions(2)) {
            MeowMachine.LOG.warn("[喵呜机] 非 OP 尝试操作管理后台: {}",
                    player.getName().getString());
            c.setPacketHandled(true);
            return;
        }

        var configDir = FMLPaths.CONFIGDIR.get();
        switch (msg.action) {
            case TOGGLE_AI -> {
                LlmConfig cfg = LlmConfig.cached(configDir);
                LlmConfig.save(configDir, cfg.withSwitches(!cfg.enabled(), cfg.takeover()));
            }
            case TOGGLE_TAKEOVER -> {
                LlmConfig cfg = LlmConfig.cached(configDir);
                LlmConfig.save(configDir, cfg.withSwitches(cfg.enabled(), !cfg.takeover()));
            }
            case SAVE_AI_CONFIG -> {
                LlmConfig current = LlmConfig.cached(configDir);
                String key = msg.apiKey == null ? "" : msg.apiKey.trim();
                // 客户端不回传密钥：留空就沿用旧的（这样改 endpoint/模型不用重填 key）
                LlmConfig updated = current.withAiConfig(msg.endpoint,
                        key.isEmpty() ? current.apiKey() : key, msg.model);
                LlmConfig.save(configDir, updated);
                MeowMachine.LOG.info("[喵呜机] AI 配置已保存 endpoint={} model={} key={}",
                        updated.endpoint(), updated.model(),
                        updated.apiKey().isEmpty() ? "(空)" : "(已设置)");
            }
            case OPEN_REVIEW -> {
                Network.CHANNEL.sendTo(
                        ReviewListPacket.of(SubmissionQueue.snapshot()), player.connection.connection,
                        NetworkDirection.PLAY_TO_CLIENT);
                // 打开待审界面时**不能再回推后台状态包**：那会让客户端把刚打开的审核界面
                // 又 setScreen 回后台（0.2.0 引入的回归，表现就是「待审队列点了没反应」）。
                c.setPacketHandled(true);
                return;
            }
        }

        Network.CHANNEL.sendTo(AdminStatePacket.collect(), player.connection.connection,
                NetworkDirection.PLAY_TO_CLIENT);
        c.setPacketHandled(true);
    }
}
