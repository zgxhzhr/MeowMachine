package io.github.zgxhzhr.meowmachine.network;

import io.github.zgxhzhr.meowmachine.MeowMachine;
import io.github.zgxhzhr.meowmachine.review.ApprovedIndex;
import io.github.zgxhzhr.meowmachine.review.ReturnRequests;
import io.github.zgxhzhr.meowmachine.review.ReviewerRoster;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 已通过条目的退回：所有人可提交「退回申请」；审核员/OP 直接执行退回。
 */
public record RequestReturnPacket(String entryId, String reason) {

    private static final int MAX_REASON = 120;

    public static void encode(RequestReturnPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.entryId == null ? "" : msg.entryId, 40);
        buf.writeUtf(msg.reason == null ? "" : msg.reason, MAX_REASON);
    }

    public static RequestReturnPacket decode(FriendlyByteBuf buf) {
        return new RequestReturnPacket(buf.readUtf(40), buf.readUtf(MAX_REASON));
    }

    public static void handle(RequestReturnPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        ServerPlayer player = c.getSender();
        if (player == null) {
            c.setPacketHandled(true);
            return;
        }
        UUID id;
        try {
            id = UUID.fromString(msg.entryId == null ? "" : msg.entryId.trim());
        } catch (IllegalArgumentException e) {
            c.setPacketHandled(true);
            return;
        }
        var entry = ApprovedIndex.find(id);
        if (entry.isEmpty()) {
            Network.CHANNEL.sendTo(NoticePacket.of("chat.meow_machine.return_not_found"),
                    player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
            c.setPacketHandled(true);
            return;
        }
        String reason = msg.reason == null ? "" : msg.reason.strip();
        boolean reviewer = ReviewerRoster.canFinalize(player) || ReviewerRoster.isOp(player);
        if (reviewer) {
            ApprovedIndex.remove(id);
            ReturnRequests.clear(id);
            // 通知作者（在线才通知）
            var author = player.getServer() == null ? null
                    : player.getServer().getPlayerList().getPlayerByName(entry.get().author());
            if (author != null) {
                author.sendSystemMessage(Component.translatable(
                        "chat.meow_machine.return_done_author", entry.get().title(),
                        reason.isBlank() ? "-" : reason));
            }
            player.sendSystemMessage(Component.translatable(
                    "chat.meow_machine.return_done", entry.get().title()));
            MeowMachine.LOG.info("[喵呜机] {} 退回了已通过条目 {}（原因：{}）",
                    player.getName().getString(), id, reason);
        } else {
            ReturnRequests.request(id, player.getName().getString(), reason);
            // 提醒在线审核员
            if (player.getServer() != null) {
                for (ServerPlayer p : player.getServer().getPlayerList().getPlayers()) {
                    if (ReviewerRoster.canFinalize(p) || ReviewerRoster.isOp(p)) {
                        p.sendSystemMessage(Component.translatable(
                                "chat.meow_machine.return_requested_reviewer",
                                player.getName().getString(), entry.get().title(),
                                reason.isBlank() ? "-" : reason));
                    }
                }
            }
            player.sendSystemMessage(Component.translatable(
                    "chat.meow_machine.return_requested", entry.get().title()));
        }
        c.setPacketHandled(true);
    }
}
