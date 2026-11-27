package com.transmutationconvenient.item;

import com.transmutationconvenient.network.NetworkHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 連擊石的「僵直」狀態。
 *
 * <p>這不是藥水效果，而是一份只有伺服器端使用的短時間狀態表：
 * 每次攻擊把目標的 UUID 對應到「結束的遊戲刻」與當下的視角；4 刻（0.2 秒）內：
 * <ul>
 *   <li>非玩家生物直接取消 {@code LivingEvent.LivingTickEvent}，
 *       所以 AI、移動、攻擊、視角更新全部停止；</li>
 *   <li>玩家另外送 {@link NetworkHandler.StunPacket}，由客戶端鎖住滑鼠視角與移動輸入；
 *       伺服器端也會每刻把玩家的視角與水平速度拉回。</li>
 * </ul>
 * 狀態只用 UUID 記錄，不寫入 NBT、不建立 MobEffect，也不跨存檔保存。
 */
public final class StunManager {

	/** 0.2 秒 = 4 刻。 */
	public static final int STUN_TICKS = 4;

	private static final Map<UUID, StunData> STUNS = new HashMap<>();

	private StunManager() {
	}

	/** 對目標施加（或刷新）僵直；玩家會另外收到客戶端鎖定封包。 */
	public static void stun(LivingEntity target, int ticks) {
		if (target.level().isClientSide() || ticks <= 0) {
			return;
		}
		UUID id = target.getUUID();
		StunData current = active(target);
		float yaw = current == null ? target.getYRot() : current.yaw;
		float pitch = current == null ? target.getXRot() : current.pitch;
		StunData data = new StunData(target.level().getGameTime() + ticks, yaw, pitch);
		STUNS.put(id, data);
		if (target instanceof ServerPlayer player) {
			NetworkHandler.CHANNEL.send(
					PacketDistributor.PLAYER.with(() -> player),
					new NetworkHandler.StunPacket(ticks, yaw, pitch));
		}
	}

	/** 目標現在是否仍在僵直中；順便清掉已過期的紀錄。 */
	public static boolean isStunned(LivingEntity entity) {
		return active(entity) != null;
	}

	/**
	 * 把目標的移動與視角鎖在僵直開始時的方向。
	 *
	 * <p>回傳 true 表示這一刻仍在僵直中；呼叫端可据此外決定是否取消整段
	 * {@code LivingTickEvent}。
	 */
	public static boolean applyLock(LivingEntity entity) {
		StunData data = active(entity);
		if (data == null) {
			return false;
		}
		entity.setDeltaMovement(Vec3.ZERO);
		// 僵直取消目標 tick 時，原版不會再遞減受擊無敵時間；這裡直接歸零，
		// 讓遠古版本的詛咒（移除受擊冷卻）在僵直期間仍然成立。
		entity.invulnerableTime = 0;
		entity.setYRot(data.yaw);
		entity.setXRot(data.pitch);
		entity.setYHeadRot(data.yaw);
		return true;
	}

	/** 實體死亡或離線時清掉狀態，避免 UUID 留在表裡。 */
	public static void clear(LivingEntity entity) {
		STUNS.remove(entity.getUUID());
	}

	private static StunData active(LivingEntity entity) {
		UUID id = entity.getUUID();
		StunData data = STUNS.get(id);
		if (data == null) {
			return null;
		}
		if (entity.level().getGameTime() >= data.endTick) {
			STUNS.remove(id);
			return null;
		}
		return data;
	}

	private static final class StunData {

		private final long endTick;
		private final float yaw;
		private final float pitch;

		private StunData(long endTick, float yaw, float pitch) {
			this.endTick = endTick;
			this.yaw = yaw;
			this.pitch = pitch;
		}
	}
}
