package com.transmutationconvenient.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;

/**
 * 客戶端創造標籤重建。
 *
 * <p>ProjectE 的精簡狀態改變時，不能用「只改目前開啟的介面」的做法；必須把每個
 * {@link CreativeModeTab} 重新 build 一次，讓 Forge 的建構事件與
 * {@code CreativeModeTabMixin} 的過濾重新跑。這樣 ProjectE 自己的標籤、原版的
 * 戰鬥／功能方塊標籤、搜尋標籤都會一起更新。
 */
public final class ClientCreativeTabs {

	private ClientCreativeTabs() {
	}

	public static void rebuild() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null) {
			return;
		}

		CreativeModeTab.ItemDisplayParameters parameters =
				new CreativeModeTab.ItemDisplayParameters(
						minecraft.level.enabledFeatures(),
						minecraft.player.hasPermissions(2),
						minecraft.level.registryAccess());
		for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
			tab.buildContents(parameters);
		}
	}
}
