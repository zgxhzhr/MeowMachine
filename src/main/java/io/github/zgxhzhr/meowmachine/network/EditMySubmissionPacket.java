package io.github.zgxhzhr.meowmachine.network;

import io.github.zgxhzhr.meowmachine.MeowMachine;
import io.github.zgxhzhr.meowmachine.review.DraftStore;
import io.github.zgxhzhr.meowmachine.review.SubmissionHistory;
import io.github.zgxhzhr.meowmachine.review.SubmissionState;
import io.github.zgxhzhr.meowmachine.review.Submission;
import io.github.zgxhzhr.meowmachine.review.SubmissionQueue;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 客户端 → 服务端：把「我的投稿」里的某一篇拉回来继续编辑。
 *
 * <p>两种情况：
 * <ul>
 *   <li>就是当前草稿 → 直接把草稿推回客户端打开编辑器；</li>
 *   <li>还在人工队列里 → 从队列撤回、设为当前草稿，再打开编辑器
 *       （撤稿是作者的权利；否则「我的投稿」只能看不能改，是个死角）。</li>
 * </ul>
 */
public record EditMySubmissionPacket(String fullId) {

    public static void encode(EditMySubmissionPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.fullId == null ? "" : msg.fullId, 40);
    }

    public static EditMySubmissionPacket decode(FriendlyByteBuf buf) {
        return new EditMySubmissionPacket(buf.readUtf(40));
    }

    public static void handle(EditMySubmissionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        ServerPlayer player = c.getSender();
        if (player == null) {
            c.setPacketHandled(true);
            return;
        }
        Submission draft = DraftStore.load(player);
        String id = msg.fullId == null ? "" : msg.fullId.trim();

        Submission target;
        if (draft.id().toString().equals(id)) {
            target = draft;
        } else {
            UUID uuid;
            try {
                uuid = UUID.fromString(id);
            } catch (IllegalArgumentException e) {
                c.setPacketHandled(true);
                return;
            }
            Optional<Submission> queued = SubmissionQueue.byAuthor(player.getUUID()).stream()
                    .map(SubmissionQueue.Entry::submission)
                    .filter(s -> s.id().equals(uuid))
                    .findFirst();
            if (queued.isEmpty()) {
                // 不在队列里 → 也许是历史里「被打回」的稿子，允许拉回来改
                Optional<SubmissionHistory.Record> hist =
                        SubmissionHistory.find(player.getUUID(), uuid);
                if (hist.isPresent() && !"approved".equals(hist.get().state())) {
                    Submission restore = new Submission(uuid, player.getUUID(),
                            player.getName().getString(), hist.get().title(),
                            hist.get().body(), SubmissionState.DRAFT, hist.get().revision() + 1);
                    DraftStore.save(player, restore);
                    Network.CHANNEL.sendTo(
                            new OpenEditorPacket(restore.title(), restore.body(), "draft"),
                            player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
                    c.setPacketHandled(true);
                    return;
                }
                Network.CHANNEL.sendTo(NoticePacket.of("chat.meow_machine.cannot_edit"),
                        player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
                c.setPacketHandled(true);
                return;
            }
            // 从队列撤回：作者要改，就不该同时让审核员审一份要变的稿子
            SubmissionQueue.drop(uuid);
            target = queued.get();
            DraftStore.save(player, target);
            MeowMachine.LOG.info("[喵呜机] 作者撤回待审稿继续编辑 id={} author={}",
                    uuid, player.getName().getString());
        }

        Network.CHANNEL.sendTo(
                new OpenEditorPacket(target.title(), target.body(),
                        target.state().name().toLowerCase()),
                player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
        c.setPacketHandled(true);
    }
}
