package io.github.zgxhzhr.meowmachine.client;

import io.github.zgxhzhr.meowmachine.network.MySubmissionsPacket;
import io.github.zgxhzhr.meowmachine.network.Network;
import io.github.zgxhzhr.meowmachine.network.RantPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 对「逆天 AI 审核」写一句吐槽（最多 120 字，每篇 5 条）。 */
public class RantScreen extends Screen {

    private final VerdictScreen parent;
    private final MySubmissionsPacket.Entry entry;
    private EditBox box;

    public RantScreen(VerdictScreen parent, MySubmissionsPacket.Entry entry) {
        super(Component.translatable("gui.meow_machine.rant_title"));
        this.parent = parent;
        this.entry = entry;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        this.box = new EditBox(this.font, cx - 150, this.height / 2 - 20, 300, 20,
                Component.translatable("gui.meow_machine.rant_title"));
        this.box.setMaxLength(120);
        this.addRenderableWidget(this.box);
        this.setFocused(this.box);
        addRenderableWidget(Button.builder(Component.translatable("gui.meow_machine.btn.send"),
                        b -> {
                            Network.CHANNEL.sendToServer(
                                    new RantPacket(entry.id(), box.getValue()));
                            if (this.minecraft != null) {
                                this.minecraft.setScreen(parent);
                            }
                        })
                .bounds(cx - 104, this.height / 2 + 10, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.meow_machine.cancel"),
                        b -> {
                            if (this.minecraft != null) {
                                this.minecraft.setScreen(parent);
                            }
                        })
                .bounds(cx + 4, this.height / 2 + 10, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        g.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 44, 0xFFFFFF);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
