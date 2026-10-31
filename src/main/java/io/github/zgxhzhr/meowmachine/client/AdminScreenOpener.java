package io.github.zgxhzhr.meowmachine.client;

import io.github.zgxhzhr.meowmachine.network.AdminStatePacket;
import net.minecraft.client.Minecraft;

/**
 * 打开/刷新管理后台（只在客户端加载）。
 *
 * <p>关键点：后台**已经开着**时只原地刷新状态，不重建界面——
 * 否则每次点开关都会把没保存的 AI 配置输入清空。
 */
public final class AdminScreenOpener {

    private AdminScreenOpener() {
    }

    public static void open(AdminStatePacket state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AdminScreen existing) {
            existing.applyState(state);
            return;
        }
        mc.setScreen(new AdminScreen(state));
    }
}
