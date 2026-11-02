package io.github.zgxhzhr.meowmachine.client;

import io.github.zgxhzhr.meowmachine.network.ReviewListPacket;
import net.minecraft.client.Minecraft;

/** 打开审核界面（只在客户端加载）。 */
public final class ReviewerScreenOpener {

    private ReviewerScreenOpener() {
    }

    private static volatile ReviewListPacket latest = null;

    public static void open(ReviewListPacket msg) {
        latest = msg;
        Minecraft.getInstance().setScreen(new ReviewerScreen(msg));
    }

    /** 收到退回申请数据后重建界面，让按钮立刻出现（不用手动点刷新） */
    public static void refreshIfOpen() {
        ReviewListPacket p = latest;
        if (p == null) {
            return;
        }
        var current = Minecraft.getInstance().screen;
        if (current instanceof ReviewerScreen) {
            Minecraft.getInstance().setScreen(new ReviewerScreen(p));
        }
    }
}
