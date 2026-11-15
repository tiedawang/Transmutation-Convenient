package com.transmutationconvenient.client;

import com.transmutationconvenient.TransmutationConvenientMod;
import com.transmutationconvenient.bucket.BlackHoleBucketItem;
import com.transmutationconvenient.network.NetworkHandler;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * 客戶端：回車鍵切換黑洞之桶的模式。
 */
@Mod.EventBusSubscriber(modid = TransmutationConvenientMod.MOD_ID, value = Dist.CLIENT)
public final class BlackHoleBucketClient {

	public static final KeyMapping CYCLE_MODE = new KeyMapping(
			"key.transmutationconvenient.cycle_mode",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_ENTER,
			"key.categories.transmutationconvenient");

	private BlackHoleBucketClient() {
	}

	@SubscribeEvent
	public static void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.START) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		while (CYCLE_MODE.consumeClick()) {
			if (minecraft.screen != null || minecraft.player == null) {
				continue;
			}
			if (!BlackHoleBucketItem.isHeldBy(minecraft.player)) {
				continue;
			}
			NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.CycleModePacket());
		}
	}
}
