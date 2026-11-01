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

    public AdminTerminalItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            if (sp.hasPermissions(2)) {
                Network.CHANNEL.sendTo(AdminStatePacket.collect(), sp.connection.connection,
                        NetworkDirection.PLAY_TO_CLIENT);
            } else if (ReviewerRoster.canAnnotate(sp)) {
                boolean canFinalize = ReviewerRoster.roleOf(sp.getUUID()) == null
                        || ReviewerRoster.roleOf(sp.getUUID()).canFinalize();
                Network.CHANNEL.sendTo(
                        ReviewListPacket.of(SubmissionQueue.visibleTo(sp), canFinalize),
                        sp.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
            } else {
                sp.sendSystemMessage(Component.translatable("gui.meow_machine.admin.no_permission"));
                return InteractionResultHolder.fail(stack);
            }
        }

        return InteractionResultHolder.success(stack);
    }
}
