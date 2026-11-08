package com.transmutationconvenient.compat;

import com.transmutationconvenient.TransmutationConvenientMod;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * 便攜式轉化桌（ProjectE 的 {@code projecte:transmutation_tablet}）在物品欄裡時，
 * 任何物品一進入背包就自動學會它的轉化配方（只加知識，不賣掉、不動 EMC），
 * 並在聊天欄提示「已學習&lt;物品名&gt;」。
 *
 * <p>背包沒有「物品進入」的事件，所以用玩家 tick 掃描整個物品欄（主背包＋快捷欄＋副手）——
 * 這是唯一能覆蓋撿拾、合成、機器輸出、指令等所有取得途徑的做法；已學過的物品
 * {@code hasKnowledge} 直接跳過，不會重複學習、也不會重複提示。
 *
 * <p>首次裝上模組或第一次拿到便攜式轉化桌時，背包裡已有的未學物品也會立刻補學並逐件提示
 * ——玩家能直接看到模組生效。
 */
@Mod.EventBusSubscriber(modid = TransmutationConvenientMod.MOD_ID)
public final class PortableTabletLearner {

	private PortableTabletLearner() {
	}

	@SubscribeEvent
	public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
		if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
			return;
		}
		if (!ProjectEBridge.hasPortableTablet(player)) {
			return;
		}
		for (ItemStack stack : learnNewItems(player)) {
			player.sendSystemMessage(Component.translatable(
					"transmutationconvenient.message.learned", stack.getHoverName()));
		}
	}

	/** 掃描整個物品欄，把還沒學過、但有 EMC 價格的物品學起來；回傳這次新學到的（每格 1 個的副本）。 */
	public static List<ItemStack> learnNewItems(ServerPlayer player) {
		List<ItemStack> learned = new ArrayList<>();
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			if (ProjectEBridge.learn(player, stack)) {
				learned.add(stack.copyWithCount(1));
			}
		}
		return learned;
	}
}
