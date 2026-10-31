package io.github.zgxhzhr.meowmachine.review;

import io.github.zgxhzhr.meowmachine.MeowMachine;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 草稿存取入口。实际数据放在**世界存档**（{@link DraftSavedData}），
 * 位置是 {@code <世界>/data/meow_machine_drafts.dat}。
 *
 * <p>0.1.0 曾存在玩家 NBT 里，问题是：玩家 NBT 只随在线实体走，审核员裁决时作者
 * 常常已经下线，想补写结果很别扭；而草稿本质是「这个存档里的投稿」，跟档走更合理
 * （也顺手让「关掉界面不丢稿」有了落点）。
 *
 * <p>这里只做「玩家 → 存档」的适配，所有调用点仍然按 {@link ServerPlayer} 传参。
 */
public final class DraftStore {

    private DraftStore() {
    }

    /** 读草稿；没有就造一份新的（不落盘，等真正编辑/提交时再写） */
    public static Submission load(ServerPlayer player) {
        if (player == null) {
            return Submission.draft(new java.util.UUID(0L, 0L), "unknown");
        }
        DraftSavedData data = DraftSavedData.get(player.getServer());
        Submission stored = data == null ? null : data.get(player.getUUID());
        return stored != null ? stored : Submission.draft(player.getUUID(), player.getName().getString());
    }

    /** 写草稿 */
    public static void save(ServerPlayer player, Submission sub) {
        save(player == null ? null : player.getServer(), sub);
    }

    /** 写草稿（无实体版；离线补发结果时用） */
    public static void save(MinecraftServer server, Submission sub) {
        DraftSavedData data = DraftSavedData.get(server);
        if (data == null || sub == null) {
            MeowMachine.LOG.warn("[喵呜机] 草稿存档不可用，本次草稿未落盘");
            return;
        }
        data.put(sub);
    }
}
