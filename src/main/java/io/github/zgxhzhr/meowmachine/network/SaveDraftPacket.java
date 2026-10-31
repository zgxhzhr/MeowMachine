package io.github.zgxhzhr.meowmachine.network;

import io.github.zgxhzhr.meowmachine.MeowMachine;
import io.github.zgxhzhr.meowmachine.review.DraftStore;
import io.github.zgxhzhr.meowmachine.review.Submission;
import io.github.zgxhzhr.meowmachine.review.SubmissionQueue;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 客户端 → 服务端：**暂存草稿**（关界面时发，不提交审核）。
 *
 * <p>0.1.0 只有「提交」会写草稿，导致写了一半按 ESC 就全丢。
 * 现在关界面时把当前内容存下来，下次右键卷轴接着写。
 *
 * <p>服务端规则：
 * <ul>
 *   <li>内容没变 → 什么都不做（不白白涨 revision）</li>
 *   <li>内容变了 → 存成 DRAFT；如果这份稿正在人工队列里，顺手撤出队列
 *       （作者都还在改，让审核员审一个半成品没有意义）</li>
 * </ul>
 */
public record SaveDraftPacket(String title, String body) {

    public static void encode(SaveDraftPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.title, Network.MAX_TITLE_LEN);
        buf.writeUtf(msg.body, Network.MAX_BODY_LEN);
    }

    public static SaveDraftPacket decode(FriendlyByteBuf buf) {
        return new SaveDraftPacket(buf.readUtf(Network.MAX_TITLE_LEN),
                buf.readUtf(Network.MAX_BODY_LEN));
    }

    public static void handle(SaveDraftPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        ServerPlayer player = c.getSender();
        if (player == null) {
            c.setPacketHandled(true);
            return;
        }
        String title = clamp(msg.title, Network.MAX_TITLE_LEN);
        String body = clamp(msg.body, Network.MAX_BODY_LEN);

        Submission existing = DraftStore.load(player);
        if (!existing.state().editableByAuthor()) {
            // 已通过的稿子不接受编辑，也不该被一个迟到/伪造的包改掉
            Network.CHANNEL.sendTo(NoticePacket.of("chat.meow_machine.cannot_edit"),
                    player.connection.connection,
                    net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
            c.setPacketHandled(true);
            return;
        }
        if (title.equals(existing.title()) && body.equals(existing.body())) {
            c.setPacketHandled(true);
            return;
        }
        Submission updated = existing.withContent(title, body);
        DraftStore.save(player, updated);
        SubmissionQueue.drop(existing.id());
        MeowMachine.LOG.debug("[喵呜机] 草稿已暂存 player={} revision={}",
                player.getName().getString(), updated.revision());
        c.setPacketHandled(true);
    }

    private static String clamp(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
