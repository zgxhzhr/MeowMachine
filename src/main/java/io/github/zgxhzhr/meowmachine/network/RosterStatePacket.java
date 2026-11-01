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
 * 服务端 → 客户端：审核员名册 + 在线玩家（打开/刷新「审核员管理」界面用）。
 *
 * @param members 名册成员：uuid / 名字（可能为空，旧数据）/ 角色（小写）
 * @param online  在线玩家：直接点名字任命，不用手打
 */
public record RosterStatePacket(List<Entry> members, List<Online> online) {

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

    /** 在线玩家（给界面点选用的；role 为空表示尚未任命） */
    public record Online(String uuid, String name, String role) {
    }

    public static RosterStatePacket collect(net.minecraft.server.level.ServerPlayer viewer) {
        List<Entry> list = new ArrayList<>();
        for (ReviewerRoster.Member m : ReviewerRoster.members()) {
            list.add(Entry.of(m));
        }
        List<Online> online = new ArrayList<>();
        if (viewer != null && viewer.getServer() != null) {
            for (net.minecraft.server.level.ServerPlayer p : viewer.getServer().getPlayerList().getPlayers()) {
                // 包含自己：单人测试时也要能把某人（包括自己）任命成审核员
                io.github.zgxhzhr.meowmachine.review.ReviewerRole r =
                        ReviewerRoster.roleOf(p.getUUID());
                online.add(new Online(p.getUUID().toString(), p.getName().getString(),
                        r == null ? "" : r.name().toLowerCase()));
            }
        }
        return new RosterStatePacket(List.copyOf(list), List.copyOf(online));
    }

    public static void encode(RosterStatePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.members.size());
        for (Entry e : msg.members) {
            buf.writeUtf(e.uuid(), 40);
            buf.writeUtf(e.name(), 40);
            buf.writeUtf(e.role(), 16);
        }
        buf.writeVarInt(msg.online.size());
        for (Online o : msg.online) {
            buf.writeUtf(o.uuid(), 40);
            buf.writeUtf(o.name(), 40);
            buf.writeUtf(o.role() == null ? "" : o.role(), 16);
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
        int on = buf.readVarInt();
        if (on < 0 || on > MAX_MEMBERS) {
            throw new DecoderException("喵呜机：在线玩家条数非法: " + on);
        }
        List<Online> online = new ArrayList<>(Math.min(on, 16));
        for (int i = 0; i < on; i++) {
            online.add(new Online(buf.readUtf(40), buf.readUtf(40), buf.readUtf(16)));
        }
        return new RosterStatePacket(list, online);
    }

    public static void handle(RosterStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> RosterScreenHandler.show(msg));
        c.setPacketHandled(true);
    }
}
