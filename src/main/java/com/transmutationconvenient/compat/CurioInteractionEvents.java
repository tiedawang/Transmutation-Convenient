package com.transmutationconvenient.compat;

import com.transmutationconvenient.TransmutationConvenientMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 空手右鍵方塊／實體時，讓 Curios 槽裡的大天使的懲戒接手。
 *
 * <p>右鍵空氣沒有服務端事件，客戶端另外送 {@code CurioUsePacket}；這裡只處理
 * 服務端看得到的方塊與實體互動。只有主手是空、而且確實戴著大天使的懲戒時
 * 才取消原本的互動並發射箭。
 */
@Mod.EventBusSubscriber(modid = TransmutationConvenientMod.MOD_ID)
public final class CurioInteractionEvents {

	private CurioInteractionEvents() {
	}

	@SubscribeEvent
	public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
		handle(event);
	}

	@SubscribeEvent
	public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
		handle(event);
	}

	private static void handle(PlayerInteractEvent event) {
		if (event.getLevel().isClientSide()) {
			return;
		}
		if (!(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}
		if (!player.getMainHandItem().isEmpty()) {
			return;
		}
		if (!CurioActions.hasArchangel(player)) {
			return;
		}
		CurioActions.fireArchangel(player);
		event.setCanceled(true);
		event.setCancellationResult(InteractionResult.SUCCESS);
	}
}
