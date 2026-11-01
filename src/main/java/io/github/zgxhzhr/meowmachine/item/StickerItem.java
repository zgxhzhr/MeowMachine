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
        if (level.isClientSide) {
            // 客户端才需要界面；服务端保持无副作用
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                    net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> io.github.zgxhzhr.meowmachine.client.StickerViewer.open(stickerId));
        }
        return InteractionResultHolder.success(stack);
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
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
