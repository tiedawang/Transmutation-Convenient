package com.transmutationconvenient.bucket;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * 黑洞之桶的三種模式。
 *
 * <p>切換順序固定為：吸引物品 → 吸引液體 → 不可控吸引 → 吸引物品。
 * 模式存在物品 NBT 的 {@code Mode} 字串裡（見 {@code BlackHoleBucketItem}）。
 *
 * <p>物品名稱下面那一行狀態字：前兩種用紫色，不可控吸引用紅色。
 */
public enum BucketMode {

	ITEMS("items", "item.transmutationconvenient.black_hole_bucket.mode.items", ChatFormatting.LIGHT_PURPLE),
	FLUIDS("fluids", "item.transmutationconvenient.black_hole_bucket.mode.fluids", ChatFormatting.LIGHT_PURPLE),
	UNCONTROLLED("uncontrolled", "item.transmutationconvenient.black_hole_bucket.mode.uncontrolled", ChatFormatting.RED);

	private final String id;
	private final String translationKey;
	private final ChatFormatting color;

	BucketMode(String id, String translationKey, ChatFormatting color) {
		this.id = id;
		this.translationKey = translationKey;
		this.color = color;
	}

	public String getId() {
		return id;
	}

	public BucketMode next() {
		BucketMode[] values = values();
		return values[(ordinal() + 1) % values.length];
	}

	/** 物品名稱下面那一行（已帶顏色）。 */
	public Component displayName() {
		return Component.translatable(translationKey).withStyle(color);
	}

	public static BucketMode byId(String id) {
		for (BucketMode mode : values()) {
			if (mode.id.equals(id)) {
				return mode;
			}
		}
		return ITEMS;
	}
}
