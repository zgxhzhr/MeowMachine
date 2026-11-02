package io.github.zgxhzhr.meowmachine.privilege;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 本次会话里由「手持物品等级」封顶后的生效等级；没登记就按玩家真实等级。 */
public final class EffectiveTiers {

    private static final Map<UUID, AccessTier> SESSION = new ConcurrentHashMap<>();

    private EffectiveTiers() {
    }

    public static void set(ServerPlayer player, AccessTier tier) {
        if (player != null && tier != null) {
            SESSION.put(player.getUUID(), tier);
        }
    }

    public static void clear(ServerPlayer player) {
        if (player != null) {
            SESSION.remove(player.getUUID());
        }
    }

    public static AccessTier of(ServerPlayer player) {
        if (player == null) {
            return AccessTier.NONE;
        }
        return SESSION.getOrDefault(player.getUUID(), AccessTier.of(player));
    }

    /** 终裁需要审核员档 */
    public static boolean canFinalize(ServerPlayer player) {
        return of(player).level() >= AccessTier.REVIEWER.level();
    }
}
