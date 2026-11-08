package com.transmutationconvenient.machine;

import com.transmutationconvenient.compat.ProjectEBridge;
import com.transmutationconvenient.pipe.EmcTableOwners;
import com.transmutationconvenient.registry.ModBlockEntities;
import moze_intel.projecte.api.capabilities.block_entity.IEmcStorage.EmcAction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 自動販賣機的方塊實體 —— 一個大箱子份量的物品欄，加一格「暫存 EMC」。
 *
 * <p>每個遊戲刻的動作順序照規格固定：
 * <ol>
 *   <li>先看自己暫存了多少 EMC，全部打進這台機器放置者的 EMC 帳戶；</li>
 *   <li>再把物品欄裡所有有 EMC 價值的物品一次賣光，換到的 EMC 暫存起來；</li>
 *   <li>下一個遊戲刻從第一步重來。</li>
 * </ol>
 * 因此每個遊戲刻「有多少物品就賣多少、有多少 EMC 就打多少 EMC」，沒有速度上限，
 * 也沒有限流 —— 販賣量只取決於這一刻物品欄裡有什麼。
 *
 * <p>暫存的 EMC 不進管網、也不當成一般的 EMC 存儲：它唯一的目的地就是玩家帳戶。
 * 放置者離線、或找不到人時 {@link EmcTableOwners#deposit} 回傳 0，這批 EMC 留在
 * 機器裡等主人回來，不會憑空消失。
 *
 * <p>物品欄透過原版 {@link Container} 介面開放，因此大箱子介面、漏斗、其他模組的
 * 物品輸送都能直接使用，不需要本模組另外寫一套搬運邏輯。
 */
public class AutoVendingMachineBlockEntity extends BlockEntity implements Container {

	/** 物品欄格數：一個大箱子的份量（6 排 × 9 格）。 */
	public static final int SLOTS = 54;

	private static final String EMC_TAG = "PendingEmc";

	private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);

	/** 已經賣掉、還沒打進玩家帳戶的 EMC。 */
	private long pendingEmc;

	public AutoVendingMachineBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.AUTO_VENDING_MACHINE.get(), pos, state);
	}

	/** 服務端每個遊戲刻跑一次；客戶端沒有方塊實體邏輯。 */
	public static void serverTick(Level level, BlockPos pos, BlockState state, AutoVendingMachineBlockEntity machine) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		machine.tick(serverLevel);
	}

	private void tick(ServerLevel level) {
		// 第一步：把暫存的 EMC 全部打進放置者的帳戶。
		if (pendingEmc > 0L) {
			long deposited = EmcTableOwners.deposit(level, worldPosition, pendingEmc, EmcAction.EXECUTE);
			if (deposited > 0L) {
				pendingEmc -= deposited;
				setChanged();
			}
		}
		// 第二步：賣掉物品欄裡所有有價值的物品，換成 EMC 暫存。
		long earned = sellAll();
		if (earned > 0L) {
			pendingEmc += earned;
			setChanged();
		}
	}

	/**
	 * 把物品欄裡所有有 EMC 價值的物品一次賣光，回傳換到的 EMC 總額。
	 *
	 * <p>用 ProjectE 的出售價（{@code getSellValue}），與轉化桌折算 EMC 用的是同一個值；
	 * 沒有 EMC 價值的物品賣不掉，留在原位（不吞物品、也不憑空生 EMC）。
	 */
	private long sellAll() {
		long total = 0L;
		for (int slot = 0; slot < SLOTS; slot++) {
			ItemStack stack = items.get(slot);
			if (stack.isEmpty()) {
				continue;
			}
			long value = ProjectEBridge.sellValueOf(stack);
			if (value <= 0L) {
				continue;
			}
			total += value * stack.getCount();
			items.set(slot, ItemStack.EMPTY);
		}
		return total;
	}

	/** 這台機器暫存、還沒打進玩家帳戶的 EMC。 */
	public long pendingEmc() {
		return pendingEmc;
	}

	public Component getDisplayName() {
		return Component.translatable("block.transmutationconvenient.auto_vending_machine");
	}

	// ------------------------------------------------------------------
	// 物品欄（Container）
	// ------------------------------------------------------------------

	@Override
	public int getContainerSize() {
		return SLOTS;
	}

	@Override
	public boolean isEmpty() {
		for (ItemStack stack : items) {
			if (!stack.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public ItemStack getItem(int slot) {
		return items.get(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
		if (!removed.isEmpty()) {
			setChanged();
		}
		return removed;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return ContainerHelper.takeItem(items, slot);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		items.set(slot, stack);
		if (stack.getCount() > getMaxStackSize()) {
			stack.setCount(getMaxStackSize());
		}
		setChanged();
	}

	@Override
	public boolean stillValid(Player player) {
		return Container.stillValidBlockEntity(this, player);
	}

	@Override
	public void clearContent() {
		items.clear();
		setChanged();
	}

	// ------------------------------------------------------------------
	// 存檔
	// ------------------------------------------------------------------

	@Override
	protected void saveAdditional(CompoundTag tag) {
		super.saveAdditional(tag);
		ContainerHelper.saveAllItems(tag, items);
		tag.putLong(EMC_TAG, pendingEmc);
	}

	@Override
	public void load(CompoundTag tag) {
		super.load(tag);
		items.clear();
		ContainerHelper.loadAllItems(tag, items);
		pendingEmc = Math.max(0L, tag.getLong(EMC_TAG));
	}
}
