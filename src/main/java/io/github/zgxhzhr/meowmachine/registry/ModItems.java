package io.github.zgxhzhr.meowmachine.registry;

import io.github.zgxhzhr.meowmachine.MeowMachine;
import io.github.zgxhzhr.meowmachine.item.AdminTerminalItem;
import io.github.zgxhzhr.meowmachine.item.SubmissionScrollItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 物品与创造标签注册。 */
public final class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MeowMachine.MOD_ID);

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MeowMachine.MOD_ID);

    /**
     * 投稿卷轴：右击打开投稿界面。
     *
     * <p>堆叠数为 1 —— 它承载「当前作者的草稿」这一状态，叠起来会互相干扰。
     */
    public static final RegistryObject<Item> SUBMISSION_SCROLL =
            ITEMS.register("submission_scroll",
                    () -> new SubmissionScrollItem(new Item.Properties().stacksTo(1)));

    /** 管理终端：右键打开审核后台（仅 OP） */
    public static final RegistryObject<Item> ADMIN_TERMINAL =
            ITEMS.register("admin_terminal",
                    () -> new AdminTerminalItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MeowMachine.MOD_ID))
                    .icon(() -> new ItemStack(SUBMISSION_SCROLL.get()))
                    .displayItems((params, output) -> {
                        output.accept(SUBMISSION_SCROLL.get());
                        output.accept(ADMIN_TERMINAL.get());
                    })
                    .build());

    private ModItems() {
    }
}
