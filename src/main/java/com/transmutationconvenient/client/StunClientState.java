package com.transmutationconvenient.client;

/**
 * 客戶端僵直狀態。
 *
 * <p>只由伺服器的 {@code StunPacket} 設定；{@link StunClientEvents} 每刻消耗，
 * {@link com.transmutationconvenient.mixin.StunMouseMixin} 在還有僵直時取消滑鼠轉向。
 */
public final class StunClientState {

	private static int remainingTicks;
	private static float yaw;
	private static float pitch;

	private StunClientState() {
	}

	public static void begin(int ticks, float yaw, float pitch) {
		remainingTicks = Math.max(0, ticks);
		StunClientState.yaw = yaw;
		StunClientState.pitch = pitch;
	}

	public static void tick() {
		if (remainingTicks > 0) {
			remainingTicks--;
		}
	}

	public static boolean isStunned() {
		return remainingTicks > 0;
	}

	public static float yaw() {
		return yaw;
	}

	public static float pitch() {
		return pitch;
	}

	public static void clear() {
		remainingTicks = 0;
	}
}
