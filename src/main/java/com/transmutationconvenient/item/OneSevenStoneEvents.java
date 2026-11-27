package com.transmutationconvenient.item;

import com.transmutationconvenient.TransmutationConvenientMod;
import com.transmutationconvenient.compat.CurioActions;
import com.transmutationconvenient.effect.AncientCurseEffect;
import com.transmutationconvenient.registry.ModEffects;
import com.transmutationconvenient.registry.ModItems;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.PotionColorCalculationEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 連擊石的兩個效果：
 *
 * <ul>
 *   <li>佩戴在 Curios 的吊墜／護符、放在快捷欄，或拿在主副手時，玩家攻擊會給目標
 *       3 秒的「遠古版本的詛咒」；</li>
 *   <li>攻擊命中的目標會額外僵直 0.2 秒：不能移動、攻擊或轉動視角；
 *       僵直不是藥水效果，而是由 {@link StunManager} 的短時間狀態表控制；</li>
 *   <li>職業等級為大師的村民死亡時，固定掉落一個 連擊石，不受掠奪附魔影響。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = TransmutationConvenientMod.MOD_ID,
		bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class OneSevenStoneEvents {

	/** 3 秒 = 60 刻。 */
	private static final int CURSE_DURATION = 60;
	private static final int MASTER_VILLAGER_LEVEL = 5;

	private OneSevenStoneEvents() {
	}

	@SubscribeEvent
	public static void onAttack(AttackEntityEvent event) {
		Player player = event.getEntity();
		if (player.level().isClientSide()) {
			return;
		}
		// 被 連擊石僵直的玩家不能攻擊；這裡是伺服器端的權威判斷。
		if (StunManager.isStunned(player)) {
			event.setCanceled(true);
		}
	}

	/**
	 * 只有實際造成傷害時才上詛咒與僵直。
	 *
	 * <p>不能掛在 {@code AttackEntityEvent}：那個事件在「揮空但碰撞箱有對到」時也會發，
	 * 而目標被僵直時整個 tick 被取消、{@code invulnerableTime} 不會下降，會形成
	 * 「每點一下都刷新僵直，目標永遠無敵」的迴圈。{@code LivingHurtEvent} 已經過
	 * 無敵時間與傷害結算，只有在真的會受傷時才觸發。
	 */
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onLivingHurt(LivingHurtEvent event) {
		LivingEntity target = event.getEntity();
		if (target.level().isClientSide() || event.isCanceled() || !target.isAlive()) {
			return;
		}
		Entity source = event.getSource().getEntity();
		// 只認玩家近戰：投射物的直接來源不是玩家，維持原本的近戰觸發範圍。
		if (!(source instanceof Player player) || event.getSource().getDirectEntity() != player) {
			return;
		}
		if (!isActive(player)) {
			return;
		}
		target.addEffect(new MobEffectInstance(
				ModEffects.ANCIENT_CURSE.get(), CURSE_DURATION, 0, false, true, true), player);
		StunManager.stun(target, StunManager.STUN_TICKS);
	}

	@SubscribeEvent
	public static void onLivingTick(LivingEvent.LivingTickEvent event) {
		LivingEntity entity = event.getEntity();
		if (entity.level().isClientSide()) {
			return;
		}
		if (!StunManager.applyLock(entity)) {
			return;
		}
		// 玩家要保留連線與背包 tick，不能整段取消；非玩家直接跳過這一刻的所有
		// AI、移動、攻擊與視角更新。
		if (!(entity instanceof Player)) {
			event.setCanceled(true);
		}
	}

	/** 被僵直的生物（或牠發出的投射物）不能造成傷害。 */
	@SubscribeEvent
	public static void onLivingAttack(LivingAttackEvent event) {
		if (event.getEntity().level().isClientSide()) {
			return;
		}
		if (isStunnedSource(event.getSource().getEntity())
				|| isStunnedSource(event.getSource().getDirectEntity())) {
			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public static void onLivingDeath(LivingDeathEvent event) {
		StunManager.clear(event.getEntity());
	}

	@SubscribeEvent
	public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
		StunManager.clear(event.getEntity());
	}

	/** 大師村民死亡時固定加一個掉落物；這裡不看掠奪等級，所以永遠只有一個。 */
	@SubscribeEvent
	public static void onLivingDrops(LivingDropsEvent event) {
		if (!(event.getEntity() instanceof Villager villager)) {
			return;
		}
		if (villager.getVillagerData().getLevel() < MASTER_VILLAGER_LEVEL) {
			return;
		}
		ItemEntity drop = new ItemEntity(
				villager.level(),
				villager.getX(),
				villager.getY(),
				villager.getZ(),
				new ItemStack(ModItems.ONE_SEVEN_STONE.get()));
		drop.setDefaultPickUpDelay();
		event.getDrops().add(drop);
	}

	/** 有詛咒時把實體的藥水粒子顏色固定成紫色，避免被其他效果混色。 */
	@SubscribeEvent
	public static void onPotionColor(PotionColorCalculationEvent event) {
		if (event.getEntity().hasEffect(ModEffects.ANCIENT_CURSE.get())) {
			event.setColor(AncientCurseEffect.COLOR);
		}
	}

	/**
	 * 判斷玩家是否帶著 連擊石：主手、副手、快捷欄 0～8，或 Curios 的吊墜／護符。
	 *
	 * <p>Curios 是選填依賴，查詢包在 {@link CurioActions} 的反射入口裡；
	 * 沒裝 Curios 時只檢查背包與手部。
	 */
	private static boolean isStunnedSource(Entity source) {
		return source instanceof LivingEntity living && StunManager.isStunned(living);
	}

	public static boolean isActive(Player player) {
		Item stone = ModItems.ONE_SEVEN_STONE.get();
		if (player.getMainHandItem().is(stone) || player.getOffhandItem().is(stone)) {
			return true;
		}
		for (int slot = 0; slot < 9; slot++) {
			if (player.getInventory().getItem(slot).is(stone)) {
				return true;
			}
		}
		return CurioActions.hasOneSevenStone(player);
	}
}
