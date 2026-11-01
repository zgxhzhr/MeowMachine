package io.github.zgxhzhr.meowmachine.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 梗图物品：悬浮时显示一段轻松的小故事。
 *
 * <p>故事文案放在语言文件里（{@code item.meow_machine.sticker_<id>.story}），
 * 这样加一张新梗图只要补 json 和一行语言键，不用改代码。
 */
public class StickerItem extends Item {

    /** 梗图 id（语言键前缀用） */
    private final String stickerId;

    public StickerItem(Properties properties, String stickerId) {
        super(properties);
        this.stickerId = stickerId;
    }

    public String stickerId() {
        return stickerId;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player,
                                                  InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean view = player.isShiftKeyDown();
        if (level.isClientSide) {
            // Shift+右键：看大图；普通右键：吃（服务端结算）
            if (view) {
                net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                        net.minecraftforge.api.distmarker.Dist.CLIENT,
                        () -> () -> io.github.zgxhzhr.meowmachine.client.StickerViewer.open(stickerId));
            }
            return InteractionResultHolder.success(stack);
        }
        if (view) {
            return InteractionResultHolder.success(stack);
        }
        // 走原版进食流程：按住右键 24 tick，播放进食动画/粒子/音效，结束后结算
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    /** 进食时长（tick）：24 tick = 1.2 秒，比金苹果略快 */
    @Override
    public int getUseDuration(ItemStack stack) {
        return 24;
    }

    @Override
    public net.minecraft.world.item.UseAnim getUseAnimation(ItemStack stack) {
        return net.minecraft.world.item.UseAnim.EAT;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level,
                                     net.minecraft.world.entity.LivingEntity entity) {
        if (entity instanceof net.minecraft.server.level.ServerPlayer sp
                && io.github.zgxhzhr.meowmachine.sticker.StickerFood.eat(sp, stickerId)) {
            if (!sp.isCreative()) {
                stack.shrink(1);
            }
            // 原版物品冷却：20 秒（400 tick），物品栏上会显示冷却盖
            sp.getCooldowns().addCooldown(this, 400);
        }
        return stack;
    }

    /**
     * 把长故事折成每行不超过 {@code maxChars} 个字（中文按字数近似即可）。
     *
     * <p>悬浮提示不会自动折行，长句子会直接超出屏幕，所以在这里手动切。
     */
    public static java.util.List<String> wrap(String text, int maxChars) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            cur.append(ch);
            boolean atPunct = "，。；！？、".indexOf(ch) >= 0;
            if (cur.length() >= maxChars || (atPunct && cur.length() >= maxChars - 6)) {
                out.add(cur.toString());
                cur.setLength(0);
            }
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        }
        return out;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        String story = Component.translatable(
                "item.meow_machine.sticker_" + stickerId + ".story").getString();
        for (String line : wrap(story, 20)) {
            tooltip.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
        }
        String food = Component.translatable(
                "item.meow_machine.sticker_" + stickerId + ".food").getString();
        if (!food.isEmpty() && !food.startsWith("item.meow_machine")) {
            for (String line : wrap(food, 22)) {
                tooltip.add(Component.literal(line).withStyle(ChatFormatting.DARK_GREEN));
            }
        }
        tooltip.add(Component.translatable("item.meow_machine.sticker_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
