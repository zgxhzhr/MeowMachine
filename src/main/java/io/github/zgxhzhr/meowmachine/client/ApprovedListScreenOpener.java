package io.github.zgxhzhr.meowmachine.client;

import io.github.zgxhzhr.meowmachine.network.ApprovedListPacket;
import net.minecraft.client.Minecraft;

/** 客户端打开入口（避免服务端类加载客户端 Screen）。 */
public final class ApprovedListScreenOpener {

    private ApprovedListScreenOpener() {
    }

    public static void open(ApprovedListPacket packet) {
        Minecraft.getInstance().setScreen(new ApprovedListScreen(packet));
    }
}
