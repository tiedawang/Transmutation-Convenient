package com.transmutationconvenient.compat;

import com.transmutationconvenient.mixin.ArchangelSmiteInvoker;
import moze_intel.projecte.gameObjs.items.rings.ArchangelSmite;
import moze_intel.projecte.gameObjs.items.rings.HarvestGoddess;
import moze_intel.projecte.gameObjs.registries.PEBlocks;
import moze_intel.projecte.gameObjs.registries.PEItems;
import moze_intel.projecte.utils.WorldHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * Curios 兼容：把 ProjectE 的豐收女神戒指、禁止火把與大天使的懲戒變成可佩戴的飾品。
 *
 * <p>這個類別只在 Curios 已載入時由 {@code TransmutationConvenientMod} 呼叫，
 * 所以 Curios 缺席時不會載入任何 Curios 類別。
 */
public final class CuriosCompat {

	/** 禁止火把方塊形式的排斥範圍是方塊中心 ±8 格；佩戴時沿用同一個半徑。 */
	private static final double INTERDICTION_RADIUS = 8.0D;

	private CuriosCompat() {
	}

	/** 註冊三個 ProjectE 物品的 Curio 能力；對應的物品標籤另外放在 data/curios/tags/items。 */
	public static void register() {
		CuriosApi.registerCurio(PEItems.HARVEST_GODDESS_BAND.get(), new HarvestGoddessCurio());
		CuriosApi.registerCurio(PEBlocks.INTERDICTION_TORCH.asItem(), new InterdictionTorchCurio());
		CuriosApi.registerCurio(PEItems.ARCHANGEL_SMITE.get(), new ArchangelSmiteCurio());
	}

	/** 玩家身上是否戴著大天使的懲戒；客戶端與伺服器都可以查。 */
	public static boolean hasArchangel(Player player) {
		return !findArchangel(player).isEmpty();
	}

	/**
	 * 空手右鍵：用大天使的懲戒發射一支箭。
	 *
	 * <p>直接呼叫 ProjectE 原本的私有 {@code fireArrow}（透過 mixin invoker），
	 * 所以 EMC 消耗、HomingArrow、音效與發射角度都沿用原版 {@code use} 的行為。
	 */
	public static void fireArchangel(ServerPlayer player) {
		if (!player.getMainHandItem().isEmpty()) {
			return;
		}
		ItemStack stack = findArchangel(player);
		if (!(stack.getItem() instanceof ArchangelSmite smite)) {
			return;
		}
		((ArchangelSmiteInvoker) smite).transmutationconvenient$fireArrow(
				stack, player.level(), player, 1.0F);
	}

	/**
	 * 空手按 G：切換大天使的懲戒的 Active 模式。
	 *
	 * <p>直接呼叫 ProjectE 原本的 {@code changeMode}，所以音效、Active NBT 與
	 * 兩個模式的切換都沿用原版邏輯。
	 */
	public static void toggleArchangel(ServerPlayer player) {
		if (!player.getMainHandItem().isEmpty()) {
			return;
		}
		ItemStack stack = findArchangel(player);
		if (stack.getItem() instanceof ArchangelSmite smite) {
			smite.changeMode(player, stack, InteractionHand.MAIN_HAND);
		}
	}

	private static ItemStack findArchangel(Player player) {
		return CuriosApi.getCuriosInventory(player)
				.resolve()
				.flatMap(handler -> handler.findFirstCurio(PEItems.ARCHANGEL_SMITE.get()))
				.map(SlotResult::stack)
				.orElse(ItemStack.EMPTY);
	}

	/**
	 * 豐收女神戒指：直接呼叫 ProjectE 原本的 {@code inventoryTick}。
	 *
	 * <p>ProjectE 的戒指只會在熱鍵欄（slot 0～8）且實體是玩家時執行；
	 * 這裡傳入 slot 0 與 selected=false，讓它把 Curio 欄當成熱鍵欄來跑原本的
	 * 生長時間、EMC 消耗與 Active 狀態判斷。這樣不需要複製 ProjectE 的種植、
	 * 骨粉或 EMC 邏輯。
	 */
	private static final class HarvestGoddessCurio implements ICurioItem {

		@Override
		public boolean hasCurioCapability(ItemStack stack) {
			return true;
		}

		@Override
		public void curioTick(SlotContext slotContext, ItemStack stack) {
			if (!(slotContext.getWearer() instanceof Player player)) {
				return;
			}
			Level level = player.level();
			if (level.isClientSide()) {
				return;
			}
			if (stack.getItem() instanceof HarvestGoddess ring) {
				ring.inventoryTick(stack, level, player, 0, false);
			}
		}
	}

	/**
	 * 禁止火把：以玩家中心 ±8 格執行原版的禁止火把排斥。
	 *
	 * <p>直接呼叫 ProjectE 的 {@code WorldHelper.repelEntitiesInterdiction}，
	 * 所以原版的實體黑名單、敵對模式設定、生物與空中投射物的判斷、以及原本
	 * {@code 0.666... / (距離 + 0.1)} 的力度都保留。範圍與方塊形式相同。
	 */
	private static final class InterdictionTorchCurio implements ICurioItem {

		@Override
		public boolean hasCurioCapability(ItemStack stack) {
			return true;
		}

		@Override
		public void curioTick(SlotContext slotContext, ItemStack stack) {
			if (!(slotContext.getWearer() instanceof Player player)) {
				return;
			}
			Level level = player.level();
			if (level.isClientSide()) {
				return;
			}

			Vec3 center = player.getBoundingBox().getCenter();
			AABB area = new AABB(
					center.x - INTERDICTION_RADIUS,
					center.y - INTERDICTION_RADIUS,
					center.z - INTERDICTION_RADIUS,
					center.x + INTERDICTION_RADIUS,
					center.y + INTERDICTION_RADIUS,
					center.z + INTERDICTION_RADIUS);

			WorldHelper.repelEntitiesInterdiction(level, area, center.x, center.y, center.z);
		}
	}

	/**
	 * 大天使的懲戒：Active 模式每刻呼叫原版 {@code inventoryTick}。
	 *
	 * <p>{@code inventoryTick} 在 Active 為真時會呼叫原本的 {@code fireArrow}，
	 * 因此持續發射與自動追蹤都沿用 ProjectE；空手右鍵的單發箭由
	 * {@link #fireArchangel(ServerPlayer)} 另外處理。
	 */
	private static final class ArchangelSmiteCurio implements ICurioItem {

		@Override
		public boolean hasCurioCapability(ItemStack stack) {
			return true;
		}

		@Override
		public void curioTick(SlotContext slotContext, ItemStack stack) {
			if (!(slotContext.getWearer() instanceof ServerPlayer player)) {
				return;
			}
			if (stack.getItem() instanceof ArchangelSmite smite) {
				smite.inventoryTick(stack, player.level(), player, 0, false);
			}
		}
	}
}
