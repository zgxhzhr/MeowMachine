package io.github.zgxhzhr.meowmachine.network;

import io.github.zgxhzhr.meowmachine.client.ReviewFeedbackHandler;
import io.github.zgxhzhr.meowmachine.review.ReviewFinding;
import io.github.zgxhzhr.meowmachine.review.ReviewResult;
import io.github.zgxhzhr.meowmachine.sticker.StickerRegistry;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 服务端 → 客户端：审核结果。
 *
 * @param verdict    结论（PASS / REVISE / RETURN / PENDING）
 * @param ruleIds    命中的规则号（"?" 表示说不出依据）
 * @param stickerId  要展示的梗图 id（空字符串表示没有匹配）
 * @param stickerTexture 梗图的实际纹理路径——由服务端用 {@link StickerRegistry#texturePath}
 *                   算好，这样数据包里自定义的 {@code texture} 才会生效
 * @param comment    给作者的一句话（取前几条发现的原因）
 */
public record ReviewResultPacket(String verdict, List<String> ruleIds, String stickerId,
                                 String stickerTexture, List<String> reasons, List<String> notes) {

    /** 网络包里的列表上限：审核结果不会超过这个数，超过就是坏包 */
    private static final int MAX_RULES = 256;
    private static final int MAX_LINES = 16;
    private static final int MAX_LINE_LEN = 300;

    /** 从审核结果构造：规则号、纹理路径、摘要都在这里统一算 */
    public static ReviewResultPacket from(ReviewResult result) {
        // 只把有条款号的发现算进「命中规则」；附言（ruleId=null）只出现在说明里
        // 同一个条款可能被本地与 AI 各报一次，列表里要去重
        List<String> ruleIds = result.findings().stream()
                .filter(f -> f.ruleId() != null)
                .map(f -> String.valueOf(f.ruleId()))
                .distinct()
                .toList();
        List<String> reasons = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        for (ReviewFinding f : result.findings()) {
            String text = f.reason() == null ? "" : f.reason().strip();
            if (text.isEmpty()) {
                continue;
            }
            if (f.ruleId() != null) {
                if (reasons.size() < MAX_LINES) {
                    reasons.add(text);
                }
            } else if (notes.size() < MAX_LINES) {
                notes.add(text);
            }
        }
        return new ReviewResultPacket(result.verdict().name(), ruleIds,
                result.stickerId() == null ? "" : result.stickerId(),
                StickerRegistry.texturePath(result.stickerId()),
                List.copyOf(reasons), List.copyOf(notes));
    }

    public static void encode(ReviewResultPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.verdict, 16);
        buf.writeVarInt(msg.ruleIds.size());
        for (String r : msg.ruleIds) {
            buf.writeUtf(r, 8);
        }
        buf.writeUtf(msg.stickerId, 128);
        buf.writeUtf(msg.stickerTexture, 256);
        buf.writeVarInt(msg.reasons.size());
        for (String r : msg.reasons) {
            buf.writeUtf(r, MAX_LINE_LEN);
        }
        buf.writeVarInt(msg.notes.size());
        for (String n : msg.notes) {
            buf.writeUtf(n, MAX_LINE_LEN);
        }
    }

    public static ReviewResultPacket decode(FriendlyByteBuf buf) {
        String verdict = buf.readUtf(16);
        int n = buf.readVarInt();
        // 客户端连的是任意服务器：网络来的长度不能直接当容量用
        if (n < 0 || n > MAX_RULES) {
            throw new DecoderException("喵呜机：审核结果的规则条数非法: " + n);
        }
        List<String> rules = new ArrayList<>(Math.min(n, 16));
        for (int i = 0; i < n; i++) {
            rules.add(buf.readUtf(8));
        }
        String stickerId = buf.readUtf(128);
        String stickerTexture = buf.readUtf(256);
        int rc = buf.readVarInt();
        if (rc < 0 || rc > MAX_LINES) {
            throw new DecoderException("喵呜机：原因条数非法: " + rc);
        }
        List<String> reasons = new ArrayList<>(rc);
        for (int i = 0; i < rc; i++) {
            reasons.add(buf.readUtf(MAX_LINE_LEN));
        }
        int nc = buf.readVarInt();
        if (nc < 0 || nc > MAX_LINES) {
            throw new DecoderException("喵呜机：附言条数非法: " + nc);
        }
        List<String> notes = new ArrayList<>(nc);
        for (int i = 0; i < nc; i++) {
            notes.add(buf.readUtf(MAX_LINE_LEN));
        }
        return new ReviewResultPacket(verdict, rules, stickerId, stickerTexture,
                List.copyOf(reasons), List.copyOf(notes));
    }

    public static void handle(ReviewResultPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ReviewFeedbackHandler.show(msg));
        c.setPacketHandled(true);
    }
}
