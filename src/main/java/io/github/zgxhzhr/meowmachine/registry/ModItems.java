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

    /** 管理终端：右键打开审核后台（旧物品，等价于「审核员」档） */
    public static final RegistryObject<Item> ADMIN_TERMINAL =
            ITEMS.register("admin_terminal",
                    () -> new AdminTerminalItem(new Item.Properties().stacksTo(1),
                            io.github.zgxhzhr.meowmachine.privilege.AccessTier.REVIEWER));

    /** 分档审核终端：助理 / 审核员 / 站长，等级不够用不了 */
    public static final RegistryObject<Item> REVIEW_TERMINAL_ASSISTANT =
            ITEMS.register("review_terminal_assistant",
                    () -> new AdminTerminalItem(new Item.Properties().stacksTo(1),
                            io.github.zgxhzhr.meowmachine.privilege.AccessTier.ASSISTANT));
    public static final RegistryObject<Item> REVIEW_TERMINAL_REVIEWER =
            ITEMS.register("review_terminal_reviewer",
                    () -> new AdminTerminalItem(new Item.Properties().stacksTo(1),
                            io.github.zgxhzhr.meowmachine.privilege.AccessTier.REVIEWER));

    /** 18 张梗图对应的物品 id（顺序不重要，按名字映射） */
    public static final String[] STICKER_IDS = {
            "under_review", "dont_want_revise", "revising", "just_return_it",
            "water_again", "ai_slop", "mt_god", "format_broken",
            "getting_worse", "human_wrote_this", "approved_progress", "approved_plain",
            "approved_short", "approved_fix", "helpless", "kurushi", "laowu", "meowmeow",
            "loop_again", "no_period", "which_version", "vague_words", "punct_storm"
    };

    /**
     * 梗图食物属性：{饥饿值, 绝对饱和度}。
     *
     * <p>必须在注册期确定（FoodProperties 不能运行时改），所以这里与
     * {@code data/meow_machine/stickers/*.json} 的 food 字段保持一致；
     * 挂上原版食物属性后，AppleSkin 之类的模组才会显示饥饿/饱和度图标。
     */
    private static final java.util.Map<String, float[]> STICKER_FOOD = java.util.Map.ofEntries(
            java.util.Map.entry("under_review", new float[]{2, 0.4f}),
            java.util.Map.entry("dont_want_revise", new float[]{4, 1.2f}),
            java.util.Map.entry("revising", new float[]{3, 0.6f}),
            java.util.Map.entry("just_return_it", new float[]{1, 0.0f}),
            java.util.Map.entry("water_again", new float[]{4, 1.6f}),
            java.util.Map.entry("ai_slop", new float[]{6, 4.8f}),
            java.util.Map.entry("mt_god", new float[]{5, 2.4f}),
            java.util.Map.entry("format_broken", new float[]{5, 3.0f}),
            java.util.Map.entry("getting_worse", new float[]{3, 0.8f}),
            java.util.Map.entry("human_wrote_this", new float[]{2, 1.0f}),
            java.util.Map.entry("approved_progress", new float[]{6, 6.0f}),
            java.util.Map.entry("approved_plain", new float[]{6, 7.2f}),
            java.util.Map.entry("approved_short", new float[]{4, 2.4f}),
            java.util.Map.entry("approved_fix", new float[]{5, 4.0f}),
            java.util.Map.entry("helpless", new float[]{3, 1.4f}),
            java.util.Map.entry("kurushi", new float[]{2, 0.2f}),
            java.util.Map.entry("laowu", new float[]{4, 1.5f}),
            java.util.Map.entry("meowmeow", new float[]{7, 6.0f}),
            java.util.Map.entry("loop_again", new float[]{5, 2.0f}),
            java.util.Map.entry("no_period", new float[]{3, 1.8f}),
            java.util.Map.entry("which_version", new float[]{4, 3.2f}),
            java.util.Map.entry("vague_words", new float[]{2, 0.6f}),
            java.util.Map.entry("punct_storm", new float[]{6, 2.8f})
    );

    /** 梗图 id → 物品 */
    private static final java.util.Map<String, RegistryObject<Item>> STICKER_ITEMS =
            new java.util.LinkedHashMap<>();

    static {
        for (String id : STICKER_IDS) {
            float[] food = STICKER_FOOD.getOrDefault(id, new float[]{0, 0});
            Item.Properties props = new Item.Properties().stacksTo(16);
            if (food[0] >= 1) {
                props = props.food(new net.minecraft.world.food.FoodProperties.Builder()
                        .nutrition((int) food[0])
                        .saturationMod(food[1] / (food[0] * 2f))
                        .alwaysEat()
                        .build());
            }
            Item.Properties finalProps = props;
            STICKER_ITEMS.put(id, ITEMS.register("sticker_" + id,
                    () -> new io.github.zgxhzhr.meowmachine.item.StickerItem(finalProps, id)));
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
                    () -> new io.github.zgxhzhr.meowmachine.item.MeowIconItem(new Item.Properties(),
                            io.github.zgxhzhr.meowmachine.privilege.AccessTier.REVIEWER));

    /** 分档喵喵喵：助理 / 审核员 / 站长，等级不够用不了 */
    public static final RegistryObject<Item> MEOW_ICON_ASSISTANT =
            ITEMS.register("meow_icon_assistant",
                    () -> new io.github.zgxhzhr.meowmachine.item.MeowIconItem(new Item.Properties(),
                            io.github.zgxhzhr.meowmachine.privilege.AccessTier.ASSISTANT));
    public static final RegistryObject<Item> MEOW_ICON_REVIEWER =
            ITEMS.register("meow_icon_reviewer",
                    () -> new io.github.zgxhzhr.meowmachine.item.MeowIconItem(new Item.Properties(),
                            io.github.zgxhzhr.meowmachine.privilege.AccessTier.REVIEWER));
    public static final RegistryObject<Item> MEOW_ICON_NORMAL =
            ITEMS.register("meow_icon_normal",
                    () -> new io.github.zgxhzhr.meowmachine.item.MeowIconItem(new Item.Properties(),
                            io.github.zgxhzhr.meowmachine.privilege.AccessTier.NORMAL));

    /** 神秘合成的输出占位（合成前只显示它） */
    public static final RegistryObject<Item> MYSTERY_RESULT =
            ITEMS.register("mystery_result",
                    () -> new io.github.zgxhzhr.meowmachine.item.MysteryResultItem(new Item.Properties()));

    /**
     * 按梗图 id 找对应物品；数据包新增的图**没有物品**（物品必须在编译期注册），
     * 这种图仍然可以用于审核，但不会产出物品。
     */
    public static net.minecraft.world.item.Item stickerItemOrNull(String stickerId) {
        if (stickerId == null || stickerId.isBlank()) {
            return null;
        }
        String path = stickerId.contains(":") ? stickerId.substring(stickerId.indexOf(':') + 1) : stickerId;
        var ro = STICKER_ITEMS.get(path);
        return ro == null ? null : ro.get();
    }

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MeowMachine.MOD_ID))
                    .icon(() -> new ItemStack(SUBMISSION_SCROLL.get()))
                    .displayItems((params, output) -> {
                        output.accept(SUBMISSION_SCROLL.get());
                        output.accept(ADMIN_TERMINAL.get());
                        output.accept(MEOW_ICON.get());
                        output.accept(MYSTERY_RESULT.get());
                        // 18 张梗图物品：创造栏里能直接翻看（正常游戏靠审核结果获得）
                        for (RegistryObject<Item> sticker : STICKER_ITEMS.values()) {
                            output.accept(sticker.get());
                        }
                    })
                    .build());

    private ModItems() {
    }
}
