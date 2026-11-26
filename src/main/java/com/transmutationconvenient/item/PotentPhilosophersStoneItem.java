package com.transmutationconvenient.item;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * 烈性賢者之石。
 *
 * <p>它本身是合成催化劑：與任意可燒煉的物品一起放在合成格時，會產生該物品的燒煉產物，
 * 而它自己不會被消耗。實作上必須覆寫 Forge 的 {@code hasCraftingRemainingItem} 與
 * {@code getCraftingRemainingItem}，否則原版合成會把它當普通材料吃掉。
 *
 * <p>右鍵方塊時擁有打火石的功能：點燃營火、蠟燭、蠟燭蛋糕，或在方塊上點火；
 * 不消耗物品本身，也不消耗耐久。
 *
 * <p>堆疊數固定為 1：{@link ItemStack#getCraftingRemainingItem()} 會回傳當下整疊的副本，
 * 若允許堆疊，合成後會把整疊加回去而複製物品。
 */
public class PotentPhilosophersStoneItem extends Item {

	public PotentPhilosophersStoneItem(Properties properties) {
		super(properties);
	}

	@Override
	public boolean hasCraftingRemainingItem(ItemStack stack) {
		return true;
	}

	@Override
	public ItemStack getCraftingRemainingItem(ItemStack stack) {
		return stack.copy();
	}

	/**
	 * 與原版打火石相同的點火流程，但不呼叫 {@code hurtAndBreak}，因為這顆石頭不是耐久工具。
	 * 點燃營火／蠟燭時只改 {@code LIT}；點火時才用 {@link BaseFireBlock#getState} 取得火焰狀態。
	 */
	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		Level level = context.getLevel();
		BlockPos clickedPos = context.getClickedPos();
		BlockState clickedState = level.getBlockState(clickedPos);

		if (CampfireBlock.canLight(clickedState)
				|| CandleBlock.canLight(clickedState)
				|| CandleCakeBlock.canLight(clickedState)) {
			level.playSound(player, clickedPos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS,
					1.0F, level.getRandom().nextFloat() * 0.4F + 0.8F);
			level.setBlock(clickedPos,
					clickedState.setValue(BlockStateProperties.LIT, Boolean.TRUE), 11);
			level.gameEvent(player, GameEvent.BLOCK_CHANGE, clickedPos);
			return InteractionResult.sidedSuccess(level.isClientSide);
		}

		BlockPos firePos = clickedPos.relative(context.getClickedFace());
		if (BaseFireBlock.canBePlacedAt(level, firePos, context.getHorizontalDirection())) {
			level.playSound(player, firePos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS,
					1.0F, level.getRandom().nextFloat() * 0.4F + 0.8F);
			BlockState fireState = BaseFireBlock.getState(level, firePos);
			level.setBlock(firePos, fireState, 11);
			level.gameEvent(player, GameEvent.BLOCK_PLACE, clickedPos);
			return InteractionResult.sidedSuccess(level.isClientSide);
		}

		return InteractionResult.FAIL;
	}
}
