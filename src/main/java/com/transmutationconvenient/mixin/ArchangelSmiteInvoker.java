package com.transmutationconvenient.mixin;

import moze_intel.projecte.gameObjs.items.rings.ArchangelSmite;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 把 ProjectE 大天使的懲戒的私有 {@code fireArrow} 暴露給 Curios 兼容層。
 *
 * <p>{@code ArchangelSmite.use} 會先從玩家手上取得物品，再把該物品交給
 * {@code fireArrow}；飾品不在手上，所以不能直接呼叫 {@code use}。這裡只
 * 增加一個 invoker，讓 Curios 版本能把手上的戒指物品直接交給同一個
 * {@code fireArrow}，不複製 ProjectE 的箭矢、音效或 EMC 邏輯。
 */
@Mixin(ArchangelSmite.class)
public interface ArchangelSmiteInvoker {

	@Invoker("fireArrow")
	void transmutationconvenient$fireArrow(ItemStack stack, Level level, LivingEntity entity, float velocity);
}
