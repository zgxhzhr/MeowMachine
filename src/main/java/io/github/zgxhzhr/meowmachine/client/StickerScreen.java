package io.github.zgxhzhr.meowmachine.client;

import io.github.zgxhzhr.meowmachine.network.ReviewResultPacket;
import io.github.zgxhzhr.meowmachine.sticker.StickerRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 审核结果弹窗：显示梗图 + 结论 + 命中的规则。
 *
 * <p>梗图纹理路径**由 sticker id 推导**（{@code <ns>:textures/sticker/<path>.png}），
 * 所以客户端不需要读数据包就知道画哪张图 —— 服务端只发 id 就够了。
 *
 * <p>找不到纹理时不会崩，只画一个占位框并提示缺图。
 */
public class StickerScreen extends Screen {

    private static final int MAX_IMG = 192;

    private final ReviewResultPacket result;
    private final ResourceLocation texture;

    public StickerScreen(ReviewResultPacket result) {
        super(Component.translatable("gui.meow_machine.result_title"));
        this.result = result;
        this.texture = textureOf(result.stickerId());
    }

    /** 把 sticker id 转成纹理路径；id 为空时返回 null */
    private static ResourceLocation textureOf(String stickerId) {
        if (stickerId == null || stickerId.isBlank()) {
            return null;
        }
        try {
            ResourceLocation id = ResourceLocation.tryParse(stickerId);
            if (id == null) {
                return null;
            }
            return new ResourceLocation(id.getNamespace(),
                    "textures/sticker/" + id.getPath() + ".png");
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);

        int cx = this.width / 2;
        int y = this.height / 4;

        // 结论标题
        g.drawCenteredString(this.font,
                Component.translatable("gui.meow_machine.result." + result.verdict().toLowerCase()),
                cx, y - 24, 0xFFFFFF);

        // 梗图
        if (texture != null) {
            int size = Math.min(MAX_IMG, this.width - 40);
            int x = cx - size / 2;
            g.blit(texture, x, y, 0, 0, size, size, size, size);
            y += size + 8;
        } else {
            int size = MAX_IMG;
            g.fill(cx - size / 2, y, cx + size / 2, y + size, 0xFF202020);
            g.drawCenteredString(this.font,
                    Component.translatable("gui.meow_machine.no_sticker"),
                    cx, y + size / 2 - 4, 0xAAAAAA);
            y += size + 8;
        }

        // 命中的规则
        if (!result.ruleIds().isEmpty()) {
            g.drawCenteredString(this.font,
                    Component.translatable("gui.meow_machine.result.rules",
                            String.join("、", result.ruleIds())),
                    cx, y, 0xFFFF80);
            y += 12;
        }

        // 一句话说明
        if (!result.comment().isEmpty()) {
            g.drawCenteredString(this.font,
                    Component.literal(result.comment()), cx, y, 0xDDDDDD);
            y += 12;
        }

        g.drawCenteredString(this.font,
                Component.translatable("gui.meow_machine.click_to_close"),
                cx, this.height - 30, 0x888888);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // 点任意处关闭
        this.onClose();
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
