package io.github.zgxhzhr.meowmachine.network;

import io.github.zgxhzhr.meowmachine.MeowMachine;
import io.github.zgxhzhr.meowmachine.review.ReviewActions;
import io.github.zgxhzhr.meowmachine.review.Verdict;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 客户端 → 服务端：界面上点了「通过 / 协改 / 打回」。 */
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
            c.setPacketHandled(true);
            return;
        }
        ReviewActions.Outcome outcome = ReviewActions.decide(player, msg.fullId, v);
        MeowMachine.LOG.info("[喵呜机] 界面裁决 player={} id={} verdict={} outcome={}",
                player.getName().getString(), msg.fullId.substring(0, Math.min(8, msg.fullId.length())),
                v, outcome);
        c.setPacketHandled(true);
    }
}
