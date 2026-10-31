package io.github.zgxhzhr.meowmachine.client;

import io.github.zgxhzhr.meowmachine.network.MySubmissionsPacket;
import net.minecraft.client.Minecraft;

/** 打开「我的投稿」列表（只在客户端加载）。 */
public final class MySubmissionsScreenOpener {

    private MySubmissionsScreenOpener() {
    }

    public static void open(MySubmissionsPacket packet) {
        Minecraft.getInstance().setScreen(new MySubmissionsScreen(packet));
    }
}
