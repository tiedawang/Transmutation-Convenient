package com.transmutationconvenient.mixin;

import com.transmutationconvenient.TransmutationConvenientMod;
import com.transmutationconvenient.compat.ProjectEBridge;
import com.transmutationconvenient.jei.TransmutationConvenientJeiPlugin;
import com.transmutationconvenient.network.NetworkHandler;
import com.transmutationconvenient.util.SlotNeed;
import com.transmutationconvenient.util.TransferPlan;
import com.transmutationconvenient.util.WorkbenchKind;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.common.transfer.RecipeTransferService;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * JEI 的快速填入入口 —— 在「懸停提示」與「真正轉移」兩處攔截。
 *
 * <p>核心規則（逐個材料判斷，不是整批一刀切）：一件材料只要「背包裡有」<b>或</b>
 * 「已學轉化配方且 EMC 不少於它的單價」，對 JEI 來說就算「已有」。
 *
 * <ul>
 *   <li>缺的材料<b>全部</b>可買 → 懸停不報缺料、快速填入由我們接手（服務端用 EMC 補）。</li>
 *   <li>缺的材料裡<b>有買不了的</b> → 只把<b>那幾格</b>標紅，其餘（已學且買得起的）不標紅；
 *       快速填入不執行 —— 玩家先把買不了的那幾件準備好，再按 + 就能補齊其餘。</li>
 * </ul>
 *
 * <p>材料有替代品（標籤型配方）時：先挑背包裡已有的那一個；都沒持有才依 JEI 顯示順序
 * 挑第一個「已學且單價買得起」的替代品。若輪到它時 EMC 不夠（共享池，前面幾組先扣），
 * 就順位到下一個可買的替代品再判一次，直到找到買得起的或全部試完。
 *
 * <p>標紅用的錯誤由 JEI 的 {@code IRecipeTransferHandlerHelper} 構造
 * （{@link TransmutationConvenientJeiPlugin} 在 JEI 初始化時把 helper 存下來）。
 */
@Mixin(RecipeTransferService.class)
public class RecipeTransferServiceMixin {

	private static final String TRANSFER =
			"transferRecipe(Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;Lmezz/jei/api/gui/IRecipeLayoutDrawable;Lnet/minecraft/world/entity/player/Player;Z)Z";

	private static final String TRANSFER_ERROR =
			"getTransferRecipeError(Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;Lmezz/jei/api/gui/IRecipeLayoutDrawable;Lnet/minecraft/world/entity/player/Player;)Ljava/util/Optional;";

	/** 懸停：只把「買不了」的槽標紅，能買的不標紅。 */
	@Inject(method = TRANSFER_ERROR, at = @At("HEAD"), cancellable = true, remap = false)
	private void transmutationconvenient$markOnlyUnbuyable(AbstractContainerScreen<?> screen,
			IRecipeLayoutDrawable<?> recipeLayout, Player player,
			CallbackInfoReturnable<Optional<IRecipeTransferError>> cir) {
		WorkbenchKind kind = WorkbenchKind.of(screen.getMenu());
		if (kind == null) {
			return;
		}
		TransferPlan plan = buildPlan(player, recipeLayout.getRecipeSlotsView(), kind);
		if (plan == null || plan.missing().isEmpty()) {
			return;      // 不是目標配方，或背包本來就齊 → 原生
		}
		if (plan.unbuyable().isEmpty()) {
			cir.setReturnValue(Optional.empty());      // 全部可買 → 視為可轉移
			cir.cancel();
			return;
		}
		if (TransmutationConvenientJeiPlugin.HELPER == null) {
			return;      // 拿不到 helper → 讓 JEI 原生處理
		}
		cir.setReturnValue(Optional.of(TransmutationConvenientJeiPlugin.HELPER.createUserErrorForMissingSlots(
				Component.translatable("jei.tooltip.error.recipe.transfer.missing"), plan.unbuyable())));
		cir.cancel();
	}

