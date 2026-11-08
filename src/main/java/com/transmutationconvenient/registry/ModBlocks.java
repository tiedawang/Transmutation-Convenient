package com.transmutationconvenient.registry;

import com.transmutationconvenient.TransmutationConvenientMod;
import com.transmutationconvenient.machine.AutoVendingMachineBlock;
import com.transmutationconvenient.pipe.EmcPipeBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 方塊註冊。 */
public final class ModBlocks {

	public static final DeferredRegister<Block> BLOCKS =
			DeferredRegister.create(ForgeRegistries.BLOCKS, TransmutationConvenientMod.MOD_ID);

	/** EMC 管道：外觀與連線規則沿用無限科技的 RT 傳輸線，介質換成 EMC。 */
	public static final RegistryObject<Block> EMC_PIPE = BLOCKS.register(
			"emc_pipe",
			() -> new EmcPipeBlock(BlockBehaviour.Properties.of()
					.strength(0.3F)
					.sound(SoundType.GLASS)
					.noOcclusion()
					.isValidSpawn((state, level, pos, entityType) -> false)
					.isRedstoneConductor((state, level, pos) -> false)
					.isSuffocating((state, level, pos) -> false)
					.isViewBlocking((state, level, pos) -> false)));

	/**
	 * 自動販賣機：每個遊戲刻把裡面所有物品賣成 EMC，再把 EMC 打進放置者的帳戶。
	 *
	 * <p>方塊屬性照原版熔爐（硬度 3.5、需要正確工具才掉落），模型暫時借用熔爐的。
	 */
	public static final RegistryObject<Block> AUTO_VENDING_MACHINE = BLOCKS.register(
			"auto_vending_machine",
			() -> new AutoVendingMachineBlock(BlockBehaviour.Properties.of()
					.mapColor(MapColor.STONE)
					.requiresCorrectToolForDrops()
					.strength(3.5F)));

	private ModBlocks() {
	}

	public static void register(IEventBus modEventBus) {
		BLOCKS.register(modEventBus);
	}
}
