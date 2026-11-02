package io.github.zgxhzhr.meowmachine.client;

import io.github.zgxhzhr.meowmachine.network.Network;
import io.github.zgxhzhr.meowmachine.network.ReturnDecisionPacket;
import io.github.zgxhzhr.meowmachine.network.ReturnRequestListPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** 「退回申请」处理：每条两个按钮（同意退回 / 驳回）。 */
public class ReturnRequestsScreen extends Screen {

    private static final int PANEL_W = 420;
    private static final int ROW_H = 30;

    private final Screen parent;
    private final List<ReturnRequestListPacket.Entry> entries;

    public ReturnRequestsScreen(Screen parent, ReturnRequestListPacket packet) {
        super(Component.translatable("gui.meow_machine.return_requests_title"));
        this.parent = parent;
        this.entries = packet == null ? List.of() : packet.entries();
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int left = cx - PANEL_W / 2;
        int top = 44;
        for (int i = 0; i < entries.size() && i < 8; i++) {
            ReturnRequestListPacket.Entry e = entries.get(i);
            int y = top + i * ROW_H;
            addRenderableWidget(Button.builder(Component.translatable("gui.meow_machine.btn.return_now"),
                            b -> {
                                Network.CHANNEL.sendToServer(new ReturnDecisionPacket(e.fullId(), true));
                                this.onClose();
                            })
                    .bounds(left + PANEL_W - 160, y, 76, 18).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.meow_machine.btn.dismiss"),
                            b -> {
                                Network.CHANNEL.sendToServer(new ReturnDecisionPacket(e.fullId(), false));
                                this.onClose();
                            })
                    .bounds(left + PANEL_W - 80, y, 76, 18).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.meow_machine.close"),
                        b -> this.onClose())
                .bounds(cx - 50, this.height - 28, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int cx = this.width / 2;
        int left = cx - PANEL_W / 2;
        int top = 44;
        g.drawCenteredString(this.font, this.title, cx, 16, 0xFFFFFF);
        if (entries.isEmpty()) {
            g.drawCenteredString(this.font,
                    Component.translatable("gui.meow_machine.return_requests_empty"),
                    cx, this.height / 2, 0xAAAAAA);
        }
        for (int i = 0; i < entries.size() && i < 8; i++) {
            ReturnRequestListPacket.Entry e = entries.get(i);
            int y = top + i * ROW_H;
            g.drawString(this.font, Component.literal(e.shortId() + "  " + trim(e.title(), 200)),
                    left, y, 0xFFFF80, false);
            String line = e.requester() + "：" + (e.reason().isBlank() ? "-" : e.reason());
            g.drawString(this.font, Component.literal(trim(line, PANEL_W - 190)),
                    left, y + 12, 0xCCCCCC, false);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private String trim(String s, int maxWidth) {
        if (s == null) {
            return "";
        }
        if (this.font.width(s) <= maxWidth) {
            return s;
        }
        int i = s.length();
        while (i > 0 && this.font.width(s.substring(0, i) + "…") > maxWidth) {
            i--;
        }
        return s.substring(0, i) + "…";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
