package io.github.zgxhzhr.meowmachine.client;

import io.github.zgxhzhr.meowmachine.network.RosterStatePacket;
import net.minecraft.client.Minecraft;

/**
 * 打开/刷新「审核员管理」界面（只在客户端加载）。
 *
 * <p>和后台一样：界面已经开着时只原地刷新成员列表，不重建——
 * 否则每次撤销都会把「玩家名」输入框里没提交的内容清空。
 */
public final class RosterScreenHandler {

    private RosterScreenHandler() {
    }

    public static void show(RosterStatePacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof RosterScreen existing) {
            existing.applyState(packet);
            return;
        }
        mc.setScreen(new RosterScreen(packet));
    }
}
