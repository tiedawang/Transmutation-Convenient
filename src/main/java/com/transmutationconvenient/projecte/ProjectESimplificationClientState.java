package com.transmutationconvenient.projecte;

/**
 * 客戶端看到的 ProjectE 精簡狀態。
 *
 * <p>只是一個給創造標籤過濾與介面用的開關；真正的配方表在伺服器端，
 * 客戶端透過 {@code ProjectESimplificationPacket} 收到狀態後重建創造標籤。
 */
public final class ProjectESimplificationClientState {

	private static volatile boolean enabled;

	private ProjectESimplificationClientState() {
	}

	public static boolean isEnabled() {
		return enabled;
	}

	public static void setEnabled(boolean value) {
		enabled = value;
	}
}
