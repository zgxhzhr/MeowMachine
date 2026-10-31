package io.github.zgxhzhr.meowmachine.network;

import io.github.zgxhzhr.meowmachine.client.ReviewerScreenOpener;
import io.github.zgxhzhr.meowmachine.review.Submission;
import io.github.zgxhzhr.meowmachine.review.SubmissionQueue;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 服务端 → 客户端：待审列表，收到就打开审核界面。
 *
 * @param entries 每条：fullId / 短 id / 作者 / 标题
 */
public record ReviewListPacket(List<Entry> entries) {

    /** 一次最多下发多少条：界面本身只显示 8 条，多发的没有意义 */
    private static final int MAX_ENTRIES = 512;

    /** 一条待审 */
    public record Entry(String fullId, String shortId, String author, String title) {

        public static Entry of(Submission s) {
            String full = s.id().toString();
            return new Entry(full, full.substring(0, 8), s.authorName(), s.title());
        }
    }

    /** 由队列快照构造 */
    public static ReviewListPacket of(List<SubmissionQueue.Entry> queue) {
        List<Entry> list = new ArrayList<>();
        for (SubmissionQueue.Entry e : queue) {
            list.add(Entry.of(e.submission()));
        }
        return new ReviewListPacket(List.copyOf(list));
    }

    public static void encode(ReviewListPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entries.size());
        for (Entry e : msg.entries) {
            buf.writeUtf(e.fullId(), 40);
            buf.writeUtf(e.shortId(), 12);
            buf.writeUtf(e.author(), 40);
            buf.writeUtf(e.title(), Network.MAX_TITLE_LEN);
        }
    }

    public static ReviewListPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        // 客户端连的是任意服务器，网络来的长度不能直接当容量用
        if (n < 0 || n > MAX_ENTRIES) {
            throw new DecoderException("喵呜机：待审列表条数非法: " + n);
        }
        List<Entry> list = new ArrayList<>(Math.min(n, 16));
        for (int i = 0; i < n; i++) {
            list.add(new Entry(buf.readUtf(40), buf.readUtf(12), buf.readUtf(40),
                    buf.readUtf(Network.MAX_TITLE_LEN)));
        }
        return new ReviewListPacket(list);
    }

    public static void handle(ReviewListPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ReviewerScreenOpener.open(msg));
        c.setPacketHandled(true);
    }
}
