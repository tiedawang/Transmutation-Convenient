package com.transmutationconvenient.registry;

import com.transmutationconvenient.TransmutationConvenientMod;
import com.transmutationconvenient.machine.AutoVendingMachineBlockEntity;
import com.transmutationconvenient.pipe.EmcPipeBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 方塊實體類型註冊。 */
public final class ModBlockEntities {

	public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
			DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, TransmutationConvenientMod.MOD_ID);

	public static final RegistryObject<BlockEntityType<EmcPipeBlockEntity>> EMC_PIPE =
			BLOCK_ENTITIES.register("emc_pipe",
					() -> BlockEntityType.Builder.of(EmcPipeBlockEntity::new,
							ModBlocks.EMC_PIPE.get()).build(null));

	public static final RegistryObject<BlockEntityType<AutoVendingMachineBlockEntity>> AUTO_VENDING_MACHINE =
			BLOCK_ENTITIES.register("auto_vending_machine",
					() -> BlockEntityType.Builder.of(AutoVendingMachineBlockEntity::new,
							ModBlocks.AUTO_VENDING_MACHINE.get()).build(null));

	private ModBlockEntities() {
	}

	public static void register(IEventBus modEventBus) {
		BLOCK_ENTITIES.register(modEventBus);
	}
}
