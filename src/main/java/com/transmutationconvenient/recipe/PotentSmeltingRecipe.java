package com.transmutationconvenient.recipe;

import com.transmutationconvenient.registry.ModItems;
import com.transmutationconvenient.registry.ModRecipes;
import java.util.Optional;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * 烈性賢者之石的燒煉合成。
 *
 * <p>這是一個「特殊合成配方」：合成格裡必須恰好有兩個非空欄位，一個是烈性賢者之石，
 * 另一個是可燒煉的物品。結果不是固定物品，而是當下依照該物品查到的熔爐配方產物。
 *
 * <p>查詢燒煉配方需要 {@link Level}；{@link #matches} 有 level 可用，但
 * {@link #assemble} 只有 {@link RegistryAccess}，因此伺服器端從
 * {@link ServerLifecycleHooks#getCurrentServer()} 取得伺服器的主世界。
 * 客戶端菜單不會呼叫 {@code assemble}；若其他模組在客戶端呼叫，取不到伺服器時回傳空，
 * 不影響伺服器端的實際合成。
 */
public class PotentSmeltingRecipe extends CustomRecipe {

	public PotentSmeltingRecipe(ResourceLocation id, CraftingBookCategory category) {
		super(id, category);
	}

	@Override
	public boolean matches(CraftingContainer container, Level level) {
		ItemStack input = findSmeltableInput(container);
		return !input.isEmpty() && findSmelting(level, input).isPresent();
	}

	@Override
	public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
		ItemStack input = findSmeltableInput(container);
		if (input.isEmpty()) {
			return ItemStack.EMPTY;
		}
		MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
		if (server == null) {
			return ItemStack.EMPTY;
		}
		Level level = server.overworld();
		return findSmelting(level, input)
				.map(recipe -> recipe.assemble(
						new SimpleContainer(input.copyWithCount(1)), level.registryAccess()))
				.orElse(ItemStack.EMPTY);
	}

	@Override
	public boolean canCraftInDimensions(int width, int height) {
		return width * height >= 2;
	}

	@Override
	public RecipeSerializer<?> getSerializer() {
		return ModRecipes.POTENT_SMELTING_SERIALIZER.get();
	}

	@Override
	public ItemStack getToastSymbol() {
		return new ItemStack(ModItems.POTENT_PHILOSOPHERS_STONE.get());
	}

	/**
	 * 找出「被燒煉的物品」：合成格必須恰好有一個烈性賢者之石與一個其他物品。
	 * 回傳空代表排列不符（例如兩個賢者之石、兩個非賢者之石物品、或只有一個物品）。
	 */
	private static ItemStack findSmeltableInput(CraftingContainer container) {
		ItemStack catalyst = ItemStack.EMPTY;
		ItemStack input = ItemStack.EMPTY;

		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (stack.isEmpty()) {
				continue;
			}
			if (stack.is(ModItems.POTENT_PHILOSOPHERS_STONE.get())) {
				if (!catalyst.isEmpty()) {
					return ItemStack.EMPTY;
				}
				catalyst = stack;
			} else {
				if (!input.isEmpty()) {
					return ItemStack.EMPTY;
				}
				input = stack;
			}
		}

		return catalyst.isEmpty() ? ItemStack.EMPTY : input;
	}

	private static Optional<SmeltingRecipe> findSmelting(Level level, ItemStack stack) {
		if (stack.isEmpty()) {
			return Optional.empty();
		}
		SimpleContainer container = new SimpleContainer(stack.copyWithCount(1));
		return level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, container, level);
	}
}
