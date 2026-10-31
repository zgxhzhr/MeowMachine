package io.github.zgxhzhr.meowmachine;

import io.github.zgxhzhr.meowmachine.command.MeowCommands;
import io.github.zgxhzhr.meowmachine.review.ResultInbox;
import io.github.zgxhzhr.meowmachine.review.ReviewActions;
import io.github.zgxhzhr.meowmachine.sticker.StickerRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 服务端侧的事件挂钩。
 *
 * <ul>
 *   <li>资源重载 → 加载梗图注册表（**仅服务端**：客户端渲染用服务端发来的纹理路径）</li>
 *   <li>命令注册 → {@code /meow} 命令树</li>
 *   <li>玩家登录 → 补发他离线期间被裁决的结果（{@link ResultInbox}）</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = MeowMachine.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MeowMachineEvents {

    private MeowMachineEvents() {
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new StickerRegistry());
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        MeowCommands.register(event.getDispatcher());
    }

    /**
     * 玩家上线：把离线期间攒下的审核结果补发给他。
     *
     * <p>没有这一步，审核员对离线作者的裁决就等于扔进黑洞：
     * 队列已 claim、草稿没写、也没通知（0.1.0 就是这么丢的）。
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        var pending = ResultInbox.drain(player.getUUID());
        if (pending.isEmpty()) {
            return;
        }
        player.getServer().execute(() -> {
            for (ResultInbox.Delivery d : pending) {
                ReviewActions.deliverResult(player, d.submissionId(), d.result());
                MeowMachine.LOG.info("[喵呜机] 补发离线审核结果 player={} verdict={}",
                        player.getName().getString(), d.result().verdict());
            }
        });
    }
}
