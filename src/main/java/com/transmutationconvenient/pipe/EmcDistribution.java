package com.transmutationconvenient.pipe;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 均分演算法（潔淨室規格書第一部分第四節）。
 *
 * <p>這個類刻意<b>不依賴任何 Minecraft 類別</b>：只處理「一批能量怎麼分給一群目標」。
 * 目標的能力與寫入都是即時查詢，所以「模擬」與「實際寫入」用的是同一套判定，
 * 不會出現「模擬能收、實際收不下」的落差。
 *
 * <p>分配分四段：
 * <ol>
 *   <li>公平份額：這批能量除以目標數量，向下取整。</li>
 *   <li>收不下的先寫入並退出名單：能接收量不超過公平份額的目標，把它的量寫進去、
 *       從待分配量扣除、目標數減一、重算份額；重複到份額不再變化。</li>
 *   <li>餘數：剩下的目標都還想要超過份額的能量，依名單順序每個先給「份額加一」，
 *       還有餘數才給，否則給份額；收不下的把能收的全給它並退出名單。</li>
 *   <li>兜底：還有沒送出的，依序給第一個仍然能接收的目標。</li>
 * </ol>
 *
 * <p>經過以上步驟仍未送出的能量由呼叫端留在緩衝，不丟棄、也不硬塞給某一台。
 */
public final class EmcDistribution {

	/** 一個可以被寫入的目標。 */
	public interface Target {

		/** 這個目標現在還能收多少（模擬；與實際寫入用同一套判定）。 */
		long capacity();

		/** 實際寫入，回傳真的收下多少。 */
		long offer(long amount);
	}

	private EmcDistribution() {
	}

	/**
	 * 把一批能量均分給目標。
	 *
	 * @param available 這一批待分配的能量（呼叫端通常是網路緩衝的全部）
	 * @param targets   目標清單；順序決定餘數優先給誰
	 * @return 實際送出的總量（{@code available} 減去留在緩衝的剩餘）
	 */
	public static long distribute(long available, List<? extends Target> targets) {
		if (available <= 0L || targets == null || targets.isEmpty()) {
			return 0L;
		}
		List<Target> pending = new ArrayList<>(targets);
		long remaining = available;

		// 第一輪＋第二輪：收不下的先寫入並退出名單，份額隨目標數減少而變大。
		boolean shrunk = true;
		while (shrunk && !pending.isEmpty()) {
			shrunk = false;
			Iterator<Target> iterator = pending.iterator();
			while (iterator.hasNext() && !pending.isEmpty()) {
				long share = remaining / pending.size();
				Target target = iterator.next();
				long capacity = Math.max(0L, target.capacity());
				if (capacity <= share) {
					remaining -= write(target, capacity);
					iterator.remove();
					shrunk = true;
				}
			}
		}

		// 餘數分配：剩下的目標都還想要超過份額的能量。
		if (!pending.isEmpty() && remaining > 0L) {
			long share = remaining / pending.size();
			long remainder = remaining - share * pending.size();
			Iterator<Target> iterator = pending.iterator();
			while (iterator.hasNext()) {
				Target target = iterator.next();
				long want = share + (remainder > 0L ? 1L : 0L);
				if (remainder > 0L) {
					remainder--;
				}
				long capacity = Math.max(0L, target.capacity());
				remaining -= write(target, Math.min(want, capacity));
				if (capacity < want) {
					iterator.remove();      // 這一輪已滿，後面不再問它
				}
			}
		}

		// 兜底：還有沒送出的能量，依序給第一個仍然能接收的目標，直到送完或沒有目標為止。
		for (Target target : pending) {
			if (remaining <= 0L) {
				break;
			}
			long capacity = Math.max(0L, target.capacity());
			if (capacity <= 0L) {
				continue;
			}
			remaining -= write(target, Math.min(remaining, capacity));
		}

		return available - remaining;
	}

	/** 實際寫入一次；回傳真的收下的量（不會超過要求，也不會是負數）。 */
	private static long write(Target target, long amount) {
		if (amount <= 0L) {
			return 0L;
		}
		long given = target.offer(amount);
		if (given <= 0L) {
			return 0L;
		}
		return Math.min(given, amount);
	}
}
