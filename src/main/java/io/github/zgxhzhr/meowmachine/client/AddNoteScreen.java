package io.github.zgxhzhr.meowmachine.client;

import io.github.zgxhzhr.meowmachine.network.AnnotatePacket;
import io.github.zgxhzhr.meowmachine.network.Network;
import io.github.zgxhzhr.meowmachine.network.ReviewListPacket;
import io.github.zgxhzhr.meowmachine.review.ReviewNotes;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 写审核意见（标注）。
 *
 * <p>助理没有终裁权，但可以留意见；审核员/OP 也能用（终裁前先写一条依据）。
 * 写完服务端会把列表重推一次，所以这里直接关界面即可。
 */
public class AddNoteScreen extends Screen {

    private static final int PANEL_W = 320;

    private final ReviewListPacket.Entry entry;
    private EditBox input;

    public AddNoteScreen(ReviewListPacket.Entry entry) {
        super(Component.translatable("gui.meow_machine.note_title"));
        this.entry = entry;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int left = cx - PANEL_W / 2;
        int y = this.height / 2 - 20;

        this.input = new EditBox(this.font, left, y, PANEL_W, 20,
                Component.translatable("gui.meow_machine.note_hint"));
        this.input.setMaxLength(ReviewNotes.MAX_NOTE_LEN);
        this.input.setHint(Component.translatable("gui.meow_machine.note_hint"));
        this.addRenderableWidget(this.input);
        this.setInitialFocus(this.input);

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.meow_machine.note_send"), b -> send())
                .bounds(cx - 104, y + 28, 100, 20).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.meow_machine.cancel"), b -> this.onClose())
                .bounds(cx + 4, y + 28, 100, 20).build());
    }

    private void send() {
        String text = input.getValue().strip();
        if (text.isEmpty()) {
            return;
        }
        Network.CHANNEL.sendToServer(new AnnotatePacket(entry.fullId(), text));
        this.onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            send();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int cx = this.width / 2;
        g.drawCenteredString(this.font, this.title, cx, 20, 0xFFFFFF);
        g.drawCenteredString(this.font,
                Component.literal(trimToWidth(entry.shortId() + "  " + entry.title(), PANEL_W)),
                cx, this.height / 2 - 36, 0xFFFF80);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private String trimToWidth(String s, int maxWidth) {
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
