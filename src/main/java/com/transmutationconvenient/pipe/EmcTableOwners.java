package com.transmutationconvenient.pipe;

import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.capabilities.PECapabilities;
import moze_intel.projecte.api.capabilities.block_entity.IEmcStorage.EmcAction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.UUID;

/**
 * 轉化桌的主人與 EMC 入帳。
 *
 * <p>接在管網上的轉化桌，其 EMC 會記到「放置這張桌子的玩家」的 EMC 帳戶：
 * <ol>
 *   <li>有放置紀錄（由 {@code BlockItemPlaceMixin} 在玩家放下桌子時寫入）就記給那位玩家；</li>
 *   <li>沒有紀錄（模組裝上之前就存在的桌子、指令放的桌子）就退回「桌子 16 格內
 *       最近的玩家」；</li>
 *   <li>都找不到（例如主人在離線狀態）就不收這批 EMC —— EMC 留在生產方，
 *       等主人回來再繼續。</li>
 * </ol>
 *
 * <p>入帳透過 ProjectE 的 API 完成：{@link IKnowledgeProvider#getEmc()}／
 * {@link IKnowledgeProvider#setEmc(BigInteger)} 加上 {@link IKnowledgeProvider#syncEmc}
 * —— 與轉化桌介面自己加減 EMC 走的是同一條路。
 */
public final class EmcTableOwners {

	/** 沒有放置紀錄時，認定「桌子附近的玩家」的半徑（格）。 */
	private static final double FALLBACK_RADIUS = 16.0;

	private EmcTableOwners() {
	}

	/** 玩家放下轉化桌時記下主人。 */
	public static void record(Level level, BlockPos pos, ServerPlayer player) {
		if (level instanceof ServerLevel serverLevel) {
			TableOwnerData.get(serverLevel.getServer())
					.setOwner(serverLevel.dimension(), pos, player.getUUID());
		}
	}

	/** 這張桌子現在該把 EMC 記給哪位在線玩家；沒有就回傳 null。 */
	@Nullable
	public static ServerPlayer resolveOwner(ServerLevel level, BlockPos pos) {
		UUID ownerId = TableOwnerData.get(level.getServer()).ownerOf(level.dimension(), pos);
		if (ownerId != null) {
			return level.getServer().getPlayerList().getPlayer(ownerId);
		}
		Player nearest = level.getNearestPlayer(
				pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, FALLBACK_RADIUS, false);
		return nearest instanceof ServerPlayer serverPlayer ? serverPlayer : null;
	}

	/**
	 * 把 EMC 記到這張轉化桌的持有者帳戶。
	 *
	 * @return 實際收下的 EMC（找不到持有者就回 0，EMC 留在生產方）
	 */
	public static long deposit(ServerLevel level, BlockPos tablePos, long amount, EmcAction action) {
		return deposit(resolveOwner(level, tablePos), amount, action);
	}

	/**
	 * 把 EMC 記到指定玩家的帳戶（呼叫端已經解析過主人時用這個，避免重複解析）。
	 *
	 * <p>寫入的是玩家自己的 EMC 帳戶（與轉化桌介面加減 EMC 走同一條路），寫完立刻
	 * 同步給該玩家的客戶端。
	 *
	 * @return 實際收下的 EMC；{@code owner} 為 null（離線或找不到人）時回 0
	 */
	public static long deposit(@Nullable ServerPlayer owner, long amount, EmcAction action) {
		if (owner == null || amount <= 0L) {
			return 0L;
		}
		IKnowledgeProvider knowledge = owner.getCapability(PECapabilities.KNOWLEDGE_CAPABILITY).orElse(null);
		if (knowledge == null) {
			return 0L;
		}
		if (action.execute()) {
			knowledge.setEmc(knowledge.getEmc().add(BigInteger.valueOf(amount)));
			knowledge.syncEmc(owner);
		}
		return amount;
	}
}
