package io.github.zgxhzhr.meowmachine.progress;

import io.github.zgxhzhr.meowmachine.MeowMachine;
import io.github.zgxhzhr.meowmachine.advancement.MeowAdvancements;
import io.github.zgxhzhr.meowmachine.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 梗图收藏册：审核结果里出现的梗图，会给作者发一张同款物品；集齐 8 张不同 → 成就。
 *
 * <p>为什么不用 {@code inventory_changed} 成就：那个触发器只能判「背包里有某样东西」，
 * 判不了「不同种类够 8 个」。所以已收集的种类记在玩家持久数据里，由这里判定并授奖。
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
     * @param player    收件人（离线时不发，item 只走在线路径）
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

        String path = stickerId.contains(":") ? stickerId.substring(stickerId.indexOf(':') + 1) : stickerId;
        CompoundTag data = player.getPersistentData();
        Set<String> owned = new LinkedHashSet<>();
        ListTag list = data.getList(KEY, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            owned.add(list.getString(i));
        }
        if (!owned.add(path)) {
            return;
        }
        ListTag updated = new ListTag();
        for (String id : owned) {
            updated.add(net.minecraft.nbt.StringTag.valueOf(id));
        }
        data.put(KEY, updated);

        MeowMachine.LOG.info("[喵呜机] 梗图收集 {}/{} player={} sticker={}",
                owned.size(), COLLECTOR_TARGET, player.getName().getString(), path);
        if (owned.size() >= COLLECTOR_TARGET) {
            MeowAdvancements.award(player, ADVANCEMENT);
        }
    }

    /** 已收集种类数（自测/日志用） */
    public static int collected(ServerPlayer player) {
        if (player == null) {
            return 0;
        }
        return player.getPersistentData().getList(KEY, Tag.TAG_STRING).size();
    }
}
