package io.github.zgxhzhr.meowmachine.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.github.zgxhzhr.meowmachine.registry.ModItems;
import io.github.zgxhzhr.meowmachine.review.ReviewFinding;
import io.github.zgxhzhr.meowmachine.review.ReviewResult;
import io.github.zgxhzhr.meowmachine.review.ReviewerRole;
import io.github.zgxhzhr.meowmachine.network.Network;
import io.github.zgxhzhr.meowmachine.network.ReviewListPacket;
import io.github.zgxhzhr.meowmachine.review.ReviewActions;
import io.github.zgxhzhr.meowmachine.review.ReviewerRoster;
import io.github.zgxhzhr.meowmachine.review.Submission;
import io.github.zgxhzhr.meowmachine.review.SubmissionQueue;
import io.github.zgxhzhr.meowmachine.review.Verdict;
import io.github.zgxhzhr.meowmachine.sticker.StickerRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code /meow} 命令树。
 *
 * <pre>
 * /meow reviewers grant &lt;player&gt; &lt;reviewer|assistant&gt;   OP
 * /meow reviewers revoke &lt;player&gt;                        OP
 * /meow reviewers list                                    OP
 * /meow selfreview &lt;on|off&gt;                               OP
 * /meow review list                                       审核员
 * /meow review decide &lt;id前缀&gt; &lt;pass|revise|return&gt;       审核员
 * </pre>
 */
public final class MeowCommands {

    private MeowCommands() {
    }

    public static void register(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("meow")
                // ---------- OP 专用：名册管理 ----------
                .then(Commands.literal("reviewers")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.literal("grant")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("role", StringArgumentType.word())
                                                .suggests((c, b) -> {
                                                    b.suggest("reviewer");
                                                    b.suggest("assistant");
                                                    return b.buildFuture();
                                                })
                                                .executes(MeowCommands::grant))))
                        .then(Commands.literal("revoke")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(MeowCommands::revoke)))
                        .then(Commands.literal("list")
                                .executes(MeowCommands::listReviewers)))
                .then(Commands.literal("selfreview")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("value", StringArgumentType.word())
                                .suggests((c, b) -> {
                                    b.suggest("on");
                                    b.suggest("off");
                                    return b.buildFuture();
                                })
                                .executes(MeowCommands::selfReview)))
                // ---------- 审核员：待审列表与裁决 ----------
                .then(Commands.literal("review")
                        .then(Commands.literal("list")
                                .executes(MeowCommands::reviewList))
                        .then(Commands.literal("decide")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .then(Commands.argument("verdict", StringArgumentType.word())
                                                .suggests((c, b) -> {
                                                    b.suggest("pass");
                                                    b.suggest("revise");
                                                    b.suggest("return");
                                                    return b.buildFuture();
                                                })
                                                .executes(MeowCommands::decide))))
                        .then(Commands.literal("open")
                                .executes(MeowCommands::openReviewer))
                        .then(Commands.literal("give")
                                .executes(MeowCommands::giveScroll)))
        );
    }

    // ---------------- 名册 ----------------

    private static int grant(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            String roleName = StringArgumentType.getString(ctx, "role").toUpperCase();
            ReviewerRole role = ReviewerRole.valueOf(roleName);
            ReviewerRoster.grant(target.getUUID(), role);
            ctx.getSource().sendSuccess(() -> Component.translatable(
                    "command.meow_machine.grant", target.getName().getString(), role.name()),
                    true);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.translatable("command.meow_machine.bad_role"));
            return 0;
        }
    }

    private static int revoke(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            ReviewerRoster.revoke(target.getUUID());
            ctx.getSource().sendSuccess(() -> Component.translatable(
                    "command.meow_machine.revoke", target.getName().getString()), true);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal(e.toString()));
            return 0;
        }
    }

    private static int listReviewers(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "command.meow_machine.selfreview_state",
                ReviewerRoster.isSelfReviewEnabled() ? "on" : "off"), false);
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "command.meow_machine.queue_size", SubmissionQueue.size()), false);
        return 1;
    }

    private static int selfReview(CommandContext<CommandSourceStack> ctx) {
        boolean on = "on".equalsIgnoreCase(StringArgumentType.getString(ctx, "value"));
        ReviewerRoster.setSelfReview(on);
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "command.meow_machine.selfreview_set", on ? "on" : "off"), true);
        return 1;
    }

    // ---------------- 审核 ----------------

    private static int reviewList(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer self = ctx.getSource().getPlayer();
        if (!ReviewerRoster.canAnnotate(self)) {
            ctx.getSource().sendFailure(Component.translatable("command.meow_machine.no_permission"));
            return 0;
        }
        List<SubmissionQueue.Entry> all = SubmissionQueue.snapshot();
        if (all.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable(
                    "command.meow_machine.queue_empty"), false);
            return 1;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "command.meow_machine.queue_size", all.size()), false);
        int shown = Math.min(all.size(), 10);
        for (int i = 0; i < shown; i++) {
            Submission s = all.get(i).submission();
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "  " + shortId(s.id()) + "  " + s.authorName() + " · " + s.title()), false);
        }
        return 1;
    }

    private static int decide(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer self = ctx.getSource().getPlayer();
        String idPrefix = StringArgumentType.getString(ctx, "id");
        String verdictName = StringArgumentType.getString(ctx, "verdict");

        Verdict verdict;
        try {
            verdict = Verdict.valueOf(verdictName.toUpperCase());
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.translatable("command.meow_machine.bad_verdict"));
            return 0;
        }

        ReviewActions.Outcome outcome = ReviewActions.decide(self, idPrefix, verdict);
        if (outcome != ReviewActions.Outcome.OK) {
            ctx.getSource().sendFailure(Component.translatable(ReviewActions.messageKey(outcome)));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "command.meow_machine.decided_ok", idPrefix, verdict.name()), true);
        return 1;
    }

    /** 打开审核界面 */
    private static int openReviewer(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer self = ctx.getSource().getPlayer();
        if (!ReviewerRoster.canAnnotate(self)) {
            ctx.getSource().sendFailure(Component.translatable("command.meow_machine.no_permission"));
            return 0;
        }
        List<ReviewListPacket.Entry> list = SubmissionQueue.snapshot().stream()
                .map(e -> new ReviewListPacket.Entry(
                        e.submission().id().toString(),
                        shortId(e.submission().id()),
                        e.submission().authorName(),
                        e.submission().title()))
                .toList();
        Network.CHANNEL.sendTo(new ReviewListPacket(list), self.connection.connection,
                net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
        return 1;
    }

    private static int giveScroll(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer self = ctx.getSource().getPlayer();
        if (self == null) {
            return 0;
        }
        self.getInventory().add(new net.minecraft.world.item.ItemStack(ModItems.SUBMISSION_SCROLL.get()));
        return 1;
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }
}
