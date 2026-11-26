package com.transmutationconvenient.recipe;

import com.google.gson.JsonObject;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;

/**
 * {@link ConsumingShapelessRecipe} 的序列化器。
 *
 * <p>JSON 格式與 {@code minecraft:crafting_shapeless} 完全相同；這裡直接借用原版序列化器
 * 解析，再換成會清空合成剩餘物的子類別。網路同步也借用原版，因此客戶端看到的一般配方資料不變。
 */
public class ConsumingShapelessRecipeSerializer implements RecipeSerializer<ConsumingShapelessRecipe> {

	@Override
	public ConsumingShapelessRecipe fromJson(ResourceLocation id, JsonObject json) {
		return copyOf(RecipeSerializer.SHAPELESS_RECIPE.fromJson(id, json));
	}

	@Override
	public ConsumingShapelessRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
		return copyOf(RecipeSerializer.SHAPELESS_RECIPE.fromNetwork(id, buffer));
	}

	@Override
	public void toNetwork(FriendlyByteBuf buffer, ConsumingShapelessRecipe recipe) {
		RecipeSerializer.SHAPELESS_RECIPE.toNetwork(buffer, recipe);
	}

	private static ConsumingShapelessRecipe copyOf(ShapelessRecipe recipe) {
		return new ConsumingShapelessRecipe(
				recipe.getId(),
				recipe.getGroup(),
				recipe.category(),
				recipe.getResultItem(RegistryAccess.EMPTY).copy(),
				recipe.getIngredients());
	}
}
