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
        if (!ReviewerRoster.canAnnotate(player)) {
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
        MeowMachine.LOG.info("[喵呜机] 审核意见 {} by={} ok={}",
                id, player.getName().getString(), ok);
        Network.CHANNEL.sendTo(
                ReviewListPacket.of(SubmissionQueue.visibleTo(player),
                        ReviewerRoster.roleOf(player.getUUID()) == null
                                || ReviewerRoster.roleOf(player.getUUID()).canFinalize()),
                player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
        c.setPacketHandled(true);
    }
}
