package com.transmutationconvenient.client;

import com.transmutationconvenient.TransmutationConvenientMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 玩家僵直的客戶端部分。
 *
 * <p>伺服器的 {@code StunPacket} 讓這裡在 4 刻內把移動輸入清零、把視角拉回
 * 僵直開始時的角度；滑鼠轉向本身由 {@code StunMouseMixin} 攔住。
 */
@Mod.EventBusSubscriber(modid = TransmutationConvenientMod.MOD_ID, value = Dist.CLIENT,
		bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StunClientEvents {

	private StunClientEvents() {
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onMovementInput(MovementInputUpdateEvent event) {
		if (!StunClientState.isStunned()) {
			return;
		}
		Input input = event.getInput();
		input.leftImpulse = 0.0F;
		input.forwardImpulse = 0.0F;
		input.jumping = false;
		input.shiftKeyDown = false;
		input.up = false;
		input.down = false;
		input.left = false;
		input.right = false;
	}

	@SubscribeEvent
	public static void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) {
			return;
		}
		if (StunClientState.isStunned()) {
			LocalPlayer player = Minecraft.getInstance().player;
			if (player != null) {
				player.setYRot(StunClientState.yaw());
				player.setXRot(StunClientState.pitch());
				player.setYHeadRot(StunClientState.yaw());
				player.setDeltaMovement(0.0D, player.getDeltaMovement().y, 0.0D);
				player.setSprinting(false);
			}
		}
		StunClientState.tick();
	}
}
