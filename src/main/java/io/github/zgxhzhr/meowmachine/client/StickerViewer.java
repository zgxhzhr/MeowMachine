package io.github.zgxhzhr.meowmachine.client;

import net.minecraft.client.Minecraft;

/** 打开「梗图放大查看」界面（客户端专用入口，避免服务端加载客户端类）。 */
public final class StickerViewer {

    private StickerViewer() {
    }

    public static void open(String stickerId) {
        Minecraft.getInstance().setScreen(new StickerViewScreen(stickerId));
    }
}
