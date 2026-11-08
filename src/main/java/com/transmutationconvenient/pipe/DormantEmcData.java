package com.transmutationconvenient.pipe;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * 「休眠網路」緩衝的存檔資料。
 *
 * <p>正常情況下網路緩衝由成員管道各自保管（每根管道 NBT 的 {@code stored}）：區塊卸載時
 * 管道帶著自己的份額躲進存檔，重新載入時合併回來。
 *
 * <p>但有一種情況沒有「已載入的管道」可以保管緩衝：管網一部分在未載入區塊、一部分已載入，
 * 玩家拆掉最後一根已載入的管道。被拆管道分攤到的能量按規格要「回到網路緩衝」，可是此刻
 * 網路上沒有任何已載入的管道能持有它。這時網路**進入休眠**，把緩衝寄存在這份世界存檔資料
 * 裡，並以某根未載入管道的位置當作 key；那根管道重新載入（或伺服器重啟後載入）時，
 * 金額會回到重建起來的網路。因此「拆最後一根已載入管道」不會再讓能量消失。
 *
 * <p>資料格式：{@code entries: [{pos: long, amount: long}, ...]}，一個 key 一筆。
 */
public class DormantEmcData extends SavedData {

	private static final String DATA_NAME = "transmutationconvenient_dormant_emc";
	private static final String ENTRIES_KEY = "entries";
	private static final String POS_KEY = "pos";
	private static final String AMOUNT_KEY = "amount";

	private final Map<Long, Long> amounts = new HashMap<>();

	/** 取得這個世界的休眠緩衝資料（世界第一次呼叫時建立）。 */
	public static DormantEmcData get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(
				DormantEmcData::load, DormantEmcData::new, DATA_NAME);
	}

	public static DormantEmcData load(CompoundTag tag) {
		DormantEmcData data = new DormantEmcData();
		ListTag list = tag.getList(ENTRIES_KEY, Tag.TAG_COMPOUND);
		for (int index = 0; index < list.size(); index++) {
			CompoundTag entry = list.getCompound(index);
			long amount = entry.getLong(AMOUNT_KEY);
			if (amount > 0L) {
				data.amounts.merge(entry.getLong(POS_KEY), amount, DormantEmcData::saturatedAdd);
			}
		}
		return data;
	}

	@Override
	public CompoundTag save(CompoundTag tag) {
		ListTag list = new ListTag();
		amounts.forEach((pos, amount) -> {
			CompoundTag entry = new CompoundTag();
			entry.putLong(POS_KEY, pos);
			entry.putLong(AMOUNT_KEY, amount);
			list.add(entry);
		});
		tag.put(ENTRIES_KEY, list);
		return tag;
	}

	/** 記下一筆寄放的緩衝（同一個 key 再加就累加）。 */
	public void put(long pos, long amount) {
		if (amount <= 0L) {
			return;
		}
		amounts.merge(pos, amount, DormantEmcData::saturatedAdd);
		setDirty();
	}

	/** 把某個 key 寄放的緩衝領出來（沒有就回 0）。 */
	public long take(long pos) {
		Long amount = amounts.remove(pos);
		if (amount == null) {
			return 0L;
		}
		setDirty();
		return amount;
	}

	private static long saturatedAdd(long left, long right) {
		long sum = left + right;
		return sum < 0L ? Long.MAX_VALUE : sum;
	}
}
