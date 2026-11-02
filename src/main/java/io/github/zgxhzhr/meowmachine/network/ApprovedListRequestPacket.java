package io.github.zgxhzhr.meowmachine.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** 客户端 → 服务端：请求「已通过条目」列表（右键喵喵喵图标发出，所有人可用）。 */
public record ApprovedListRequestPacket() {

    public static void encode(ApprovedListRequestPacket msg, FriendlyByteBuf buf) {
    }

    public static ApprovedListRequestPacket decode(FriendlyByteBuf buf) {
        return new ApprovedListRequestPacket();
    }

    public static void handle(ApprovedListRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        ServerPlayer player = c.getSender();
        if (player == null) {
            c.setPacketHandled(true);
            return;
        }
        Network.CHANNEL.sendTo(ApprovedListPacket.of(
                io.github.zgxhzhr.meowmachine.privilege.EffectiveTiers.canFinalize(player)
                        ),
                player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
        c.setPacketHandled(true);
    }
}
