package io.github.zgxhzhr.meowmachine.network;

import io.github.zgxhzhr.meowmachine.client.ReviewFeedbackHandler;
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
 * @param verdict    通过 / 协改 / 打回
 * @param ruleIds    命中的规则号（"?" 表示说不出依据）
 * @param stickerId  要展示的梗图 id（空字符串表示没有匹配）
 * @param comment    给作者的一句话（取前几条发现的原因）
 */
public record ReviewResultPacket(String verdict, List<String> ruleIds, String stickerId, String comment) {

    public static void encode(ReviewResultPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.verdict, 16);
        buf.writeVarInt(msg.ruleIds.size());
        for (String r : msg.ruleIds) {
            buf.writeUtf(r, 8);
        }
        buf.writeUtf(msg.stickerId, 128);
        buf.writeUtf(msg.comment, 512);
    }

    public static ReviewResultPacket decode(FriendlyByteBuf buf) {
        String verdict = buf.readUtf(16);
        int n = buf.readVarInt();
        List<String> rules = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            rules.add(buf.readUtf(8));
        }
        return new ReviewResultPacket(verdict, rules, buf.readUtf(128), buf.readUtf(512));
    }

    public static void handle(ReviewResultPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ReviewFeedbackHandler.show(msg));
        c.setPacketHandled(true);
    }
}
