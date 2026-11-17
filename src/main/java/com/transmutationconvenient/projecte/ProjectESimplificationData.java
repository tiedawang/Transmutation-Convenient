package com.transmutationconvenient.projecte;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 每個世界各自的 ProjectE 精簡狀態。
 *
 * <p>存檔放在主世界資料夾；切換世界或重開伺服器都會沿用，不會因為重啟而把
 * 已經移除的配方默默加回來。
 */
public final class ProjectESimplificationData extends SavedData {

	private static final String DATA_NAME = "transmutationconvenient_projecte_simplification";
	private static final String SIMPLIFIED_KEY = "simplified";

	private boolean simplified;

	public static ProjectESimplificationData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(
				ProjectESimplificationData::load,
				ProjectESimplificationData::new,
				DATA_NAME);
	}

	private static ProjectESimplificationData load(CompoundTag tag) {
		ProjectESimplificationData data = new ProjectESimplificationData();
		data.simplified = tag.getBoolean(SIMPLIFIED_KEY);
		return data;
	}

	@Override
	public CompoundTag save(CompoundTag tag) {
		tag.putBoolean(SIMPLIFIED_KEY, simplified);
		return tag;
	}

	public boolean isSimplified() {
		return simplified;
	}

	public void setSimplified(boolean simplified) {
		this.simplified = simplified;
		setDirty();
	}
}
