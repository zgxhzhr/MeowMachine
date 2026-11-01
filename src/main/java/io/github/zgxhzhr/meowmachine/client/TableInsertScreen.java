package io.github.zgxhzhr.meowmachine.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 插入表格的小对话框：选行列数、可切换「首行表头」。
 *
 * <p>以前「⊞」固定插 2×3、表头永远开，改不了；现在在这里调完再插。
 */
public class TableInsertScreen extends Screen {

    /** 行列上限：屏幕放得下、也不至于点太多次 */
    private static final int MAX = 8;

    /** 插入回调：rows / cols / header */
    public interface Callback {
        void insert(int rows, int cols, boolean header);
    }

    private final Screen parent;
    private final Callback callback;

    private int rows = 2;
    private int cols = 3;
    private boolean header = true;

    public TableInsertScreen(Screen parent, Callback callback) {
        super(Component.translatable("gui.meow_machine.table_title"));
        this.parent = parent;
        this.callback = callback;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;

        // 行 / 列：− 数字 +
        addSpinner(cx - 120, cy - 20, true);
        addSpinner(cx + 20, cy - 20, false);

        addRenderableWidget(Button.builder(headerLabel(), b -> {
                    this.header = !this.header;
                    this.rebuildWidgets();
                })
                .bounds(cx - 90, cy + 14, 180, 20).build());

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.meow_machine.table_insert"),
                        b -> {
                            this.callback.insert(rows, cols, header);
                            if (this.minecraft != null) {
                                this.minecraft.setScreen(parent);
                            }
                        })
                .bounds(cx - 94, cy + 44, 90, 20).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.meow_machine.cancel"),
                        b -> {
                            if (this.minecraft != null) {
                                this.minecraft.setScreen(parent);
                            }
                        })
                .bounds(cx + 4, cy + 44, 90, 20).build());
    }

    private Component headerLabel() {
        return Component.translatable("gui.meow_machine.table_header",
                Component.translatable(header
                        ? "gui.meow_machine.table_on" : "gui.meow_machine.table_off"));
    }

    private void addSpinner(int x, int y, boolean isRows) {
        addRenderableWidget(Button.builder(Component.literal("-"), b -> {
                    if (isRows) {
                        rows = Math.max(1, rows - 1);
                    } else {
                        cols = Math.max(1, cols - 1);
                    }
                    this.rebuildWidgets();
                })
                .bounds(x, y, 22, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
                    if (isRows) {
                        rows = Math.min(MAX, rows + 1);
                    } else {
                        cols = Math.min(MAX, cols + 1);
                    }
                    this.rebuildWidgets();
                })
                .bounds(x + 66, y, 22, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int cx = this.width / 2;
        int cy = this.height / 2;

        g.drawCenteredString(this.font, this.title, cx, cy - 56, 0xFFFFFF);
        g.drawCenteredString(this.font,
                Component.translatable("gui.meow_machine.table_rows", rows), cx - 88, cy - 4 - 12, 0xAAAAAA);
        g.drawCenteredString(this.font,
                Component.translatable("gui.meow_machine.table_cols", cols), cx + 52, cy - 4 - 12, 0xAAAAAA);
        // 数字本身
        g.drawCenteredString(this.font, Component.literal(String.valueOf(rows)),
                cx - 120 + 44, cy - 15, 0xFFFF80);
        g.drawCenteredString(this.font, Component.literal(String.valueOf(cols)),
                cx + 20 + 44, cy - 15, 0xFFFF80);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
