package com.transmutationconvenient.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

/**
 * Curios 行動的安全入口。
 *
 * <p>這個類別不直接引用任何 Curios 類別；只有在 Curios 已載入時才用反射呼叫
 * {@link CuriosCompat}。因此 Curios 缺席時，網路封包、右鍵事件與主類別的
 * Curios 註冊都不會因為缺類而崩潰。
 */
public final class CurioActions {

	private static final String COMPAT_CLASS = "com.transmutationconvenient.compat.CuriosCompat";

	private CurioActions() {
	}

	public static boolean hasArchangel(Player player) {
		Object result = invoke("hasArchangel", Player.class, player);
		return result instanceof Boolean value && value;
	}

	public static void fireArchangel(ServerPlayer player) {
		invoke("fireArchangel", ServerPlayer.class, player);
	}

	public static void toggleArchangel(ServerPlayer player) {
		invoke("toggleArchangel", ServerPlayer.class, player);
	}

	/** 玩家身上是否戴著 連擊石（Curios 吊墜或護符）。 */
	public static boolean hasOneSevenStone(Player player) {
		Object result = invoke("hasOneSevenStone", Player.class, player);
		return result instanceof Boolean value && value;
	}

	private static Object invoke(String method, Class<?> parameter, Object argument) {
		if (!ModList.get().isLoaded("curios")) {
			return null;
		}
		try {
			return Class.forName(COMPAT_CLASS)
					.getMethod(method, parameter)
					.invoke(null, argument);
		} catch (ReflectiveOperationException | LinkageError error) {
			return null;
		}
	}
}
