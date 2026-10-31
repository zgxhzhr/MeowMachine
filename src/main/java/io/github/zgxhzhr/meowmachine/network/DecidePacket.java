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
 * 客户端 → 服务端：裁决（通过 / 协改 / 打回）+ **原因**。
 *
 * <p>原因两部分：规则号（{@code ruleId}，-1 表示说不出具体条款）+ 一句话说明（可空）。
 * 有了规则号，梗图会优先用该规则的专属图（第 18 条 →「又是灌水」），
 * 作者也能在弹窗里看到「命中：第 N 条」和审核员写的话。
 */
public record DecidePacket(String fullId, String verdict, int ruleId, String note) {

    public static void encode(DecidePacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.fullId, 40);
        buf.writeUtf(msg.verdict, 16);
        buf.writeVarInt(msg.ruleId);
        buf.writeUtf(msg.note == null ? "" : msg.note, 200);
    }

    public static DecidePacket decode(FriendlyByteBuf buf) {
        return new DecidePacket(buf.readUtf(40), buf.readUtf(16), buf.readVarInt(), buf.readUtf(200));
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
        ReviewActions.Outcome outcome = ReviewActions.decide(player, msg.fullId, v, msg.ruleId, msg.note);
        MeowMachine.LOG.info("[喵呜机] 界面裁决 player={} id={} verdict={} rule={} outcome={}",
                player.getName().getString(), msg.fullId.substring(0, Math.min(8, msg.fullId.length())),
                v, msg.ruleId, outcome);
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
