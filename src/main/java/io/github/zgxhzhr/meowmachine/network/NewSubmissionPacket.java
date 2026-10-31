package io.github.zgxhzhr.meowmachine.network;

import io.github.zgxhzhr.meowmachine.MeowMachine;
import io.github.zgxhzhr.meowmachine.review.DraftStore;
import io.github.zgxhzhr.meowmachine.review.Submission;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：丢掉当前草稿，开一份全新投稿。
 *
 * <p>没有这个包时，每个玩家一辈子只能有「那一份」稿子——投稿被裁决后再打开卷轴还是它，
 * 已通过的稿子还不能改，于是再也投不了第二篇。
 *
 * <p>服务端生成新草稿后把 {@link OpenEditorPacket} 发回去，客户端借此原地换成空白编辑界面。
 */
public record NewSubmissionPacket() {

    public static void encode(NewSubmissionPacket msg, FriendlyByteBuf buf) {
        // 无载荷
    }

    public static NewSubmissionPacket decode(FriendlyByteBuf buf) {
        return new NewSubmissionPacket();
    }

    public static void handle(NewSubmissionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        ServerPlayer player = c.getSender();
        if (player == null) {
            c.setPacketHandled(true);
            return;
        }
        Submission fresh = DraftStore.startNew(player);
        MeowMachine.LOG.info("[喵呜机] 新投稿已创建 player={} id={}",
                player.getName().getString(), fresh.id());
        Network.CHANNEL.sendTo(
                new OpenEditorPacket(fresh.title(), fresh.body(),
                        fresh.state().name().toLowerCase()),
                player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
        c.setPacketHandled(true);
    }
}
