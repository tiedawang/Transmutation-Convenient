package com.transmutationconvenient.pipe;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 「誰放了這張轉化桌」的紀錄 —— 存在世界存檔裡。
 *
 * <p>ProjectE 的轉化桌方塊本身不儲存主人：它是純方塊、沒有方塊實體，
 * 誰右鍵就顯示誰的 EMC 帳戶。要把 EMC 打進「放置者的帳戶」，
 * 只能由本模組自己記住桌子與放置者的對應關係；紀錄寫在
 * 主世界的 {@code world/data/transmutationconvenient_table_owners.dat} 裡，
 * 跨存檔重啟都還在。
 *
 * <p>沒被記錄的桌子（模組裝上之前就存在的、用指令放的）由
 * {@link EmcTableOwners} 退回「桌子附近的最近玩家」。
 */
public class TableOwnerData extends SavedData {

	private static final String DATA_NAME = "transmutationconvenient_table_owners";
	private static final String OWNERS_KEY = "owners";
	private static final String DIMENSION_KEY = "dimension";
	private static final String POS_KEY = "pos";
	private static final String OWNER_KEY = "owner";

	private final Map<String, UUID> owners = new HashMap<>();

	public static TableOwnerData get(MinecraftServer server) {
		return server.overworld().getDataStorage()
				.computeIfAbsent(TableOwnerData::load, TableOwnerData::new, DATA_NAME);
	}

	public static TableOwnerData load(CompoundTag tag) {
		TableOwnerData data = new TableOwnerData();
		ListTag list = tag.getList(OWNERS_KEY, Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag entry = list.getCompound(i);
			try {
				UUID owner = UUID.fromString(entry.getString(OWNER_KEY));
				data.owners.put(key(entry.getString(DIMENSION_KEY), entry.getLong(POS_KEY)), owner);
			} catch (IllegalArgumentException ignored) {
				// 壞掉的紀錄直接跳過，不讓一個 UUID 拖垮整份資料
			}
		}
		return data;
	}

	@Override
	public CompoundTag save(CompoundTag tag) {
		ListTag list = new ListTag();
		owners.forEach((key, owner) -> {
			int at = key.lastIndexOf('@');
			CompoundTag entry = new CompoundTag();
			entry.putString(DIMENSION_KEY, key.substring(0, at));
			entry.putLong(POS_KEY, Long.parseLong(key.substring(at + 1)));
			entry.putString(OWNER_KEY, owner.toString());
			list.add(entry);
		});
		tag.put(OWNERS_KEY, list);
		return tag;
	}

	public void setOwner(ResourceKey<Level> dimension, BlockPos pos, UUID owner) {
		owners.put(key(dimension.location().toString(), pos.asLong()), owner);
		setDirty();
	}

	@Nullable
	public UUID ownerOf(ResourceKey<Level> dimension, BlockPos pos) {
		return owners.get(key(dimension.location().toString(), pos.asLong()));
	}

	private static String key(String dimension, long pos) {
		return dimension + "@" + pos;
	}
}
