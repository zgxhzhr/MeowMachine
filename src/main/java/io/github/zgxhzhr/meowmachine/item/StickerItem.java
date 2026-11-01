package io.github.zgxhzhr.meowmachine.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
    public void appendHoverText(ItemStack stack, Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.meow_machine.sticker_" + stickerId + ".story")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
