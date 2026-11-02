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

    private final io.github.zgxhzhr.meowmachine.privilege.AccessTier tier;

    public MeowIconItem(Properties properties, io.github.zgxhzhr.meowmachine.privilege.AccessTier tier) {
        super(properties);
        this.tier = tier;
    }

    public io.github.zgxhzhr.meowmachine.privilege.AccessTier tier() {
        return tier;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level,
                                java.util.List<net.minecraft.network.chat.Component> tooltip,
                                net.minecraft.world.item.TooltipFlag flag) {
        tooltip.add(net.minecraft.network.chat.Component.translatable(
                "item.meow_machine.meow_icon.desc")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        String howKey = switch (tier) {
            case NORMAL -> "item.meow_machine.meow_icon_normal.how";
            case ASSISTANT -> "item.meow_machine.meow_icon_assistant.how";
            default -> "item.meow_machine.meow_icon_reviewer.how";
        };
        tooltip.add(net.minecraft.network.chat.Component.translatable(howKey)
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        tooltip.add(net.minecraft.network.chat.Component.translatable(
                "item.meow_machine.tier_line", tier.label())
                .withStyle(net.minecraft.ChatFormatting.GOLD));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof net.minecraft.server.level.ServerPlayer sp) {
            var actual = io.github.zgxhzhr.meowmachine.privilege.AccessTier.of(sp);
            if (!tier.usableBy(actual)) {
                sp.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                        "chat.meow_machine.tier_denied", tier.label(), actual.label()));
                return InteractionResultHolder.fail(stack);
            }
        }
        if (level.isClientSide) {
            // 只在客户端发包：服务端调用 sendToServer 会去拿 Minecraft.getInstance()，
            // 专用服务器上直接 NoClassDefFoundError（审查 P0-8）
            Network.CHANNEL.sendToServer(new ApprovedListRequestPacket());
        }
        return InteractionResultHolder.success(stack);
    }
}
