package io.github.zgxhzhr.meowmachine.client;

import io.github.zgxhzhr.meowmachine.network.Network;
import io.github.zgxhzhr.meowmachine.network.RequestReturnPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 退回（或申请退回）时填写理由。 */
public class ReturnReasonScreen extends Screen {

    private final Screen parent;
    private final String entryId;
    private final boolean reviewer;
    private EditBox box;

    public ReturnReasonScreen(Screen parent, String entryId, boolean reviewer) {
        super(Component.translatable(reviewer
                ? "gui.meow_machine.return_reason_title"
                : "gui.meow_machine.request_reason_title"));
        this.parent = parent;
        this.entryId = entryId;
        this.reviewer = reviewer;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        this.box = new EditBox(this.font, cx - 150, this.height / 2 - 16, 300, 20,
                Component.translatable("gui.meow_machine.return_reason_title"));
        this.box.setMaxLength(120);
        this.box.setHint(Component.translatable("gui.meow_machine.return_reason_hint"));
        this.addRenderableWidget(this.box);
        this.setFocused(this.box);
        addRenderableWidget(Button.builder(Component.translatable("gui.meow_machine.btn.send"),
                        b -> {
                            Network.CHANNEL.sendToServer(
                                    new RequestReturnPacket(entryId, box.getValue()));
                            if (this.minecraft != null) {
                                this.minecraft.setScreen(parent);
                            }
                        })
                .bounds(cx - 104, this.height / 2 + 12, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.meow_machine.cancel"),
                        b -> {
                            if (this.minecraft != null) {
                                this.minecraft.setScreen(parent);
                            }
                        })
                .bounds(cx + 4, this.height / 2 + 12, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        g.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 40, 0xFFFFFF);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
