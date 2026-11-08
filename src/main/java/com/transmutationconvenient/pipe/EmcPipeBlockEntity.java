package com.transmutationconvenient.pipe;

import com.transmutationconvenient.registry.ModBlockEntities;
import moze_intel.projecte.api.capabilities.PECapabilities;
import moze_intel.projecte.api.capabilities.block_entity.IEmcStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nullable;

/**
 * EMC 管道的方塊實體（潔淨室規格書 11.4、11.12）。
 *
 * <p>管道是<b>被動接收方</b>：對外提供 ProjectE 的 EMC 儲存能力，能量收集器、反物質繼電器
 * 主動把 EMC 推過來時，管道把這批 EMC 併入所屬網路的共享緩衝，並回報實際接收量
 * （收不下的留在推送方手裡）。能量凝聚器在 ProjectE 是<b>只收不推</b>的，它是管道的
 * 輸送目標而不是來源。管道不主動抽取任何方塊的 EMC。
 *
 * <p>每根管道另外記著自己分攤到的緩衝份額（{@link #share()}）。份額唯一的用途是
 * 讓能量隨管道一起進出世界存檔：區塊卸載時管道帶著份額離開、重新載入時合併回來，
 * 因此存檔、讀檔、卸載、重載都不會讓能量消失或複製。發放與接收的即時帳都由
 * 網路保管（見 {@link EmcNetwork}），方塊實體自己不跑分配邏輯。
 */
public class EmcPipeBlockEntity extends BlockEntity implements IEmcStorage {

	private static final String SHARE_TAG = "stored";

	/** 連線外觀的自檢間隔（tick）：補上漏掉的方塊事件（例如鄰塊的區塊當時還沒載入）。 */
	private static final int CONNECTION_CHECK_INTERVAL = 10;

	private LazyOptional<IEmcStorage> emcStorage = LazyOptional.of(() -> this);

	/** 這根管道分攤到的網路緩衝（存檔）；執行期由網路維護。 */
	private long share;

	/** 所屬網路；區塊卸載或管道被拆掉時清空。 */
	@Nullable
	private EmcNetwork network;

	public EmcPipeBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.EMC_PIPE.get(), pos, state);
	}

	/**
	 * 每個遊戲刻：還沒接上網路就接上（放下的新管道、區塊重新載入的管道都靠這一步）；
	 * 另外每 {@value #CONNECTION_CHECK_INTERVAL} 刻自檢一次連線外觀，補上漏掉的方塊事件
	 * （例如鄰塊所在區塊當時還沒載入、事件沒送到這根管道）。
	 */
	public static void serverTick(Level level, BlockPos pos, BlockState state, EmcPipeBlockEntity pipe) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		pipe.ensureNetwork(serverLevel);
		if ((level.getGameTime() + pos.asLong()) % CONNECTION_CHECK_INTERVAL == 0L
				&& state.getBlock() instanceof EmcPipeBlock block) {
			block.refreshConnections(level, pos, state);
		}
	}

	void ensureNetwork(ServerLevel level) {
		EmcNetwork current = network;
		if (current != null && !current.isDisposed() && current.members().containsKey(worldPosition)) {
			return;
		}
		network = EmcNetworkManager.of(level).join(this);
	}

	// ------------------------------------------------------------------
	// 份額與網路歸屬（同一個套件內的網路物件使用）
	// ------------------------------------------------------------------

	long share() {
		return share;
	}

	void setShare(long value) {
		long clamped = Math.max(0L, value);
		if (clamped == share) {
			return;
		}
		share = clamped;
		setChanged();       // 份額變了：這個區塊的存檔要跟著更新，否則重啟後能量會回捲
	}

	void attachTo(EmcNetwork value) {
		network = value;
	}

	void detach() {
		network = null;
	}

	// ------------------------------------------------------------------
	// IEmcStorage：被動接收，併入網路共享緩衝
	// ------------------------------------------------------------------

	@Override
	public long getStoredEmc() {
		EmcNetwork current = network;
		return current == null ? 0L : current.buffer();
	}

	@Override
	public long getMaximumEmc() {
		EmcNetwork current = network;
		return current == null ? 0L : current.capacity();
	}

	@Override
	public long insertEmc(long amount, EmcAction action) {
		if (amount <= 0L || action == null || !(level instanceof ServerLevel serverLevel)) {
			return 0L;
		}
		ensureNetwork(serverLevel);
		EmcNetwork current = network;
		if (current == null || current.isDisposed()) {
			return 0L;
		}
		return current.insert(this, amount, action);
	}

	@Override
	public long extractEmc(long amount, EmcAction action) {
		return 0L;      // 管道不主動抽取，也不讓別人從管道抽 EMC
	}

	// ------------------------------------------------------------------
	// 存檔
	// ------------------------------------------------------------------

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		tag.putLong(SHARE_TAG, share);
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		share = Math.max(0L, tag.getLong(SHARE_TAG));
	}

	// ------------------------------------------------------------------
	// 生命週期
	// ------------------------------------------------------------------

	/**
	 * 方塊實體被移除時（區塊卸載、管道被拆、世界卸載）把管道從網路移出。
	 *
	 * <p>管道被拆的情況已經在 {@link EmcPipeBlock#onRemove} 處理過（那時才能分辨
	 * 「方塊真的沒了」與「只是方塊狀態變了」），這裡對已移除的管道是無害的空操作。
	 */
	@Override
	public void setRemoved() {
		super.setRemoved();
		if (level instanceof ServerLevel serverLevel) {
			EmcNetworkManager.of(serverLevel).onPipeUnloaded(worldPosition);
		}
		network = null;
	}

	// ------------------------------------------------------------------
	// 能力
	// ------------------------------------------------------------------

	@Override
	public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
		if (capability == PECapabilities.EMC_STORAGE_CAPABILITY) {
			return emcStorage.cast();
		}
		return super.getCapability(capability, side);
	}

	@Override
	public void invalidateCaps() {
		super.invalidateCaps();
		emcStorage.invalidate();
	}

	@Override
	public void reviveCaps() {
		super.reviveCaps();
		emcStorage = LazyOptional.of(() -> this);
	}
}
