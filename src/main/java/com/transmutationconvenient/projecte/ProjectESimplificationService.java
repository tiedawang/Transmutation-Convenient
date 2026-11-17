package com.transmutationconvenient.projecte;

import com.transmutationconvenient.TransmutationConvenientMod;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipe;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * ProjectE 精簡模式：移除暗物質／紅物質／寶石套裝與能量收集器 MK1～3 的合成配方，
 * 並把便攜式轉化桌、暗物質台座的配方換成較便宜的版本。
 *
 * <p>做法是直接替換伺服器 {@link RecipeManager} 的配方表，再發送
 * {@link ClientboundUpdateRecipesPacket} 讓客戶端（含 JEI）同步。還原時使用第一次
 * 精簡前留下的原始配方；因此伺服器啟動時若存檔已經是精簡狀態，必須先套用一次，
 * 把原始配方留起來，之後才能完整還原。
 */
public final class ProjectESimplificationService {

	private static final ResourceLocation TRANSMUTATION_TABLET =
			new ResourceLocation("projecte", "transmutation_tablet");
	private static final ResourceLocation DARK_MATTER_PEDESTAL =
			new ResourceLocation("projecte", "dm_pedestal");

	/** 要從配方表與創造標籤移除的物品／配方。 */
	private static final Set<ResourceLocation> DISABLED_IDS = Set.of(
			id("dm_helmet"), id("dm_chestplate"), id("dm_leggings"), id("dm_boots"),
			id("rm_helmet"), id("rm_chestplate"), id("rm_leggings"), id("rm_boots"),
			id("gem_helmet"), id("gem_chestplate"), id("gem_leggings"), id("gem_boots"),
			id("collector_mk1"), id("collector_mk2"), id("collector_mk3"));

	/** 配方仍在，但內容要換掉的物品。 */
	private static final Set<ResourceLocation> MODIFIED_IDS = Set.of(
			TRANSMUTATION_TABLET, DARK_MATTER_PEDESTAL);

	private static final TagKey<Item> FORGE_STONE =
			TagKey.create(Registries.ITEM, new ResourceLocation("forge", "stone"));

	/** 精簡前的原始配方；key 是配方 id。 */
	private static final Map<ResourceLocation, Recipe<?>> ORIGINALS = new HashMap<>();

	private static boolean enabled;

	private ProjectESimplificationService() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	/** 伺服器啟動時只設定狀態；已經是精簡狀態才需要實際套用。 */
	public static void initialize(MinecraftServer server, boolean simplified) {
		enabled = false;
		if (simplified) {
			apply(server, true);
		}
	}

	/**
	 * 套用或解除精簡。這個方法可以在伺服器啟動、/reload、指令時重複呼叫。
	 *
	 * @param simplified true = 精簡；false = 還原
	 */
	public static void apply(MinecraftServer server, boolean simplified) {
		if (simplified) {
			simplify(server);
			enabled = true;
		} else {
			// 已經是完整配方就不必再重建一次；/reload 後也走這條。
			if (enabled) {
				restore(server);
			}
			enabled = false;
		}
	}

	/** 伺服器端配方表是否還是完整版（用來判斷 /reload 後要不要重新精簡）。 */
	public static boolean hasDisabledRecipes(MinecraftServer server) {
		for (Recipe<?> recipe : server.getRecipeManager().getRecipes()) {
			if (DISABLED_IDS.contains(recipe.getId())) {
				return true;
			}
		}
		return false;
	}

	/** 這個物品是否屬於精簡時要從創造標籤移除的內容。 */
	public static boolean isDisabledItem(Item item) {
		ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
		return id != null && DISABLED_IDS.contains(id);
	}

