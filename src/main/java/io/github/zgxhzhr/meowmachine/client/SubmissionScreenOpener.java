package io.github.zgxhzhr.meowmachine.client;

import io.github.zgxhzhr.meowmachine.network.OpenEditorPacket;
import net.minecraft.client.Minecraft;

/**
 * 打开投稿编辑界面。
 *
 * <p>这个类只在客户端被加载（调用点包在 {@code DistExecutor.unsafeRunWhenOn(Dist.CLIENT, ...)} 里），
 * 所以直接引用 {@link Minecraft} 是安全的。
 */
public final class SubmissionScreenOpener {

    private SubmissionScreenOpener() {
    }

    public static void open(OpenEditorPacket msg) {
        Minecraft.getInstance().setScreen(new SubmissionScreen(msg));
    }
}
