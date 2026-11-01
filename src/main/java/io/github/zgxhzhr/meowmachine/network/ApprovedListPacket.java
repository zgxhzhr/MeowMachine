package io.github.zgxhzhr.meowmachine.network;

import io.github.zgxhzhr.meowmachine.client.ApprovedListScreenOpener;
import io.github.zgxhzhr.meowmachine.review.ApprovedIndex;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** 服务端 → 客户端：已通过条目列表（所有人可见）。 */
public record ApprovedListPacket(List<Entry> entries) {

    private static final int MAX_ENTRIES = 200;
    private static final int MAX_BODY = 2000;

    public record Entry(String id, String shortId, String title, String author, String body,
                        int revision) {
    }

    public static ApprovedListPacket of() {
        List<Entry> list = new ArrayList<>();
        for (var e : ApprovedIndex.all()) {
            if (list.size() >= MAX_ENTRIES) {
                break;
            }
            String body = e.body() == null ? "" : e.body();
            if (body.length() > MAX_BODY) {
                body = body.substring(0, MAX_BODY);
            }
            String id = e.id().toString();
            list.add(new Entry(id, id.substring(0, 8), e.title(), e.author(), body, e.revision()));
        }
        return new ApprovedListPacket(List.copyOf(list));
    }

    public static void encode(ApprovedListPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entries.size());
        for (Entry e : msg.entries) {
            buf.writeUtf(e.id(), 40);
            buf.writeUtf(e.shortId(), 12);
            buf.writeUtf(e.title(), Network.MAX_TITLE_LEN);
            buf.writeUtf(e.author(), 40);
            buf.writeUtf(e.body(), MAX_BODY);
            buf.writeVarInt(e.revision());
        }
    }

    public static ApprovedListPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_ENTRIES) {
            throw new DecoderException("喵呜机：已通过列表条数非法: " + n);
        }
        List<Entry> list = new ArrayList<>(Math.min(n, 32));
        for (int i = 0; i < n; i++) {
            list.add(new Entry(buf.readUtf(40), buf.readUtf(12), buf.readUtf(Network.MAX_TITLE_LEN),
                    buf.readUtf(40), buf.readUtf(MAX_BODY), buf.readVarInt()));
        }
        return new ApprovedListPacket(list);
    }

    public static void handle(ApprovedListPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ApprovedListScreenOpener.open(msg));
        c.setPacketHandled(true);
    }
}
