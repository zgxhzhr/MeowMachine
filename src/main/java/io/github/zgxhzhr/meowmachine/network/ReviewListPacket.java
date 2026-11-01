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
                        List<String> diffLines, List<String> localRules) {

        public static Entry of(Submission s, boolean withDiff) {
            String full = s.id().toString();
            // 审核员要能看出结构：表格用 | a | b |、列表用 • / 1、，纯文本会把这些压成一串
            String plain = RichHtml.toAiText(s.body());
            if (plain.isBlank()) {
                plain = RichHtml.stripTags(s.body());
            }
            if (plain.length() > MAX_BODY) {
                plain = plain.substring(0, MAX_BODY);
            }
            List<String> diff = new ArrayList<>();
            io.github.zgxhzhr.meowmachine.review.ApprovedSavedData.Entry prev =
                    io.github.zgxhzhr.meowmachine.review.ApprovedIndex.find(s.id()).orElse(null);
            if (withDiff && prev != null) {
                var d = io.github.zgxhzhr.meowmachine.review.EditDiffs.of(prev.body(), s.body());
                diff.addAll(d.summaryLines());
            }
            // 预检也由服务端算（原来客户端自己重跑，和真正生效的结论可能不一致）
            List<String> precheck = new ArrayList<>();
            for (var f : io.github.zgxhzhr.meowmachine.review.LocalRules
                    .check(s.title(), plain, s.body())) {
                precheck.add("第" + (f.ruleId() == null ? "?" : f.ruleId()) + "条："
                        + (f.reason() == null ? "" : f.reason()));
                if (precheck.size() >= 8) {
                    break;
                }
            }
            return new Entry(full, full.substring(0, 8), s.authorName(), s.title(), plain,
                    ReviewNotes.of(s.id()), AiOpinions.of(s.id()), List.copyOf(diff),
                    List.copyOf(precheck));
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
        int i = 0;
        for (SubmissionQueue.Entry e : queue) {
            // 主线程上做 stripTags/对比是有成本的：只给前 150 条算，其余等客户端重拉
            if (i++ >= 150) {
                break;
            }
            list.add(Entry.of(e.submission(), i <= 40));
        }
        return new ReviewListPacket(List.copyOf(list), canFinalize, assistant,
                StickerRegistry.idsForState("approved"));
    }

    /** 单包字节预算（Netty 上限 2MB，留足余量；超了就少发几条，客户端可重拉） */
    private static final int BYTE_BUDGET = 1_200_000;

    public static void encode(ReviewListPacket msg, FriendlyByteBuf buf) {
        // 超出预算的条目直接不发：前端还有「更多」提示，重新打开列表即可
        java.util.List<Entry> sendable = new ArrayList<>();
        int budget = BYTE_BUDGET;
        for (Entry e : msg.entries) {
            int cost = e.body().length() * 2 + 200;
            if (cost > budget) {
                break;
            }
            budget -= cost;
            sendable.add(e);
        }
        buf.writeVarInt(sendable.size());
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
            buf.writeVarInt(e.localRules().size());
            for (String n : e.localRules()) {
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
            int lc = buf.readVarInt();
            if (lc < 0 || lc > 32) {
                throw new DecoderException("喵呜机：预检条数非法: " + lc);
            }
            List<String> pre = new ArrayList<>(lc);
            for (int k = 0; k < lc; k++) {
                pre.add(buf.readUtf(240));
            }
            list.add(new Entry(fullId, shortId, author, title, body, List.copyOf(notes),
                    List.copyOf(ai), List.copyOf(diff), List.copyOf(pre)));
        }
        return new ReviewListPacket(list, canFinalize, assistant, List.copyOf(approved));
    }

    public static void handle(ReviewListPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ReviewerScreenOpener.open(msg));
        c.setPacketHandled(true);
    }
}
