package com.transmutationconvenient.client;

import com.transmutationconvenient.TransmutationConvenientMod;
import com.transmutationconvenient.projecte.ProjectESimplificationClientState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 客戶端連線生命週期：斷線時清掉上一個世界的 ProjectE 精簡狀態。 */
@Mod.EventBusSubscriber(modid = TransmutationConvenientMod.MOD_ID, value = Dist.CLIENT,
		bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientEvents {

	private ClientEvents() {
	}

	@SubscribeEvent
	public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
		ProjectESimplificationClientState.setEnabled(false);
	}
}
