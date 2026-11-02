package io.github.zgxhzhr.meowmachine.network;

import io.github.zgxhzhr.meowmachine.MeowMachine;
import io.github.zgxhzhr.meowmachine.advancement.MeowAdvancements;
import io.github.zgxhzhr.meowmachine.privilege.EffectiveTiers;
import io.github.zgxhzhr.meowmachine.review.ApprovedIndex;
import io.github.zgxhzhr.meowmachine.review.ReturnRequests;
import io.github.zgxhzhr.meowmachine.review.SubmissionHistory;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** 审核员在待审列表里处理「退回申请」：同意或驳回。 */
public record ReturnDecisionPacket(String entryId, boolean accept) {

    public static void encode(ReturnDecisionPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.entryId == null ? "" : msg.entryId, 40);
        buf.writeBoolean(msg.accept);
    }

    public static ReturnDecisionPacket decode(FriendlyByteBuf buf) {
        return new ReturnDecisionPacket(buf.readUtf(40), buf.readBoolean());
    }

    public static void handle(ReturnDecisionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        ServerPlayer player = c.getSender();
        if (player == null) {
            c.setPacketHandled(true);
            return;
        }
        if (!EffectiveTiers.canFinalize(player)) {
            Network.CHANNEL.sendTo(NoticePacket.of("gui.meow_machine.admin.no_permission"),
                    player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
            c.setPacketHandled(true);
            return;
        }
        UUID id;
        try {
            id = UUID.fromString(msg.entryId == null ? "" : msg.entryId.trim());
        } catch (RuntimeException e) {
            c.setPacketHandled(true);
            return;
        }
        var req = ReturnRequests.get(id).orElse(null);
        var entry = ApprovedIndex.find(id).orElse(null);
        if (entry == null) {
            ReturnRequests.clear(id);
            c.setPacketHandled(true);
            return;
        }
        if (!msg.accept) {
            ReturnRequests.clear(id);
            notify(player, "chat.meow_machine.return_request_dismissed");
            c.setPacketHandled(true);
            return;
        }
        List<String> reasons = List.of(req != null && req.reason() != null && !req.reason().isBlank()
                ? "被审核员退回：" + req.reason() : "被审核员退回");
        UUID authorId;
        try {
            authorId = UUID.fromString(entry.authorUuid());
        } catch (RuntimeException e) {
            authorId = null;
        }
        if (authorId != null) {
            SubmissionHistory.markReturned(authorId, id, entry.title(), entry.body(),
                    entry.revision(), reasons);
        }
        ApprovedIndex.remove(id);
        ReturnRequests.clear(id);
        if (player.getServer() != null) {
            ServerPlayer author = authorId == null ? null
                    : player.getServer().getPlayerList().getPlayer(authorId);
            if (author != null) {
                author.sendSystemMessage(Component.translatable(
                        "chat.meow_machine.return_done_author", entry.title(),
                        req != null && req.reason() != null && !req.reason().isBlank()
                                ? req.reason() : "审核员同意退回"));
                MeowAdvancements.award(author, "entry_returned");
            }
            ServerPlayer requester = req == null || req.requesterId() == null ? null
                    : player.getServer().getPlayerList().getPlayer(req.requesterId());
            if (requester != null) {
                requester.giveExperiencePoints(50);
                MeowAdvancements.award(requester, "return_accepted");
                requester.sendSystemMessage(Component.translatable(
                        "chat.meow_machine.return_reward"));
            }
        }
        notify(player, "chat.meow_machine.return_request_accepted");
        MeowMachine.LOG.info("[喵呜机] {} 在待审列表里同意了退回申请 {}", player.getName().getString(), id);
        c.setPacketHandled(true);
    }

    private static void notify(ServerPlayer player, String key) {
        Network.CHANNEL.sendTo(NoticePacket.of(key), player.connection.connection,
                NetworkDirection.PLAY_TO_CLIENT);
    }
}
