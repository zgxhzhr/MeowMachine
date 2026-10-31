package io.github.zgxhzhr.meowmachine;

import io.github.zgxhzhr.meowmachine.network.Network;
import io.github.zgxhzhr.meowmachine.registry.ModItems;
import io.github.zgxhzhr.meowmachine.review.ReviewerRoster;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 喵呜机：内容审核模拟器。
 *
 * <p>核心流程：玩家用「投稿卷轴」写内容 → 提交 → 审核（人工优先 / 本地规则兜底 / AI 可选接管）
 * → 结果通过梗图反馈。梗图由数据包动态注册，不硬编码。
 */
@Mod(MeowMachine.MOD_ID)
public final class MeowMachine {

    public static final String MOD_ID = "meow_machine";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    public MeowMachine() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(bus);
        ModItems.TABS.register(bus);
        Network.register();
        // 名册必须在开局读一次：0.1.0 漏了这一步，导致 file 恒为 null、
        // save() 全程空转，任命与 selfReview 开关重启就丢。
        bus.addListener(this::commonSetup);
        LOG.info("[喵呜机] 加载完成");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> ReviewerRoster.load(FMLPaths.CONFIGDIR.get()));
    }
}
