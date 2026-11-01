package io.github.zgxhzhr.meowmachine.client;

import io.github.zgxhzhr.meowmachine.network.Network;
import io.github.zgxhzhr.meowmachine.network.RosterActionPacket;
import io.github.zgxhzhr.meowmachine.network.RosterStatePacket;
import io.github.zgxhzhr.meowmachine.review.ReviewerRole;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 审核员管理：上半是名册（名字 / 角色 / 撤销），下半是**在线玩家点选任命**。
 *
 * <p>以前靠手打玩家名，容易打错还看不见选的是谁；现在服务端把在线玩家一起发来，
 * 点一行就能任命，不用输入框（离线玩家仍然走命令）。
 */
public class RosterScreen extends Screen {

    private static final int PANEL_W = 340;
    private static final int ROW_H = 22;
    private static final int ROW_BTN_W = 66;
    private static final int ONLINE_BTN_W = 58;

    private List<RosterStatePacket.Entry> members;
    private List<RosterStatePacket.Online> online;
    private int shownMembers;
    private int shownOnline;

    public RosterScreen(RosterStatePacket packet) {
        super(Component.translatable("gui.meow_machine.roster_title"));
        this.members = packet.members();
        this.online = packet.online();
    }

    /** 收到新名册：整屏重建 */
    public void applyState(RosterStatePacket packet) {
        this.members = packet.members();
        this.online = packet.online();
        this.rebuildWidgets();
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int left = cx - PANEL_W / 2;
        int top = 30;

        int avail = this.height - top - 60;
        int memberRows = Math.max(1, Math.min(6, (avail - 26) / ROW_H));
        this.shownMembers = Math.min(members.size(), memberRows);

        for (int i = 0; i < shownMembers; i++) {
            RosterStatePacket.Entry e = members.get(i);
            addRenderableWidget(Button.builder(
                            Component.translatable("gui.meow_machine.roster.revoke"),
                            b -> Network.CHANNEL.sendToServer(RosterActionPacket.revoke(e.uuid())))
                    .bounds(left + PANEL_W - ROW_BTN_W, top + i * ROW_H + 2, ROW_BTN_W, 18).build());
        }

        // 在线玩家：一行名字 + 两个任命按钮
        int onlineTop = top + shownMembers * ROW_H + 24;
        int room = Math.max(1, (this.height - 34 - onlineTop) / ROW_H);
        this.shownOnline = Math.min(online.size(), Math.min(5, room));

        for (int i = 0; i < shownOnline; i++) {
            RosterStatePacket.Online p = online.get(i);
            int y = onlineTop + i * ROW_H + 2;
            addRenderableWidget(Button.builder(
                            Component.translatable("gui.meow_machine.roster.grant_reviewer"),
                            b -> Network.CHANNEL.sendToServer(
                                    RosterActionPacket.grant(p.name(), ReviewerRole.REVIEWER)))
                    .bounds(left + PANEL_W - ONLINE_BTN_W * 2 - 14, y, ONLINE_BTN_W, 18).build());
            addRenderableWidget(Button.builder(
                            Component.translatable("gui.meow_machine.roster.grant_assistant"),
                            b -> Network.CHANNEL.sendToServer(
                                    RosterActionPacket.grant(p.name(), ReviewerRole.ASSISTANT)))
                    .bounds(left + PANEL_W - ONLINE_BTN_W - 6, y, ONLINE_BTN_W, 18).build());
        }

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.meow_machine.close"), b -> this.onClose())
                .bounds(cx - 50, this.height - 24, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int cx = this.width / 2;
        int left = cx - PANEL_W / 2;
        int top = 30;

        g.drawCenteredString(this.font, this.title, cx, 14, 0xFFFFFF);
        g.drawString(this.font,
                Component.translatable("gui.meow_machine.roster.members_header"),
                left, top - 12, 0xFFAA44, false);

        if (shownMembers == 0) {
            g.drawString(this.font,
                    Component.translatable("gui.meow_machine.roster.empty"),
                    left, top + 6, 0xAAAAAA, false);
        }

        for (int i = 0; i < shownMembers; i++) {
            RosterStatePacket.Entry e = members.get(i);
            int y = top + i * ROW_H;
            g.drawString(this.font,
                    Component.literal(trimToWidth(e.displayName(), 160)), left, y + 6, 0xFFFF80, false);
            g.drawString(this.font,
                    Component.translatable("gui.meow_machine.roster.role." + e.role()),
                    left + 168, y + 6, 0xCCCCCC, false);
        }

        int onlineTop = top + shownMembers * ROW_H + 24;
        g.drawString(this.font,
                Component.translatable("gui.meow_machine.roster.online_header"),
                left, onlineTop - 12, 0xFFAA44, false);

        if (shownOnline == 0) {
            g.drawString(this.font,
                    Component.translatable("gui.meow_machine.roster.no_online"),
                    left, onlineTop + 6, 0xAAAAAA, false);
        }

        for (int i = 0; i < shownOnline; i++) {
            RosterStatePacket.Online p = online.get(i);
            int y = onlineTop + i * ROW_H;
            g.drawString(this.font,
                    Component.literal(trimToWidth(p.name(), 150)), left, y + 6, 0xDDDDDD, false);
        }

        if (members.size() > shownMembers || online.size() > shownOnline) {
            g.drawCenteredString(this.font,
                    Component.translatable("gui.meow_machine.roster.more",
                            Math.max(members.size() - shownMembers, online.size() - shownOnline)),
                    cx, this.height - 40, 0xFF8080);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

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
