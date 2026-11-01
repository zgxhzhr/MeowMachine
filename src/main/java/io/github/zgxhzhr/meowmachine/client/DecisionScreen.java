package io.github.zgxhzhr.meowmachine.client;

import io.github.zgxhzhr.meowmachine.network.DecidePacket;
import io.github.zgxhzhr.meowmachine.network.Network;
import io.github.zgxhzhr.meowmachine.network.ReviewListPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 裁决原因选择界面：审核员点「打回 / 协改」后先选原因再提交。
 *
 * <p>以前只有三颗按钮，结论里没有任何依据——作者只看到一张「被打回」的梗图，
 * 不知道哪里要改。现在可以选**规则号**（决定用哪张梗图：第 18 条 →「又是灌水」）
 * 并补一句话，作者那边会显示「命中：第 N 条」+ 这句话。
 *
 * <p>选「说不出依据」时走兜底梗图（「要不直接打回算了」）。
 */
public class DecisionScreen extends Screen {

    /** 可选的规则：只列我们真的知道语义的条款 + 「说不出依据」 */
    private static final int[] RULES = {1, 3, 6, 7, 10, 12, 18, 19, 22, 23, 24, 26, 29, 31, -1};

    private static final int COLS = 3;
    private static final int BTN_W = 100;
    private static final int BTN_H = 18;
    private static final int GAP = 4;
    private static final int ROW_H = 22;

    private final ReviewListPacket.Entry entry;
    private final String verdict;
    private final Map<Integer, Button> ruleButtons = new LinkedHashMap<>();

    private int selectedRule = -1;
    private EditBox noteBox;

    public DecisionScreen(ReviewListPacket.Entry entry, String verdict) {
        super(Component.translatable("gui.meow_machine.decide_title"));
        this.entry = entry;
        this.verdict = verdict;
    }

    @Override
    protected void init() {
        ImeCompat.onTextScreenOpened();
        int cx = this.width / 2;
        int gridW = COLS * BTN_W + (COLS - 1) * GAP;
        int left = cx - gridW / 2;
        int top = 30;

        for (int i = 0; i < RULES.length; i++) {
            int rule = RULES[i];
            int col = i % COLS;
            int row = i / COLS;
            Button b = addRenderableWidget(Button.builder(
                            ruleLabel(rule),
                            btn -> {
                                selectedRule = rule;
                                refreshRuleLabels();
                            })
                    .bounds(left + col * (BTN_W + GAP), top + row * ROW_H, BTN_W, BTN_H).build());
            ruleButtons.put(rule, b);
        }
        refreshRuleLabels();

        int noteY = top + ((RULES.length + COLS - 1) / COLS) * ROW_H + 12;
        this.noteBox = new EditBox(this.font, cx - 150, noteY, 300, 18,
                Component.translatable("gui.meow_machine.decide.note_hint"));
        this.noteBox.setMaxLength(120);
        this.noteBox.setHint(Component.translatable("gui.meow_machine.decide.note_hint"));
        addRenderableWidget(noteBox);

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.meow_machine.decide.confirm"),
                        b -> confirm())
                .bounds(cx - 154, noteY + 24, 150, 20).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.meow_machine.close"),
                        b -> this.onClose())
                .bounds(cx + 4, noteY + 24, 150, 20).build());
    }

    private Component ruleLabel(int rule) {
        if (rule < 0) {
            return Component.translatable("gui.meow_machine.reason.unexplained");
        }
        return Component.translatable("gui.meow_machine.reason." + rule);
    }

    private void refreshRuleLabels() {
        for (Map.Entry<Integer, Button> e : ruleButtons.entrySet()) {
            Component base = ruleLabel(e.getKey());
            e.getValue().setMessage(e.getKey() == selectedRule
                    ? Component.literal("» ").append(base)
                    : base);
        }
    }

    private void confirm() {
        String note = noteBox == null ? "" : noteBox.getValue().trim();
        Network.CHANNEL.sendToServer(
                new DecidePacket(entry.fullId(), verdict, selectedRule, note, ""));
        this.onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int cx = this.width / 2;

        g.drawCenteredString(this.font, this.title, cx, 12, 0xFFFFFF);
        g.drawCenteredString(this.font,
                Component.translatable("gui.meow_machine.decide.who",
                        entry.shortId(), entry.author(), verdict),
                cx, 24, 0xAAAAAA);

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        ImeCompat.onTextScreenClosed();
        super.removed();
    }
}
