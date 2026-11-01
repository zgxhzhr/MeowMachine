package io.github.zgxhzhr.meowmachine.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * 玩家持久数据的正确位置。
 *
 * <p>直接写 {@code player.getPersistentData()} 根节点是错的：Forge 在死亡重生时
 * 只搬运 {@code PlayerPersisted} 子节点，其余会被丢掉——收藏进度、成就计数、
 * 「已发放终端/图标」标记全都会在死一次之后回退（审查 P1）。
 */
public final class PlayerData {

    private PlayerData() {
    }

    /** 拿到（必要时创建）持久化子节点 */
    public static CompoundTag persisted(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(ServerPlayer.PERSISTED_NBT_TAG, net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            root.put(ServerPlayer.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(ServerPlayer.PERSISTED_NBT_TAG);
    }
}
