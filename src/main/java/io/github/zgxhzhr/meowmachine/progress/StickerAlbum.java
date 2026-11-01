package io.github.zgxhzhr.meowmachine.progress;

import io.github.zgxhzhr.meowmachine.MeowMachine;
import io.github.zgxhzhr.meowmachine.advancement.MeowAdvancements;
import io.github.zgxhzhr.meowmachine.item.StickerItem;
import io.github.zgxhzhr.meowmachine.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 梗图收藏册：记录玩家收集到的**不同种类**梗图，集齐 8 种发成就。
 *
 * <p>两条入口：
 * <ul>
 *   <li>{@link #grant} —— 审核结果发放梗图物品时调用；</li>
 *   <li>{@link #scanInventory} —— 登录时扫背包补记。创造模式直接拿的、或 0.18 之前
 *       就拿到的梗图不会经过发放逻辑，不补扫的话进度永远到不了 8。</li>
 * </ul>
 */
public final class StickerAlbum {

    /** 集齐多少种算「收藏家」 */
    public static final int COLLECTOR_TARGET = 8;
    private static final String KEY = "meow_machine_stickers";
    private static final String ADVANCEMENT = "sticker_collector";

    private StickerAlbum() {
    }

    /**
     * 发一张梗图物品并记录收集进度。
     *
     * @param player    收件人
     * @param stickerId 梗图 id（可带命名空间）
     */
    public static void grant(ServerPlayer player, String stickerId) {
        if (player == null || stickerId == null || stickerId.isBlank()) {
            return;
        }
        ItemStack stack = ModItems.stickerStack(stickerId);
        if (!stack.isEmpty()) {
            // 背包满了就掉在脚边，别悄悄吞掉
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        String path = pathOf(stickerId);
        Set<String> owned = loadOwned(player);
        if (owned.add(path)) {
            saveOwned(player, owned);
            announce(player, stack, owned.size());
        }
    }

    /**
     * 扫背包补记：创造模式拿的、旧版本就有的梗图都算进来。
     *
     * @return 新增的种类数
     */
    public static int scanInventory(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        Set<String> owned = loadOwned(player);
        int before = owned.size();
        ItemStack firstNew = ItemStack.EMPTY;

        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof StickerItem sticker && owned.add(sticker.stickerId())) {
                if (firstNew.isEmpty()) {
                    firstNew = stack;
                }
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.getItem() instanceof StickerItem sticker && owned.add(sticker.stickerId())) {
                if (firstNew.isEmpty()) {
                    firstNew = stack;
                }
            }
        }
        if (owned.size() > before) {
            saveOwned(player, owned);
            announce(player, firstNew, owned.size());
        }
        return owned.size() - before;
    }

    /** 已收集种类数 */
    public static int collected(ServerPlayer player) {
        return loadOwned(player).size();
    }

    // ---------------- 内部 ----------------

    private static String pathOf(String stickerId) {
        return stickerId.contains(":") ? stickerId.substring(stickerId.indexOf(':') + 1) : stickerId;
    }

    private static Set<String> loadOwned(ServerPlayer player) {
        Set<String> owned = new LinkedHashSet<>();
        CompoundTag data = io.github.zgxhzhr.meowmachine.util.PlayerData.persisted(player);
        ListTag list = data.getList(KEY, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            owned.add(list.getString(i));
        }
        return owned;
    }

    private static void saveOwned(ServerPlayer player, Set<String> owned) {
        ListTag list = new ListTag();
        for (String id : owned) {
            list.add(StringTag.valueOf(id));
        }
        io.github.zgxhzhr.meowmachine.util.PlayerData.persisted(player).put(KEY, list);
    }

    private static void announce(ServerPlayer player, ItemStack stack, int count) {
        MeowMachine.LOG.info("[喵呜机] 梗图收集 {}/{} player={}",
                count, COLLECTOR_TARGET, player.getName().getString());
        Component name = stack.isEmpty()
                ? Component.literal("梗图")
                : stack.getHoverName();
        player.sendSystemMessage(Component.translatable(
                "chat.meow_machine.sticker_progress", name, count, COLLECTOR_TARGET));
        if (count == COLLECTOR_TARGET) {
            MeowAdvancements.award(player, ADVANCEMENT);
            player.sendSystemMessage(Component.translatable(
                    "chat.meow_machine.sticker_complete", COLLECTOR_TARGET));
        }
    }
}
