package io.github.zgxhzhr.meowmachine.client;

import io.github.zgxhzhr.meowmachine.network.AdminActionPacket;
import io.github.zgxhzhr.meowmachine.network.AdminStatePacket;
import io.github.zgxhzhr.meowmachine.network.Network;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * 管理后台：AI 开关 / 接管 / 单人自审 + **游戏内 AI 配置** + 待审入口。
 *
 * <p>照 TLM 的做法把 AI 配置搬进游戏内界面（它也是 GUI 里填站点与密钥），
 * 不用玩家去翻 {@code config/meow_machine_ai.json}。
 *
 * <p>本次修的关键交互问题：以前服务端每次操作都回推一个完整状态包，
 * 客户端就 {@code setScreen(new AdminScreen(...))} 重建整个界面——
 * 于是在 endpoint/key/model 里填了一半字、随手点一下「AI 接管」，
 * 刚填的内容全被清空。现在同样是状态包，但只**原地刷新按钮文案**，不动输入框。
 */
public class AdminScreen extends Screen {

    private static final int FIELD_W = 300;
    private static final int FIELD_H = 18;

    private AdminStatePacket state;

    private EditBox endpointBox;
    private EditBox apiKeyBox;
    private EditBox modelBox;

    /** 开关按钮 → 语言 key（刷新文案时按这个重建） */
    private final Map<AdminActionPacket.Action, String> toggleKeys = new EnumMap<>(AdminActionPacket.Action.class);
    private final Map<AdminActionPacket.Action, Button> toggleButtons = new EnumMap<>(AdminActionPacket.Action.class);
    private Button openReviewButton;

    public AdminScreen(AdminStatePacket state) {
        super(Component.translatable("gui.meow_machine.admin_title"));
        this.state = state;
    }

    /** 收到新状态：只刷新显示，不重建界面（重建会清掉未保存的输入） */
    public void applyState(AdminStatePacket newState) {
        this.state = newState;
        refreshLabels();
    }

    private void refreshLabels() {
        for (Map.Entry<AdminActionPacket.Action, Button> e : toggleButtons.entrySet()) {
            String key = toggleKeys.get(e.getKey());
            if (key == null) {
                continue;
            }
            e.getValue().setMessage(Component.translatable(key).append(": ").append(
                    Component.translatable(isOn(e.getKey())
                            ? "gui.meow_machine.on" : "gui.meow_machine.off")));
        }
        if (openReviewButton != null) {
            openReviewButton.setMessage(Component.translatable(
                    "gui.meow_machine.admin.open_review", state.pending()));
        }
    }

    private boolean isOn(AdminActionPacket.Action action) {
        return switch (action) {
            case TOGGLE_AI -> state.aiEnabled();
            case TOGGLE_TAKEOVER -> state.aiTakeover();
            case TOGGLE_SELF_REVIEW -> state.selfReview();
            default -> false;
        };
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int left = cx - FIELD_W / 2;
        int y = 28;

        // ---------- 开关（紧凑排布：1080p 全屏逻辑高只有 270，排松了按钮会掉出屏幕）----------
        addToggle(left, y, "gui.meow_machine.admin.ai", state.aiEnabled(),
                AdminActionPacket.Action.TOGGLE_AI);
        y += 20;
        addToggle(left, y, "gui.meow_machine.admin.takeover", state.aiTakeover(),
                AdminActionPacket.Action.TOGGLE_TAKEOVER);
        y += 20;
        addToggle(left, y, "gui.meow_machine.admin.selfreview", state.selfReview(),
                AdminActionPacket.Action.TOGGLE_SELF_REVIEW);
        y += 22;

        // ---------- AI 配置三项（标签画在框上方，前面留出 label 的高度）----------
        y += 10;
        this.endpointBox = new EditBox(this.font, left, y, FIELD_W, FIELD_H,
                Component.translatable("gui.meow_machine.admin.endpoint"));
        this.endpointBox.setMaxLength(256);
        this.endpointBox.setValue(state.endpoint());
        this.endpointBox.setHint(Component.literal("https://api.openai.com/v1"));
        addRenderableWidget(endpointBox);
        y += 22;

        this.apiKeyBox = new EditBox(this.font, left, y, FIELD_W, FIELD_H,
                Component.translatable("gui.meow_machine.admin.apikey"));
        this.apiKeyBox.setMaxLength(256);
        this.apiKeyBox.setValue(state.apiKey());
        this.apiKeyBox.setHint(Component.literal("sk-..."));
        addRenderableWidget(apiKeyBox);
        y += 22;

        this.modelBox = new EditBox(this.font, left, y, FIELD_W, FIELD_H,
                Component.translatable("gui.meow_machine.admin.model"));
        this.modelBox.setMaxLength(128);
        this.modelBox.setValue(state.model());
        this.modelBox.setHint(Component.literal("gpt-4o-mini"));
        addRenderableWidget(modelBox);
        y += 26;

        // ---------- 保存（和「待审队列」并排放，省一行高度）----------
        int half = (FIELD_W - 4) / 2;
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.meow_machine.admin.save"),
                        b -> Network.CHANNEL.sendToServer(AdminActionPacket.saveAiConfig(
                                endpointBox.getValue(), apiKeyBox.getValue(), modelBox.getValue())))
                .bounds(left, y, half, 20).build());

        this.openReviewButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.meow_machine.admin.open_review", state.pending()),
                        b -> Network.CHANNEL.sendToServer(
                                AdminActionPacket.of(AdminActionPacket.Action.OPEN_REVIEW)))
                .bounds(left + half + 4, y, half, 20).build());

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.meow_machine.close"), b -> this.onClose())
                .bounds(cx - 50, this.height - 24, 100, 20).build());
    }

    private void addToggle(int x, int y, String key, boolean on, AdminActionPacket.Action action) {
        Button button = addRenderableWidget(Button.builder(
                        Component.translatable(key).append(": ").append(
                                Component.translatable(on
                                        ? "gui.meow_machine.on" : "gui.meow_machine.off")),
                        b -> Network.CHANNEL.sendToServer(AdminActionPacket.of(action)))
                .bounds(x, y, FIELD_W, 20).build());
        toggleKeys.put(action, key);
        toggleButtons.put(action, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int cx = this.width / 2;
        int left = cx - FIELD_W / 2;

        g.drawCenteredString(this.font, this.title, cx, 16, 0xFFFFFF);

        // 输入框的标签
        g.drawString(this.font, Component.translatable("gui.meow_machine.admin.endpoint"),
                left, endpointBox.getY() - 10, 0xAAAAAA, false);
        g.drawString(this.font, Component.translatable("gui.meow_machine.admin.apikey"),
                left, apiKeyBox.getY() - 10, 0xAAAAAA, false);
        g.drawString(this.font, Component.translatable("gui.meow_machine.admin.model"),
                left, modelBox.getY() - 10, 0xAAAAAA, false);

        // 名册人数 / 离线待补发，放底部一行（在关闭按钮上方）
        g.drawCenteredString(this.font,
                Component.translatable("gui.meow_machine.admin.stats",
                        state.reviewers(), state.inbox()),
                cx, this.height - 40, 0x888888);

        if (!state.hasKey()) {
            g.drawCenteredString(this.font,
                    Component.translatable("gui.meow_machine.admin.no_key"),
                    cx, this.height - 52, 0xFF8080);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
