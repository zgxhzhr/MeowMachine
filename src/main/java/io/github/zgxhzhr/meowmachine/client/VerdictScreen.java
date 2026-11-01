package io.github.zgxhzhr.meowmachine.client;

import io.github.zgxhzhr.meowmachine.network.MySubmissionsPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** 结论详情：**原因全文 + 梗图原图**，与正文分开显示。 */
public class VerdictScreen extends Screen {

    private static final int PANEL_W = 340;
    private static final int LINE_H = 10;
    private static final int TOP = 64;
    private static final int STICKER_SIZE = 120;

    private final Screen parent;
    private final MySubmissionsPacket.Entry entry;
    private final List<String> lines = new ArrayList<>();
    private int viewHeight;
    private int scroll = 0;
    private ResourceLocation texture = null;

    public VerdictScreen(Screen parent, MySubmissionsPacket.Entry entry) {
        super(Component.translatable("gui.meow_machine.verdict_title"));
        this.parent = parent;
        this.entry = entry;
    }

    @Override
    protected void init() {
        int bottom = this.height - 34;
        this.viewHeight = bottom - TOP - 6;

        lines.clear();
        lines.add(Component.translatable("gui.meow_machine.view.state",
                Component.translatable("gui.meow_machine.state." + entry.state())).getString());
        lines.add("");
        List<String> rules = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        if (entry.reasons() != null) {
            for (String r : entry.reasons()) {
                if (r != null && r.startsWith("第 ")) {
                    rules.add(r);
                } else {
                    notes.add(r);
                }
            }
        }
        if (rules.isEmpty() && notes.isEmpty()) {
            lines.add(Component.translatable("gui.meow_machine.view.no_reason").getString());
        }
        for (String reason : rules) {
            for (String seg : wrap(reason, PANEL_W - 28)) {
                lines.add("· " + seg);
            }
        }
        if (!notes.isEmpty()) {
            lines.add("");
            lines.add(Component.translatable("gui.meow_machine.result.notes").getString());
            for (String note : notes) {
                for (String seg : wrap(note, PANEL_W - 28)) {
                    lines.add("· " + seg);
                }
            }
        }
        this.texture = resolveTexture(entry.stickerId());
        clamp();

        int cx = this.width / 2;
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.meow_machine.close"),
                        b -> {
                            if (this.minecraft != null) {
                                this.minecraft.setScreen(parent);
                            }
                        })
                .bounds(cx - 50, this.height - 28, 100, 20).build());
    }

    private ResourceLocation resolveTexture(String stickerId) {
        if (stickerId == null || stickerId.isBlank()) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(stickerId);
        String ns = id == null ? "meow_machine" : id.getNamespace();
        String path = id == null ? stickerId : id.getPath();
        ResourceLocation tex = new ResourceLocation(ns, "textures/sticker/" + path + ".png");
        try {
            return Minecraft.getInstance().getResourceManager().getResource(tex).isPresent() ? tex : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void clamp() {
        int max = Math.max(0, lines.size() - viewHeight / LINE_H);
        scroll = Math.max(0, Math.min(scroll, max));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll -= (int) Math.signum(delta) * 2;
        clamp();
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int cx = this.width / 2;
        int left = cx - PANEL_W / 2;

        g.drawCenteredString(this.font, this.title, cx, 12, 0xFFFFFF);
        g.drawString(this.font, Component.literal(entry.shortId() + "  " + entry.title()),
                left, 28, 0xFFFF80, false);

        // 原因区（可滚动）
        g.fill(left - 1, TOP - 1, left + PANEL_W + 1, TOP + viewHeight + 1, 0xFF808080);
        g.fill(left, TOP, left + PANEL_W, TOP + viewHeight, 0xFF101010);
        int visible = viewHeight / LINE_H;
        for (int i = 0; i < visible && scroll + i < lines.size(); i++) {
            g.drawString(this.font, Component.literal(lines.get(scroll + i)),
                    left + 8, TOP + 4 + i * LINE_H, 0xE0E0E0, false);
        }

        // 梗图原图（右侧，与文字分开）
        if (texture != null) {
            int x = left + PANEL_W + 8;
            int y = TOP;
            g.fill(x - 1, y - 1, x + STICKER_SIZE + 1, y + STICKER_SIZE + 1, 0xFF808080);
            g.blit(texture, x, y, 0, 0, STICKER_SIZE, STICKER_SIZE, STICKER_SIZE, STICKER_SIZE);
        }

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
    public boolean isPauseScreen() {
        return false;
    }
}
