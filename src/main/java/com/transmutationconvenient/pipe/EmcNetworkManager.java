package com.transmutationconvenient.pipe;

import com.transmutationconvenient.TransmutationConvenientMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 每個世界（{@link ServerLevel}）一份的管網管理器（潔淨室規格書 11.2、11.11、11.13）。
 *
 * <p>它負責四件事：
 * <ol>
 *   <li><b>成員維護</b>：管道加入/離開網路、兩張網路合併、一張網路因管道被拆而分裂。
 *       能量在合併時相加、分裂時依管道成員數量分攤，除不盡的餘數歸最後一張，不丟棄。</li>
 *   <li><b>每刻一次的發放</b>：每個遊戲刻走一遍網路清單，讓每張網路各執行一次發放 ——
 *       不是每根管道各自走一遍。</li>
 *   <li><b>端點變動的通知</b>：相鄰方塊變了（{@link EmcPipeBlock#neighborChanged}）、
 *       附近區塊載入了（{@link ChunkEvent.Load}）時，把受影響的網路標成「端點待重收」。</li>
 *   <li><b>區塊索引</b>：記錄每張網路在哪些區塊有管道，區塊載入時只通知真正受影響的網路，
 *       不驚動整個世界。</li>
 * </ol>
 *
 * <p>網路物件常駐：管道所在區塊卸載不會解散網路（成員帶著自己的份額離開，見
 * {@link EmcNetwork}），所以能量不會因為離開視野而消失。
 */
@Mod.EventBusSubscriber(modid = TransmutationConvenientMod.MOD_ID,
		bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EmcNetworkManager {

	private static final Map<ServerLevel, EmcNetworkManager> MANAGERS = new WeakHashMap<>();

	private final ServerLevel level;
	private final Map<BlockPos, EmcNetwork> pipeIndex = new HashMap<>();
	private final Map<ChunkPos, Set<EmcNetwork>> chunkIndex = new HashMap<>();
	private final Set<EmcNetwork> networks = new LinkedHashSet<>();

	private EmcNetworkManager(ServerLevel level) {
		this.level = level;
	}

	/** 取得這個世界的管理器（每個世界一份；世界卸載時丟掉）。 */
	public static EmcNetworkManager of(ServerLevel level) {
		synchronized (MANAGERS) {
			return MANAGERS.computeIfAbsent(level, EmcNetworkManager::new);
		}
	}

	// ------------------------------------------------------------------
	// 事件：每刻發放、區塊載入、世界卸載
	// ------------------------------------------------------------------

	@SubscribeEvent
	public static void onLevelTick(TickEvent.LevelTickEvent event) {
		if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel serverLevel)) {
			return;
		}
		of(serverLevel).tick();
	}

	@SubscribeEvent
	public static void onChunkLoad(ChunkEvent.Load event) {
		if (event.getLevel() instanceof ServerLevel serverLevel) {
			of(serverLevel).onChunkLoaded(event.getChunk().getPos());
		}
	}

	@SubscribeEvent
	public static void onLevelUnload(LevelEvent.Unload event) {
		if (event.getLevel() instanceof ServerLevel serverLevel) {
			synchronized (MANAGERS) {
				MANAGERS.remove(serverLevel);
			}
		}
	}

	// ------------------------------------------------------------------
	// 每刻發放
	// ------------------------------------------------------------------

	private void tick() {
		if (networks.isEmpty()) {
			return;
		}
		// 發放時可能會寫方塊狀態（流動外觀），連帶觸發方塊事件；走副本避免 ConcurrentModification
		for (EmcNetwork network : networks.toArray(new EmcNetwork[0])) {
			if (!network.isDisposed()) {
				network.tick();
			}
		}
	}

	// ------------------------------------------------------------------
	// 成員維護
	// ------------------------------------------------------------------

	/** 管道（重新）加入網路；會自動與相鄰管道所屬的網路合併。 */
	EmcNetwork join(EmcPipeBlockEntity pipe) {
		BlockPos pos = pipe.getBlockPos();
		EmcNetwork existing = pipeIndex.get(pos);
		if (existing != null && !existing.isDisposed()) {
			existing.adopt(pipe);
			pipeIndex.put(pos, existing);
			chunkIndexAdd(pos, existing);
			existing.addCarry(takeCarry(pos));
			return existing;
		}
		if (existing != null) {
			pipeIndex.remove(pos);
			chunkIndexRemove(pos, existing);
		}

		EmcNetwork target = null;
		for (Direction direction : Direction.values()) {
			EmcNetwork neighbour = pipeIndex.get(pos.relative(direction));
			if (neighbour == null || neighbour.isDisposed()) {
				continue;
			}
			target = target == null ? neighbour : merge(target, neighbour);
		}
		if (target == null) {
			target = new EmcNetwork(level);
			networks.add(target);
		}
		pipeIndex.put(pos, target);
		chunkIndexAdd(pos, target);
		target.addLoadedMember(pipe);
		// 重啟後：這個位置若剛好是某張休眠網路寄存緩衝的 key，把金額領回來
		target.addCarry(takeCarry(pos));
		return target;
	}

	/** 從「休眠緩衝」的世界存檔資料裡，領回某個位置寄放的金額（沒有就回 0）。 */
	private long takeCarry(BlockPos pos) {
		return DormantEmcData.get(level).take(pos.asLong());
	}

	/** 管道被拆掉：從網路移出；如果網路因此分成好幾張，能量依成員數量分攤到各張。 */
	void onPipeRemoved(BlockPos pos) {
		EmcNetwork network = pipeIndex.remove(pos);
		if (network == null) {
			return;
		}
		chunkIndexRemove(pos, network);
		network.removeBrokenMember(pos);
		if (network.members().isEmpty()) {
			if (network.offlineMembers().isEmpty()) {
				discard(network);
				return;
			}
			// 這張網路在未載入區塊裡還有管道：它沒有消失，只是暫時沒有已載入的成員。
			// 被拆管道回到緩衝的那份能量寄到世界存檔資料裡，等那些管道載入時領回。
			network.enterDormant(network.offlineMembers().iterator().next());
			return;
		}
		List<List<BlockPos>> components = components(network);
		if (components.size() > 1) {
			split(network, components);
		} else {
			network.respread();
		}
	}

	/**
	 * 管道所在區塊卸載：它帶著自己的份額躲進存檔，網路緩衝同步扣掉。
	 *
	 * <p><b>不以「已載入成員清空」判定網路消失</b>：方塊還在世界裡，只是看不到。
	 * 成員位置（{@code pipeIndex}／{@code chunkIndex}）保留，等區塊重新載入時
	 * 同一根管道會接回同一張網路；若這張網路接下來連「已載入成員」都沒有了，
	 * 它會保持休眠，緩衝（如果有）寄在世界存檔資料裡。
	 */
	void onPipeUnloaded(BlockPos pos) {
		EmcNetwork network = pipeIndex.get(pos);
		if (network == null) {
			return;
		}
		network.removeLoadedMember(pos);
		network.addOfflineMember(pos);
	}

	/** 某根管道旁邊的方塊變了：那張網路的端點清單待重收。 */
	void markDirty(BlockPos pipePos) {
		EmcNetwork network = pipeIndex.get(pipePos);
		if (network != null && !network.isDisposed()) {
			network.markEndpointsDirty();
		}
	}

	// ------------------------------------------------------------------
	// 合併與分裂
	// ------------------------------------------------------------------

	private EmcNetwork merge(EmcNetwork target, EmcNetwork other) {
		if (target == other) {
			return target;
		}
		target.revive();
		other.revive();
		target.setBuffer(saturatedAdd(target.buffer(), other.buffer()));
		for (Map.Entry<BlockPos, EmcPipeBlockEntity> entry : other.members().entrySet()) {
			BlockPos pos = entry.getKey();
			target.members().put(pos, entry.getValue());
			entry.getValue().attachTo(target);
			pipeIndex.put(pos, target);
			chunkIndexRemove(pos, other);
			chunkIndexAdd(pos, target);
		}
		for (BlockPos pos : other.offlineMembers()) {
			target.offlineMembers().add(pos);
			pipeIndex.put(pos, target);
			chunkIndexRemove(pos, other);
			chunkIndexAdd(pos, target);
		}
		other.members().clear();
		other.offlineMembers().clear();
		target.markEndpointsDirty();
		target.syncAllFlow();
		target.respread();
		discard(other);
		return target;
	}

	/**
	 * 一張網路被拆成好幾張：緩衝依「各分量的管道數量」分攤，除不盡的餘數歸最後一張，
	 * 不得丟棄（規格 11.2）。每張新網路再把分到的緩衝平均分攤給自己的成員。
	 */
	private void split(EmcNetwork network, List<List<BlockPos>> components) {
		long total = network.buffer();
		int totalLoaded = network.members().size();
		long assigned = 0L;
		for (int index = 0; index < components.size(); index++) {
			List<BlockPos> component = components.get(index);
			int loaded = 0;
			for (BlockPos pos : component) {
				if (network.members().containsKey(pos)) {
					loaded++;
				}
			}
			long part = index == components.size() - 1
					? Math.max(0L, total - assigned)
					: (totalLoaded <= 0 ? 0L : mulDiv(total, loaded, totalLoaded));
			assigned += part;
			EmcNetwork child = new EmcNetwork(level);
			for (BlockPos pos : component) {
				EmcPipeBlockEntity pipe = network.members().remove(pos);
				if (pipe != null) {
					child.members().put(pos, pipe);
					pipe.attachTo(child);
				} else {
					network.offlineMembers().remove(pos);
					child.offlineMembers().add(pos);
				}
				pipeIndex.put(pos, child);
				chunkIndexRemove(pos, network);
				chunkIndexAdd(pos, child);
			}
			child.setBuffer(part);
			child.respread();
			child.markEndpointsDirty();
			child.syncAllFlow();
			networks.add(child);
			if (child.members().isEmpty() && !child.offlineMembers().isEmpty() && child.buffer() > 0L) {
				// 這一段只剩未載入的管道：把緩衝寄存起來，等它們載入時領回
				child.enterDormant(child.offlineMembers().iterator().next());
			}
		}
		discard(network);
	}

	/** 依管道座標的六向相鄰關係，把剩下的成員切成連通分量。 */
	private static List<List<BlockPos>> components(EmcNetwork network) {
		Set<BlockPos> remaining = new HashSet<>(network.members().keySet());
		remaining.addAll(network.offlineMembers());
		List<List<BlockPos>> result = new ArrayList<>();
		while (!remaining.isEmpty()) {
			BlockPos start = remaining.iterator().next();
			remaining.remove(start);
			List<BlockPos> component = new ArrayList<>();
			Deque<BlockPos> queue = new ArrayDeque<>();
			queue.add(start);
			while (!queue.isEmpty()) {
				BlockPos pos = queue.poll();
				component.add(pos);
				for (Direction direction : Direction.values()) {
					BlockPos next = pos.relative(direction);
					if (remaining.remove(next)) {
						queue.add(next);
					}
				}
			}
			result.add(component);
		}
		return result;
	}

	private void discard(EmcNetwork network) {
		networks.remove(network);
		network.dispose();
	}

	// ------------------------------------------------------------------
	// 區塊索引
	// ------------------------------------------------------------------

	private void onChunkLoaded(ChunkPos pos) {
		markChunkDirty(pos);
		markChunkDirty(new ChunkPos(pos.x + 1, pos.z));
		markChunkDirty(new ChunkPos(pos.x - 1, pos.z));
		markChunkDirty(new ChunkPos(pos.x, pos.z + 1));
		markChunkDirty(new ChunkPos(pos.x, pos.z - 1));
	}

	private void markChunkDirty(ChunkPos pos) {
		Set<EmcNetwork> affected = chunkIndex.get(pos);
		if (affected == null || affected.isEmpty()) {
			return;
		}
		for (EmcNetwork network : affected.toArray(new EmcNetwork[0])) {
			if (!network.isDisposed()) {
				network.markEndpointsDirty();
			}
		}
	}

	private void chunkIndexAdd(BlockPos pos, EmcNetwork network) {
		chunkIndex.computeIfAbsent(new ChunkPos(pos), key -> new HashSet<>()).add(network);
	}

	private void chunkIndexRemove(BlockPos pos, EmcNetwork network) {
		ChunkPos key = new ChunkPos(pos);
		Set<EmcNetwork> affected = chunkIndex.get(key);
		if (affected == null) {
			return;
		}
		affected.remove(network);
		if (affected.isEmpty()) {
			chunkIndex.remove(key);
		}
	}

	// ------------------------------------------------------------------

	private static long saturatedAdd(long left, long right) {
		long sum = left + right;
		return sum < 0L ? Long.MAX_VALUE : sum;
	}

	/** 精確計算 {@code total * part / whole}（向下取整）；用 BigInteger 避免極大數值相乘時溢位。 */
	private static long mulDiv(long total, int part, int whole) {
		if (whole <= 0) {
			return 0L;
		}
		return BigInteger.valueOf(total)
				.multiply(BigInteger.valueOf(part))
				.divide(BigInteger.valueOf(whole))
				.longValue();
	}
}
