package com.transmutationconvenient.compat;

import com.transmutationconvenient.recipe.ConsumingShapelessRecipe;
import java.util.HashMap;
import java.util.Map;
import moze_intel.projecte.api.mapper.collector.IMappingCollector;
import moze_intel.projecte.api.mapper.recipe.INSSFakeGroupManager;
import moze_intel.projecte.api.mapper.recipe.RecipeTypeMapper;
import moze_intel.projecte.api.nss.NSSItem;
import moze_intel.projecte.api.nss.NormalizedSimpleStack;
import moze_intel.projecte.emc.mappers.recipe.BaseRecipeTypeMapper;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.NotNull;

/**
 * 讓 ProjectE 用「真正消耗 1 個賢者之石」的方式計算烈性賢者之石的 EMC。
 *
 * <p>ProjectE 的 {@link BaseRecipeTypeMapper} 在計算材料時，只要物品有
 * {@code hasCraftingRemainingItem}，就會把它的合成剩餘物以負數加回成本。
 * ProjectE 的賢者之石正是可重複使用的合成剩餘物，所以一般計算會把
 * 「1 個賢者之石」完全抵消，讓烈性賢者之石的 EMC 只剩 2 個烈焰粉，
 * 反而低於賢者之石本身。
 *
 * <p>本映射器只處理 {@link ConsumingShapelessRecipe}（烈性賢者之石本身的配方），
 * 直接依配方欄位把每個材料各算 1 次，不做合成剩餘物扣除。
 * 優先級 100 高於 ProjectE 內建的 {@code VanillaRecipeTypeMapper}（0），
 * 因此這條配方不會再被內建映射器用「抵消賢者之石」的方式重算一次。
 *
 * <p>材料 EMC 仍然由 ProjectE 的轉化求解器依實際 EMC 表計算；不寫死數值，
 * 所以模組包調整賢者之石或烈焰粉的 EMC 時，烈性賢者之石會跟著變。
 */
@RecipeTypeMapper(priority = 100)
public class PotentStoneRecipeMapper extends BaseRecipeTypeMapper {

	@Override
	public @NotNull String getName() {
		return "PotentStoneRecipe";
	}

	@Override
	public @NotNull String getDescription() {
		return "Maps the potent philosopher's stone recipe without treating the philosopher's stone as reusable.";
	}

	@Override
	public boolean canHandle(@NotNull RecipeType<?> recipeType) {
		return recipeType == RecipeType.CRAFTING;
	}

	@Override
	public boolean handleRecipe(
			@NotNull IMappingCollector<NormalizedSimpleStack, Long> collector,
			@NotNull Recipe<?> recipe,
			@NotNull RegistryAccess registryAccess,
			@NotNull INSSFakeGroupManager fakeGroupManager) {
		if (!(recipe instanceof ConsumingShapelessRecipe)) {
			return false;
		}

		ItemStack result = recipe.getResultItem(registryAccess);
		if (result.isEmpty()) {
			return false;
		}

		Map<NormalizedSimpleStack, Integer> ingredients = new HashMap<>();
		for (Ingredient ingredient : recipe.getIngredients()) {
			ItemStack[] choices = ingredient.getItems();
			if (choices.length != 1 || choices[0].isEmpty()) {
				// 烈性賢者之石的配方是固定材料；若資料被改壞，交回內建映射器處理。
				return false;
			}
			ingredients.merge(NSSItem.createItem(choices[0]), 1, Integer::sum);
		}
		if (ingredients.isEmpty()) {
			return false;
		}

		collector.addConversion(result.getCount(), NSSItem.createItem(result), ingredients);
		return true;
	}
}
