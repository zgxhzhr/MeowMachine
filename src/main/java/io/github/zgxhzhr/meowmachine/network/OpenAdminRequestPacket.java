package io.github.zgxhzhr.meowmachine.network;

import io.github.zgxhzhr.meowmachine.review.ReviewerRoster;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 客户端 → 服务端：界面按钮请求打开完整审核后台（仅 OP）。 */
public record OpenAdminRequestPacket() {

    public static void encode(OpenAdminRequestPacket msg, FriendlyByteBuf buf) {
    }

    public static OpenAdminRequestPacket decode(FriendlyByteBuf buf) {
        return new OpenAdminRequestPacket();
    }

    public static void handle(OpenAdminRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        ServerPlayer player = c.getSender();
        if (player == null) {
            c.setPacketHandled(true);
            return;
        }
        if (!ReviewerRoster.isOp(player)) {
            Network.CHANNEL.sendTo(NoticePacket.of("gui.meow_machine.admin.no_permission"),
                    player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
            c.setPacketHandled(true);
            return;
        }
        Network.CHANNEL.sendTo(AdminStatePacket.collect(player),
                player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
        c.setPacketHandled(true);
    }
}
