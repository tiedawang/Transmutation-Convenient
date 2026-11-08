package com.transmutationconvenient.machine;

import com.transmutationconvenient.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 自動販賣機 —— 把物品欄裡的東西每刻全部賣成 EMC，再把 EMC 全部打進放置者的帳戶。
 *
 * <p>物品欄是一個大箱子（54 格），右鍵打開原版的大箱子介面；因為方塊實作原版的
 * {@link net.minecraft.world.Container}，漏斗或其他模組的物品輸送也能直接把物品送進來。
 * 送進來的物品在下一個遊戲刻就會被賣掉 —— 這台機器沒有「停止販賣」的開關，
 * 效率是「每個遊戲刻能賣多少就賣多少」。
 *
 * <p>「這台機器是誰放的」沿用轉化桌那一套：玩家用方塊物品放下時由
 * {@code BlockItemPlaceMixin} 記到世界存檔的放置者表，之後每刻把 EMC 打進那位
 * 玩家的 ProjectE 帳戶。沒有放置紀錄的機器（指令放的、模組裝上前就存在的）退回
 * 「機器附近最近的玩家」，與轉化桌的規則相同。
 */
public class AutoVendingMachineBlock extends BaseEntityBlock {

	public AutoVendingMachineBlock(Properties properties) {
		super(properties);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new AutoVendingMachineBlockEntity(pos, state);
	}

	/** 販賣邏輯只在服務端跑；客戶端只負責顯示模型。 */
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		if (level.isClientSide()) {
			return null;
		}
		return createTickerHelper(type, ModBlockEntities.AUTO_VENDING_MACHINE.get(),
				AutoVendingMachineBlockEntity::serverTick);
	}

	/** 右鍵打開大箱子介面（54 格）；沒有 owner 限制，誰都能把東西放進去賣。 */
	@Override
	public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
			InteractionHand hand, BlockHitResult hit) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (!(level.getBlockEntity(pos) instanceof AutoVendingMachineBlockEntity machine)) {
			return InteractionResult.PASS;
		}
		player.openMenu(new SimpleMenuProvider(
				(containerId, inventory, p) -> ChestMenu.sixRows(containerId, inventory, machine),
				machine.getDisplayName()));
		return InteractionResult.CONSUME;
	}

	/**
	 * 方塊模型照常渲染。
	 *
	 * <p>1.20.1 的 {@link BaseEntityBlock} 預設回傳 {@link RenderShape#INVISIBLE}
	 * （給「完全由方塊實體渲染」的方塊用）；不覆寫成 {@link RenderShape#MODEL}
	 * 的話，借用的熔爐模型整塊都不會被畫出來。
	 */
	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}
}
