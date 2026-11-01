package io.github.zgxhzhr.meowmachine.item;

import io.github.zgxhzhr.meowmachine.network.ApprovedListRequestPacket;
import io.github.zgxhzhr.meowmachine.network.Network;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** 「喵喵喵」图标物品：右键打开**已通过条目列表**（所有人可见，可查看/提交编辑）。 */
public class MeowIconItem extends Item {

    public MeowIconItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            Network.CHANNEL.sendToServer(new ApprovedListRequestPacket());
        }
        return InteractionResultHolder.success(stack);
    }
}
