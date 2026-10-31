package io.github.zgxhzhr.meowmachine.network;

import io.github.zgxhzhr.meowmachine.client.SubmissionScreenOpener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 → 客户端：把当前草稿送到客户端并打开编辑界面。
 *
 * @param title  草稿标题
 * @param body   草稿正文
 * @param status 当前状态字符串（draft / submitted / returned），客户端据此决定按钮可用性
 */
public record OpenEditorPacket(String title, String body, String status) {

    public static void encode(OpenEditorPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.title, Network.MAX_TITLE_LEN);
        buf.writeUtf(msg.body, Network.MAX_BODY_LEN);
        buf.writeUtf(msg.status, 32);
    }

    public static OpenEditorPacket decode(FriendlyByteBuf buf) {
        return new OpenEditorPacket(buf.readUtf(Network.MAX_TITLE_LEN),
                buf.readUtf(Network.MAX_BODY_LEN), buf.readUtf(32));
    }

    public static void handle(OpenEditorPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        // 只在客户端处理；DistExecutor 保证这个类在服务端不会被加载
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> SubmissionScreenOpener.open(msg));
        c.setPacketHandled(true);
    }
}
