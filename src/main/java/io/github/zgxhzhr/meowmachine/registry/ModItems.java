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

    /** 18 张梗图对应的物品 id（顺序不重要，按名字映射） */
    public static final String[] STICKER_IDS = {
            "under_review", "dont_want_revise", "revising", "just_return_it",
            "water_again", "ai_slop", "mt_god", "format_broken",
            "getting_worse", "human_wrote_this", "approved_progress", "approved_plain",
            "approved_short", "approved_fix", "helpless", "kurushi", "laowu", "meowmeow"
    };

    /** 梗图 id → 物品 */
    private static final java.util.Map<String, RegistryObject<Item>> STICKER_ITEMS =
            new java.util.LinkedHashMap<>();

    static {
        for (String id : STICKER_IDS) {
            STICKER_ITEMS.put(id, ITEMS.register("sticker_" + id,
                    () -> new io.github.zgxhzhr.meowmachine.item.StickerItem(
                            new Item.Properties().stacksTo(16), id)));
        }
    }

    /** 某个梗图 id 的物品栈（未知 id 返回空栈） */
    public static net.minecraft.world.item.ItemStack stickerStack(String stickerId) {
        if (stickerId == null || stickerId.isBlank()) {
            return net.minecraft.world.item.ItemStack.EMPTY;
        }
        String path = stickerId.contains(":")
                ? stickerId.substring(stickerId.indexOf(':') + 1) : stickerId;
        RegistryObject<Item> item = STICKER_ITEMS.get(path);
        return item == null
                ? net.minecraft.world.item.ItemStack.EMPTY
                : new net.minecraft.world.item.ItemStack(item.get());
    }

    /**
     * 成就图标用的物品（贴图是「喵喵喵」表情）。
     *
     * <p>成就有 {@code display.icon} 只能引用物品，不能直接引用贴图；
     * 所以注册一个纯图标物品：不进创造栏、没有配方，正常游戏拿不到。
     */
    public static final RegistryObject<Item> MEOW_ICON =
            ITEMS.register("meow_icon",
                    () -> new Item(new Item.Properties()));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MeowMachine.MOD_ID))
                    .icon(() -> new ItemStack(SUBMISSION_SCROLL.get()))
                    .displayItems((params, output) -> {
                        output.accept(SUBMISSION_SCROLL.get());
                        output.accept(ADMIN_TERMINAL.get());
                        output.accept(MEOW_ICON.get());
                        // 18 张梗图物品：创造栏里能直接翻看（正常游戏靠审核结果获得）
                        for (RegistryObject<Item> sticker : STICKER_ITEMS.values()) {
                            output.accept(sticker.get());
                        }
                    })
                    .build());

    private ModItems() {
    }
}
