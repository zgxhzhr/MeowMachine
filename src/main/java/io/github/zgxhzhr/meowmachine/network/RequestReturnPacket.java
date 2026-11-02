package io.github.zgxhzhr.meowmachine.network;

import io.github.zgxhzhr.meowmachine.MeowMachine;
import io.github.zgxhzhr.meowmachine.review.ApprovedIndex;
import io.github.zgxhzhr.meowmachine.review.ReturnRequests;
import io.github.zgxhzhr.meowmachine.review.ReviewerRoster;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 已通过条目的退回：所有人可提交「退回申请」；审核员/OP 直接执行退回。
 */
public record RequestReturnPacket(String entryId, String reason) {

    private static final int MAX_REASON = 120;

    public static void encode(RequestReturnPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.entryId == null ? "" : msg.entryId, 40);
        buf.writeUtf(msg.reason == null ? "" : msg.reason, MAX_REASON);
    }

    public static RequestReturnPacket decode(FriendlyByteBuf buf) {
        return new RequestReturnPacket(buf.readUtf(40), buf.readUtf(MAX_REASON));
    }

    public static void handle(RequestReturnPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        ServerPlayer player = c.getSender();
        if (player == null) {
            c.setPacketHandled(true);
            return;
        }
        UUID id;
        try {
            id = UUID.fromString(msg.entryId == null ? "" : msg.entryId.trim());
        } catch (IllegalArgumentException e) {
            c.setPacketHandled(true);
            return;
        }
        var entry = ApprovedIndex.find(id);
        if (entry.isEmpty()) {
            Network.CHANNEL.sendTo(NoticePacket.of("chat.meow_machine.return_not_found"),
                    player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
            c.setPacketHandled(true);
            return;
        }
        String reason = msg.reason == null ? "" : msg.reason.strip();
        boolean reviewer = io.github.zgxhzhr.meowmachine.privilege.EffectiveTiers.canFinalize(player);
        if (reviewer) {
            var pending = ReturnRequests.get(id).orElse(null);
            markAuthorReturned(entry.get(), java.util.List.of(reason.isBlank()
                    ? "被审核员退回" : "被审核员退回：" + reason));
            ApprovedIndex.remove(id);
            ReturnRequests.clear(id);
            rewardRequester(player, pending);
            // 通知作者（在线才通知）
            var author = player.getServer() == null ? null
                    : player.getServer().getPlayerList().getPlayerByName(entry.get().author());
            if (author != null) {
                author.sendSystemMessage(Component.translatable(
                        "chat.meow_machine.return_done_author", entry.get().title(),
                        reason.isBlank() ? "-" : reason));
            }
            player.sendSystemMessage(Component.translatable(
                    "chat.meow_machine.return_done", entry.get().title()));
            MeowMachine.LOG.info("[喵呜机] {} 退回了已通过条目 {}（原因：{}）",
                    player.getName().getString(), id, reason);
        } else {
            ReturnRequests.request(id, player.getName().getString(), player.getUUID(), reason);
            // 提醒在线审核员
            if (player.getServer() != null) {
                for (ServerPlayer p : player.getServer().getPlayerList().getPlayers()) {
                    if (ReviewerRoster.canFinalize(p) || ReviewerRoster.isOp(p)) {
                        p.sendSystemMessage(Component.translatable(
                                "chat.meow_machine.return_requested_reviewer",
                                player.getName().getString(), entry.get().title(),
                                reason.isBlank() ? "-" : reason));
                    }
                }
            }
            player.sendSystemMessage(Component.translatable(
                    "chat.meow_machine.return_requested", entry.get().title()));

            // AI 也参与裁决：AI 审核已启用时，让 AI 重新复核这条已通过条目。
            // 认为确实该改 -> 直接退回并通知；认为没问题 -> 驳回这条申请。
            var cfg = io.github.zgxhzhr.meowmachine.ai.AiConfigStore.get();
            var server = player.getServer();
            if (cfg.takeover() && cfg.usable() && server != null) {
                final java.util.UUID targetId = id;
                final String title = entry.get().title();
                final String authorName = entry.get().author();
                io.github.zgxhzhr.meowmachine.ai.AiReviewer.review(cfg, title, entry.get().body())
                        .thenAcceptAsync(outcome -> server.execute(() -> {
                            if (!outcome.available()) {
                                return;
                            }
                            if (outcome.findings().isEmpty()) {
                                ReturnRequests.clear(targetId);
                                Network.CHANNEL.sendTo(NoticePacket.of(
                                                "chat.meow_machine.return_ai_rejected"),
                                        player.connection.connection,
                                        NetworkDirection.PLAY_TO_CLIENT);
                                return;
                            }
                            var pendingAi = ReturnRequests.get(targetId).orElse(null);
                            String aiWhy = outcome.findings().isEmpty() ? null
                                    : outcome.findings().get(0).reason();
                            java.util.List<String> aiReasons = new java.util.ArrayList<>();
                            aiReasons.add(aiWhy == null || aiWhy.isBlank()
                                    ? "AI 复核后同意退回" : "AI 复核后同意退回：" + aiWhy);
                            if (outcome.note() != null && !outcome.note().isBlank()) {
                                aiReasons.add(outcome.note().trim());
                            }
                            markAuthorReturned(entry.get(), aiReasons);
                            ApprovedIndex.remove(targetId);
                            ReturnRequests.clear(targetId);
                            rewardRequester(player, pendingAi);
                            var author = server.getPlayerList().getPlayerByName(authorName);
                            if (author != null) {
                                author.sendSystemMessage(Component.translatable(
                                        "chat.meow_machine.return_done_author", title,
                                        aiWhy == null || aiWhy.isBlank() ? "AI 复核后同意退回" : aiWhy));
                            }
                            Network.CHANNEL.sendTo(NoticePacket.of(
                                            "chat.meow_machine.return_ai_accepted"),
                                    player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
                            for (ServerPlayer p2 : server.getPlayerList().getPlayers()) {
                                if (io.github.zgxhzhr.meowmachine.privilege.EffectiveTiers
                                        .canFinalize(p2)) {
                                    p2.sendSystemMessage(Component.translatable(
                                            "chat.meow_machine.return_ai_accepted_by", title));
                                }
                            }
                            MeowMachine.LOG.info("[喵呜机] AI 复核后同意退回已通过条目 {}", targetId);
                        }), server);
            }
        }
        c.setPacketHandled(true);
    }

    /**
     * 「申请退回」被采纳时的奖励：给申请人经验、一张「通过」系列梗图，并解锁成就。
     * 申请人不在线就跳过（奖励不排队，避免离线堆积）。
     */
    private static void rewardRequester(ServerPlayer actor,
                                        io.github.zgxhzhr.meowmachine.review.ReturnRequestsSavedData.Request req) {
        if (req == null || req.requesterId() == null || actor.getServer() == null) {
            return;
        }
        ServerPlayer requester = actor.getServer().getPlayerList().getPlayer(req.requesterId());
        if (requester == null) {
            return;
        }
        requester.giveExperiencePoints(50);
        var sticker = io.github.zgxhzhr.meowmachine.registry.ModItems.STICKER_ITEMS
                .get("approved_plain");
        if (sticker != null && sticker.get() != null) {
            var stack = new net.minecraft.world.item.ItemStack(sticker.get());
            if (!requester.getInventory().add(stack)) {
                requester.drop(stack, false);
            }
        }
        io.github.zgxhzhr.meowmachine.advancement.MeowAdvancements
                .award(requester, "return_accepted");
        requester.sendSystemMessage(Component.translatable(
                "chat.meow_machine.return_reward"));
    }

    /** 退回后更新作者的历史状态（列表里变成「被打回」，可编辑），并在其在线时发成就 */
    private static void markAuthorReturned(
            io.github.zgxhzhr.meowmachine.review.ApprovedSavedData.Entry entry,
            java.util.List<String> reasons) {
        io.github.zgxhzhr.meowmachine.review.SubmissionHistory.markReturnedById(
                entry.id(), entry.title(), entry.body(), entry.revision(), reasons);
        java.util.UUID authorId = null;
        try {
            authorId = java.util.UUID.fromString(entry.authorUuid());
        } catch (RuntimeException ignored) {
        }
        if (authorId == null) {
            return;
        }
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        var author = server.getPlayerList().getPlayer(authorId);
        if (author != null) {
            io.github.zgxhzhr.meowmachine.advancement.MeowAdvancements
                    .award(author, "entry_returned");
        }
    }
}
