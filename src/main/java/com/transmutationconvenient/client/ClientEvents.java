package com.transmutationconvenient.client;

import com.transmutationconvenient.TransmutationConvenientMod;
import com.transmutationconvenient.network.NetworkHandler;
import com.transmutationconvenient.projecte.ProjectESimplificationClientState;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ContainerScreenEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
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

	/** 黑洞之桶的總量數字要畫在物品格之後，才不會被物品圖示遮住。 */
	@SubscribeEvent
	public static void onContainerForeground(ContainerScreenEvent.Render.Foreground event) {
		if (event.getContainerScreen() instanceof BlackHoleBucketScreen screen) {
			screen.renderStoredCounts(event.getGuiGraphics());
		}
	}

	/** 空手對空氣右鍵：請服務端用 Curios 槽裡的大天使的懲戒發射一支箭。 */
	@SubscribeEvent
	public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || event.getEntity() != minecraft.player) {
			return;
		}
		if (!minecraft.player.getMainHandItem().isEmpty()) {
			return;
		}
		NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.CurioUsePacket());
	}
}
