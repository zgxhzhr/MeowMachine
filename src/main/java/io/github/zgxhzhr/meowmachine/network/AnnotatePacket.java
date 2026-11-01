package io.github.zgxhzhr.meowmachine.network;

import io.github.zgxhzhr.meowmachine.MeowMachine;
import io.github.zgxhzhr.meowmachine.review.ReviewNotes;
import io.github.zgxhzhr.meowmachine.review.ReviewerRoster;
import io.github.zgxhzhr.meowmachine.review.SubmissionQueue;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 客户端 → 服务端：给某篇待审稿留一条意见（标注）。
 *
 * <p>助理没有终裁权，但可以写意见；终裁时这些意见会并进给作者的结果里。
 * 写完服务端会把列表重推一次，界面上的意见栏随之刷新。
 */
public record AnnotatePacket(String fullId, String note) {

    private static final int MAX_RAW = 400;

    public static void encode(AnnotatePacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.fullId == null ? "" : msg.fullId, 40);
        buf.writeUtf(msg.note == null ? "" : msg.note, MAX_RAW);
    }

    public static AnnotatePacket decode(FriendlyByteBuf buf) {
        return new AnnotatePacket(buf.readUtf(40), buf.readUtf(MAX_RAW));
    }

    public static void handle(AnnotatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        ServerPlayer player = c.getSender();
        if (player == null) {
            c.setPacketHandled(true);
            return;
        }
        // 写意见是助理的活：审核员/OP 直接裁决，不走意见通道
        if (ReviewerRoster.roleOf(player.getUUID())
                != io.github.zgxhzhr.meowmachine.review.ReviewerRole.ASSISTANT) {
            c.setPacketHandled(true);
            return;
        }
        UUID id;
        try {
            id = UUID.fromString(msg.fullId() == null ? "" : msg.fullId().trim());
        } catch (IllegalArgumentException e) {
            c.setPacketHandled(true);
            return;
        }
        boolean ok = SubmissionQueue.snapshot().stream()
                .anyMatch(e -> e.submission().id().equals(id))
                && ReviewNotes.add(id, player.getName().getString(), msg.note());
        if (ok) {
            io.github.zgxhzhr.meowmachine.advancement.MeowAdvancements.award(
                    player, io.github.zgxhzhr.meowmachine.advancement.MeowAdvancements.FIRST_NOTE);
        }
        MeowMachine.LOG.info("[喵呜机] 审核意见 {} by={} ok={}",
                id, player.getName().getString(), ok);
        Network.CHANNEL.sendTo(
                ReviewListPacket.of(SubmissionQueue.visibleTo(player),
                        ReviewerRoster.canFinalize(player),
                        ReviewerRoster.roleOf(player.getUUID())
                                == io.github.zgxhzhr.meowmachine.review.ReviewerRole.ASSISTANT),
                player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
        c.setPacketHandled(true);
    }
}
