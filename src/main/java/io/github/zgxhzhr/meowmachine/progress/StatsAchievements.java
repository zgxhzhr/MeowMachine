package io.github.zgxhzhr.meowmachine.progress;

import io.github.zgxhzhr.meowmachine.MeowMachine;
import io.github.zgxhzhr.meowmachine.advancement.MeowAdvancements;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/** 次数类成就：审核 10 次 / 打回 5 次 / 修改已通过条目 5 次 / 编辑已通过条目 1 次。 */
public final class StatsAchievements {

    private static final String KEY = "meow_machine_stats";

    private StatsAchievements() {
    }

    /** 审核员终裁一次 */
    public static void onReview(ServerPlayer reviewer, boolean rejected) {
        if (reviewer == null) {
            return;
        }
        int reviewed = inc(reviewer, "reviewed");
        MeowMachine.LOG.info("[喵呜机] 审核统计 reviewed={} player={}", reviewed,
                reviewer.getName().getString());
        if (reviewed >= 10) {
            MeowAdvancements.award(reviewer, "reviewer_ten");
        }
        if (rejected && inc(reviewer, "rejected") >= 5) {
            MeowAdvancements.award(reviewer, "rejector_five");
        }
    }

    /** 作者修改了某个「已通过」条目并重新提交 */
    public static void onEditApproved(ServerPlayer author) {
        if (author == null) {
            return;
        }
        MeowAdvancements.award(author, "edited_approved");
        if (inc(author, "edited") >= 5) {
            MeowAdvancements.award(author, "editor_five");
        }
    }

    private static int inc(ServerPlayer player, String field) {
        CompoundTag data = io.github.zgxhzhr.meowmachine.util.PlayerData.persisted(player);
        CompoundTag stats = data.contains(KEY) ? data.getCompound(KEY) : new CompoundTag();
        int n = stats.getInt(field) + 1;
        stats.putInt(field, n);
        data.put(KEY, stats);
        return n;
    }
}
