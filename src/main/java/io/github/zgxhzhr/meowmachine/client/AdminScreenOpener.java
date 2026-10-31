package io.github.zgxhzhr.meowmachine.client;

import io.github.zgxhzhr.meowmachine.network.AdminStatePacket;
import net.minecraft.client.Minecraft;

/** 打开管理后台（只在客户端加载）。 */
public final class AdminScreenOpener {

    private AdminScreenOpener() {
    }

    public static void open(AdminStatePacket state) {
        Minecraft.getInstance().setScreen(new AdminScreen(state));
    }
}
