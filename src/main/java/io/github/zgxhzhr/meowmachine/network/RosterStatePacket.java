package io.github.zgxhzhr.meowmachine.network;

import io.github.zgxhzhr.meowmachine.client.RosterScreenHandler;
import io.github.zgxhzhr.meowmachine.review.ReviewerRoster;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 服务端 → 客户端：审核员名册（打开/刷新「审核员管理」界面用）。
 *
 * @param members 名册成员：uuid / 名字（可能为空，旧数据）/ 角色（小写）
 */
public record RosterStatePacket(List<Entry> members) {

    /** 名册上限：真到 512 个审核员也该换别的方案了，超出按坏包处理 */
    private static final int MAX_MEMBERS = 512;

    public record Entry(String uuid, String name, String role) {

        public static Entry of(ReviewerRoster.Member m) {
            return new Entry(m.uuid().toString(),
                    m.name() == null ? "" : m.name(),
                    m.role().name().toLowerCase());
        }

        /** 界面显示用：没名字（旧格式数据）就显示短 UUID */
        public String displayName() {
            if (name != null && !name.isBlank()) {
                return name;
            }
            return uuid.length() >= 8 ? uuid.substring(0, 8) : uuid;
        }
    }

    public static RosterStatePacket collect() {
        List<Entry> list = new ArrayList<>();
        for (ReviewerRoster.Member m : ReviewerRoster.members()) {
            list.add(Entry.of(m));
        }
        return new RosterStatePacket(List.copyOf(list));
    }

    public static void encode(RosterStatePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.members.size());
        for (Entry e : msg.members) {
            buf.writeUtf(e.uuid(), 40);
            buf.writeUtf(e.name(), 40);
            buf.writeUtf(e.role(), 16);
        }
    }

    public static RosterStatePacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_MEMBERS) {
            throw new DecoderException("喵呜机：名册条数非法: " + n);
        }
        List<Entry> list = new ArrayList<>(Math.min(n, 16));
        for (int i = 0; i < n; i++) {
            list.add(new Entry(buf.readUtf(40), buf.readUtf(40), buf.readUtf(16)));
        }
        return new RosterStatePacket(list);
    }

    public static void handle(RosterStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> RosterScreenHandler.show(msg));
        c.setPacketHandled(true);
    }
}
