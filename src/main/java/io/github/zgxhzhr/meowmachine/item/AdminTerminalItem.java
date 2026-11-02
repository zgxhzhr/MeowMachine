package io.github.zgxhzhr.meowmachine.item;

import io.github.zgxhzhr.meowmachine.network.AdminStatePacket;
import io.github.zgxhzhr.meowmachine.network.ReviewListPacket;
import io.github.zgxhzhr.meowmachine.review.ReviewerRoster;
import io.github.zgxhzhr.meowmachine.review.SubmissionQueue;
import io.github.zgxhzhr.meowmachine.network.Network;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkDirection;

/**
 * 审核终端：右键打开审核后台，**按权限分档**（服务端校验，不靠界面隐藏）。
 *
 * <ul>
 *   <li>OP → 完整后台（AI 设置、审核员管理、待审列表、裁决）</li>
 *   <li>审核员 → 待审列表 + 裁决</li>
 *   <li>助理 → 待审列表，只能查看与标注，不能终裁</li>
 *   <li>其他人 → 无权限提示</li>
 * </ul>
 */
public class AdminTerminalItem extends Item {

    private final io.github.zgxhzhr.meowmachine.privilege.AccessTier tier;

    public io.github.zgxhzhr.meowmachine.privilege.AccessTier tier() {
        return tier;
    }

    public AdminTerminalItem(Properties properties,
                            io.github.zgxhzhr.meowmachine.privilege.AccessTier tier) {
        super(properties);
        this.tier = tier;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            // 物品自带等级：低档物品不能进高档界面（防串权）
            var actual = io.github.zgxhzhr.meowmachine.privilege.AccessTier.of(sp);
            if (!tier.usableBy(actual)) {
                sp.sendSystemMessage(Component.translatable(
                        "chat.meow_machine.tier_denied", tier.label(), actual.label()));
                return InteractionResultHolder.fail(stack);
            }
            // 物品封顶：用 min(玩家等级, 物品等级) 决定开哪个界面 ——
            // 审核员拿「助理终端」也只能用助理面板；站长拿助理终端同样降级
            var effective = tier.effectiveFor(actual);
            io.github.zgxhzhr.meowmachine.privilege.EffectiveTiers.set(sp, effective);
            if (effective == io.github.zgxhzhr.meowmachine.privilege.AccessTier.REVIEWER) {
                // 审核员档：待审列表 + 裁决；站长额外给完整后台
                if (sp.hasPermissions(2)) {
                    Network.CHANNEL.sendTo(AdminStatePacket.collect(sp), sp.connection.connection,
                            NetworkDirection.PLAY_TO_CLIENT);
                } else {
                    Network.CHANNEL.sendTo(
                            ReviewListPacket.of(SubmissionQueue.visibleTo(sp), true, false),
                            sp.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
                    // 同时把待处理的退回申请发过去：否则界面首次打开时按钮不会出现
                    Network.CHANNEL.sendTo(
                            io.github.zgxhzhr.meowmachine.network.ReturnRequestListPacket.of(),
                            sp.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
                }
            } else if (effective == io.github.zgxhzhr.meowmachine.privilege.AccessTier.ASSISTANT) {
                // 助理档：只能查看与标注，终裁按钮不给
                Network.CHANNEL.sendTo(
                        ReviewListPacket.of(SubmissionQueue.visibleTo(sp), false, true),
                        sp.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
            } else {
                sp.sendSystemMessage(Component.translatable("gui.meow_machine.admin.no_permission"));
                return InteractionResultHolder.fail(stack);
            }
        }

        return InteractionResultHolder.success(stack);
    }
}
