package io.github.zgxhzhr.meowmachine.client;

import io.github.zgxhzhr.meowmachine.network.ReviewListPacket;
import net.minecraft.client.Minecraft;

/** 打开审核界面（只在客户端加载）。 */
public final class ReviewerScreenOpener {

    private ReviewerScreenOpener() {
    }

    public static void open(ReviewListPacket msg) {
        Minecraft.getInstance().setScreen(new ReviewerScreen(msg));
    }
}
