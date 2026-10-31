package io.github.zgxhzhr.meowmachine.client;

import io.github.zgxhzhr.meowmachine.network.NoticePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * 收到服务端一句提示，显示在聊天栏。
 *
 * <p>0.1.0 里「审核助理点了按钮没反应」「已通过的稿子还能重投」
 * 这类情况只在服务端日志留痕，玩家完全不知道为什么失败。
 */
public final class NoticeHandler {

    private NoticeHandler() {
    }

    public static void show(NoticePacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        String arg = msg.arg() == null ? "" : msg.arg();
        Component text = arg.isBlank()
                ? Component.translatable(msg.messageKey())
                : Component.translatable(msg.messageKey(), arg);
        mc.player.displayClientMessage(text, false);
    }
}
