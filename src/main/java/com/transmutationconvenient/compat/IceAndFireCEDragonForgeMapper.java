package com.transmutationconvenient.compat;

import moze_intel.projecte.api.mapper.recipe.RecipeTypeMapper;
import moze_intel.projecte.emc.mappers.recipe.BaseRecipeTypeMapper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

/**
 * 讓 ProjectE 認識「冰火傳說社區版」（Ice and Fire CE）的龍爐配方。
 *
 * <p>原版冰火與社區版共用同一個 mod id {@code iceandfire}，但社區版把 Java
 * 類別搬到 {@code com.iafenvoy...}。ProjectE Integration 7.2.5 的舊龍爐映射器
 * 是為原版編譯的，遇到社區版會找不到舊類別；本專案的 {@code CraftingMapperGuardMixin}
 * 會把那個錯誤跳過，這支映射器則負責補上社區版的龍爐配方。
 *
 * <p>配方類型仍然是 {@code iceandfire:dragonforge}，配方物件有公開的
 * {@code getInput()} 與 {@code getBlood()}。這裡用反射讀取，避免把社區版的 jar
 * 加進編譯依賴；沒裝社區版時，這個類別也不會被 ProjectE 載入
 * （{@code requiredMods = "iceandfire"}，且 {@link #canHandle} 會再確認社區版類別存在）。
 *
 * <p>材料 → 產物的 EMC 推算沿用 ProjectE 的 {@link BaseRecipeTypeMapper}：
 * 材料中只要有任何一件沒有 EMC 值，這條轉化就不成立，成品也沒有 EMC 值；
 * 本映射器不補值、不把缺值當成 0。
 */
@RecipeTypeMapper(requiredMods = "iceandfire", priority = 1)
public class IceAndFireCEDragonForgeMapper extends BaseRecipeTypeMapper {

	/** 龍爐配方類型；原版與社區版都使用這個登錄名。 */
	private static final ResourceLocation DRAGON_FORGE_TYPE = new ResourceLocation("iceandfire", "dragonforge");

	/** 社區版龍爐配方類別；用來區分原版與社區版，避免在原版環境重複映射。 */
	private static final String CE_RECIPE_CLASS = "com.iafenvoy.iceandfire.recipe.DragonForgeRecipe";

	/** 只在社區版環境啟用；原版留給 ProjectE Integration 原本的映射器。 */
	private static final boolean COMMUNITY_EDITION = classExists(CE_RECIPE_CLASS);

	@Override
	public @NotNull String getName() {
		return "IceAndFireCEDragonForgeMapper";
	}

	@Override
	public @NotNull String getDescription() {
		return "Maps Ice and Fire Community Edition dragon forge recipes to EMC values.";
	}

	@Override
	public boolean canHandle(@NotNull RecipeType<?> recipeType) {
		return COMMUNITY_EDITION && DRAGON_FORGE_TYPE.equals(ForgeRegistries.RECIPE_TYPES.getKey(recipeType));
	}

	/**
	 * 社區版龍爐配方沒有把血液放進一般的材料清單，必須自己取
	 * {@code getInput()} 與 {@code getBlood()}。讀不到就拋出例外，
	 * 讓 ProjectE 跳過這條配方，而不是用空材料算出錯誤的 EMC。
	 */
	@Override
	protected @NotNull Collection<Ingredient> getIngredients(@NotNull Recipe<?> recipe) {
		try {
			Method input = recipe.getClass().getMethod("getInput");
			Method blood = recipe.getClass().getMethod("getBlood");
			return List.of(
					(Ingredient) input.invoke(recipe),
					(Ingredient) blood.invoke(recipe));
		} catch (ReflectiveOperationException | ClassCastException e) {
			throw new IllegalStateException("無法讀取社區版龍爐配方的材料", e);
		}
	}

	private static boolean classExists(String className) {
		try {
			Class.forName(className, false, IceAndFireCEDragonForgeMapper.class.getClassLoader());
			return true;
		} catch (ClassNotFoundException | LinkageError e) {
			return false;
		}
	}
}
