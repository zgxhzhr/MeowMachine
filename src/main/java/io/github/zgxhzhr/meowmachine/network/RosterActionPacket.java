package io.github.zgxhzhr.meowmachine.network;

import io.github.zgxhzhr.meowmachine.MeowMachine;
import io.github.zgxhzhr.meowmachine.review.ReviewerRole;
import io.github.zgxhzhr.meowmachine.review.ReviewerRoster;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 客户端 → 服务端：审核员管理界面的操作。
 *
 * <p>服务端**每次都重新校验 OP**：客户端能伪造任何包，界面隐藏不算权限。
 * 任命只接受**在线玩家名**（GUI 里没法像命令那样用选择器），撤销按 UUID。
 */
public record RosterActionPacket(Action action, String target, String role) {

    public enum Action {
        OPEN,
        GRANT,
        REVOKE
    }

    public static RosterActionPacket open() {
        return new RosterActionPacket(Action.OPEN, "", "");
    }

    public static RosterActionPacket grant(String playerName, ReviewerRole role) {
        return new RosterActionPacket(Action.GRANT,
                playerName == null ? "" : playerName.trim(),
                role == null ? "" : role.name().toLowerCase());
    }

    public static RosterActionPacket revoke(String uuid) {
        return new RosterActionPacket(Action.REVOKE, uuid == null ? "" : uuid, "");
    }

    public static void encode(RosterActionPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.action);
        buf.writeUtf(msg.target, 40);
        buf.writeUtf(msg.role, 16);
    }

    public static RosterActionPacket decode(FriendlyByteBuf buf) {
        return new RosterActionPacket(buf.readEnum(Action.class), buf.readUtf(40), buf.readUtf(16));
    }

    public static void handle(RosterActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        ServerPlayer player = c.getSender();
        if (player == null) {
            c.setPacketHandled(true);
            return;
        }
        if (!player.hasPermissions(2)) {
            MeowMachine.LOG.warn("[喵呜机] 非 OP 尝试操作审核员名册: {}",
                    player.getName().getString());
            MeowMachine.LOG.warn("[喵呜机] 已拒绝，未做任何改动");
            c.setPacketHandled(true);
            return;
        }
        MinecraftServer server = player.getServer();

        switch (msg.action) {
            case OPEN -> {
                // 只是打开界面，下面统一回状态
            }
            case GRANT -> {
                if (msg.target.isBlank()) {
                    notice(player, "chat.meow_machine.roster_need_name", "");
                } else if (server == null) {
                    notice(player, "chat.meow_machine.player_not_online", msg.target);
                } else {
                    ServerPlayer target = server.getPlayerList().getPlayerByName(msg.target);
                    if (target == null) {
                        notice(player, "chat.meow_machine.player_not_online", msg.target);
                    } else {
                        ReviewerRole role = parseRole(msg.role);
                        ReviewerRoster.grant(target.getUUID(),
                                target.getName().getString(), role);
                        MeowMachine.LOG.info("[喵呜机] 后台任命 {} 为 {}（操作者 {}）",
                                target.getName().getString(), role,
                                player.getName().getString());
                    }
                }
            }
            case REVOKE -> {
                try {
                    UUID uuid = UUID.fromString(msg.target);
                    ReviewerRoster.revoke(uuid);
                    MeowMachine.LOG.info("[喵呜机] 后台撤销审核身份 uuid={}（操作者 {}）",
                            uuid, player.getName().getString());
                } catch (IllegalArgumentException e) {
                    notice(player, "chat.meow_machine.roster_bad_target", msg.target);
                }
            }
        }

        Network.CHANNEL.sendTo(RosterStatePacket.collect(player), player.connection.connection,
                NetworkDirection.PLAY_TO_CLIENT);
        c.setPacketHandled(true);
    }

    private static ReviewerRole parseRole(String s) {
        try {
            return ReviewerRole.valueOf(s.toUpperCase());
        } catch (RuntimeException e) {
            return ReviewerRole.REVIEWER;
        }
    }

    private static void notice(ServerPlayer player, String key, String arg) {
        Network.CHANNEL.sendTo(new NoticePacket(key, arg == null ? "" : arg),
                player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }
}
