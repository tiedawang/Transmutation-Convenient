package com.transmutationconvenient.pipe;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * 端點優先級（潔淨室規格書 11.7）。
 *
 * <p>只有四類方塊有優先級，其他方塊（含其他模組的 EMC 儲存方塊）沒有，
 * 不參與管道的輸送：
 * <ul>
 *   <li>轉化桌：3</li>
 *   <li>能量凝聚器 MK1、MK2：3</li>
 *   <li>反物質繼電器 MK1、MK2、MK3：2</li>
 *   <li>能量收集器 MK1、MK2、MK3：1</li>
 * </ul>
 *
 * <p>數值照規格書寫死，不做任何換算。優先級只認方塊的註冊名。
 */
public final class EmcPriority {

	/** 轉化桌的優先級（它同時也是「沒有方塊實體、只能靠註冊名辨認」的端點）。 */
	public static final int TABLE = 3;

	private static final ResourceLocation TABLE_ID = new ResourceLocation("projecte", "transmutation_table");

	private static final Map<ResourceLocation, Integer> PRIORITIES = new HashMap<>();

	static {
		put("condenser_mk1", 3);
		put("condenser_mk2", 3);
		put("relay_mk1", 2);
		put("relay_mk2", 2);
		put("relay_mk3", 2);
		put("collector_mk1", 1);
		put("collector_mk2", 1);
		put("collector_mk3", 1);
	}

	private EmcPriority() {
	}

	private static void put(String path, int priority) {
		PRIORITIES.put(new ResourceLocation("projecte", path), priority);
	}

	/** 這個方塊是不是轉化桌（沒有方塊實體、沒有 EMC 儲存能力，只能靠註冊名辨認）。 */
	public static boolean isTransmutationTable(BlockState state) {
		return TABLE_ID.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
	}

	/** 這個方塊的優先級；沒有優先級的方塊回傳 null（不參與輸送）。 */
	@Nullable
	public static Integer priorityOf(Block block) {
		return PRIORITIES.get(BuiltInRegistries.BLOCK.getKey(block));
	}
}
