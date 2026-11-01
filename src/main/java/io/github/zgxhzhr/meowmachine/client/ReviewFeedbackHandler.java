package io.github.zgxhzhr.meowmachine.client;

import io.github.zgxhzhr.meowmachine.network.ReviewResultPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * 收到审核结果后打开梗图弹窗。
 *
 * <p>聊天栏只留一行摘要（方便事后翻记录），主要反馈走 {@link StickerScreen} 的大图。
 *
 * <p>0.1.0 直接把枚举名塞进聊天栏（玩家看到的是「审核结果：REVISE」），
 * 规则行还会拼成「第 3、12 条」。现在两条都走本地化。
 */
public final class ReviewFeedbackHandler {

    private ReviewFeedbackHandler() {
    }

    public static void show(ReviewResultPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        // 聊天栏摘要（结论用本地化名）
        mc.player.displayClientMessage(
                Component.translatable("chat.meow_machine.result",
                        Component.translatable(verdictKey(msg.verdict()))), false);
        if (!msg.ruleIds().isEmpty()) {
            mc.player.displayClientMessage(
                    Component.translatable("chat.meow_machine.rules", joinRules(msg.ruleIds())), false);
        }

        // 正在编辑投稿时先把草稿存了再弹结果，否则编辑界面被顶掉、内容直接消失
        if (mc.screen instanceof SubmissionScreen sub) {
            sub.onClose();
        }
        // 主反馈：梗图弹窗
        mc.setScreen(new StickerScreen(msg));
    }

    private static String verdictKey(String verdict) {
        return "gui.meow_machine.result." + (verdict == null ? "pending" : verdict.toLowerCase());
    }

    private static String joinRules(java.util.List<String> ruleIds) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ruleIds.size() && i < 5; i++) {
            if (i > 0) {
                sb.append("、");
            }
            sb.append("第 ").append(ruleIds.get(i)).append(" 条");
        }
        return sb.toString();
    }
}
