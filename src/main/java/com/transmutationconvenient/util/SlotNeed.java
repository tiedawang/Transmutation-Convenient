package com.transmutationconvenient.util;

import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import net.minecraft.world.item.ItemStack;

/**
 * 配方的一格：材料 + 它在 JEI 配方介面裡的槽位視圖（標紅需要後者）。
 *
 * <p>不能放在 {@code com.transmutationconvenient.mixin} 套件 —— Mixin 會把該套件下所有類
 * 視為 mixin 資產，普通類被直接引用會拋 {@code IllegalClassLoadError}。
 */
public record SlotNeed(ItemStack stack, IRecipeSlotView view) {
}
