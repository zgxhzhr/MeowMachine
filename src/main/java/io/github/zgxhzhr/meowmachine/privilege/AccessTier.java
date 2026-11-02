package io.github.zgxhzhr.meowmachine.privilege;

import io.github.zgxhzhr.meowmachine.review.ReviewerRoster;
import net.minecraft.server.level.ServerPlayer;

/** 权限等级：物品自带等级，玩家等级不够就用不了，避免低权限物品串到高权限功能。 */
public enum AccessTier {

    NONE(0, "无"),
    ASSISTANT(1, "助理"),
    REVIEWER(2, "审核员"),
    OP(3, "站长");

    private final int level;
    private final String label;

    AccessTier(int level, String label) {
        this.level = level;
        this.label = label;
    }

    public int level() {
        return level;
    }

    public String label() {
        return label;
    }

    public boolean allowedBy(AccessTier actual) {
        return actual != null && actual.level >= this.level;
    }

    /** 玩家实际等级：OP > 审核员 > 助理 */
    public static AccessTier of(ServerPlayer player) {
        if (player == null) {
            return NONE;
        }
        if (ReviewerRoster.isOp(player)) {
            return OP;
        }
        if (ReviewerRoster.canFinalize(player)) {
            return REVIEWER;
        }
        if (ReviewerRoster.canAnnotate(player)) {
            return ASSISTANT;
        }
        return NONE;
    }
}
