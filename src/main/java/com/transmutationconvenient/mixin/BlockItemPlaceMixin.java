package com.transmutationconvenient.mixin;

import com.transmutationconvenient.machine.AutoVendingMachineBlock;
import com.transmutationconvenient.pipe.EmcPriority;
import com.transmutationconvenient.pipe.EmcTableOwners;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 記下「誰放了會把 EMC 記到主人帳戶上的方塊」—— 轉化桌與自動販賣機。
 *
 * <p>Forge 1.20.1 的 {@code BlockEvent.EntityPlaceEvent} 只有終界使者、寒冰行者
 * 那類實體放置方塊時才會觸發，玩家拿方塊物品放下方塊並不會經過它 ——
 * 所以改在 {@link BlockItem#place} 的回傳點攔一次：放下去的是轉化桌或自動販賣機、
 * 而且確實放置成功，就把放置者記到 {@code TableOwnerData} 裡。
 *
 * <p>只在服務端記、且玩家存在時才記；發射器之類沒有玩家的放置不會有紀錄，
 * 這些方塊之後由「方塊附近的最近玩家」退回認主。
 */
@Mixin(BlockItem.class)
public abstract class BlockItemPlaceMixin {

	@Inject(
			method = "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;",
			at = @At("RETURN"))
	private void transmutationconvenient$recordOwner(BlockPlaceContext context,
			CallbackInfoReturnable<InteractionResult> cir) {
		InteractionResult result = cir.getReturnValue();
		if (result == null || !result.consumesAction()) {
			return;
		}
		Level level = context.getLevel();
		if (level.isClientSide() || !(context.getPlayer() instanceof ServerPlayer player)) {
			return;
		}
		BlockPos pos = context.getClickedPos();
		BlockState state = level.getBlockState(pos);
		if (!EmcPriority.isTransmutationTable(state) && !(state.getBlock() instanceof AutoVendingMachineBlock)) {
			return;
		}
		EmcTableOwners.record(level, pos, player);
	}
}
