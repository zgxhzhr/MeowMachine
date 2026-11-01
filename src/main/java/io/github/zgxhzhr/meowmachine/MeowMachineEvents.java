package io.github.zgxhzhr.meowmachine;

import io.github.zgxhzhr.meowmachine.command.MeowCommands;
import io.github.zgxhzhr.meowmachine.review.ResultInbox;
import io.github.zgxhzhr.meowmachine.review.ReviewActions;
import io.github.zgxhzhr.meowmachine.review.ReviewerRoster;
import io.github.zgxhzhr.meowmachine.review.SubmissionHistory;
import io.github.zgxhzhr.meowmachine.review.SubmissionQueue;
import io.github.zgxhzhr.meowmachine.sticker.StickerRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 服务端侧的事件挂钩。
 *
 * <ul>
 *   <li>资源重载 → 加载梗图注册表（**仅服务端**：客户端渲染用服务端发来的纹理路径）</li>
 *   <li>命令注册 → {@code /meow} 命令树</li>
 *   <li>服务端启动 → 把待审队列从世界存档读回来（重启不丢待审）</li>
 *   <li>服务端停止 → 解绑存档后端</li>
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

    /** 服务端启动：绑定队列与信箱存档，恢复上次未审完的稿子、没送出去的结论 */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        SubmissionQueue.attach(event.getServer());
        ResultInbox.attach(event.getServer());
        SubmissionHistory.attach(event.getServer());
        io.github.zgxhzhr.meowmachine.review.ReviewNotes.attach(event.getServer());
    }

    /** 服务端停止：解绑，避免关服过程中继续往存档写 */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        SubmissionQueue.detach();
        ResultInbox.detach();
        SubmissionHistory.detach();
        io.github.zgxhzhr.meowmachine.review.ReviewNotes.detach();
    }

    /**
     * 玩家上线：**每人**首次进入世界发一个审核终端（只发一次）。
     *
     * <p>能不能用由权限决定（右键时服务端校验）：没权限的人会看到「无权限」提示。
     * 按需求：物品人人都有，权限另算。
     */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) {
            return;
        }
        // 扫背包补记梗图收集：创造模式拿的、旧版本已有的都算，否则收藏家成就永远差几张
        io.github.zgxhzhr.meowmachine.progress.StickerAlbum.scanInventory(sp);

        net.minecraft.nbt.CompoundTag data = sp.getPersistentData();
        if (data.getBoolean("meow_machine_terminal_granted")) {
            return;
        }
        data.putBoolean("meow_machine_terminal_granted", true);
        sp.getInventory().add(new net.minecraft.world.item.ItemStack(
                io.github.zgxhzhr.meowmachine.registry.ModItems.ADMIN_TERMINAL.get()));
        sp.sendSystemMessage(Component.translatable("chat.meow_machine.terminal_granted"));
        MeowMachine.LOG.info("[喵呜机] 已发放审核终端给 {}", sp.getName().getString());
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
