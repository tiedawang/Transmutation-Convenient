package com.transmutationconvenient.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 把原版的左鍵點擊入口 {@code Minecraft#startAttack} 開給自動連點使用。
 *
 * <p>用原版入口而不是直接呼叫 {@code gameMode.attack}，所以攻擊冷卻、揮空、
 * 方塊／實體判斷與原版完全一致；這裡不修改任何冷卻數值。
 */
@Mixin(Minecraft.class)
public interface MinecraftAttackInvoker {

	@Invoker("startAttack")
	boolean transmutationconvenient$startAttack();
}
