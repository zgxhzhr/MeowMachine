package io.github.zgxhzhr.meowmachine.network;

import io.github.zgxhzhr.meowmachine.review.DraftStore;
import io.github.zgxhzhr.meowmachine.review.Submission;
import io.github.zgxhzhr.meowmachine.review.SubmissionQueue;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：请求「我的投稿」列表。
 *
 * <p>提交端以前没有任何地方能看到自己的稿子审到哪了——只有一张梗图弹窗。
 * 这个请求由编辑界面的「我的投稿」按钮发出，服务端回 {@link MySubmissionsPacket}。
 */
public record MySubmissionsRequestPacket() {

    public static void encode(MySubmissionsRequestPacket msg, FriendlyByteBuf buf) {
        // 无载荷
    }

    public static MySubmissionsRequestPacket decode(FriendlyByteBuf buf) {
        return new MySubmissionsRequestPacket();
    }

    public static void handle(MySubmissionsRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        ServerPlayer player = c.getSender();
        if (player == null) {
            c.setPacketHandled(true);
            return;
        }
        Submission draft = DraftStore.load(player);
        Network.CHANNEL.sendTo(MySubmissionsPacket.of(draft, player), player.connection.connection,
                NetworkDirection.PLAY_TO_CLIENT);
        c.setPacketHandled(true);
    }
}
