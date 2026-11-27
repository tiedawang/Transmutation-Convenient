package com.transmutationconvenient.client;

import com.transmutationconvenient.TransmutationConvenientMod;
import com.transmutationconvenient.item.OneSevenStoneEvents;
import com.transmutationconvenient.mixin.MinecraftAttackInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 連擊石的每刻自動連點。
 *
 * <p>只有同時滿足以下條件才會作用：
 * <ul>
 *   <li>連擊石在 Curios、快捷欄或主副手；</li>
 *   <li>玩家按著左鍵；</li>
 *   <li>準心命中非方塊、非空氣的實體；</li>
 *   <li>沒有開啟介面、不是旁觀者、手沒有被使用中。</li>
 * </ul>
 *
 * <p>每客戶端刻呼叫一次原版的 {@code Minecraft#startAttack}；**不碰玩家的攻擊冷卻**。
 * 原版在冷卻未滿時本來就不會送出攻擊，因此這就是把「按住左鍵」模擬成每刻一次的點擊，
 * 冷卻、揮空、方塊／實體判斷與傷害縮放全部沿用原版。
 */
@Mod.EventBusSubscriber(modid = TransmutationConvenientMod.MOD_ID, value = Dist.CLIENT,
		bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class OneSevenStoneAutoAttack {

	private OneSevenStoneAutoAttack() {
	}

	@SubscribeEvent
	public static void onClientTick(TickEvent.ClientTickEvent event) {
		if (event.phase != TickEvent.Phase.END) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.screen != null || minecraft.player == null || minecraft.level == null
				|| minecraft.gameMode == null) {
			return;
		}
		if (!minecraft.options.keyAttack.isDown()
				|| minecraft.player.isSpectator()
				|| minecraft.player.isHandsBusy()
				|| StunClientState.isStunned()
				|| !OneSevenStoneEvents.isActive(minecraft.player)) {
			return;
		}
		if (!(minecraft.hitResult instanceof EntityHitResult hit)) {
			return;
		}
		Entity target = hit.getEntity();
		if (target == minecraft.player || !target.isAttackable() || !target.isAlive()) {
			return;
		}
		((MinecraftAttackInvoker) minecraft).transmutationconvenient$startAttack();
	}
}
