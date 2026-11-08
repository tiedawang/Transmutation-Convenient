package com.transmutationconvenient.pipe;

import moze_intel.projecte.api.capabilities.PECapabilities;
import moze_intel.projecte.api.capabilities.block_entity.IEmcStorage;
import moze_intel.projecte.api.capabilities.block_entity.IEmcStorage.EmcAction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * 管網端點：掛在管道側面、有優先級、可以收 EMC 的相鄰方塊（潔淨室規格書 11.7）。
 *
 * <p>只有兩種：
 * <ul>
 *   <li><b>轉化桌端點</b>：方塊本身沒有方塊實體、也沒有 EMC 儲存能力，只能靠方塊的
 *       註冊名辨認；EMC 寫入放置該桌子的玩家帳戶（沒有放置紀錄時退回桌子附近最近的玩家）。</li>
 *   <li><b>EMC 儲存端點</b>：任何帶有 ProjectE {@code IEmcStorage} 能力、
 *       而且屬於四類有優先級方塊之一的方塊。</li>
 * </ul>
 *
 * <p>同一個方塊即使透過多個面連上同一張網路，也只算一個端點 —— 由收集端以座標去重達成。
 */
final class EmcEndpoint {

	private final BlockPos pos;
	private final int priority;
	private final boolean table;
	private final Direction side;

	private EmcEndpoint(BlockPos pos, int priority, boolean table, Direction side) {
		this.pos = pos;
		this.priority = priority;
		this.table = table;
		this.side = side;
	}

	/**
	 * 判定一個相鄰方塊是不是端點。
	 *
	 * @param side 該方塊面向管道的那一面（查能力用）
	 * @return 沒有優先級、或根本沒有 EMC 儲存能力時回傳 null
	 */
	@Nullable
	static EmcEndpoint classify(ServerLevel level, BlockPos pos, Direction side) {
		BlockState state = level.getBlockState(pos);
		if (EmcPriority.isTransmutationTable(state)) {
			return new EmcEndpoint(pos.immutable(), EmcPriority.TABLE, true, side);
		}
		Integer priority = EmcPriority.priorityOf(state.getBlock());
		if (priority == null) {
			return null;
		}
		if (storage(level, pos, side) == null) {
			return null;
		}
		return new EmcEndpoint(pos.immutable(), priority, false, side);
	}

	@Nullable
	private static IEmcStorage storage(ServerLevel level, BlockPos pos, Direction side) {
		BlockEntity blockEntity = level.getBlockEntity(pos);
		return blockEntity == null ? null
				: blockEntity.getCapability(PECapabilities.EMC_STORAGE_CAPABILITY, side).orElse(null);
	}

	BlockPos pos() {
		return pos;
	}

	int priority() {
		return priority;
	}

	/** 建一個「這一次發放」用的寫入口；能力與轉化桌主人只在第一次用到時解析一次。 */
	EmcDistribution.Target socket(ServerLevel level) {
		return new Socket(level, this);
	}

	private static final class Socket implements EmcDistribution.Target {

		private final ServerLevel level;
		private final EmcEndpoint endpoint;
		private boolean storageResolved;
		private IEmcStorage storage;
		private boolean ownerResolved;
		private ServerPlayer owner;

		private Socket(ServerLevel level, EmcEndpoint endpoint) {
			this.level = level;
			this.endpoint = endpoint;
		}

		@Override
		public long capacity() {
			if (!level.isLoaded(endpoint.pos)) {
				return 0L;      // 端點所在區塊沒載入：這一刻收不了，能量留在緩衝
			}
			if (endpoint.table) {
				// 玩家在線時視為無限（玩家的 EMC 帳戶沒有上限）；離線或找不到人＝不可接收
				return owner() != null ? Long.MAX_VALUE : 0L;
			}
			IEmcStorage target = storage();
			return target == null ? 0L : Math.max(0L, target.insertEmc(Long.MAX_VALUE, EmcAction.SIMULATE));
		}

		@Override
		public long offer(long amount) {
			if (amount <= 0L || !level.isLoaded(endpoint.pos)) {
				return 0L;
			}
			if (endpoint.table) {
				return EmcTableOwners.deposit(owner(), amount, EmcAction.EXECUTE);
			}
			IEmcStorage target = storage();
			return target == null ? 0L : Math.max(0L, target.insertEmc(amount, EmcAction.EXECUTE));
		}

		@Nullable
		private IEmcStorage storage() {
			if (!storageResolved) {
				storageResolved = true;
				storage = EmcEndpoint.storage(level, endpoint.pos, endpoint.side);
			}
			return storage;
		}

		@Nullable
		private ServerPlayer owner() {
			if (!ownerResolved) {
				ownerResolved = true;
				owner = EmcTableOwners.resolveOwner(level, endpoint.pos);
			}
			return owner;
		}
	}
}
