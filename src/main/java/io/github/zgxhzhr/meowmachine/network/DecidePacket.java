package io.github.zgxhzhr.meowmachine.network;

import io.github.zgxhzhr.meowmachine.MeowMachine;
import io.github.zgxhzhr.meowmachine.review.ReviewActions;
import io.github.zgxhzhr.meowmachine.review.Verdict;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：界面上点了「通过 / 协改 / 打回」。
 *
 * <p>无论成败都给玩家一句回音：以前点了没反应（助理没有终审权时服务端只写日志），
 * 玩家只会以为按钮坏了。
 */
public record DecidePacket(String fullId, String verdict) {

    public static void encode(DecidePacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.fullId, 40);
        buf.writeUtf(msg.verdict, 16);
    }

    public static DecidePacket decode(FriendlyByteBuf buf) {
        return new DecidePacket(buf.readUtf(40), buf.readUtf(16));
    }

    public static void handle(DecidePacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        ServerPlayer player = c.getSender();
        if (player == null) {
            c.setPacketHandled(true);
            return;
        }
        Verdict v;
        try {
            v = Verdict.valueOf(msg.verdict.toUpperCase());
        } catch (IllegalArgumentException e) {
            notice(player, "command.meow_machine.bad_verdict");
            c.setPacketHandled(true);
            return;
        }
        ReviewActions.Outcome outcome = ReviewActions.decide(player, msg.fullId, v);
        MeowMachine.LOG.info("[喵呜机] 界面裁决 player={} id={} verdict={} outcome={}",
                player.getName().getString(), msg.fullId.substring(0, Math.min(8, msg.fullId.length())),
                v, outcome);
        if (outcome == ReviewActions.Outcome.OK) {
            player.displayClientMessage(Component.translatable(
                    "command.meow_machine.decided_ok",
                    msg.fullId.substring(0, Math.min(8, msg.fullId.length())), v.name()), false);
        } else {
            notice(player, ReviewActions.messageKey(outcome));
        }
        c.setPacketHandled(true);
    }

    private static void notice(ServerPlayer player, String key) {
        Network.CHANNEL.sendTo(NoticePacket.of(key), player.connection.connection,
                NetworkDirection.PLAY_TO_CLIENT);
    }
}
