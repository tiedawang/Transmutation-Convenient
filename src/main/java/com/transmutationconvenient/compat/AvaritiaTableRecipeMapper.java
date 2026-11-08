package com.transmutationconvenient.compat;

import moze_intel.projecte.api.mapper.recipe.RecipeTypeMapper;
import moze_intel.projecte.emc.mappers.recipe.BaseRecipeTypeMapper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * 讓 ProjectE 認識「無盡貪婪：重生」（Re:Avaritia）的兩個自訂配方類型。
 *
 * <p><b>終焉鍛造台（{@code avaritia:extreme_smithing_table}）</b>：配方類型
 * {@code avaritia:extreme_smithing_recipe}，配方類別 {@code ExtremeSmithingRecipe}
 * 實作的是原版 {@code SmithingRecipe} 介面，但既不是 {@code SmithingTransformRecipe}
 * 也不是 {@code SmithingTrimRecipe}。ProjectE 的 {@code SmithingRecipeMapper} 只認
 * {@code RecipeType.SMITHING}、{@code FallbackRecipeTypeMapper} 只收那兩個具體子類，
 * 所以這批配方<b>沒有任何 mapper 會讀</b> —— 用終焉鍛造台鍛造出來的成品因此沒有 EMC 值。
 * 這是本 mapper 真正修的東西（1.4.2 出貨資料裡有 8 條，實測全部從 0 變成有值）。
 *
 * <p><b>四個工作台（伏聆／煉獄／終末／終焉，共用 {@code avaritia:crafting_table_recipe}）</b>：
 * 配方類別只實作它自己的 {@code ISpecialRecipe}（{@code Recipe<Container>}），
 * <b>不是</b>原版的 {@code CraftingRecipe}，所以 ProjectE 的 fallback 不吃它們。
 * 不過 Re:Avaritia 自己就帶了一支 ProjectE 的 {@code @RecipeTypeMapper}
 * （{@code AvaritiaExtremeRecipeMapper}）處理這個類型，正常情況下已經有值 ——
 * 這裡也認它，是為了在玩家停用 Re:Avaritia 那個 mapper（{@code mapping.toml}）時兜底。
 *
 * <p>材料 → 產物的 EMC 推算、多選項材料的假分組、容器物品扣除全部沿用 ProjectE 的
 * {@link BaseRecipeTypeMapper} 規則。ProjectE 用 {@link RecipeTypeMapper} 註解掃描
 * 所有模組的類來載入這種 mapper，{@code requiredMods = "avaritia"} 讓沒裝無盡貪婪時
 * 整支不載入。
 */
@RecipeTypeMapper(requiredMods = "avaritia")
public class AvaritiaTableRecipeMapper extends BaseRecipeTypeMapper {

	/** 四個工作台共用的配方類型。 */
	private static final ResourceLocation TABLE_RECIPE_TYPE = new ResourceLocation("avaritia", "crafting_table_recipe");

	/** 終焉鍛造台的配方類型。 */
	private static final ResourceLocation EXTREME_SMITHING_RECIPE_TYPE = new ResourceLocation("avaritia", "extreme_smithing_recipe");

	@Override
	public @NotNull String getName() {
		return "AvaritiaTableRecipes";
	}

	@Override
	public @NotNull String getDescription() {
		return "Maps Re:Avaritia table crafting and extreme smithing recipes to EMC values based on their ingredients.";
	}

	@Override
	public boolean canHandle(@NotNull RecipeType<?> recipeType) {
		ResourceLocation id = ForgeRegistries.RECIPE_TYPES.getKey(recipeType);
		return TABLE_RECIPE_TYPE.equals(id) || EXTREME_SMITHING_RECIPE_TYPE.equals(id);
	}

	/**
	 * 終焉鍛造台配方要自己取材料。
	 *
	 * <p>{@code ExtremeSmithingRecipe#getIngredients()} 會把 {@code additions} 拆成「前三個匹配項」，
	 * 如果材料是單一物品（匹配項只有一個），那個方法會直接越界。這裡改讀它的三個 public 欄位，
	 * 回傳「模板 ×1 ＋ 基底 ×1 ＋ 添加物 ×3」——與它自己 {@code matches} 的槽位需求一致。
	 *
	 * <p>讀不到（版本差異、欄位改名）就退回父類預設，讓這條配方被跳過，不影響其他配方。
	 */
	@Override
	protected @NotNull Collection<Ingredient> getIngredients(@NotNull Recipe<?> recipe) {
		ResourceLocation type = ForgeRegistries.RECIPE_TYPES.getKey(recipe.getType());
		if (EXTREME_SMITHING_RECIPE_TYPE.equals(type)) {
			List<Ingredient> ingredients = readExtremeSmithingIngredients(recipe);
			if (ingredients != null) {
				return ingredients;
			}
		}
		return super.getIngredients(recipe);
	}

	@Nullable
	private static List<Ingredient> readExtremeSmithingIngredients(Recipe<?> recipe) {
		try {
			Class<?> type = recipe.getClass();
			Ingredient template = (Ingredient) type.getField("template").get(recipe);
			Ingredient base = (Ingredient) type.getField("base").get(recipe);
			Ingredient additions = (Ingredient) type.getField("additions").get(recipe);
			return List.of(template, base, additions, additions, additions);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}
}
