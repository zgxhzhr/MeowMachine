package io.github.zgxhzhr.meowmachine.network;

import io.github.zgxhzhr.meowmachine.client.NoticeHandler;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 → 客户端：一条小的聊天栏提示（带一个可选的参数）。
 *
 * <p>以前所有「操作失败」都只在服务端日志里留一行：审核助理点按钮点了没反应、
 * 已通过的稿子还能再投、命令打错也没有回音。这个包让这些情况都能给玩家一句话。
 *
 * @param messageKey 语言文件 key
 * @param arg        可选参数（空串表示不带参数）
 */
public record NoticePacket(String messageKey, String arg) {

    public static NoticePacket of(String messageKey) {
        return new NoticePacket(messageKey, "");
    }

    public static void encode(NoticePacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.messageKey, 128);
        buf.writeUtf(msg.arg, 128);
    }

    public static NoticePacket decode(FriendlyByteBuf buf) {
        String key = buf.readUtf(128);
        String arg = buf.readUtf(128);
        if (key.isBlank()) {
            throw new DecoderException("喵呜机：提示包缺少语言 key");
        }
        return new NoticePacket(key, arg);
    }

    public static void handle(NoticePacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> NoticeHandler.show(msg));
        c.setPacketHandled(true);
    }
}
