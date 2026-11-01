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
                                 String stickerTexture, String comment) {

    /** 网络包里的列表上限：审核结果不会超过这个数，超过就是坏包 */
    private static final int MAX_RULES = 256;
    private static final int MAX_COMMENT = 400;

    /** 从审核结果构造：规则号、纹理路径、摘要都在这里统一算 */
    public static ReviewResultPacket from(ReviewResult result) {
        // 只把有条款号的发现算进「命中规则」；附言（ruleId=null）只出现在说明里
        List<String> ruleIds = result.findings().stream()
                .filter(f -> f.ruleId() != null)
                .map(f -> String.valueOf(f.ruleId()))
                .toList();
        return new ReviewResultPacket(result.verdict().name(), ruleIds,
                result.stickerId() == null ? "" : result.stickerId(),
                StickerRegistry.texturePath(result.stickerId()),
                summarize(result.findings()));
    }

    private static String summarize(List<ReviewFinding> findings) {
        if (findings == null || findings.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(3, findings.size()); i++) {
            if (i > 0) {
                sb.append(" / ");
            }
            sb.append(findings.get(i).reason());
        }
        String s = sb.toString();
        return s.length() <= MAX_COMMENT ? s : s.substring(0, MAX_COMMENT);
    }

    public static void encode(ReviewResultPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.verdict, 16);
        buf.writeVarInt(msg.ruleIds.size());
        for (String r : msg.ruleIds) {
            buf.writeUtf(r, 8);
        }
        buf.writeUtf(msg.stickerId, 128);
        buf.writeUtf(msg.stickerTexture, 256);
        buf.writeUtf(msg.comment, MAX_COMMENT);
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
        return new ReviewResultPacket(verdict, rules, buf.readUtf(128),
                buf.readUtf(256), buf.readUtf(MAX_COMMENT));
    }

    public static void handle(ReviewResultPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ReviewFeedbackHandler.show(msg));
        c.setPacketHandled(true);
    }
}
