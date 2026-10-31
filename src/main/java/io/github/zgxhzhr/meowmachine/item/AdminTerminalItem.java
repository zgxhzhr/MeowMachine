package io.github.zgxhzhr.meowmachine.item;

import io.github.zgxhzhr.meowmachine.network.AdminStatePacket;
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
 * 管理终端：右键打开审核后台。
 *
 * <p>**只有 OP 能开**（服务端校验，不依赖客户端界面隐藏）。
 */
public class AdminTerminalItem extends Item {

    public AdminTerminalItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            if (!sp.hasPermissions(2)) {
                sp.sendSystemMessage(Component.translatable("gui.meow_machine.admin.no_permission"));
                return InteractionResultHolder.fail(stack);
            }
            Network.CHANNEL.sendTo(AdminStatePacket.collect(), sp.connection.connection,
                    NetworkDirection.PLAY_TO_CLIENT);
        }

        return InteractionResultHolder.success(stack);
    }
}
