package io.github.zgxhzhr.meowmachine.client;

import io.github.zgxhzhr.meowmachine.network.ReviewResultPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * 审核结果弹窗：梗图 + 结论 + 命中规则 + **原因全文** + **附言**。
 *
 * <p>以前原因只截成一行、附言混在原因里看不出来；现在分开排版并支持滚轮滚动，
 * 正文不会出现在这里（正文去「我的投稿 → 查看」）。
 */
public class StickerScreen extends Screen {

    private static final int MAX_IMG = 160;
    private static final int LINE_H = 10;

    private final ReviewResultPacket result;
    private final ResourceLocation texture;
    private final List<String> reasonLines = new ArrayList<>();
    private int scroll = 0;
    private int textTop = 0;
    private int textHeight = 0;

    public StickerScreen(ReviewResultPacket result) {
        super(Component.translatable("gui.meow_machine.result_title"));
        this.result = result;
        this.texture = resolveTexture(result);
    }

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

    private void buildLines() {
        reasonLines.clear();
        if (result.ruleIds() != null && !result.ruleIds().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < result.ruleIds().size() && i < 8; i++) {
                if (i > 0) {
                    sb.append("、");
                }
                sb.append("第 ").append(result.ruleIds().get(i)).append(" 条");
            }
            reasonLines.add(Component.translatable(
                    "gui.meow_machine.result.rules", sb.toString()).getString());
            reasonLines.add("");
        }
        if (result.reasons() != null && !result.reasons().isEmpty()) {
            reasonLines.add(Component.translatable("gui.meow_machine.result.reasons").getString());
            for (String r : result.reasons()) {
                for (String seg : wrap(r, this.width - 40)) {
                    reasonLines.add("  " + seg);
                }
            }
        }
        if (result.notes() != null) {
            java.util.List<String> notes = new java.util.ArrayList<>();
            java.util.List<String> rants = new java.util.ArrayList<>();
            for (String n : result.notes()) {
                if (n != null && n.startsWith("吐槽：")) {
                    rants.add(n.substring(3));
                } else if (n != null && !n.isBlank()) {
                    notes.add(n);
                }
            }
            if (!notes.isEmpty()) {
                reasonLines.add("");
                reasonLines.add(Component.translatable("gui.meow_machine.result.notes").getString());
                for (String n : notes) {
                    for (String seg : wrap(n, this.width - 40)) {
                        reasonLines.add("  " + seg);
                    }
                }
            }
            if (!rants.isEmpty()) {
                reasonLines.add("");
                reasonLines.add(Component.translatable("gui.meow_machine.result.rant").getString());
                for (String n : rants) {
                    for (String seg : wrap(n, this.width - 40)) {
                        reasonLines.add("  " + seg);
                    }
                }
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int maxScroll = Math.max(0, reasonLines.size() - textHeight / LINE_H);
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(delta) * 2));
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        buildLines();

        int cx = this.width / 2;
        int y = Math.max(16, this.height / 8);

        g.drawCenteredString(this.font,
                Component.translatable("gui.meow_machine.result." + result.verdict().toLowerCase()),
                cx, y, 0xFFFFFF);
        y += 16;

        int size = Math.min(MAX_IMG, Math.min(this.width - 40, this.height / 3));
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

        this.textTop = y;
        this.textHeight = Math.max(LINE_H, this.height - 28 - y);
        int visible = textHeight / LINE_H;
        for (int i = 0; i < visible && scroll + i < reasonLines.size(); i++) {
            g.drawString(this.font, Component.literal(reasonLines.get(scroll + i)),
                    cx - this.width / 2 + 20, y + i * LINE_H, 0xDDDDDD, false);
        }
        if (reasonLines.size() > visible) {
            g.drawString(this.font,
                    Component.literal((scroll + visible) + "/" + reasonLines.size()),
                    this.width - 60, y - 10, 0x808080, false);
        }

        g.drawCenteredString(this.font,
                Component.translatable("gui.meow_machine.click_to_close"),
                cx, this.height - 16, 0x888888);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private List<String> wrap(String text, int maxWidth) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            out.add("");
            return out;
        }
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (this.font.width(cur.toString() + ch) > maxWidth && cur.length() > 0) {
                out.add(cur.toString());
                cur.setLength(0);
            }
            cur.append(ch);
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        }
        return out;
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
