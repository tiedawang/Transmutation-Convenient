package com.transmutationconvenient.registry;

import com.transmutationconvenient.TransmutationConvenientMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 物品與創造模式物品欄註冊。 */
public final class ModItems {

	public static final DeferredRegister<Item> ITEMS =
			DeferredRegister.create(ForgeRegistries.ITEMS, TransmutationConvenientMod.MOD_ID);

	public static final DeferredRegister<CreativeModeTab> TABS =
			DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TransmutationConvenientMod.MOD_ID);

	public static final RegistryObject<Item> EMC_PIPE =
			ITEMS.register("emc_pipe", () -> new BlockItem(ModBlocks.EMC_PIPE.get(), new Item.Properties()));

	public static final RegistryObject<Item> AUTO_VENDING_MACHINE =
			ITEMS.register("auto_vending_machine",
					() -> new BlockItem(ModBlocks.AUTO_VENDING_MACHINE.get(), new Item.Properties()));

	/** 本模組專屬創造模式物品欄「便捷轉化」—— EMC 管道與自動販賣機。 */
	public static final RegistryObject<CreativeModeTab> TAB = TABS.register(
			"transmutationconvenient",
			() -> CreativeModeTab.builder()
					.title(Component.translatable("itemGroup.transmutationconvenient"))
					.icon(() -> new ItemStack(EMC_PIPE.get()))
					.displayItems((params, output) -> {
						output.accept(EMC_PIPE.get());
						output.accept(AUTO_VENDING_MACHINE.get());
					})
					.build());

	private ModItems() {
	}

	public static void register(IEventBus modEventBus) {
		ITEMS.register(modEventBus);
		TABS.register(modEventBus);
	}
}
