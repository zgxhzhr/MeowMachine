package io.github.zgxhzhr.meowmachine.review;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * 每个玩家一份草稿，存在**玩家 NBT**（Forge 的 persistentData）里。
 *
 * <p>选玩家 NBT 而不是世界存档的理由：草稿是「作者本人的东西」，
 * 跟人走比跟世界走合理（换个存档也能接着写），而且不用自己管 SavedData 的脏标记。
 *
 * <p>存的内容只有「作者可编辑的那些字段 + 状态」——审核发现之类的临时结果不落盘。
 */
public final class DraftStore {

    private static final String KEY = "meow_machine_draft";

    private DraftStore() {
    }

    /** 读草稿；没有就造一份新的（不落盘，等真正编辑/提交时再写） */
    public static Submission load(ServerPlayer player) {
        CompoundTag persistent = player.getPersistentData();
        if (!persistent.contains(KEY)) {
            return Submission.draft(player.getUUID(), player.getName().getString());
        }
        try {
            CompoundTag t = persistent.getCompound(KEY);
            SubmissionState state;
            try {
                state = SubmissionState.valueOf(t.getString("state"));
            } catch (IllegalArgumentException e) {
                state = SubmissionState.DRAFT;
            }
            return new Submission(
                    UUID.fromString(t.getString("id")),
                    player.getUUID(),
                    player.getName().getString(),
                    t.getString("title"),
                    t.getString("body"),
                    state,
                    t.getInt("revision"));
        } catch (RuntimeException e) {
            // 存档里的草稿坏了不该让玩家打不开界面，退化成新草稿
            return Submission.draft(player.getUUID(), player.getName().getString());
        }
    }

    /** 写草稿 */
    public static void save(ServerPlayer player, Submission sub) {
        CompoundTag t = new CompoundTag();
        t.putString("id", sub.id().toString());
        t.putString("title", sub.title());
        t.putString("body", sub.body());
        t.putString("state", sub.state().name());
        t.putInt("revision", sub.revision());
        player.getPersistentData().put(KEY, t);
    }

    /** 清空草稿（作者想重写时） */
    public static void clear(ServerPlayer player) {
        player.getPersistentData().remove(KEY);
    }
}
