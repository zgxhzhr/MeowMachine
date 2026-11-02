package io.github.zgxhzhr.meowmachine.network;

import io.github.zgxhzhr.meowmachine.MeowMachine;
import io.github.zgxhzhr.meowmachine.review.ReviewerRoster;
import io.github.zgxhzhr.meowmachine.review.SubmissionQueue;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：请求待审列表（审核员用）。
 *
 * <p>以前列表只能由命令或后台按钮推一次，界面上没有「刷新/重新打开」的入口；
 * 从详情页审完一条回来时列表会是旧的。有了这个请求，界面可以随时重取。
 */
public record ReviewListRequestPacket() {

    public static void encode(ReviewListRequestPacket msg, FriendlyByteBuf buf) {
        // 无载荷
    }

    public static ReviewListRequestPacket decode(FriendlyByteBuf buf) {
        return new ReviewListRequestPacket();
    }

    public static void handle(ReviewListRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
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
        MeowMachine.LOG.debug("[喵呜机] 审核界面请求列表 player={}",
                player.getName().getString());
        if (io.github.zgxhzhr.meowmachine.privilege.EffectiveTiers.canFinalize(player)) {
            Network.CHANNEL.sendTo(ReturnRequestListPacket.of(), player.connection.connection,
                    NetworkDirection.PLAY_TO_CLIENT);
        }
        Network.CHANNEL.sendTo(
                ReviewListPacket.of(SubmissionQueue.visibleTo(player),
                        ReviewerRoster.canFinalize(player),
                        ReviewerRoster.roleOf(player.getUUID())
                                == io.github.zgxhzhr.meowmachine.review.ReviewerRole.ASSISTANT),
                player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
        c.setPacketHandled(true);
    }
}
