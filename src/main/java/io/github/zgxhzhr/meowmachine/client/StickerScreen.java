package io.github.zgxhzhr.meowmachine.client;

import io.github.zgxhzhr.meowmachine.network.ReviewResultPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 审核结果弹窗：显示梗图 + 结论 + 命中的规则。
 *
 * <p>纹理路径**由服务端随结果包发来**（{@code ReviewResultPacket.stickerTexture}），
 * 所以数据包里自定义的 {@code texture} 会生效；包里的路径为空时退回按 id 推
 * （{@code <ns>:textures/sticker/<path>.png}）。
 *
 * <p>找不到纹理时不画紫黑方块，而是给一个占位框 + 提示：以前只有 id 非法才会走占位分支，
 * 「png 缺失」这种情况其实会画 missing-texture。
 */
public class StickerScreen extends Screen {

    private static final int MAX_IMG = 192;
    /** 图下面还要画「命中规则 / 一句话说明 / 点击关闭」，给它们留的高度 */
    private static final int RESERVED_BOTTOM = 96;

    private final ReviewResultPacket result;
    private final ResourceLocation texture;

    public StickerScreen(ReviewResultPacket result) {
        super(Component.translatable("gui.meow_machine.result_title"));
        this.result = result;
        this.texture = resolveTexture(result);
    }

    /** 优先用服务端给的路径，其次按 sticker id 推；都不可用返回 null */
    private static ResourceLocation resolveTexture(ReviewResultPacket result) {
        String given = result.stickerTexture();
        if (given != null && !given.isBlank()) {
            ResourceLocation parsed = ResourceLocation.tryParse(given);
            if (parsed != null && resourceExists(parsed)) {
                return parsed;
            }
        }
        String stickerId = result.stickerId();
        if (stickerId == null || stickerId.isBlank()) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(stickerId);
        if (id == null) {
            return null;
        }
        ResourceLocation derived = new ResourceLocation(id.getNamespace(),
                "textures/sticker/" + id.getPath() + ".png");
        return resourceExists(derived) ? derived : null;
    }

    private static boolean resourceExists(ResourceLocation texture) {
        try {
            return Minecraft.getInstance().getResourceManager().getResource(texture).isPresent();
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);

        int cx = this.width / 2;
        int top = Math.max(18, this.height / 8);
        int y = top;

        // 结论标题
        g.drawCenteredString(this.font,
                Component.translatable("gui.meow_machine.result." + result.verdict().toLowerCase()),
                cx, y, 0xFFFFFF);
        y += 16;

        // 梗图：尺寸同时受宽、高约束，矮窗口（GUI scale 4 的逻辑高 270）也不会盖住底部文字
        int size = Math.min(MAX_IMG, Math.min(this.width - 40, this.height - y - RESERVED_BOTTOM));
        size = Math.max(48, size);
        if (texture != null) {
            g.blit(texture, cx - size / 2, y, 0, 0, size, size, size, size);
        } else {
            g.fill(cx - size / 2, y, cx + size / 2, y + size, 0xFF202020);
            g.drawCenteredString(this.font,
                    Component.translatable("gui.meow_machine.no_sticker"),
                    cx, y + size / 2 - 4, 0xAAAAAA);
        }
        y += size + 8;

        // 命中的规则
        if (!result.ruleIds().isEmpty()) {
            g.drawCenteredString(this.font,
                    Component.translatable("gui.meow_machine.result.rules", joinRules(result.ruleIds())),
                    cx, y, 0xFFFF80);
            y += 12;
        }

        // 一句话说明
        if (result.comment() != null && !result.comment().isEmpty()) {
            g.drawCenteredString(this.font,
                    Component.literal(trimToWidth(result.comment(), this.width - 40)), cx, y, 0xDDDDDD);
            y += 12;
        }

        g.drawCenteredString(this.font,
                Component.translatable("gui.meow_machine.click_to_close"),
                cx, this.height - 24, 0x888888);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private static String joinRules(java.util.List<String> ruleIds) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ruleIds.size() && i < 5; i++) {
            if (i > 0) {
                sb.append("、");
            }
            sb.append("第 ").append(ruleIds.get(i)).append(" 条");
        }
        return sb.toString();
    }

    private String trimToWidth(String s, int maxWidth) {
        if (this.font.width(s) <= maxWidth) {
            return s;
        }
        String ellipsis = "…";
        int i = s.length();
        while (i > 0 && this.font.width(s.substring(0, i) + ellipsis) > maxWidth) {
            i--;
        }
        return s.substring(0, i) + ellipsis;
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
