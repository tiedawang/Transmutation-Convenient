package com.transmutationconvenient.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;

/**
 * 支援的工作台種類 —— 只靠選單的註冊名辨識（不依賴各模組的類）。
 *
 * <p>slot 佈局有兩種：
 * <ul>
 *   <li>原版工作台與無盡重生的四張合成桌：slot 0 = 產物、slot 1 起是合成格；</li>
 *   <li>無盡重生的終焉鍛造台（{@code ItemCombinerMenu}）：slot 0 起是五個材料格，產物在 slot 5。</li>
 * </ul>
 * 所以「產物格在哪、材料格從哪開始」由各 enum 自己帶。
 */
public enum WorkbenchKind {

	/** 原版工作台：3×3。 */
	VANILLA("minecraft:crafting", 0, 1, 9),

	/** 伏聆工作台：3×3。 */
	AVARITIA_SCULK("avaritia:sculk_crafting_tile_table", 0, 1, 9),

	/** 煉獄工作台：5×5。 */
	AVARITIA_NETHER("avaritia:nether_crafting_tile_table", 0, 1, 25),

	/** 終末工作台：7×7。 */
	AVARITIA_END("avaritia:end_crafting_tile_table", 0, 1, 49),

	/** 終焉工作台：9×9。 */
	AVARITIA_EXTREME("avaritia:extreme_crafting_table", 0, 1, 81),

	/** 終焉鍛造台：材料格 slot 0～4（模板、基底、三個添加物），產物在 slot 5。 */
	AVARITIA_SMITHING("avaritia:extreme_smithing_table", 5, 0, 5);

	/** 選單的註冊名（registry key）。 */
	public final String menuId;

	/** 產物格。 */
	public final int resultSlot;

	/** 材料（合成）格的第一格。 */
	public final int gridStart;

	/** 材料（合成）格數量。 */
	public final int gridSize;

	WorkbenchKind(String menuId, int resultSlot, int gridStart, int gridSize) {
		this.menuId = menuId;
		this.resultSlot = resultSlot;
		this.gridStart = gridStart;
		this.gridSize = gridSize;
	}

	/**
	 * 依選單判斷是哪一種工作台；不是支援的工作台就回傳 null。
	 *
	 * <p>{@link AbstractContainerMenu#getType()} 對「沒有註冊選單型別的選單」會直接拋
	 * {@link UnsupportedOperationException}（其他模組的自訂選單常見）。
	 * 這裡視為「不是目標工作台」，不能讓例外冒到 JEI 的 GUI 事件裡。
	 */
	@Nullable
	public static WorkbenchKind of(AbstractContainerMenu menu) {
		ResourceLocation id;
		try {
			id = BuiltInRegistries.MENU.getKey(menu.getType());
		} catch (RuntimeException e) {
			return null;
		}
		if (id == null) {
			return null;
		}
		String name = id.toString();
		for (WorkbenchKind kind : values()) {
			if (kind.menuId.equals(name)) {
				return kind;
			}
		}
		return null;
	}
}
