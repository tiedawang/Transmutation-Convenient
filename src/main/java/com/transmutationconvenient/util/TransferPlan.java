package com.transmutationconvenient.util;

import mezz.jei.api.gui.ingredient.IRecipeSlotView;

import java.util.List;

/**
 * 一次快速填入的判定結果。
 *
 * <p>{@code needed} 是「每格 1 個」的實際材料清單（已從 JEI 顯示的替代品裡挑好）；
 * {@code missing} 是背包持有量不夠的格；{@code unbuyable} 是缺料裡「買不了」的格（悬停要標紅的那些）。
 *
 * <p>這個類會被 mixin（客戶端）引用，放在 {@code util} 是為了避開 mixin 套件不能放普通類的限制。
 */
public record TransferPlan(List<SlotNeed> needed, List<SlotNeed> missing, List<IRecipeSlotView> unbuyable) {
}
