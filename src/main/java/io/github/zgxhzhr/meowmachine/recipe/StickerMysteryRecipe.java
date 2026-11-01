package io.github.zgxhzhr.meowmachine.recipe;

import io.github.zgxhzhr.meowmachine.item.StickerItem;
import io.github.zgxhzhr.meowmachine.registry.ModItems;
import io.github.zgxhzhr.meowmachine.registry.ModRecipes;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * 神秘合成：**一张梗图 + 任意一个物品** → 当前世界配方表里的随机物品。
 *
 * <p>合成前输出框只显示「未揭晓的合成结果」，真正结果在合成时才知道，
 * 且每次不同（结果池见 {@link MysteryPoolSavedData}）。
 */
public class StickerMysteryRecipe extends CustomRecipe {

    public StickerMysteryRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        int total = 0;
        boolean sticker = false;
        boolean other = false;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            total++;
            if (stack.getItem() instanceof StickerItem si) {
                if (sticker) {
                    return false; // 只允许一张梗图
                }
                // 只有「已通过」系列的梗图能用来抽奖（全梗图输入太超模）
                String id = "meow_machine:" + si.stickerId();
                if (!"approved".equals(io.github.zgxhzhr.meowmachine.sticker.StickerRegistry.stateOf(id))) {
                    return false;
                }
                sticker = true;
            } else {
                other = true;
            }
            if (total > 2) {
                return false;
            }
        }
        return sticker && other;
    }

    /** 输出框预览：永远显示占位，合成后才揭晓 */
    @Override
    public ItemStack getResultItem(RegistryAccess access) {
        return new ItemStack(ModItems.MYSTERY_RESULT.get());
    }

    /** 合成只给「未揭晓」占位；右键占位才抽出真结果（输出框不会提前暴露） */
    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess access) {
        return new ItemStack(ModItems.MYSTERY_RESULT.get());
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.STICKER_MYSTERY.get();
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer container) {
        // 梗图消耗掉，其它物品也消耗（简化：不做返还）
        return NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
    }
}
