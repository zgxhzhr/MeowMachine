package io.github.zgxhzhr.meowmachine.network;

import io.github.zgxhzhr.meowmachine.client.ReturnRequestsOpener;
import io.github.zgxhzhr.meowmachine.review.ApprovedIndex;
import io.github.zgxhzhr.meowmachine.review.ReturnRequests;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** 服务端 → 客户端：待处理的「退回申请」，审核员在待审界面里处理。 */
public record ReturnRequestListPacket(List<Entry> entries) {

    private static final int MAX = 64;

    public record Entry(String fullId, String shortId, String title, String requester, String reason) {
    }

    public static ReturnRequestListPacket of() {
        List<Entry> out = new ArrayList<>();
        for (var r : ReturnRequests.all()) {
            if (out.size() >= MAX) {
                break;
            }
            var entry = ApprovedIndex.find(r.entryId()).orElse(null);
            if (entry == null) {
                continue;
            }
            String id = r.entryId().toString();
            out.add(new Entry(id, id.substring(0, 8), entry.title(),
                    r.requester() == null ? "" : r.requester(),
                    r.reason() == null ? "" : r.reason()));
        }
        return new ReturnRequestListPacket(List.copyOf(out));
    }

    public static void encode(ReturnRequestListPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entries.size());
        for (Entry e : msg.entries) {
            buf.writeUtf(e.fullId(), 40);
            buf.writeUtf(e.shortId(), 12);
            buf.writeUtf(e.title(), Network.MAX_TITLE_LEN);
            buf.writeUtf(e.requester(), 40);
            buf.writeUtf(e.reason(), 128);
        }
    }

    public static ReturnRequestListPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n < 0 || n > MAX) {
            throw new DecoderException("喵呜机：退回申请条数非法: " + n);
        }
        List<Entry> out = new ArrayList<>(Math.min(n, 16));
        for (int i = 0; i < n; i++) {
            out.add(new Entry(buf.readUtf(40), buf.readUtf(12),
                    buf.readUtf(Network.MAX_TITLE_LEN), buf.readUtf(40), buf.readUtf(128)));
        }
        return new ReturnRequestListPacket(out);
    }

    public static void handle(ReturnRequestListPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ReturnRequestsOpener.open(msg));
        c.setPacketHandled(true);
    }
}
