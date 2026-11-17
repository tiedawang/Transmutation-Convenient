package com.transmutationconvenient.projecte;

import com.transmutationconvenient.TransmutationConvenientMod;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 精簡模式下，把被移除的 ProjectE 物品從所有創造標籤中拿掉。
 *
 * <p>用 {@link EventPriority#LOWEST} 是最後才執行：ProjectE 自己會把盔甲、收集器
 * 加進它的標籤與原版標籤，等它加完之後再統一移除。這樣就不用碰
 * {@code CreativeModeTab} 的私有欄位，也不怕不同載入順序。
 */
@Mod.EventBusSubscriber(modid = TransmutationConvenientMod.MOD_ID,
		bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ProjectESimplificationCreativeTabEvents {

	private ProjectESimplificationCreativeTabEvents() {
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
		if (!ProjectESimplificationClientState.isEnabled()) {
			return;
		}

		List<ItemStack> toRemove = new ArrayList<>();
		for (Map.Entry<ItemStack, CreativeModeTab.TabVisibility> entry : event.getEntries()) {
			if (ProjectESimplificationService.isDisabledItem(entry.getKey().getItem())) {
				toRemove.add(entry.getKey());
			}
		}
		for (ItemStack stack : toRemove) {
			event.getEntries().remove(stack);
		}
	}
}
