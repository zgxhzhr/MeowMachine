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

    /** 以「手上物品」为准：手里是什么等级的物品，就用什么权限（不会因之前开过别的界面而残留）。 */
    public static AccessTier of(ServerPlayer player) {
        if (player == null) {
            return AccessTier.NONE;
        }
        AccessTier actual = AccessTier.of(player);
        AccessTier fromItem = fromHand(player);
        if (fromItem == null) {
            return SESSION.getOrDefault(player.getUUID(), actual);
        }
        return fromItem.effectiveFor(actual);
    }

    /** 主手/副手里的分档物品等级；不是分档物品返回 null */
    public static AccessTier fromHand(ServerPlayer player) {
        for (var hand : net.minecraft.world.InteractionHand.values()) {
            var stack = player.getItemInHand(hand);
            var item = stack.getItem();
            if (item instanceof io.github.zgxhzhr.meowmachine.item.MeowIconItem icon) {
                return icon.tier();
            }
            if (item instanceof io.github.zgxhzhr.meowmachine.item.AdminTerminalItem term) {
                return term.tier();
            }
        }
        return null;
    }

    /** 终裁需要审核员档 */
    public static boolean canFinalize(ServerPlayer player) {
        return of(player).level() >= AccessTier.REVIEWER.level();
    }
}
