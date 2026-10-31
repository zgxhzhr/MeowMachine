package io.github.zgxhzhr.meowmachine.network;

import io.github.zgxhzhr.meowmachine.client.ReviewerScreenOpener;
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
 * @param entries 每条：fullId / 短id / 作者 / 标题
 */
public record ReviewListPacket(List<Entry> entries) {

    /** 一条待审 */
    public record Entry(String fullId, String shortId, String author, String title) {
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
        List<Entry> list = new ArrayList<>(n);
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