	/** 把目前的配方表同步給所有玩家；JEI 會透過 RecipesUpdatedEvent 重建。 */
	public static void syncRecipes(MinecraftServer server) {
		ClientboundUpdateRecipesPacket packet =
				new ClientboundUpdateRecipesPacket(server.getRecipeManager().getRecipes());
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.connection.send(packet);
		}
	}

	private static void simplify(MinecraftServer server) {
		RecipeManager manager = server.getRecipeManager();
		Map<ResourceLocation, Recipe<?>> next = new LinkedHashMap<>();

		for (Recipe<?> recipe : manager.getRecipes()) {
			ResourceLocation id = recipe.getId();
			if (DISABLED_IDS.contains(id) || MODIFIED_IDS.contains(id)) {
				// putIfAbsent：/reload 後看到的是原始配方，重複套用時也不會把
				// 已經換過的便宜配方當成原始配方存起來。
				ORIGINALS.putIfAbsent(id, recipe);
				continue;
			}
			next.put(id, recipe);
		}

		Recipe<?> tablet = createTablet(true);
		if (tablet != null) {
			next.put(TRANSMUTATION_TABLET, tablet);
		}
		Recipe<?> pedestal = createPedestal(true);
		if (pedestal != null) {
			next.put(DARK_MATTER_PEDESTAL, pedestal);
		}

		manager.replaceRecipes(next.values());
		TransmutationConvenientMod.LOGGER.info(
				"[便捷轉化] 已精簡 ProjectE：移除 {} 個配方，替換 {} 個配方。",
				DISABLED_IDS.size(), MODIFIED_IDS.size());
		logValidation(server);
	}

	private static void restore(MinecraftServer server) {
		RecipeManager manager = server.getRecipeManager();
		Map<ResourceLocation, Recipe<?>> next = new LinkedHashMap<>();

		for (Recipe<?> recipe : manager.getRecipes()) {
			ResourceLocation id = recipe.getId();
			if (DISABLED_IDS.contains(id) || MODIFIED_IDS.contains(id)) {
				continue;
			}
			next.put(id, recipe);
		}

		for (ResourceLocation id : DISABLED_IDS) {
			Recipe<?> original = ORIGINALS.get(id);
			if (original != null) {
				next.put(id, original);
			}
		}
		for (ResourceLocation id : MODIFIED_IDS) {
			Recipe<?> original = ORIGINALS.get(id);
			if (original != null) {
				next.put(id, original);
			}
		}

		// 保險：若 ORIGINALS 沒有便攜桌／台座（例如狀態是在舊存檔直接改出來的），
		// 至少要用程式重建原版配方，不能讓這兩個配方消失。
		if (!next.containsKey(TRANSMUTATION_TABLET)) {
			Recipe<?> original = createTablet(false);
			if (original != null) {
				next.put(TRANSMUTATION_TABLET, original);
			}
		}
		if (!next.containsKey(DARK_MATTER_PEDESTAL)) {
			Recipe<?> original = createPedestal(false);
			if (original != null) {
				next.put(DARK_MATTER_PEDESTAL, original);
			}
		}

		manager.replaceRecipes(next.values());
		TransmutationConvenientMod.LOGGER.info("[便捷轉化] 已解除 ProjectE 精簡。");
		logValidation(server);
	}

	/**
	 * 只寫 log、不改配方：用來確認移除目標真的不在配方表裡，以及兩個替換配方
	 * 目前用的是哪一種材料。指令切換與伺服器啟動各會出現一次，方便追查存檔狀態。
	 */
	private static void logValidation(MinecraftServer server) {
		RecipeManager manager = server.getRecipeManager();
		long disabledLeft = manager.getRecipes().stream()
				.filter(recipe -> DISABLED_IDS.contains(recipe.getId()))
				.count();
		TransmutationConvenientMod.LOGGER.info(
				"[便捷轉化] 配方驗證：移除目標剩 {} 個；便攜桌={}；台座={}",
				disabledLeft,
				describe(manager.byKey(TRANSMUTATION_TABLET).orElse(null)),
				describe(manager.byKey(DARK_MATTER_PEDESTAL).orElse(null)));
	}

	private static String describe(Recipe<?> recipe) {
		if (recipe == null) {
			return "缺少";
		}
		if (!(recipe instanceof ShapedRecipe shaped)) {
			return recipe.getClass().getSimpleName();
		}
		StringBuilder builder = new StringBuilder();
		for (Ingredient ingredient : shaped.getIngredients()) {
			ItemStack[] stacks = ingredient.getItems();
			if (stacks.length == 0) {
				builder.append("-,");
			} else {
				builder.append(BuiltInRegistries.ITEM.getKey(stacks[0].getItem())).append(',');
			}
		}
		return builder.toString();
	}


	/**
	 * 便攜式轉化桌：原版 D = 暗物質塊；精簡版 D = 鑽石塊。
	 */
	private static Recipe<?> createTablet(boolean simplified) {
		Item result = item("projecte:transmutation_tablet");
		Item table = item("projecte:transmutation_table");
		Item darkBlock = item("projecte:dark_matter_block");
		if (result == null || table == null || (!simplified && darkBlock == null)) {
			return null;
		}

		Ingredient d = simplified ? Ingredient.of(Items.DIAMOND_BLOCK) : Ingredient.of(darkBlock);
		Map<Character, Ingredient> keys = Map.of(
				'D', d,
				'S', Ingredient.of(FORGE_STONE),
				'T', Ingredient.of(table));
		return shaped(TRANSMUTATION_TABLET,
				new String[]{"DSD", "STS", "DSD"}, keys, new ItemStack(result));
	}

	/**
	 * 暗物質台座：原版 D = 暗物質塊；精簡版 D = 暗物質。
	 */
	private static Recipe<?> createPedestal(boolean simplified) {
		Item result = item("projecte:dm_pedestal");
		Item redMatter = item("projecte:red_matter");
		Item dark = item(simplified ? "projecte:dark_matter" : "projecte:dark_matter_block");
		if (result == null || redMatter == null || dark == null) {
			return null;
		}

		Map<Character, Ingredient> keys = Map.of(
				'D', Ingredient.of(dark),
				'R', Ingredient.of(redMatter));
		return shaped(DARK_MATTER_PEDESTAL,
				new String[]{"RDR", "RDR", "DDD"}, keys, new ItemStack(result));
	}

	private static ShapedRecipe shaped(ResourceLocation id, String[] pattern,
			Map<Character, Ingredient> keys, ItemStack result) {
		int width = pattern[0].length();
		int height = pattern.length;
		NonNullList<Ingredient> ingredients =
				NonNullList.withSize(width * height, Ingredient.EMPTY);
		for (int y = 0; y < height; y++) {
			String row = pattern[y];
			for (int x = 0; x < width; x++) {
				Ingredient ingredient = keys.get(row.charAt(x));
				if (ingredient != null) {
					ingredients.set(y * width + x, ingredient);
				}
			}
		}
		return new ShapedRecipe(id, "", CraftingBookCategory.MISC,
				width, height, ingredients, result, true);
	}

	private static Item item(String id) {
		Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(id));
		return item == Items.AIR ? null : item;
	}

	private static ResourceLocation id(String path) {
		return new ResourceLocation("projecte", path);
	}
}
