package io.github.zgxhzhr.meowmachine.network;

import io.github.zgxhzhr.meowmachine.client.MySubmissionsScreenOpener;
import io.github.zgxhzhr.meowmachine.review.Submission;
import io.github.zgxhzhr.meowmachine.review.SubmissionHistory;
import io.github.zgxhzhr.meowmachine.review.SubmissionQueue;
import io.github.zgxhzhr.meowmachine.review.SubmissionState;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 服务端 → 客户端：我的投稿列表（当前草稿 + 待审队列里自己的稿子）。
 *
 * @param rows 第一行永远是当前草稿，其余是队列里的稿子
 */
public record MySubmissionsPacket(List<Entry> rows) {

    /** 网络侧的列表上限：正常玩家不会超过这个数 */
    private static final int MAX_ROWS = 256;

    /**
     * 一条投稿。
     *
     * @param id      完整 UUID（后续操作可用）
     * @param shortId 8 位短 id（显示用）
     * @param title   标题
     * @param state   状态（小写：draft / submitted / approved / returned）
     * @param pending  是否在人工队列里
     * @param editable 能否点「编辑」（已通过的不行）
     */
    public record Entry(String id, String shortId, String title, String state,
                        boolean pending, boolean editable) {
    }

    /** 组装：当前草稿打头，后面接队列里属于该玩家的条目（按 id 去重） */
    public static MySubmissionsPacket of(Submission draft, ServerPlayer player) {
        List<Entry> rows = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (draft != null) {
            rows.add(entryOf(draft, draft.state() == SubmissionState.SUBMITTED));
            seen.add(draft.id().toString());
        }
        if (player != null) {
            for (SubmissionQueue.Entry e : SubmissionQueue.byAuthor(player.getUUID())) {
                Submission s = e.submission();
                if (seen.add(s.id().toString())) {
                    rows.add(entryOf(s, true));
                }
            }
            // 已出结论的稿子不在队列里了，靠历史补上——否则通过后就「查无此人」
            for (SubmissionHistory.Record r : SubmissionHistory.byAuthor(player.getUUID())) {
                if (seen.add(r.id().toString())) {
                    rows.add(new Entry(r.id().toString(), r.id().toString().substring(0, 8),
                            r.title(), r.state(), false,
                            !"approved".equals(r.state())));
                }
            }
        }
        return new MySubmissionsPacket(List.copyOf(rows));
    }

    private static Entry entryOf(Submission s, boolean pending) {
        String id = s.id().toString();
        String state = s.state().name().toLowerCase();
        return new Entry(id, id.substring(0, 8), s.title(), state, pending,
                !"approved".equals(state));
    }

    public static void encode(MySubmissionsPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.rows.size());
        for (Entry e : msg.rows) {
            buf.writeUtf(e.id(), 40);
            buf.writeUtf(e.shortId(), 12);
            buf.writeUtf(e.title(), Network.MAX_TITLE_LEN);
            buf.writeUtf(e.state(), 16);
            buf.writeBoolean(e.pending());
            buf.writeBoolean(e.editable());
        }
    }

    public static MySubmissionsPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_ROWS) {
            throw new DecoderException("喵呜机：投稿列表条数非法: " + n);
        }
        List<Entry> rows = new ArrayList<>(Math.min(n, 16));
        for (int i = 0; i < n; i++) {
            rows.add(new Entry(buf.readUtf(40), buf.readUtf(12),
                    buf.readUtf(Network.MAX_TITLE_LEN), buf.readUtf(16),
                    buf.readBoolean(), buf.readBoolean()));
        }
        return new MySubmissionsPacket(rows);
    }

    public static void handle(MySubmissionsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> MySubmissionsScreenOpener.open(msg));
        c.setPacketHandled(true);
    }
}
