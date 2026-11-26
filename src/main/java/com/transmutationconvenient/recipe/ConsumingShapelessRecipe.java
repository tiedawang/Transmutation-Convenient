package com.transmutationconvenient.recipe;

import com.transmutationconvenient.registry.ModRecipes;
import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.resources.ResourceLocation;

/**
 * 一般無序配方，但合成後不保留任何「合成剩餘物」。
 *
 * <p>用途只有烈性賢者之石本身：ProjectE 的賢者之石預設是 {@code hasCraftingRemainingItem}
 * 的可重複使用催化劑；若用普通 {@code minecraft:crafting_shapeless}，賢者之石在合成後
 * 會被放回合成格，等於不消耗。這個配方類別刻意覆寫 {@link #getRemainingItems} 回傳全空，
 * 讓合成烈性賢者之石時真的消耗一個 ProjectE 賢者之石與兩個烈焰粉。
 *
 * <p>它仍然繼承 {@link ShapelessRecipe}，因此 JEI 與配方書看到的是一般的無序配方。
 */
public class ConsumingShapelessRecipe extends ShapelessRecipe {

	public ConsumingShapelessRecipe(
			ResourceLocation id,
			String group,
			CraftingBookCategory category,
			ItemStack result,
			NonNullList<Ingredient> ingredients) {
		super(id, group, category, result, ingredients);
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return ModRecipes.CONSUMING_SHAPELESS_SERIALIZER.get();
	}

	@Override
	public NonNullList<ItemStack> getRemainingItems(CraftingContainer container) {
		return NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
	}
}
