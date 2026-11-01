package io.github.zgxhzhr.meowmachine.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 插入表格的小对话框：选行列数、可切换「首行表头」。
 *
 * <p>布局：标题 → 两行计数器（标签在左、− 数字 + 在右）→ 表头开关 → 插入/关闭。
 * 之前把标签和数字画在了按钮上，所以看起来是糊的。
 */
public class TableInsertScreen extends Screen {

    /** 行列上限 */
    private static final int MAX = 8;
    private static final int BTN = 22;
    private static final int GAP = 26;

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
        int y = this.height / 2 - 30;

        // 行数：[-] 数字 [+]
        addRowSpinner(cx - 96, y, true);
        // 列数：[-] 数字 [+]
        addRowSpinner(cx + 26, y, false);

        addRenderableWidget(Button.builder(headerLabel(), b -> {
                    this.header = !this.header;
                    this.rebuildWidgets();
                })
                .bounds(cx - 90, y + 34, 180, 20).build());

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.meow_machine.table_insert"),
                        b -> {
                            this.callback.insert(rows, cols, header);
                            if (this.minecraft != null) {
                                this.minecraft.setScreen(parent);
                            }
                        })
                .bounds(cx - 94, y + 64, 90, 20).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.meow_machine.cancel"),
                        b -> {
                            if (this.minecraft != null) {
                                this.minecraft.setScreen(parent);
                            }
                        })
                .bounds(cx + 4, y + 64, 90, 20).build());
    }

    /** 一行计数器：− (数字) + */
    private void addRowSpinner(int x, int y, boolean isRows) {
        addRenderableWidget(Button.builder(Component.literal("-"), b -> {
                    if (isRows) {
                        rows = Math.max(1, rows - 1);
                    } else {
                        cols = Math.max(1, cols - 1);
                    }
                    this.rebuildWidgets();
                })
                .bounds(x, y, BTN, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
                    if (isRows) {
                        rows = Math.min(MAX, rows + 1);
                    } else {
                        cols = Math.min(MAX, cols + 1);
                    }
                    this.rebuildWidgets();
                })
                .bounds(x + BTN + GAP, y, BTN, 20).build());
    }

    private Component headerLabel() {
        return Component.translatable("gui.meow_machine.table_header",
                Component.translatable(header
                        ? "gui.meow_machine.table_on" : "gui.meow_machine.table_off"));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int cx = this.width / 2;
        int y = this.height / 2 - 30;

        g.drawCenteredString(this.font, this.title, cx, y - 26, 0xFFFFFF);

        // 标签画在计数器左边，数字画在两个按钮中间
        g.drawString(this.font, Component.translatable("gui.meow_machine.table_rows_label"),
                cx - 140, y + 6, 0xAAAAAA, false);
        g.drawCenteredString(this.font, Component.literal(String.valueOf(rows)),
                cx - 96 + BTN + GAP / 2, y + 6, 0xFFFF80);

        g.drawString(this.font, Component.translatable("gui.meow_machine.table_cols_label"),
                cx - 18, y + 6, 0xAAAAAA, false);
        g.drawCenteredString(this.font, Component.literal(String.valueOf(cols)),
                cx + 26 + BTN + GAP / 2, y + 6, 0xFFFF80);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
