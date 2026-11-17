package com.transmutationconvenient.projecte;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.Set;

/**
 * 精簡模式要處理的 ProjectE 物品 id。
 *
 * <p>這個類別刻意不碰伺服器類別，讓 {@code NBTManagerMixin} 在客戶端也能安全載入。
 */
public final class ProjectESimplificationContent {

	private static final Set<ResourceLocation> DISABLED_IDS = Set.of(
			id("dm_helmet"), id("dm_chestplate"), id("dm_leggings"), id("dm_boots"),
			id("rm_helmet"), id("rm_chestplate"), id("rm_leggings"), id("rm_boots"),
			id("gem_helmet"), id("gem_chestplate"), id("gem_leggings"), id("gem_boots"),
			id("collector_mk1"), id("collector_mk2"), id("collector_mk3"),
			id("rm_katar"), id("rm_morning_star"));

	private ProjectESimplificationContent() {
	}

	public static Set<ResourceLocation> disabledIds() {
		return DISABLED_IDS;
	}

	public static boolean isDisabledId(ResourceLocation id) {
		return DISABLED_IDS.contains(id);
	}

	public static boolean isDisabledItem(Item item) {
		ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
		return id != null && DISABLED_IDS.contains(id);
	}

	private static ResourceLocation id(String path) {
		return new ResourceLocation("projecte", path);
	}
}
