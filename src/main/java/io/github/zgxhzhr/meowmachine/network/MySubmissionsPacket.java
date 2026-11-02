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
     * @param body     正文（剥标签纯文本，够「查看」看内容）
     */
    public record Entry(String id, String shortId, String title, String state,
                        boolean pending, boolean editable, String body,
                        List<String> reasons, String stickerId) {
    }

    /** 组装：当前草稿打头，后面接队列里属于该玩家的条目（按 id 去重） */
    public static MySubmissionsPacket of(Submission draft, ServerPlayer player) {
        List<Entry> rows = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (draft != null) {
            rows.add(entryOf(draft, draft.state() == SubmissionState.SUBMITTED, List.of(), ""));
            seen.add(draft.id().toString());
        }
        if (player != null) {
            for (SubmissionQueue.Entry e : SubmissionQueue.byAuthor(player.getUUID())) {
                Submission s = e.submission();
                if (seen.add(s.id().toString())) {
                    rows.add(entryOf(s, true, List.of(), ""));
                }
            }
            // 已出结论的稿子不在队列里了，靠历史补上——否则通过后就「查无此人」
            for (SubmissionHistory.Record r : SubmissionHistory.byAuthor(player.getUUID())) {
                if (seen.add(r.id().toString())) {
                    rows.add(new Entry(r.id().toString(), r.id().toString().substring(0, 8),
                            r.title(), r.state(), false,
                            !"approved".equals(r.state()), plainBody(r.body()),
                            r.reasons(), r.stickerId()));
                }
            }
        }
        return new MySubmissionsPacket(List.copyOf(rows));
    }

    private static Entry entryOf(Submission s, boolean pending, List<String> reasons, String sticker) {
        String id = s.id().toString();
        String state = s.state().name().toLowerCase();
        return new Entry(id, id.substring(0, 8), s.title(), state, pending,
                !"approved".equals(state), plainBody(s.body()), reasons, sticker);
    }

    /** 正文在列表包里的长度上限 */
    private static final int MAX_BODY = 4096;

    private static String plainBody(String body) {
        String plain = io.github.zgxhzhr.meowmachine.richtext.RichHtml.stripTags(body);
        return plain.length() > MAX_BODY ? plain.substring(0, MAX_BODY) : plain;
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
            buf.writeUtf(e.body() == null ? "" : e.body(), MAX_BODY);
            buf.writeVarInt(e.reasons() == null ? 0 : e.reasons().size());
            if (e.reasons() != null) {
                for (String r : e.reasons()) {
                    buf.writeUtf(r, 400);
                }
            }
            buf.writeUtf(e.stickerId() == null ? "" : e.stickerId(), 64);
        }
    }

    public static MySubmissionsPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_ROWS) {
            throw new DecoderException("喵呜机：投稿列表条数非法: " + n);
        }
        List<Entry> rows = new ArrayList<>(Math.min(n, 16));
        for (int i = 0; i < n; i++) {
            String rid = buf.readUtf(40);
            String rshort = buf.readUtf(12);
            String rtitle = buf.readUtf(Network.MAX_TITLE_LEN);
            String rstate = buf.readUtf(16);
            boolean rpending = buf.readBoolean();
            boolean reditable = buf.readBoolean();
            String rbody = buf.readUtf(MAX_BODY);
            int rc = buf.readVarInt();
            if (rc < 0 || rc > 32) {
                throw new DecoderException("喵呜机：原因条数非法: " + rc);
            }
            List<String> rreasons = new ArrayList<>(rc);
            for (int k = 0; k < rc; k++) {
                rreasons.add(buf.readUtf(400));
            }
            String rstick = buf.readUtf(64);
            rows.add(new Entry(rid, rshort, rtitle, rstate, rpending, reditable, rbody,
                    List.copyOf(rreasons), rstick));
        }
        return new MySubmissionsPacket(rows);
    }

    public static void handle(MySubmissionsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> MySubmissionsScreenOpener.open(msg));
        c.setPacketHandled(true);
    }
}
