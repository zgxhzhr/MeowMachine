package io.github.zgxhzhr.meowmachine;

import io.github.zgxhzhr.meowmachine.command.MeowCommands;
import io.github.zgxhzhr.meowmachine.sticker.StickerRegistry;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 服务端侧的事件挂钩。
 *
 * <ul>
 *   <li>资源重载 → 加载梗图注册表（客户端与服务端各一份）</li>
 *   <li>命令注册 → {@code /meow} 命令树</li>
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
}
