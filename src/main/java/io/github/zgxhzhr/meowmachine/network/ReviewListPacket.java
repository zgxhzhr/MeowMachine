package io.github.zgxhzhr.meowmachine.network;

import io.github.zgxhzhr.meowmachine.client.ReviewerScreenOpener;
import io.github.zgxhzhr.meowmachine.review.AiOpinions;
import io.github.zgxhzhr.meowmachine.review.ReviewNotes;
import io.github.zgxhzhr.meowmachine.sticker.StickerRegistry;
import io.github.zgxhzhr.meowmachine.review.Submission;
import io.github.zgxhzhr.meowmachine.richtext.RichHtml;
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
public record ReviewListPacket(List<Entry> entries, boolean canFinalize, boolean assistant,
                               List<String> approvedStickers) {

    /** 一次最多下发多少条：界面本身只显示 8 条，多发的没有意义 */
    private static final int MAX_ENTRIES = 512;

    /** 正文在列表包里的长度上限：够审核判断即可，避免列表包过大 */
    private static final int MAX_BODY = 4096;

    /**
     * 一条待审。
     *
     * @param body 正文（**剥掉 HTML 标签后的纯文本**，审核员要能读到实际内容）
     */
    public record Entry(String fullId, String shortId, String author, String title, String body,
                        List<String> notes, List<String> aiOpinions,
                        List<String> diffLines) {

        public static Entry of(Submission s) {
            String full = s.id().toString();
            String plain = RichHtml.stripTags(s.body());
            if (plain.length() > MAX_BODY) {
                plain = plain.substring(0, MAX_BODY);
            }
            List<String> diff = new ArrayList<>();
            io.github.zgxhzhr.meowmachine.review.ApprovedSavedData.Entry prev =
                    io.github.zgxhzhr.meowmachine.review.ApprovedIndex.find(s.id()).orElse(null);
            if (prev != null) {
                var d = io.github.zgxhzhr.meowmachine.review.EditDiffs.of(prev.body(), s.body());
                diff.addAll(d.summaryLines());
            }
            return new Entry(full, full.substring(0, 8), s.authorName(), s.title(), plain,
                    ReviewNotes.of(s.id()), AiOpinions.of(s.id()), List.copyOf(diff));
        }
    }

    /** 由队列快照构造（默认允许终裁） */
    public static ReviewListPacket of(List<SubmissionQueue.Entry> queue) {
        return of(queue, true, false);
    }

    /** @param canFinalize 助理为 false：只能看与写意见，不能终裁 */
    public static ReviewListPacket of(List<SubmissionQueue.Entry> queue, boolean canFinalize) {
        return of(queue, canFinalize, false);
    }

    /** @param assistant 助理：只查看与写意见，不裁决 */
    public static ReviewListPacket of(List<SubmissionQueue.Entry> queue, boolean canFinalize,
                                      boolean assistant) {
        List<Entry> list = new ArrayList<>();
        for (SubmissionQueue.Entry e : queue) {
            list.add(Entry.of(e.submission()));
        }
        return new ReviewListPacket(List.copyOf(list), canFinalize, assistant,
                StickerRegistry.idsForState("approved"));
    }

    public static void encode(ReviewListPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entries.size());
        buf.writeBoolean(msg.canFinalize());
        buf.writeBoolean(msg.assistant());
        buf.writeVarInt(msg.approvedStickers().size());
        for (String id : msg.approvedStickers()) {
            buf.writeUtf(id, 64);
        }
        for (Entry e : msg.entries) {
            buf.writeUtf(e.fullId(), 40);
            buf.writeUtf(e.shortId(), 12);
            buf.writeUtf(e.author(), 40);
            buf.writeUtf(e.title(), Network.MAX_TITLE_LEN);
            buf.writeUtf(e.body(), MAX_BODY);
            buf.writeVarInt(e.notes().size());
            for (String n : e.notes()) {
                buf.writeUtf(n, 240);
            }
            buf.writeVarInt(e.aiOpinions().size());
            for (String n : e.aiOpinions()) {
                buf.writeUtf(n, 240);
            }
            buf.writeVarInt(e.diffLines().size());
            for (String n : e.diffLines()) {
                buf.writeUtf(n, 240);
            }
        }
    }

    public static ReviewListPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        // 客户端连的是任意服务器，网络来的长度不能直接当容量用
        if (n < 0 || n > MAX_ENTRIES) {
            throw new DecoderException("喵呜机：待审列表条数非法: " + n);
        }
        boolean canFinalize = buf.readBoolean();
        boolean assistant = buf.readBoolean();
        int sc = buf.readVarInt();
        if (sc < 0 || sc > 64) {
            throw new DecoderException("喵呜机：通过梗图条数非法: " + sc);
        }
        List<String> approved = new ArrayList<>(sc);
        for (int i = 0; i < sc; i++) {
            approved.add(buf.readUtf(64));
        }
        List<Entry> list = new ArrayList<>(Math.min(n, 16));
        for (int i = 0; i < n; i++) {
            String fullId = buf.readUtf(40);
            String shortId = buf.readUtf(12);
            String author = buf.readUtf(40);
            String title = buf.readUtf(Network.MAX_TITLE_LEN);
            String body = buf.readUtf(MAX_BODY);
            int nc = buf.readVarInt();
            if (nc < 0 || nc > 16) {
                throw new DecoderException("喵呜机：意见条数非法: " + nc);
            }
            List<String> notes = new ArrayList<>(nc);
            for (int k = 0; k < nc; k++) {
                notes.add(buf.readUtf(240));
            }
            int ac = buf.readVarInt();
            if (ac < 0 || ac > 16) {
                throw new DecoderException("喵呜机：AI 意见条数非法: " + ac);
            }
            List<String> ai = new ArrayList<>(ac);
            for (int k = 0; k < ac; k++) {
                ai.add(buf.readUtf(240));
            }
            int dc = buf.readVarInt();
            if (dc < 0 || dc > 16) {
                throw new DecoderException("喵呜机：对比条数非法: " + dc);
            }
            List<String> diff = new ArrayList<>(dc);
            for (int k = 0; k < dc; k++) {
                diff.add(buf.readUtf(240));
            }
            list.add(new Entry(fullId, shortId, author, title, body, List.copyOf(notes),
                    List.copyOf(ai), List.copyOf(diff)));
        }
        return new ReviewListPacket(list, canFinalize, assistant, List.copyOf(approved));
    }

    public static void handle(ReviewListPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ReviewerScreenOpener.open(msg));
        c.setPacketHandled(true);
    }
}
