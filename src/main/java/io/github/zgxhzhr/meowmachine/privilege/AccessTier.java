package io.github.zgxhzhr.meowmachine.privilege;

import io.github.zgxhzhr.meowmachine.review.ReviewerRoster;
import net.minecraft.server.level.ServerPlayer;

/** 物品自带等级 = 使用它时的权限上限（min(玩家, 物品)），低档物品不会越级。 */
public enum AccessTier {
    NONE(0, "无"), NORMAL(1, "普通用户"), ASSISTANT(2, "助理"), REVIEWER(3, "审核员");

    private final int level; private final String label;
    AccessTier(int level, String label) { this.level = level; this.label = label; }
    public int level() { return level; }
    public String label() { return label; }

    public static AccessTier of(ServerPlayer p) {
        if (p == null) return NONE;
        if (ReviewerRoster.isOp(p) || ReviewerRoster.canFinalize(p)) return REVIEWER;
        if (ReviewerRoster.canAnnotate(p)) return ASSISTANT;
        return NORMAL;
    }
    public AccessTier effectiveFor(AccessTier player) {
        return player == null ? NONE : (player.level < this.level ? player : this);
    }
    public boolean usableBy(AccessTier player) {
        return player != null && player.level >= this.level;
    }
}
