package com.transmutationconvenient.registry;

import com.transmutationconvenient.TransmutationConvenientMod;
import com.transmutationconvenient.recipe.ConsumingShapelessRecipe;
import com.transmutationconvenient.recipe.ConsumingShapelessRecipeSerializer;
import com.transmutationconvenient.recipe.PotentSmeltingRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 自訂配方序列化器註冊。 */
public final class ModRecipes {

	public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
			DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, TransmutationConvenientMod.MOD_ID);

	/** 烈性賢者之石：與任意可燒煉物品合成，產物由熔爐配方決定。 */
	public static final RegistryObject<RecipeSerializer<PotentSmeltingRecipe>> POTENT_SMELTING_SERIALIZER =
			SERIALIZERS.register("potent_smelting",
					() -> new SimpleCraftingRecipeSerializer<>(PotentSmeltingRecipe::new));

	/** 烈性賢者之石本身的一般無序配方，但會消耗所有材料（包含 ProjectE 賢者之石）。 */
	public static final RegistryObject<RecipeSerializer<ConsumingShapelessRecipe>> CONSUMING_SHAPELESS_SERIALIZER =
			SERIALIZERS.register("consuming_shapeless", ConsumingShapelessRecipeSerializer::new);

	private ModRecipes() {
	}

	public static void register(IEventBus modEventBus) {
		SERIALIZERS.register(modEventBus);
	}
}
