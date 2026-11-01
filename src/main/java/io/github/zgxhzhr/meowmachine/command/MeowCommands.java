package io.github.zgxhzhr.meowmachine.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.github.zgxhzhr.meowmachine.network.Network;
import io.github.zgxhzhr.meowmachine.review.DraftStore;
import io.github.zgxhzhr.meowmachine.network.ReviewListPacket;
import io.github.zgxhzhr.meowmachine.registry.ModItems;
import io.github.zgxhzhr.meowmachine.review.ReviewActions;
import io.github.zgxhzhr.meowmachine.review.ReviewerRole;
import io.github.zgxhzhr.meowmachine.review.ReviewerRoster;
import io.github.zgxhzhr.meowmachine.review.Submission;
import io.github.zgxhzhr.meowmachine.review.SubmissionQueue;
import io.github.zgxhzhr.meowmachine.review.Verdict;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;

import java.util.List;
import java.util.UUID;

/**
 * {@code /meow} 命令树。
 *
 * <pre>
 * /meow reviewers grant &lt;player&gt; &lt;reviewer|assistant&gt;   OP
 * /meow reviewers revoke &lt;player&gt;                        OP
 * /meow reviewers list                                    OP（真的列出名册）
 * /meow review list                                       审核员
 * /meow review decide &lt;id前缀&gt; &lt;pass|revise|return&gt;       审核员
 * /meow review open                                       审核员（打开审核界面）
 * /meow review give                                       审核员（发卷轴）
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
                // ---------- 任何玩家：查自己的投稿状态 ----------
                .then(Commands.literal("my")
                        .executes(MeowCommands::mySubmissions))
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
                        // give 曾经没有任何权限校验，任何玩家都能无限刷卷轴
                        .then(Commands.literal("give")
                                .requires(s -> ReviewerRoster.canAnnotate(s.getPlayer()))
                                .executes(MeowCommands::giveScroll)))
        );
    }

    // ---------------- 名册 ----------------

    private static int grant(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            String roleName = StringArgumentType.getString(ctx, "role").toUpperCase();
            ReviewerRole role = ReviewerRole.valueOf(roleName);
            ReviewerRoster.grant(target.getUUID(), target.getName().getString(), role);
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

    /**
     * 列名册 + 三个状态数字。
     *
     * <p>0.1.0 这条命令叫 list 却只打队列长度，一条名册都不列——
     * 而当时名册根本没被加载过，正好也没人发现。现在真列。
     */
    private static int listReviewers(CommandContext<CommandSourceStack> ctx) {
        List<ReviewerRoster.Member> roster = ReviewerRoster.members();
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "command.meow_machine.queue_size", SubmissionQueue.size()), false);
        if (roster.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable(
                    "command.meow_machine.roster_empty"), false);
            return 1;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "command.meow_machine.roster_size", roster.size()), false);
        for (ReviewerRoster.Member m : roster) {
            String shown = m.name() == null || m.name().isBlank()
                    ? m.uuid().toString().substring(0, 8) : m.name();
            ReviewerRole role = m.role();
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "  " + shown + "  " + role.name()), false);
        }
        return 1;
    }


    // ---------------- 玩家自己 ----------------

    /**
     * {@code /meow my}：看自己当前投稿的状态，以及有没有在待审队列里。
     *
     * <p>以前提交完只有一张梗图弹窗，作者看不到「我的稿子审到哪了」；
     * 队列现在会落盘，所以这里读到的待审是真实的。
     */
    private static int mySubmissions(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer self = ctx.getSource().getPlayer();
        if (self == null) {
            return 0;
        }
        Submission draft = DraftStore.load(self);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.meow_machine.my_draft",
                shortId(draft.id()), draft.state().name().toLowerCase(), draft.revision()), false);

        List<SubmissionQueue.Entry> mine = SubmissionQueue.byAuthor(self.getUUID());
        if (mine.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable(
                    "command.meow_machine.my_none"), false);
            return 1;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable(
                "command.meow_machine.my_queue", mine.size()), false);
        for (SubmissionQueue.Entry e : mine) {
            Submission s = e.submission();
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "  " + shortId(s.id()) + "  " + s.state().name().toLowerCase() + "  " + s.title()),
                    false);
        }
        return 1;
    }

    // ---------------- 审核 ----------------

    private static int reviewList(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer self = ctx.getSource().getPlayer();
        if (!ReviewerRoster.canAnnotate(self)) {
            ctx.getSource().sendFailure(Component.translatable("command.meow_machine.no_permission"));
            return 0;
        }
        List<SubmissionQueue.Entry> all = SubmissionQueue.visibleTo(self);
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
        if (!verdict.isDecidable()) {
            // PENDING 是系统内部状态，不是审核员能拍板的东西
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
        List<ReviewListPacket.Entry> list = SubmissionQueue.visibleTo(self).stream()
                .map(e -> ReviewListPacket.Entry.of(e.submission()))
                .toList();
        boolean canFinalize = ReviewerRoster.roleOf(self.getUUID()) == null
                || ReviewerRoster.roleOf(self.getUUID()).canFinalize();
        boolean assistant = ReviewerRoster.roleOf(self.getUUID())
                == io.github.zgxhzhr.meowmachine.review.ReviewerRole.ASSISTANT;
        Network.CHANNEL.sendTo(new ReviewListPacket(list, canFinalize, assistant), self.connection.connection,
                NetworkDirection.PLAY_TO_CLIENT);
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
