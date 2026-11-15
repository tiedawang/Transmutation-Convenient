package com.transmutationconvenient.compat;

import moze_intel.projecte.api.mapper.recipe.RecipeTypeMapper;
import moze_intel.projecte.emc.mappers.recipe.BaseRecipeTypeMapper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
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
 *
 * <p><b>四個工作台（伏聆／煉獄／終末／終焉，共用 {@code avaritia:crafting_table_recipe}）</b>：
 * 配方類別只實作它自己的 {@code ISpecialRecipe}（{@code Recipe<Container>}），
 * <b>不是</b>原版的 {@code CraftingRecipe}，所以 ProjectE 的 fallback 不吃它們。
 * 不過 Re:Avaritia 自己就帶了一支 ProjectE 的 {@code @RecipeTypeMapper}
 * （{@code AvaritiaExtremeRecipeMapper}）處理這個類型，正常情況下已經有值 ——
 * 這裡也認它，是為了在玩家停用 Re:Avaritia 那個 mapper（{@code mapping.toml}）時兜底。
 *
 * <p>材料 → 產物的 EMC 推算、容器物品扣除全部沿用 ProjectE 的
 * {@link BaseRecipeTypeMapper} 規則。ProjectE 用 {@link RecipeTypeMapper} 註解掃描
 * 所有模組的類來載入這種 mapper，{@code requiredMods = "avaritia"} 讓沒裝無盡貪婪時
 * 整支不載入。
 *
 * <p><b>缺 EMC 材料的慣例</b>：材料之中只要有任何一件「沒有 EMC 值」（不是值為 0，
 * 而是根本不在 EMC 表裡），ProjectE 的轉化就不成立，成品也一樣沒有 EMC 值。
 * 這是等價交換本體與所有 EMC 擴充模組共同的慣例；本 mapper 只負責把正確的材料清單交給
 * ProjectE，不自行補值、也不把缺 EMC 的材料當成「值為 0」去加總。
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
	 * 如果材料是單一物品（匹配項只有一個），那個方法會直接越界。這裡改讀它的三個 public 欄位。
	 *
	 * <p>回傳的材料清單與 Re:Avaritia 自己的 {@code getIngredients()}／JEI 顯示完全一致：
	 * 模板 ×1 ＋ 基底 ×1 ＋ 添加物清單的前三項各 ×1。
	 * <b>不能</b>把 {@code additions} 這個多選項 Ingredient 重複三次丟給 ProjectE ——
	 * ProjectE 會替多選項材料建一個「假分組」，取所有選項裡最便宜的一個，
	 * 三個槽就變成「最便宜選項 ×3」，算出來的成品 EMC 會遠低於實際三個材料的總和。
	 *
	 * <p>材料中若有任何一件沒有 EMC 值，ProjectE 依慣例會讓這條轉化不成立，
	 * 成品也就沒有 EMC 值 —— 這裡不做任何補值。
	 *
	 * <p>選項不足三個時（自訂配方）退回「同一個添加物 Ingredient ×3」，至少不會越界。
	 * 讀不到（版本差異、欄位改名）就退回父類預設，讓這條配方被跳過，不影響其他配方。
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
			ItemStack[] options = additions.getItems();
			if (options.length >= 3) {
				// 與 ExtremeSmithingRecipe#getIngredients()／JEI 顯示一致：前三項各放一個
				return List.of(template, base,
						Ingredient.of(options[0]), Ingredient.of(options[1]), Ingredient.of(options[2]));
			}
			return List.of(template, base, additions, additions, additions);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}
}
