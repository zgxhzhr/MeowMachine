package io.github.zgxhzhr.meowmachine.client;

import io.github.zgxhzhr.meowmachine.network.ReviewResultPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * 收到审核结果后打开梗图弹窗。
 *
 * <p>聊天栏只留一行摘要（方便事后翻记录），主要反馈走 {@link StickerScreen} 的大图。
 */
public final class ReviewFeedbackHandler {

    private ReviewFeedbackHandler() {
    }

    public static void show(ReviewResultPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        // 聊天栏摘要
        mc.player.displayClientMessage(
                Component.translatable("chat.meow_machine.result", msg.verdict()), false);
        if (!msg.ruleIds().isEmpty()) {
            mc.player.displayClientMessage(
                    Component.translatable("chat.meow_machine.rules",
                            String.join("、", msg.ruleIds())), false);
        }

        // 主反馈：梗图弹窗
        mc.setScreen(new StickerScreen(msg));
    }
}
