package io.github.zgxhzhr.meowmachine.registry;

import io.github.zgxhzhr.meowmachine.MeowMachine;
import io.github.zgxhzhr.meowmachine.recipe.StickerMysteryRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 配方类型注册。 */
public final class ModRecipes {

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, MeowMachine.MOD_ID);

    /** 神秘合成：梗图 + 任意物品 */
    public static final RegistryObject<RecipeSerializer<StickerMysteryRecipe>> STICKER_MYSTERY =
            SERIALIZERS.register("sticker_mystery",
                    () -> new SimpleCraftingRecipeSerializer<>(StickerMysteryRecipe::new));

    private ModRecipes() {
    }
}