	/** 執行：缺料全部可買 → 請服務端用 EMC 補；否則交給 JEI 原生。 */
	@Inject(method = TRANSFER, at = @At("HEAD"), cancellable = true, remap = false)
	private void transmutationconvenient$fillWithEmc(AbstractContainerScreen<?> screen,
			IRecipeLayoutDrawable<?> recipeLayout, Player player, boolean maxTransfer,
			CallbackInfoReturnable<Boolean> cir) {
		AbstractContainerMenu menu = screen.getMenu();
		WorkbenchKind kind = WorkbenchKind.of(menu);
		if (kind == null) {
			return;
		}
		TransferPlan plan = buildPlan(player, recipeLayout.getRecipeSlotsView(), kind);
		if (plan == null || plan.missing().isEmpty() || !plan.unbuyable().isEmpty()) {
			return;      // 背包齊全，或還有買不了的 → 交給 JEI 原生
		}
		TransmutationConvenientMod.LOGGER.debug("[便捷轉化] 缺料全部可用 EMC 補，請服務端接手：{}", kind);
		List<ItemStack> required = new ArrayList<>();
		for (SlotNeed need : plan.needed()) {
			required.add(need.stack());
		}
		NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.FillRequestPacket(kind, required));
		cir.setReturnValue(true);
		cir.cancel();
	}

	/**
	 * 完整判定一次：挑好每格要用的材料（含替代品順位）、算出缺料與買不了的格。
	 *
	 * <p>回傳 null 表示「不是我們要接手的配方」（槽數不合理）。
	 */
	private static TransferPlan buildPlan(Player player, IRecipeSlotsView slots, WorkbenchKind kind) {
		List<IRecipeSlotView> inputs = inputSlots(slots, kind);
		if (inputs == null) {
			return null;
		}
		List<List<ItemStack>> candidates = new ArrayList<>(inputs.size());
		for (IRecipeSlotView input : inputs) {
			candidates.add(candidatesOf(input));
		}
		List<List<Integer>> buyable = buyableCandidates(player, candidates);
		int[] chosen = pickCandidates(player, candidates, buyable, kind);

		List<SlotNeed> needed;
		List<Integer> missing;
		List<Integer> unbuyable;
		int iterations = 0;
		int maxIterations = 1;
		for (List<Integer> slotBuyable : buyable) {
			maxIterations += slotBuyable.size();
		}
		while (true) {
			needed = buildNeeded(inputs, candidates, chosen);
			missing = missingIndexes(player, needed, kind);
			unbuyable = unbuyableIndexes(player, needed, missing);
			if (unbuyable.isEmpty() || ++iterations > maxIterations) {
				break;
			}
			// 買不起（或當初挑到的替代品買不到）→ 該格順位到下一個可買的替代品再判一次
			boolean advanced = false;
			for (int index : unbuyable) {
				int next = nextBuyable(buyable.get(index), chosen[index]);
				if (next >= 0) {
					chosen[index] = next;
					advanced = true;
				}
			}
			if (!advanced) {
				break;
			}
		}
		List<SlotNeed> missingNeeds = new ArrayList<>(missing.size());
		for (int index : missing) {
			missingNeeds.add(needed.get(index));
		}
		List<IRecipeSlotView> unbuyableViews = new ArrayList<>(unbuyable.size());
		for (int index : unbuyable) {
			unbuyableViews.add(needed.get(index).view());
		}
		return new TransferPlan(needed, missingNeeds, unbuyableViews);
	}

	/**
	 * 只取 INPUT 槽（含空白槽，原版配方會建滿 9 格）。
	 *
	 * <p>槽數不一定是整個合成格：原版（含無序）一律 9 槽（空白槽也建）；
	 * 無盡重生的 JEI 分類只按配方實際用到的槽建（合成桌 shaped 建 width×height、
	 * shapeless 建材料數、終焉鍛造台固定 5 格）。槽數超過合成格數才視為不支援。
	 */
	private static List<IRecipeSlotView> inputSlots(IRecipeSlotsView slots, WorkbenchKind kind) {
		List<IRecipeSlotView> inputs = new ArrayList<>();
		for (IRecipeSlotView slot : slots.getSlotViews()) {
			if (slot.getRole() == RecipeIngredientRole.INPUT) {
				inputs.add(slot);
			}
		}
		return inputs.isEmpty() || inputs.size() > kind.gridSize ? null : inputs;
	}

	/** 一個材料格的全部替代品（JEI 顯示順序、去重、每種 1 個）。 */
	private static List<ItemStack> candidatesOf(IRecipeSlotView input) {
		List<ItemStack> candidates = new ArrayList<>();
		input.getItemStacks().forEach(stack -> {
			if (stack.isEmpty()) {
				return;
			}
			ItemStack one = stack.copyWithCount(1);
			for (ItemStack known : candidates) {
				if (ItemStack.isSameItemSameTags(known, one)) {
					return;
				}
			}
			candidates.add(one);
		});
		return candidates;
	}

	/** 每個材料格「已學轉化配方且單價買得起」的替代品索引（由小到大）。 */
	private static List<List<Integer>> buyableCandidates(Player player, List<List<ItemStack>> candidates) {
		List<List<Integer>> buyable = new ArrayList<>(candidates.size());
		for (List<ItemStack> slotCandidates : candidates) {
			List<Integer> indexes = new ArrayList<>();
			for (int i = 0; i < slotCandidates.size(); i++) {
				if (ProjectEBridge.canBuyOne(player, slotCandidates.get(i))) {
					indexes.add(i);
				}
			}
			buyable.add(indexes);
		}
		return buyable;
	}

	/**
	 * 每格先挑「背包裡已有」的替代品（不花 EMC），都沒持有才挑第一個可買的替代品。
	 *
	 * <p>「背包裡有」要按持有量分配：同一種材料佔多格時，後面那格不能重複算前面已用掉的份。
	 */
	private static int[] pickCandidates(Player player, List<List<ItemStack>> candidates,
			List<List<Integer>> buyable, WorkbenchKind kind) {
		List<ItemStack> held = collectAvailable(player, kind);
		List<ItemStack> taken = new ArrayList<>();
		int[] chosen = new int[candidates.size()];
		for (int index = 0; index < candidates.size(); index++) {
			List<ItemStack> slotCandidates = candidates.get(index);
			chosen[index] = -1;
			if (slotCandidates.isEmpty()) {
				continue;      // 空白格
			}
			for (int i = 0; i < slotCandidates.size(); i++) {
				ItemStack candidate = slotCandidates.get(i);
				if (countSame(taken, candidate) < countSame(held, candidate)) {
					chosen[index] = i;
					taken.add(candidate);
					break;
				}
			}
			if (chosen[index] >= 0) {
				continue;
			}
			List<Integer> slotBuyable = buyable.get(index);
			chosen[index] = slotBuyable.isEmpty() ? 0 : slotBuyable.get(0);
		}
		return chosen;
	}

	/** 該格下一個可買的替代品索引；沒有就回 -1。 */
	private static int nextBuyable(List<Integer> slotBuyable, int current) {
		for (int index : slotBuyable) {
			if (index > current) {
				return index;
			}
		}
		return -1;
	}

	private static List<SlotNeed> buildNeeded(List<IRecipeSlotView> inputs,
			List<List<ItemStack>> candidates, int[] chosen) {
		List<SlotNeed> needed = new ArrayList<>(inputs.size());
		for (int index = 0; index < inputs.size(); index++) {
			List<ItemStack> slotCandidates = candidates.get(index);
			int pick = chosen[index];
			ItemStack stack = pick >= 0 && pick < slotCandidates.size() ? slotCandidates.get(pick) : ItemStack.EMPTY;
			needed.add(new SlotNeed(stack, inputs.get(index)));
		}
		return needed;
	}

	/**
	 * 逐格比對玩家持有量，回傳「背包的持有量不夠」的格索引。
	 *
	 * <p>同一種材料在配方裡可能需要多格（例如 A×3），背包只有 1 個時不算三格都有：
	 * 按槽位順序分配，分不到的槽才算缺。
	 */
	private static List<Integer> missingIndexes(Player player, List<SlotNeed> needed, WorkbenchKind kind) {
		List<ItemStack> available = collectAvailable(player, kind);
		List<ItemStack> used = new ArrayList<>();
		List<Integer> missing = new ArrayList<>();
		for (int index = 0; index < needed.size(); index++) {
			ItemStack need = needed.get(index).stack();
			if (need.isEmpty()) {
				continue;
			}
			if (countSame(used, need) < countSame(available, need)) {
				used.add(need);
			} else {
				missing.add(index);
			}
		}
		return missing;
	}

	/**
	 * 缺料裡「買不了」的格索引 —— 按 EMC 單價由低到高逐組分配：沒學過、或輪到它時餘額已不夠的才算買不了。
	 *
	 * <p>只遍歷 {@code missing}（背包持有量不夠的槽）。<b>背包已有的材料根本不在這個清單裡</b>，
	 * 所以某一組買不起時，不會牽連到玩家已經備好的材料。
	 *
	 * <p>不是「單件買得起就算」：EMC 是共享池，買前面幾組會把餘額吃掉；輪到某組時餘額不足，
	 * 那幾格就要在懸停提示裡標紅，不能等玩家按下按鈕才用聊天框通知。
	 */
	private static List<Integer> unbuyableIndexes(Player player, List<SlotNeed> needed, List<Integer> missing) {
		List<ItemStack> stacks = new ArrayList<>(missing.size());
		for (int index : missing) {
			stacks.add(needed.get(index).stack());
		}
		Set<Integer> buyable = ProjectEBridge.buyableIndexes(player, stacks);
		List<Integer> unbuyable = new ArrayList<>();
		for (int i = 0; i < missing.size(); i++) {
			if (!buyable.contains(i)) {
				unbuyable.add(missing.get(i));
			}
		}
		return unbuyable;
	}

	/** 玩家背包 ＋ 目前合成格裡已有的物品（JEI 轉移前會先清空合成格、退回背包）。 */
	private static List<ItemStack> collectAvailable(Player player, WorkbenchKind kind) {
		List<ItemStack> available = new ArrayList<>();
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (!stack.isEmpty()) {
				available.add(stack);
			}
		}
		AbstractContainerMenu openMenu = player.containerMenu;
		if (openMenu != null) {
			int last = Math.min(kind.gridStart + kind.gridSize - 1, openMenu.slots.size() - 1);
			for (int slot = kind.gridStart; slot <= last; slot++) {
				ItemStack stack = openMenu.slots.get(slot).getItem();
				if (!stack.isEmpty()) {
					available.add(stack);
				}
			}
		}
		return available;
	}

	/**
	 * 把清單裡的物品按「同物品、同 NBT」合併計數。
	 *
	 * <p>不能改用 {@code HashMap<ItemStack, Integer>}：1.20.1 的 {@link ItemStack}
	 * 沒有覆寫 {@code equals}/{@code hashCode}（javap 已確認），以新實例當 key 查表永遠查不到，
	 * 會把所有材料都誤判成「背包沒有」。
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
}
