package io.github.zgxhzhr.meowmachine.client;

import io.github.zgxhzhr.meowmachine.network.Network;
import io.github.zgxhzhr.meowmachine.network.RosterActionPacket;
import io.github.zgxhzhr.meowmachine.network.RosterStatePacket;
import io.github.zgxhzhr.meowmachine.review.ReviewerRole;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 审核员管理界面：列出名册（名字 + 角色 + 撤销），并可以按**在线玩家名**任命。
 *
 * <p>以前只能在命令里任命（{@code /meow reviewers grant <player> <role>}），
 * 后台只显示一个数字。现在后台点「审核员管理」就能看到谁在里面、一键撤销。
 *
 * <p>任命为什么按名字：Screen 里没法用实体选择器，最实际的做法是输入在线玩家名，
 * 服务端用 {@code PlayerList#getPlayerByName} 解析；离线玩家会回一句提示。
 */
public class RosterScreen extends Screen {

    private static final int PANEL_W = 320;
    private static final int ROW_H = 22;
    private static final int ROW_BTN_W = 66;
    /** 底部留给「输入框 + 两颗任命按钮 + 关闭」的高度 */
    private static final int BOTTOM_RESERVED = 124;

    private List<RosterStatePacket.Entry> members;
    private int shown;
    private EditBox nameBox;

    public RosterScreen(RosterStatePacket packet) {
        super(Component.translatable("gui.meow_machine.roster_title"));
        this.members = packet.members();
    }

    /** 收到新名册：只刷新列表，保留输入框里还没提交的玩家名 */
    public void applyState(RosterStatePacket packet) {
        this.members = packet.members();
        String keep = nameBox == null ? "" : nameBox.getValue();
        this.rebuildWidgets();
        if (nameBox != null) {
            nameBox.setValue(keep);
        }
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int left = cx - PANEL_W / 2;
        int top = 30;

        int fit = Math.max(1, (this.height - top - BOTTOM_RESERVED) / ROW_H);
        this.shown = Math.min(members.size(), Math.min(8, fit));

        // 每一行：名字 / 角色 / 撤销
        for (int i = 0; i < shown; i++) {
            RosterStatePacket.Entry e = members.get(i);
            addRenderableWidget(Button.builder(
                            Component.translatable("gui.meow_machine.roster.revoke"),
                            b -> Network.CHANNEL.sendToServer(RosterActionPacket.revoke(e.uuid())))
                    .bounds(left + PANEL_W - ROW_BTN_W, top + i * ROW_H + 2, ROW_BTN_W, 18).build());
        }

        // 输入框：在线玩家名
        int inputY = top + shown * ROW_H + 10;
        this.nameBox = new EditBox(this.font, left, inputY, PANEL_W, 18,
                Component.translatable("gui.meow_machine.roster.name_hint"));
        this.nameBox.setMaxLength(16);
        this.nameBox.setHint(Component.translatable("gui.meow_machine.roster.name_hint"));
        addRenderableWidget(nameBox);

        // 两颗任命按钮
        int half = (PANEL_W - 6) / 2;
        int grantY = inputY + 22;
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.meow_machine.roster.grant_reviewer"),
                        b -> grant(ReviewerRole.REVIEWER))
                .bounds(left, grantY, half, 18).build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.meow_machine.roster.grant_assistant"),
                        b -> grant(ReviewerRole.ASSISTANT))
                .bounds(left + half + 6, grantY, half, 18).build());

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.meow_machine.close"), b -> this.onClose())
                .bounds(cx - 50, this.height - 24, 100, 20).build());
    }

    private void grant(ReviewerRole role) {
        String name = nameBox == null ? "" : nameBox.getValue().trim();
        if (name.isEmpty()) {
            return;
        }
        Network.CHANNEL.sendToServer(RosterActionPacket.grant(name, role));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int cx = this.width / 2;
        int left = cx - PANEL_W / 2;
        int top = 30;

        g.drawCenteredString(this.font, this.title, cx, 14, 0xFFFFFF);

        if (shown == 0) {
            g.drawCenteredString(this.font,
                    Component.translatable("gui.meow_machine.roster.empty"),
                    cx, top + 20, 0xAAAAAA);
        }

        for (int i = 0; i < shown; i++) {
            RosterStatePacket.Entry e = members.get(i);
            int y = top + i * ROW_H;
            g.drawString(this.font,
                    Component.literal(trimToWidth(e.displayName(), 140)), left, y + 6, 0xFFFF80, false);
            g.drawString(this.font,
                    Component.translatable("gui.meow_machine.roster.role." + e.role()),
                    left + 150, y + 6, 0xCCCCCC, false);
        }

        if (members.size() > shown) {
            g.drawCenteredString(this.font,
                    Component.translatable("gui.meow_machine.roster.more",
                            members.size() - shown),
                    cx, top + shown * ROW_H + 52, 0xFF8080);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    /** 按像素宽度截断（中文/英文宽度差一倍，按字符截会溢出） */
    private String trimToWidth(String s, int maxWidth) {
        if (s == null) {
            return "";
        }
        if (this.font.width(s) <= maxWidth) {
            return s;
        }
        String ellipsis = "…";
        int i = s.length();
        while (i > 0 && this.font.width(s.substring(0, i) + ellipsis) > maxWidth) {
            i--;
        }
        return s.substring(0, i) + ellipsis;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
