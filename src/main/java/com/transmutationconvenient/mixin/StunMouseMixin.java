package com.transmutationconvenient.mixin;

import com.transmutationconvenient.client.StunClientState;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 僵直期間不讓滑鼠轉向。
 *
 * <p>在 {@code MouseHandler#turnPlayer} 的開頭取消，並把已累積的滑鼠位移清零，
 * 避免僵直結束後一次補轉一大段視角。
 */
@Mixin(MouseHandler.class)
public abstract class StunMouseMixin {

	@Shadow
	private double accumulatedDX;

	@Shadow
	private double accumulatedDY;

	@Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
	private void transmutationconvenient$lockView(CallbackInfo ci) {
		if (StunClientState.isStunned()) {
			this.accumulatedDX = 0.0D;
			this.accumulatedDY = 0.0D;
			ci.cancel();
		}
	}
}
