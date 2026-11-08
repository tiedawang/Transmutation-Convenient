package com.transmutationconvenient.pipe;

import moze_intel.projecte.api.capabilities.block_entity.IEmcStorage.EmcAction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 一張常駐的 EMC 管道網路（潔淨室規格書 11.2、11.3、11.5、11.6、11.10）。
 *
 * <p>網路是常駐物件：它持有共享緩衝、成員管道、端點清單與發放邏輯。管道被放置、
 * 被移除、端點變動時才重建/改組（由 {@link EmcNetworkManager} 處理）；每個遊戲刻只在
 * 網路層級執行一次發放，工作量與端點數量成線性關係，<b>不是每根管道各自掃一遍整張網</b>。
 *
 * <p><b>能量怎麼分攤在管道上</b>：網路緩衝是權威值，每根管道另外持有自己那一份
 * （{@link EmcPipeBlockEntity#share()}）。份額的用途只有一個 —— 讓能量能隨管道一起
 * 進出世界存檔：區塊卸載時那根管道帶著自己的份額離開（緩衝同步扣掉），重新載入時
 * 再合併回來；因此不論存檔、讀檔、卸載、重載，能量都不會消失也不會複製。
 * 每次實際發放後會重新平均分攤一次（{@link #respread()}），讓份額貼近緩衝的實際值。
 */
final class EmcNetwork {

	/** 網路的基礎容量。 */
	static final long BASE_CAPACITY = 1_000_000L;

	/** 每根管道額外貢獻的容量；管網越長，能暫存的 EMC 越多。 */
	static final long CAPACITY_PER_PIPE = 100_000L;

	/** 「最近有成功發放」的判定窗口（tick）。 */
	private static final int FLOW_WINDOW = 20;

	private final ServerLevel level;

	/** 成員管道：已載入的那些（持有方塊實體、份額由網路直接維護）。 */
	private final Map<BlockPos, EmcPipeBlockEntity> members = new LinkedHashMap<>();

	/**
	 * 已卸載、但仍然屬於這張網路的管道位置。
	 *
	 * <p>這些管道的份額不在網路緩衝裡（卸載時已經隨管道躲進各自的存檔資料），
	 * 但它們的存在讓網路不會因為「已載入成員清空」而消失 —— 拆掉最後一根已載入管道時，
	 * 網路可以帶著緩衝進入休眠（{@link #enterDormant}），等這些管道重新載入再領回。
	 */
	private final Set<BlockPos> offlineMembers = new LinkedHashSet<>();

	/** 最近一次收集到的端點（含所在區塊未載入、暫時沿用上次判定的那些）。 */
	private final Map<BlockPos, EmcEndpoint> endpointCache = new LinkedHashMap<>();

	/** 本刻要發放的目標：優先級最高的那一組端點。 */
	private List<EmcEndpoint> topEndpoints = List.of();

	private long buffer;
	private boolean endpointsDirty = true;
	private boolean flowing;
	/** 最近一次成功發放的遊戲刻；-1 表示「從未發放」（不能拿它直接做減法，會溢位）。 */
	private long lastDeliveredTick = -1L;
	private boolean disposed;

	/** 休眠時把緩衝寄存在世界存檔資料的 key（某根離線管道的位置）；恢復載入時領回。 */
	@Nullable
	private BlockPos dormantKey;

	EmcNetwork(ServerLevel level) {
		this.level = level;
	}

	// ------------------------------------------------------------------
	// 成員維護（由管理器呼叫）
	// ------------------------------------------------------------------

	Map<BlockPos, EmcPipeBlockEntity> members() {
		return members;
	}

	boolean isDisposed() {
		return disposed;
	}

	void dispose() {
		disposed = true;
		members.clear();
		offlineMembers.clear();
		endpointCache.clear();
		topEndpoints = List.of();
	}

	Set<BlockPos> offlineMembers() {
		return offlineMembers;
	}

	/** 這根管道已經卸載，但還是這張網路的成員（所在區塊沒載入）。 */
	void addOfflineMember(BlockPos pos) {
		offlineMembers.add(pos);
	}

	/**
	 * 網路進入休眠：已經沒有任何已載入的管道可以持有緩衝，把緩衝寄到世界存檔資料裡，
	 * 以 {@code key} 這根離線管道的位置當索引，等它重新載入時領回。
	 */
	void enterDormant(BlockPos key) {
		offlineMembers.add(key);
		if (buffer > 0L) {
			DormantEmcData.get(level).put(key.asLong(), buffer);
			buffer = 0L;
		}
		dormantKey = key;
		endpointsDirty = true;
	}

	/** 休眠網路重新有已載入的管道：把寄存的緩衝領回來。 */
	void revive() {
		if (dormantKey == null) {
			return;
		}
		long carried = DormantEmcData.get(level).take(dormantKey.asLong());
		dormantKey = null;
		addCarry(carried);
	}

	/** 把一批「寄放在別處」的能量併回緩衝（休眠領回、或重啟後按位置領回）。 */
	void addCarry(long amount) {
		if (amount <= 0L) {
			return;
		}
		buffer = saturatedAdd(buffer, amount);
		endpointsDirty = true;
		if (!members.isEmpty()) {
			respread();
		}
	}

	void markEndpointsDirty() {
		endpointsDirty = true;
	}

	/** 管道（重新）加入網路：把它的份額併入緩衝。 */
	void addLoadedMember(EmcPipeBlockEntity pipe) {
		boolean wasEmpty = members.isEmpty();
		members.put(pipe.getBlockPos(), pipe);
		pipe.attachTo(this);
		buffer = saturatedAdd(buffer, pipe.share());
		endpointsDirty = true;
		syncFlow(pipe);
		if (wasEmpty) {
			revive();
		}
	}

	/** 同一個座標的管道重新載入（或換了方塊實體實例）：接回網路並校正份額。 */
	void adopt(EmcPipeBlockEntity pipe) {
		BlockPos pos = pipe.getBlockPos();
		EmcPipeBlockEntity previous = members.get(pos);
		if (previous == pipe) {
			pipe.attachTo(this);
			return;
		}
		boolean wasEmpty = members.isEmpty();
		offlineMembers.remove(pos);
		if (previous != null) {
			buffer = Math.max(0L, buffer - previous.share());
			previous.detach();
		}
		members.put(pos, pipe);
		pipe.attachTo(this);
		buffer = saturatedAdd(buffer, pipe.share());
		endpointsDirty = true;
		syncFlow(pipe);
		if (wasEmpty) {
			revive();
		}
	}

	/** 管道所在區塊卸載：它帶著自己那一份緩衝離開，網路緩衝同步扣掉。 */
	void removeLoadedMember(BlockPos pos) {
		EmcPipeBlockEntity pipe = members.remove(pos);
		if (pipe == null) {
			return;
		}
		pipe.detach();
		buffer = Math.max(0L, buffer - pipe.share());
		endpointsDirty = true;
	}

	/** 管道被拆掉：它分攤到的能量回到網路緩衝（緩衝不減），之後由 {@link #respread()} 重新分攤。 */
	void removeBrokenMember(BlockPos pos) {
		EmcPipeBlockEntity pipe = members.remove(pos);
		if (pipe == null) {
			return;
		}
		pipe.detach();
		pipe.setShare(0L);
		endpointsDirty = true;
	}

	/** 把成員的流動外觀對齊網路目前的狀態（新加入的管道用）。 */
	void syncFlow(EmcPipeBlockEntity pipe) {
		EmcPipeBlock.setFlowing(level, pipe.getBlockPos(), flowing);
	}

	/** 把「所有成員」的流動外觀對齊網路目前的狀態（改組後的網路用）。 */
	void syncAllFlow() {
		for (EmcPipeBlockEntity pipe : members.values()) {
			syncFlow(pipe);
		}
	}

	long buffer() {
		return buffer;
	}

	void setBuffer(long value) {
		buffer = Math.max(0L, value);
	}

	// ------------------------------------------------------------------
	// 容量與接收
	// ------------------------------------------------------------------

	/** 網路容量：基礎容量 ＋ 每根管道的貢獻。 */
	long capacity() {
		long total = BASE_CAPACITY + CAPACITY_PER_PIPE * members.size();
		return total < 0L ? Long.MAX_VALUE : total;
	}

	/**
	 * 接收一批 EMC 並併入共享緩衝（規格 11.4）。
	 *
	 * @return 實際接收量：推送量與緩衝剩餘容量中的較小者；緩衝已滿時為 0
	 */
	long insert(EmcPipeBlockEntity pipe, long amount, EmcAction action) {
		if (amount <= 0L) {
			return 0L;
		}
		long space = capacity() - buffer;
		if (space <= 0L) {
			return 0L;
		}
		long accepted = Math.min(amount, space);
		if (action.execute()) {
			buffer = saturatedAdd(buffer, accepted);
			pipe.setShare(saturatedAdd(pipe.share(), accepted));
		}
		return accepted;
	}

	// ------------------------------------------------------------------
	// 每個遊戲刻的發放（規格 11.5、11.6）
	// ------------------------------------------------------------------

	void tick() {
		if (disposed || members.isEmpty()) {
			return;     // 休眠網路：沒有已載入的管道，也就沒有端點與外觀要更新
		}
		if (endpointsDirty) {
			refreshEndpoints();
		}
		if (buffer > 0L && !topEndpoints.isEmpty()) {
			List<EmcDistribution.Target> sockets = new ArrayList<>(topEndpoints.size());
			for (EmcEndpoint endpoint : topEndpoints) {
				sockets.add(endpoint.socket(level));
			}
			long delivered = EmcDistribution.distribute(buffer, sockets);
			if (delivered > 0L) {
				buffer = Math.max(0L, buffer - delivered);
				respread();
				lastDeliveredTick = level.getGameTime();
			}
		}
		updateFlow();
	}

	/** 緩衝的份額重新平均分攤到每根成員管道（份額合計永遠等於緩衝）。 */
	void respread() {
		int count = members.size();
		if (count <= 0) {
			return;
		}
		long share = buffer / count;
		long extra = buffer - share * count;
		for (EmcPipeBlockEntity pipe : members.values()) {
			pipe.setShare(share + (extra > 0L ? 1L : 0L));
			if (extra > 0L) {
				extra--;
			}
		}
	}

	/**
	 * 重新收集端點。
	 *
	 * <p>只在「可能有變動」時做（管道進出網路、相鄰方塊變動、附近區塊載入），
	 * 不在每次發放時重新掃描 —— 這是規格 11.13 的效能要求。
	 *
	 * <p>端點所在區塊沒載入時沿用上次的判定：那塊方塊不可能在沒載入時被改動，
	 * 保留它才能避免「區塊暫時卸載 → 端點被忘掉」。
	 */
	private void refreshEndpoints() {
		Map<BlockPos, EmcEndpoint> found = new LinkedHashMap<>();
		for (BlockPos pipePos : members.keySet()) {
			for (Direction direction : Direction.values()) {
				BlockPos neighbour = pipePos.relative(direction);
				if (found.containsKey(neighbour)) {
					continue;
				}
				EmcEndpoint endpoint = level.isLoaded(neighbour)
						? EmcEndpoint.classify(level, neighbour, direction.getOpposite())
						: endpointCache.get(neighbour);
				if (endpoint != null) {
					found.put(neighbour, endpoint);
				}
			}
		}
		endpointCache.clear();
		endpointCache.putAll(found);

		int top = Integer.MIN_VALUE;
		for (EmcEndpoint endpoint : found.values()) {
			top = Math.max(top, endpoint.priority());
		}
		if (top == Integer.MIN_VALUE) {
			topEndpoints = List.of();
		} else {
			List<EmcEndpoint> group = new ArrayList<>();
			for (EmcEndpoint endpoint : found.values()) {
				if (endpoint.priority() == top) {
					group.add(endpoint);
				}
			}
			topEndpoints = group;
		}
		endpointsDirty = false;
	}

	/**
	 * 更新流動外觀（規格 11.10）：管網裡此刻有 EMC（緩衝大於零），或最近一段時間內
	 * 有成功發放，而且確實有「有優先級的端點」可送 —— 沒有端點、或端點全都沒有優先級時，
	 * 管道不得顯示流動。
	 */
	private void updateFlow() {
		boolean recent = lastDeliveredTick >= 0L && level.getGameTime() - lastDeliveredTick < FLOW_WINDOW;
		boolean next = !topEndpoints.isEmpty() && (buffer > 0L || recent);
		if (next == flowing) {
			return;
		}
		flowing = next;
		for (EmcPipeBlockEntity pipe : members.values()) {
			syncFlow(pipe);
		}
	}

	// ------------------------------------------------------------------

	private static long saturatedAdd(long left, long right) {
		long sum = left + right;
		return sum < 0L ? Long.MAX_VALUE : sum;
	}
}
