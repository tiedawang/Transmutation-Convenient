package com.transmutationconvenient.compat;

import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.capabilities.PECapabilities;
import moze_intel.projecte.api.proxy.IEMCProxy;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 等價交換（ProjectE）的橋接 —— 只碰它的 API，不碰實作。
 *
 * <p>「有足夠 EMC」與「已學習轉化配方」都由 {@link IKnowledgeProvider} 判斷：
 * <ul>
 *   <li>{@code hasKnowledge(stack)} —— 玩家學過這件物品的轉化配方。</li>
 *   <li>{@code getEmc()} —— 轉化桌網路中的 EMC 餘額（BigInteger）。</li>
 * </ul>
 * 單價則由 {@link IEMCProxy#getValue(ItemStack)} 查（兩側都可呼叫）。
 */
public final class ProjectEBridge {

	private ProjectEBridge() {
	}

	/** 便攜式轉化桌（ProjectE 的 transmutation_tablet）。 */
	private static final ResourceLocation PORTABLE_TABLE_ID = new ResourceLocation("projecte", "transmutation_tablet");

	public static boolean isLoaded() {
		return ModList.get().isLoaded("projecte");
	}

	private static IKnowledgeProvider knowledge(Player player) {
		return player.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY).orElse(null);
	}

	/** 這疊物品的 EMC 單價；沒有價值（或 ProjectE 不在）回傳 0。 */
	public static long valueOf(ItemStack stack) {
		if (!isLoaded() || stack.isEmpty()) {
			return 0L;
		}
		try {
			return IEMCProxy.INSTANCE.getValue(stack);
		} catch (RuntimeException e) {
			return 0L;
		}
	}

	/**
	 * 這疊物品的 EMC 出售價（{@code getSellValue}）—— 玩家把物品賣回轉化網路時拿到的 EMC。
	 *
	 * <p>與 {@link #valueOf} 的差別：{@code getValue} 是物品的轉化價值（買東西時付的價），
	 * {@code getSellValue} 是賣出時實際入帳的價（ProjectE 的出售規則可以與買價不同）。
	 * 自動販賣機是「賣」，所以用這一個。
	 */
	public static long sellValueOf(ItemStack stack) {
		if (!isLoaded() || stack.isEmpty()) {
			return 0L;
		}
		try {
			return IEMCProxy.INSTANCE.getSellValue(stack);
		} catch (RuntimeException e) {
			return 0L;
		}
	}

	/** 計算這批材料總價；有任何一件沒價值就回傳 null。 */
	public static BigInteger totalValue(List<ItemStack> stacks) {
		BigInteger total = BigInteger.ZERO;
		for (ItemStack stack : stacks) {
			long value = valueOf(stack);
			if (value <= 0) {
				return null;
			}
			total = total.add(BigInteger.valueOf(value));
		}
		return total;
	}

	/** 玩家是否已學習這批材料的轉化配方。 */
	public static boolean knowsAll(Player player, List<ItemStack> stacks) {
		IKnowledgeProvider provider = knowledge(player);
		if (provider == null) {
			return false;
		}
		for (ItemStack stack : stacks) {
			if (!provider.hasKnowledge(stack)) {
				return false;
			}
		}
		return true;
	}

	/** 這一件材料是否可以從轉化桌買：已學、有價值、且 EMC 不少於它的單價。 */
	public static boolean canBuyOne(Player player, ItemStack stack) {
		IKnowledgeProvider provider = knowledge(player);
		if (provider == null) {
			return false;
		}
		if (!provider.hasKnowledge(stack)) {
			return false;
		}
		long value = valueOf(stack);
		return value > 0 && provider.getEmc().compareTo(BigInteger.valueOf(value)) >= 0;
	}

	/**
	 * 按「物品種類」分組，**按 EMC 單價由低到高**逐組判斷 EMC 是否夠買「整組需求」。
	 *
	 * <p>規則（與使用者的定義一致）：
	 * <ol>
	 *   <li>先看最便宜的一組 A：A 的缺料槽數 × 單價 ≤ 餘額，才把 A 全部算作買得起，並扣掉這筆。</li>
	 *   <li>再用剩下的餘額判斷下一組 B 的全部需求；依此類推。</li>
	 *   <li>某一組<b>因為 EMC 不夠</b>買不起 → 這一組<b>以及之後所有組</b>都算買不起
	 *       （既然連最便宜的餘額都不夠，後面的不可能買得起）。</li>
	 *   <li>某一組<b>沒學轉化配方、或沒有 EMC 價格</b> → 只標紅這一組，<b>跳過</b>繼續判斷下一組 ——
	 *       「這一組買不了」不能證明「後面的買不起」，不能讓它封鎖後續。</li>
	 * </ol>
	 *
	 * <p><b>背包裡已有的材料不會受影響</b>：呼叫端傳入的 {@code missing} 只包含「背包持有量不夠」的槽，
	 * 已有材料的槽根本不在這個清單裡，自然不會被 blocked 牽連、也不會被標紅。
	 *
	 * <p>回傳的是「買得起」的槽位索引集合；其餘缺料槽在懸停提示裡標紅，不能等按下按鈕才用聊天框通知。
	 */
	public static Set<Integer> buyableIndexes(Player player, List<ItemStack> missing) {
		IKnowledgeProvider provider = knowledge(player);
		if (provider == null) {
			return Set.of();
		}
		BigInteger remaining = provider.getEmc();
		// 按物品種類分組，保留首次出現順序
		LinkedHashMap<ItemStack, List<Integer>> groups = new LinkedHashMap<>();
		for (int i = 0; i < missing.size(); i++) {
			ItemStack key = missing.get(i).copyWithCount(1);
			groups.computeIfAbsent(key, k -> new ArrayList<>()).add(i);
		}
		// 按 EMC 單價升序（便宜的優先判斷）；同價保持首次出現順序
		List<Map.Entry<ItemStack, List<Integer>>> ordered = new ArrayList<>(groups.entrySet());
		ordered.sort(Comparator.comparingLong(entry -> valueOf(entry.getKey())));
		Set<Integer> buyable = new LinkedHashSet<>();
		boolean blocked = false;
		for (Map.Entry<ItemStack, List<Integer>> entry : ordered) {
			if (blocked) {
				break;
			}
			ItemStack sample = entry.getKey();
			boolean learned = provider.hasKnowledge(sample);
			long value = valueOf(sample);
			int count = entry.getValue().size();
			if (!learned || value <= 0) {
				// 買不了（沒學／沒有 EMC 價格）：只標紅本組、不封鎖後續
				continue;
			}
			BigInteger cost = BigInteger.valueOf(value).multiply(BigInteger.valueOf(count));
			if (remaining.compareTo(cost) >= 0) {
				buyable.addAll(entry.getValue());
				remaining = remaining.subtract(cost);
			} else {
				blocked = true;      // 整組買不起 → 本組與之後全部買不起
			}
		}
		return buyable;
	}

	/** 玩家 EMC 是否足夠買下這批材料。 */
	public static boolean canAfford(Player player, List<ItemStack> stacks) {
		IKnowledgeProvider provider = knowledge(player);
		if (provider == null) {
			return false;
		}
		BigInteger total = totalValue(stacks);
		return total != null && provider.getEmc().compareTo(total) >= 0;
	}

	/** 便攜式轉化桌是否在玩家的物品欄裡（主背包＋快捷欄＋副手都算；裝甲格不影響）。 */
	public static boolean hasPortableTablet(Player player) {
		if (!isLoaded()) {
			return false;
		}
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (!stack.isEmpty() && PORTABLE_TABLE_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 把一件物品的轉化配方學起來 —— 只加知識，不動 EMC、也不消耗物品。
	 *
	 * @return true 表示這次真的多學了一件（之前沒學過、而且有 EMC 價格）
	 */
	public static boolean learn(Player player, ItemStack stack) {
		IKnowledgeProvider provider = knowledge(player);
		if (provider == null || stack.isEmpty() || provider.hasKnowledge(stack)) {
			return false;
		}
		if (valueOf(stack) <= 0) {
			return false;      // 沒有轉化配方（沒有 EMC 價格）的東西學不了
		}
		if (!provider.addKnowledge(stack)) {
			return false;
		}
		if (player instanceof ServerPlayer serverPlayer) {
			provider.syncKnowledgeChange(serverPlayer, ItemInfo.fromStack(stack), true);
		}
		return true;
	}

	/** 付 EMC 買下這批材料（呼叫前必須先通過 {@link #canAfford}）。 */
	public static boolean purchase(Player player, List<ItemStack> stacks) {
		IKnowledgeProvider provider = knowledge(player);
		if (provider == null) {
			return false;
		}
		BigInteger total = totalValue(stacks);
		if (total == null || provider.getEmc().compareTo(total) < 0) {
			return false;
		}
		provider.setEmc(provider.getEmc().subtract(total));
		if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
			provider.sync(serverPlayer);
		}
		return true;
	}
}
