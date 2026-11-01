package io.github.zgxhzhr.meowmachine.item;

import io.github.zgxhzhr.meowmachine.recipe.MysteryPoolSavedData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 「未揭晓的合成结果」：合成时拿到它，**右键才抽出真正结果**。
 *
 * <p>为什么不在合成时直接给真物品：1.20 的输出框预览会调用 assemble，
 * 结果会提前显示在箭头上（实测直接暴露）。所以合成只给占位，揭晓放到右键。
 */
public class MysteryResultItem extends Item {

    public MysteryResultItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level,
                                java.util.List<net.minecraft.network.chat.Component> tooltip,
                                net.minecraft.world.item.TooltipFlag flag) {
        tooltip.add(net.minecraft.network.chat.Component.translatable(
                "item.meow_machine.mystery_result.desc")
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.add(net.minecraft.network.chat.Component.translatable(
                "item.meow_machine.mystery_result.use")
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            String id = MysteryPoolSavedData.roll(sp.getServer());
            var rl = id == null || id.isBlank() ? null : ResourceLocation.tryParse(id);
            var item = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
            if (item == null || item == net.minecraft.world.item.Items.AIR) {
                sp.sendSystemMessage(Component.translatable("chat.meow_machine.mystery_empty"));
                return InteractionResultHolder.success(stack);
            }
            stack.shrink(1);
            ItemStack got = new ItemStack(item);
            if (!sp.getInventory().add(got)) {
                sp.drop(got, false);
            }
            sp.sendSystemMessage(Component.translatable(
                    "chat.meow_machine.mystery_revealed", got.getHoverName()));
        }
        return InteractionResultHolder.success(stack);
    }
}
