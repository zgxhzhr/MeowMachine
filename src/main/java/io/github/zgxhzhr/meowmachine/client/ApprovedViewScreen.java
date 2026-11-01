package io.github.zgxhzhr.meowmachine.client;

import io.github.zgxhzhr.meowmachine.network.ApprovedListPacket;
import io.github.zgxhzhr.meowmachine.network.EditMySubmissionPacket;
import io.github.zgxhzhr.meowmachine.network.Network;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** 已通过条目只读正文（可从列表直接「提交编辑」）。 */
public class ApprovedViewScreen extends Screen {

    private static final int PANEL_W = 380;
    private static final int LINE_H = 10;
    private static final int TOP = 56;

    private final Screen parent;
    private final ApprovedListPacket.Entry entry;
    private final List<String> lines = new ArrayList<>();
    private int scroll = 0;
    private int viewHeight;

    public ApprovedViewScreen(Screen parent, ApprovedListPacket.Entry entry) {
        super(Component.translatable("gui.meow_machine.approved_view_title"));
        this.parent = parent;
        this.entry = entry;
    }

    @Override
    protected void init() {
        this.viewHeight = this.height - 34 - TOP - 4;
        lines.clear();
        String body = io.github.zgxhzhr.meowmachine.richtext.RichHtml.stripTags(entry.body());
        if (body.isBlank()) {
            lines.add(Component.translatable("gui.meow_machine.view_empty").getString());
        } else {
            for (String raw : body.split("\n", -1)) {
                StringBuilder cur = new StringBuilder();
                for (int i = 0; i < raw.length(); i++) {
                    char ch = raw.charAt(i);
                    if (this.font.width(cur.toString() + ch) > PANEL_W - 16 && cur.length() > 0) {
                        lines.add(cur.toString());
                        cur.setLength(0);
                    }
                    cur.append(ch);
                }
                lines.add(cur.toString());
            }
        }
        clamp();

        int cx = this.width / 2;
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.meow_machine.btn.edit"),
                        b -> {
                            Network.CHANNEL.sendToServer(new EditMySubmissionPacket(entry.id()));
                            if (this.minecraft != null) {
                                this.minecraft.setScreen(null);
                            }
                        })
                .bounds(cx - 104, this.height - 28, 100, 20).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.meow_machine.back"),
                        b -> {
                            if (this.minecraft != null) {
                                this.minecraft.setScreen(parent);
                            }
                        })
                .bounds(cx + 4, this.height - 28, 100, 20).build());
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
        g.drawString(this.font, Component.literal(entry.shortId() + "  " + entry.author()),
                left, 26, 0xFFFF80, false);
        g.drawString(this.font, Component.literal(entry.title()), left, 40, 0xDDDDDD, false);

        g.fill(left - 1, TOP - 1, left + PANEL_W + 1, TOP + viewHeight + 1, 0xFF808080);
        g.fill(left, TOP, left + PANEL_W, TOP + viewHeight, 0xFF101010);
        int visible = viewHeight / LINE_H;
        for (int i = 0; i < visible && scroll + i < lines.size(); i++) {
            g.drawString(this.font, Component.literal(lines.get(scroll + i)),
                    left + 8, TOP + 4 + i * LINE_H, 0xE0E0E0, false);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
