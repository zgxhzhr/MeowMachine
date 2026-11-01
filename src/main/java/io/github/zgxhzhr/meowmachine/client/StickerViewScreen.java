package io.github.zgxhzhr.meowmachine.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 梗图放大查看：右键梗图物品时打开，显示原图（512px）+ 名字 + 小故事。
 *
 * <p>为什么需要它：物品栏里只有 16px，气泡里的字永远看不清；
 * 想看整张图（包括文字）就得放大到这个界面。
 */
public class StickerViewScreen extends Screen {

    private static final int MAX_IMG = 256;
    private final String stickerId;
    private final ResourceLocation texture;

    public StickerViewScreen(String stickerId) {
        super(Component.translatable("gui.meow_machine.sticker_view_title"));
        this.stickerId = stickerId;
        this.texture = new ResourceLocation("meow_machine", "textures/sticker/" + stickerId + ".png");
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int cx = this.width / 2;
        int y = Math.max(12, this.height / 10);

        g.drawCenteredString(this.font, this.title, cx, y, 0xFFFFFF);
        y += 14;

        java.util.List<String> story = io.github.zgxhzhr.meowmachine.item.StickerItem.wrap(
                Component.translatable("item.meow_machine.sticker_" + stickerId + ".story").getString(), 22);
        int textH = 30 + story.size() * 12;
        int size = Math.min(MAX_IMG, Math.min(this.width - 40, this.height - textH - 40));
        size = Math.max(64, size);
        g.blit(texture, cx - size / 2, y, 0, 0, size, size, size, size);
        y += size + 8;

        g.drawCenteredString(this.font,
                Component.translatable("item.meow_machine.sticker_" + stickerId), cx, y, 0xFFFF80);
        y += 13;
        for (String line : story) {
            g.drawCenteredString(this.font, Component.literal(line), cx, y, 0xDDDDDD);
            y += 12;
        }

        g.drawCenteredString(this.font,
                Component.translatable("gui.meow_machine.click_to_close"),
                cx, this.height - 22, 0x888888);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        this.onClose();
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
