package io.github.zgxhzhr.meowmachine.network;

import io.github.zgxhzhr.meowmachine.MeowMachine;
import io.github.zgxhzhr.meowmachine.review.Rants;
import io.github.zgxhzhr.meowmachine.review.SubmissionHistory;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/** 对「逆天 AI 审核」的吐槽（附在自己的投稿结论上）。 */
public record RantPacket(String submissionId, String text) {

    private static final int MAX_TEXT = 120;

    public static void encode(RantPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.submissionId == null ? "" : msg.submissionId, 40);
        buf.writeUtf(msg.text == null ? "" : msg.text, MAX_TEXT);
    }

    public static RantPacket decode(FriendlyByteBuf buf) {
        return new RantPacket(buf.readUtf(40), buf.readUtf(MAX_TEXT));
    }

    public static void handle(RantPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        ServerPlayer player = c.getSender();
        if (player == null) {
            c.setPacketHandled(true);
            return;
        }
        UUID id;
        try {
            id = UUID.fromString(msg.submissionId == null ? "" : msg.submissionId.trim());
        } catch (IllegalArgumentException e) {
            c.setPacketHandled(true);
            return;
        }
        if (SubmissionHistory.find(player.getUUID(), id).isEmpty()) {
            Network.CHANNEL.sendTo(NoticePacket.of("chat.meow_machine.rant_no_submission"),
                    player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
            c.setPacketHandled(true);
            return;
        }
        if (Rants.add(id, msg.text)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    "chat.meow_machine.rant_ok"));
        } else {
            Network.CHANNEL.sendTo(NoticePacket.of("chat.meow_machine.rant_full"),
                    player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
        }
        MeowMachine.LOG.debug("[喵呜机] 吐槽 {} ← {}", id, msg.text);
        c.setPacketHandled(true);
    }
}
