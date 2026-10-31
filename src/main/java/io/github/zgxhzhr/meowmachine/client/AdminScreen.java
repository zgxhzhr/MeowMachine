package io.github.zgxhzhr.meowmachine.client;

import io.github.zgxhzhr.meowmachine.network.AdminActionPacket;
import io.github.zgxhzhr.meowmachine.network.AdminStatePacket;
import io.github.zgxhzhr.meowmachine.network.Network;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 管理后台：AI 开关 / 接管 / 单人自审 + **游戏内 AI 配置** + 待审入口。
 *
 * <p>照 TLM 的做法把 AI 配置搬进游戏内界面（它也是 GUI 里填站点与密钥），
 * 不用玩家去翻 {@code config/meow_machine_ai.json}。
 *
 * <p>每次操作都发 {@link AdminActionPacket} 给服务端，服务端校验权限、落盘、
 * 再把最新 {@link AdminStatePacket} 回推刷新本界面。
 */
public class AdminScreen extends Screen {

    private static final int FIELD_W = 300;
    private static final int FIELD_H = 18;

    private final AdminStatePacket state;

    private EditBox endpointBox;
    private EditBox apiKeyBox;
    private EditBox modelBox;

    public AdminScreen(AdminStatePacket state) {
        super(Component.translatable("gui.meow_machine.admin_title"));
        this.state = state;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int left = cx - FIELD_W / 2;
        int y = 36;

        // ---------- 开关 ----------
        addToggle(left, y, "gui.meow_machine.admin.ai", state.aiEnabled(),
                AdminActionPacket.Action.TOGGLE_AI);
        y += 24;
        addToggle(left, y, "gui.meow_machine.admin.takeover", state.aiTakeover(),
                AdminActionPacket.Action.TOGGLE_TAKEOVER);
        y += 24;
        addToggle(left, y, "gui.meow_machine.admin.selfreview", state.selfReview(),
                AdminActionPacket.Action.TOGGLE_SELF_REVIEW);
        y += 32;

        // ---------- AI 配置三项 ----------
        this.endpointBox = new EditBox(this.font, left, y, FIELD_W, FIELD_H,
                Component.translatable("gui.meow_machine.admin.endpoint"));
        this.endpointBox.setMaxLength(256);
        this.endpointBox.setValue(state.endpoint());
        this.endpointBox.setHint(Component.literal("https://api.openai.com/v1"));
        addRenderableWidget(endpointBox);
        y += 28;

        this.apiKeyBox = new EditBox(this.font, left, y, FIELD_W, FIELD_H,
                Component.translatable("gui.meow_machine.admin.apikey"));
        this.apiKeyBox.setMaxLength(256);
        this.apiKeyBox.setValue(state.apiKey());
        this.apiKeyBox.setHint(Component.literal("sk-..."));
        addRenderableWidget(apiKeyBox);
        y += 28;

        this.modelBox = new EditBox(this.font, left, y, FIELD_W, FIELD_H,
                Component.translatable("gui.meow_machine.admin.model"));
        this.modelBox.setMaxLength(128);
        this.modelBox.setValue(state.model());
        this.modelBox.setHint(Component.literal("gpt-4o-mini"));
        addRenderableWidget(modelBox);
        y += 26;

        // ---------- 保存 ----------
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.meow_machine.admin.save"),
                        b -> Network.CHANNEL.sendToServer(AdminActionPacket.saveAiConfig(
                                endpointBox.getValue(), apiKeyBox.getValue(), modelBox.getValue())))
                .bounds(left, y, FIELD_W, 20).build());
        y += 28;

        // ---------- 待审队列 ----------
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.meow_machine.admin.open_review", state.pending()),
                        b -> Network.CHANNEL.sendToServer(
                                AdminActionPacket.of(AdminActionPacket.Action.OPEN_REVIEW)))
                .bounds(left, y, FIELD_W, 20).build());
        y += 28;

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.meow_machine.close"), b -> this.onClose())
                .bounds(cx - 50, y, 100, 20).build());
    }

    private void addToggle(int x, int y, String key, boolean on, AdminActionPacket.Action action) {
        addRenderableWidget(Button.builder(
                        Component.translatable(key).append(": ").append(
                                Component.translatable(on
                                        ? "gui.meow_machine.on" : "gui.meow_machine.off")),
                        b -> Network.CHANNEL.sendToServer(AdminActionPacket.of(action)))
                .bounds(x, y, FIELD_W, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int cx = this.width / 2;
        int left = cx - FIELD_W / 2;

        g.drawCenteredString(this.font, this.title, cx, 16, 0xFFFFFF);

        // 输入框的标签（画在框上方或左侧会挤，这里画在框内提示位置的前面一行）
        int labelY = endpointBox.getY() - 10;
        g.drawString(this.font, Component.translatable("gui.meow_machine.admin.endpoint"),
                left, labelY, 0xAAAAAA, false);
        g.drawString(this.font, Component.translatable("gui.meow_machine.admin.apikey"),
                left, apiKeyBox.getY() - 10, 0xAAAAAA, false);
        g.drawString(this.font, Component.translatable("gui.meow_machine.admin.model"),
                left, modelBox.getY() - 10, 0xAAAAAA, false);

        if (!state.hasKey()) {
            g.drawCenteredString(this.font,
                    Component.translatable("gui.meow_machine.admin.no_key"),
                    cx, this.height - 28, 0xFF8080);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
