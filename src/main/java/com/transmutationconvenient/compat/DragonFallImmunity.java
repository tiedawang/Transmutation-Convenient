package com.transmutationconvenient.compat;

import com.transmutationconvenient.TransmutationConvenientMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 龍騎乘摔落免疫。
 *
 * <p>這是一個事件驅動的一次性旗標，不是實體屬性，也不是每個遊戲刻輪詢：
 *
 * <ol>
 *   <li>玩家按著潛行鍵從冰火傳說（原版或社區版）的龍上下來時，把玩家資料裡的
 *       「剛下龍」旗標設為真；</li>
 *   <li>玩家落地、遊戲發出摔落事件時，如果旗標為真，就把該次摔落傷害倍率設為零，
 *       並立刻把旗標設回假；</li>
 *   <li>旗標只會被摔落事件讀取一次，所以一次下龍只免疫一次摔落。</li>
 * </ol>
 *
 * <p>旗標直接存在玩家的持久化資料裡，跟著玩家存檔；沒有自訂屬性、沒有修飾符，
 * 也沒有伺服器刻輪詢。下龍後如果沒有觸發摔落傷害（例如落在水裡或在地面下龍），
 * 旗標會保留到下一次摔落事件，這與原版行為一致。
 */
@Mod.EventBusSubscriber(modid = TransmutationConvenientMod.MOD_ID)
public final class DragonFallImmunity {

	/** 玩家持久化資料中的「剛下龍」旗標名稱。 */
	private static final String JUST_DISMOUNTED_DRAGON =
			TransmutationConvenientMod.MOD_ID + ":just_dismounted_dragon";

	private DragonFallImmunity() {
	}

	/**
	 * 在玩家從龍上下來的那一刻寫入旗標。
	 *
	 * <p>Forge 的騎乘事件在實際解除騎乘之前發出，因此這時玩家還看得到坐騎，
	 * 也還讀得到潛行鍵狀態。只處理伺服器端玩家，並且只認冰火傳說的龍；
	 * 其他坐騎不會設定這個旗標。
	 */
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onDismount(EntityMountEvent event) {
		if (!event.isDismounting() || event.isCanceled()) {
			return;
		}
		if (!(event.getEntityMounting() instanceof ServerPlayer player)) {
			return;
		}

		Entity mount = event.getEntityBeingMounted();
		if (mount == null || !isIceAndFireDragon(mount) || !player.isShiftKeyDown()) {
			return;
		}

		// 原版只讓控制乘客設定旗標；若坐騎沒有回報控制乘客，仍以事件中的
		// 下龍玩家為準，避免不同版本的龍實作讓整個功能失效。
		Entity controller = mount.getControllingPassenger();
		if (controller != null && controller != player) {
			return;
		}

		player.getPersistentData().putBoolean(JUST_DISMOUNTED_DRAGON, true);
	}

	/**
	 * 在摔落傷害結算時消耗旗標。
	 *
	 * <p>這裡只把傷害倍率設為零，不取消事件；落地流程照常走完，但這次傷害是零。
	 * 設完倍率後立刻把旗標設回假，所以下一次摔落不會再被免疫。
	 */
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onFall(LivingFallEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer player)) {
			return;
		}

		CompoundTag data = player.getPersistentData();
		if (!data.getBoolean(JUST_DISMOUNTED_DRAGON)) {
			return;
		}

		event.setDamageMultiplier(0.0F);
		data.putBoolean(JUST_DISMOUNTED_DRAGON, false);
	}

	/**
	 * 判斷坐騎是不是冰火傳說的龍。
	 *
	 * <p>用實體註冊名稱判斷，不依賴冰火傳說的類別，也不新增編譯依賴；原版與
	 * 社區版的龍實體都使用 {@code iceandfire} 命名空間，路徑都含有 {@code dragon}。
	 */
	private static boolean isIceAndFireDragon(Entity entity) {
		ResourceLocation id = EntityType.getKey(entity.getType());
		return id != null
				&& "iceandfire".equals(id.getNamespace())
				&& id.getPath().contains("dragon");
	}
}
