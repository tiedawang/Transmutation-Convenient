package com.transmutationconvenient.util;

import com.transmutationconvenient.compat.ProjectEBridge;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 服務端的「快速填入」：把配方材料填進合成格，缺少的部分用轉化桌的 EMC 買。
 *
 * <p>流程（兩階段，先把所有條件驗完才動任何物品）：
 * <ol>
 *   <li>驗證工作台種類、合成格數量。</li>
 *   <li>算出背包能提供哪些、還缺哪些。</li>
 *   <li>缺的必須「已學習 + EMC 足夠」，否則整體失敗（不動任何物品）。</li>
 *   <li>清空合成格（舊物品退回背包）→ 從背包取材料 → 買缺的 → 放進合成格。</li>
 * </ol>
 */
public final class FillService {

	private FillService() {
	}

	/**
	 * 執行填入。
	 *
	 * @return null 表示成功；否則是要回給玩家的失敗原因。
	 */
	public static Component fill(ServerPlayer player, AbstractContainerMenu menu, WorkbenchKind kind, List<ItemStack> required) {
		if (WorkbenchKind.of(menu) != kind) {
			return Component.translatable("transmutationconvenient.message.workbench_mismatch");
		}
		if (required.isEmpty() || required.size() > kind.gridSize) {
			return Component.translatable("transmutationconvenient.message.bad_recipe");
		}

		// ---- 階段 1：計算背包可提供、缺哪些（不動物品） ----
		List<ItemStack> needs = new ArrayList<>(kind.gridSize);
		for (ItemStack stack : required) {
			needs.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
		}

		// 背包可提供、還缺哪些（合成格現有物品之後會退回背包，先不計入）。
		// 同一材料佔多格時要按持有量分配：先扣掉前面已分配的槽，分不到的才算缺
		// （只看「countInInventory > 0」會讓背包只有 1 個卻要 3 個的配方少買 2 個）。
		List<ItemStack> fromInventory = new ArrayList<>();
		List<ItemStack> missing = new ArrayList<>();
		for (ItemStack need : needs) {
			if (need.isEmpty()) {
				continue;
			}
			if (countSame(fromInventory, need) < countInInventory(player, need)) {
				fromInventory.add(need.copyWithCount(1));
			} else {
				missing.add(need);
			}
		}

		// 缺的材料必須可購買
		if (!missing.isEmpty()) {
			if (!ProjectEBridge.isLoaded()) {
				return Component.translatable("transmutationconvenient.message.no_projecte");
			}
			if (!ProjectEBridge.knowsAll(player, missing)) {
				return Component.translatable("transmutationconvenient.message.not_learned");
			}
			if (!ProjectEBridge.canAfford(player, missing)) {
				return Component.translatable("transmutationconvenient.message.not_enough_emc");
			}
		}

		// ---- 階段 2：清空合成格、移動、購買 ----
		// 合成格舊物品退回背包（背包滿則失敗）
		for (int i = 0; i < kind.gridSize; i++) {
			Slot slot = menu.slots.get(kind.gridStart + i);
			ItemStack old = slot.getItem();
			if (old.isEmpty()) {
				continue;
			}
			ItemStack copy = old.copy();
			slot.set(ItemStack.EMPTY);
			if (!player.getInventory().add(copy)) {
				// 放不回去：把已經退還的收回來，整體失敗
				return rollback(player, menu, kind);
			}
		}

		// 放入材料：優先從背包取，缺的直接買（第 i 個需求對應合成格第 i 格）
		for (int i = 0; i < needs.size(); i++) {
			ItemStack need = needs.get(i);
			if (need.isEmpty()) {
				continue;
			}
			boolean taken = takeFromInventory(player, need);
			menu.slots.get(kind.gridStart + i).set(taken ? need : need.copy());
		}
		if (!missing.isEmpty() && !ProjectEBridge.purchase(player, missing)) {
			// 理論上不會發生（階段 1 已驗證），仍保險處理
			return Component.translatable("transmutationconvenient.message.purchase_failed");
		}

		// 更新結果槽與同步
		menu.slotsChanged(menu.slots.get(kind.gridStart).container);
		menu.broadcastChanges();
		return null;
	}

	private static Component rollback(ServerPlayer player, AbstractContainerMenu menu, WorkbenchKind kind) {
		for (int i = 0; i < kind.gridSize; i++) {
			menu.slots.get(kind.gridStart + i).set(ItemStack.EMPTY);
		}
		menu.broadcastChanges();
		return Component.translatable("transmutationconvenient.message.inventory_full");
	}

	private static int countInInventory(ServerPlayer player, ItemStack need) {
		int count = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (!stack.isEmpty() && ItemStack.isSameItemSameTags(stack, need)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	/**
	 * 把清單裡的物品按「同物品、同 NBT」合併計數。
	 *
	 * <p>不能改用 {@code HashMap<ItemStack, Integer>}：1.20.1 的 {@link ItemStack}
	 * 沒有覆寫 {@code equals}/{@code hashCode}（javap 已確認），以新實例當 key 查表永遠查不到。
	 */
	private static int countSame(List<ItemStack> stacks, ItemStack target) {
		int count = 0;
		for (ItemStack stack : stacks) {
			if (!stack.isEmpty() && ItemStack.isSameItemSameTags(stack, target)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	private static boolean takeFromInventory(ServerPlayer player, ItemStack need) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (!stack.isEmpty() && ItemStack.isSameItemSameTags(stack, need)) {
				stack.shrink(1);
				return true;
			}
		}
		return false;
	}
}
