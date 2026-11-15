package com.transmutationconvenient.bucket;

import com.transmutationconvenient.network.NetworkHandler;
import com.transmutationconvenient.registry.ModMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * 黑洞之桶的儲存空間選單。
 *
 * <p>版面：左邊是流體側邊欄（寬 44），右邊是原版大箱子（176×222）的物品區；
 * 整體寬 220、高 222。物品區的 54 格排法與原版大箱子完全相同，玩家背包與快捷欄座標也照原版。
 *
 * <p>點擊規則不是原版槽位規則，全部由 {@link #clicked} 自己處理：
 * <ul>
 *   <li>左鍵點空格：取出一組（最多該物品的原版堆疊上限）。</li>
 *   <li>右鍵點空格：取出 1 個。</li>
 *   <li>手上有同種物品（或格位是空的）：整疊塞進桶裡。</li>
 *   <li>Shift 點桶內格：一組一組搬進背包；Shift 點背包格：整疊塞進桶裡。</li>
 *   <li>數字鍵（SWAP）：把桶內一組換到該快捷欄格；Q（THROW）：丟出一組。</li>
 * </ul>
 *
 * <p>同步：物品圖示由原版槽位同步處理（顯示堆疊數量固定為 1，實際總量由 GUI 自己畫）；
 * 「每格總量」與「流體清單」走 {@link NetworkHandler.BucketDataPacket}。
 */
public class BlackHoleBucketMenu extends AbstractContainerMenu {

	public static final int BUCKET_SLOTS = BucketStorage.SLOT_COUNT;
	public static final int PLAYER_INV_START = BUCKET_SLOTS;

	/** 左側流體側邊欄寬度。 */
	public static final int SIDEBAR_WIDTH = 44;
	/** 整個介面寬高（側邊欄 + 原版大箱子）。 */
	public static final int IMAGE_WIDTH = SIDEBAR_WIDTH + 176;
	public static final int IMAGE_HEIGHT = 222;
	/** 原版大箱子貼圖在介面裡的左上角。 */
	public static final int CHEST_X = SIDEBAR_WIDTH;
	public static final int CHEST_SLOT_X = CHEST_X + 8;
	public static final int CHEST_SLOT_Y = 18;
	public static final int PLAYER_INV_X = CHEST_X + 8;
	public static final int PLAYER_INV_Y = 140;
	public static final int HOTBAR_Y = 198;

	/** 側邊欄內流體槽的區域。 */
	public static final int SIDEBAR_PANEL_HEIGHT = 126;
	public static final int FLUID_SLOT_X = 6;
	public static final int FLUID_SLOT_WIDTH = 32;
	public static final int FLUID_AREA_Y = 8;
	public static final int FLUID_AREA_HEIGHT = SIDEBAR_PANEL_HEIGHT - FLUID_AREA_Y * 2;
	public static final int FLUID_GAP = 2;

	private final Container container;
	private final Player player;
	private final ItemStack owner;
	private final BucketStorage storage;

	private final long[] clientCounts;
	private final List<FluidStack> clientFluids;

	private int lastSyncedVersion;

	/** 服務端建構：資料直接接在玩家手上那一疊物品的 NBT 上。 */
	public BlackHoleBucketMenu(int containerId, Inventory inventory, ItemStack owner) {
		super(ModMenus.BLACK_HOLE_BUCKET.get(), containerId);
		this.player = inventory.player;
		this.owner = owner;
		this.storage = BucketStorage.fromStack(owner);
		this.clientCounts = new long[BUCKET_SLOTS];
		this.clientFluids = new ArrayList<>();
		this.lastSyncedVersion = this.storage.getVersion();
		this.container = new StorageContainer();
		initSlots(inventory);
	}

	/** 客戶端建構：從開啟封包帶來的資料還原顯示用資料。 */
	public BlackHoleBucketMenu(int containerId, Inventory inventory, FriendlyByteBuf buf) {
		super(ModMenus.BLACK_HOLE_BUCKET.get(), containerId);
		this.player = inventory.player;
		this.owner = ItemStack.EMPTY;
		this.storage = null;
		this.clientCounts = new long[BUCKET_SLOTS];
		this.clientFluids = new ArrayList<>();
		BucketStorage.readClientData(buf, this.clientCounts, this.clientFluids);
		this.container = new SimpleContainer(BUCKET_SLOTS);
		initSlots(inventory);
	}

	private void initSlots(Inventory inventory) {
		for (int row = 0; row < 6; row++) {
			for (int col = 0; col < 9; col++) {
				int index = col + row * 9;
				this.addSlot(new Slot(this.container, index,
						CHEST_SLOT_X + col * 18, CHEST_SLOT_Y + row * 18));
			}
		}
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				int inventoryIndex = 9 + row * 9 + col;
				this.addSlot(new Slot(inventory, inventoryIndex,
						PLAYER_INV_X + col * 18, PLAYER_INV_Y + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(inventory, col, PLAYER_INV_X + col * 18, HOTBAR_Y));
		}
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	// ------------------------------------------------------------ 點擊處理

	@Override
	public void clicked(int slotId, int button, ClickType type, Player clicker) {
		if (slotId >= 0 && slotId < BUCKET_SLOTS && this.storage != null) {
			handleBucketClick(slotId, button, type, clicker);
			return;
		}
		super.clicked(slotId, button, type, clicker);
	}

	private void handleBucketClick(int slotId, int button, ClickType type, Player clicker) {
		switch (type) {
			case PICKUP, PICKUP_ALL -> handlePickup(slotId, button);
			case QUICK_MOVE -> moveBucketToPlayer(slotId);
			case SWAP -> swapWithHotbar(slotId, button, clicker);
			case THROW -> throwGroup(slotId, button, clicker);
			default -> {
				// QUICK_CRAFT 等拖曳操作在桶內格不支援
			}
		}
	}

	private void handlePickup(int slotId, int button) {
		ItemStack carried = getCarried();
		if (carried.isEmpty()) {
			int amount = button == 1 ? 1 : 64;
			ItemStack taken = this.storage.extractItem(slotId, amount);
			if (!taken.isEmpty()) {
				setCarried(taken);
				persist();
			}
			return;
		}
		ItemStack remainder = this.storage.insertItemInto(slotId, carried);
		if (remainder.getCount() != carried.getCount()) {
			setCarried(remainder);
			persist();
		}
	}

	private void moveBucketToPlayer(int slotId) {
		ItemStack taken = this.storage.extractItem(slotId, 64);
		if (taken.isEmpty()) {
			return;
		}
		ItemStack moving = taken.copy();
		boolean moved = moveItemStackTo(moving, BUCKET_SLOTS, this.slots.size(), true);
		if (!moved) {
			this.storage.insertItem(taken);
			persist();
			return;
		}
		if (!moving.isEmpty()) {
			this.storage.insertItem(moving);
		}
		persist();
	}

	private void swapWithHotbar(int slotId, int hotbarIndex, Player clicker) {
		if (hotbarIndex < 0 || hotbarIndex > 8) {
			return;
		}
		ItemStack hotbarStack = clicker.getInventory().getItem(hotbarIndex);
		if (hotbarStack.isEmpty()) {
			ItemStack taken = this.storage.extractItem(slotId, 64);
			if (!taken.isEmpty()) {
				clicker.getInventory().setItem(hotbarIndex, taken);
				persist();
			}
			return;
		}
		ItemStack remainder = this.storage.insertItemInto(slotId, hotbarStack);
		if (remainder.getCount() != hotbarStack.getCount()) {
			clicker.getInventory().setItem(hotbarIndex, remainder);
			persist();
		}
	}

	private void throwGroup(int slotId, int button, Player clicker) {
		ItemStack taken = this.storage.extractItem(slotId, button == 0 ? 64 : 1);
		if (!taken.isEmpty()) {
			clicker.drop(taken, true);
			persist();
		}
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		if (this.storage == null) {
			return ItemStack.EMPTY;
		}
		Slot slot = this.slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		if (index < BUCKET_SLOTS) {
			moveBucketToPlayer(index);
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack remainder = this.storage.insertItem(stack.copy());
		if (remainder.getCount() != stack.getCount()) {
			slot.set(remainder);
			persist();
		}
		return ItemStack.EMPTY;
	}

	// ------------------------------------------------------------ 同步

	@Override
	public void broadcastChanges() {
		super.broadcastChanges();
		if (this.storage != null && this.player instanceof ServerPlayer serverPlayer) {
			int version = this.storage.getVersion();
			if (version != this.lastSyncedVersion) {
				this.lastSyncedVersion = version;
				NetworkHandler.CHANNEL.send(
						PacketDistributor.PLAYER.with(() -> serverPlayer),
						new NetworkHandler.BucketDataPacket(this.containerId, this.storage.getCounts(), this.storage.getFluids()));
			}
		}
	}

	public void applyClientData(int containerId, long[] counts, List<FluidStack> fluids) {
		if (this.storage != null || this.containerId != containerId) {
			return;
		}
		System.arraycopy(counts, 0, this.clientCounts, 0, Math.min(counts.length, this.clientCounts.length));
		this.clientFluids.clear();
		this.clientFluids.addAll(fluids);
	}

	public long getStoredCount(int slot) {
		if (this.storage != null) {
			return this.storage.getCount(slot);
		}
		return slot >= 0 && slot < this.clientCounts.length ? this.clientCounts[slot] : 0L;
	}

	public List<FluidStack> getFluids() {
		if (this.storage != null) {
			return this.storage.getFluids();
		}
		return this.clientFluids;
	}

	public boolean isOwnerStack(ItemStack stack) {
		return this.owner == stack;
	}

	/** 把指定流體抽出一桶，放到玩家游標上的空桶裡（客戶端點流體槽時呼叫）。 */
	public void extractFluidToCursor(int index) {
		if (this.storage == null) {
			return;
		}
		ItemStack carried = getCarried();
		if (!carried.is(Items.BUCKET)) {
			return;
		}
		FluidStack stored = this.storage.getFluid(index);
		if (stored.getAmount() < 1000) {
			return;
		}
		FluidStack single = stored.copy();
		single.setAmount(1000);
		ItemStack filled = FluidUtil.getFilledBucket(single);
		if (filled.isEmpty()) {
			return;
		}
		this.storage.extractFluid(index, 1000);
		if (carried.getCount() == 1) {
			setCarried(filled);
		} else {
			carried.shrink(1);
			if (!this.player.getInventory().add(filled)) {
				this.player.drop(filled, false);
			}
		}
		persist();
	}

	private void persist() {
		if (this.storage != null && !this.owner.isEmpty()) {
			this.storage.saveTo(this.owner);
		}
	}

	// -------------------------------------------------------- 流體槽幾何

	public static int fluidSlotHeight(int count) {
		if (count <= 0) {
			return 0;
		}
		int available = FLUID_AREA_HEIGHT - (count - 1) * FLUID_GAP;
		if (available < count) {
			available = FLUID_AREA_HEIGHT;
		}
		return Math.max(1, available / count);
	}

	public static int fluidSlotY(int index, int count) {
		return FLUID_AREA_Y + index * (fluidSlotHeight(count) + FLUID_GAP);
	}

	// ------------------------------------------------------------ 服務端容器

	/** 把 {@link BucketStorage} 包成原版 {@link Container}，讓槽位同步與 GUI 顯示拿到物品圖示。 */
	private final class StorageContainer implements Container {

		@Override
		public int getContainerSize() {
			return BUCKET_SLOTS;
		}

		@Override
		public boolean isEmpty() {
			return storage.isEmpty();
		}

		@Override
		public ItemStack getItem(int slot) {
			return storage.getDisplayStack(slot);
		}

		@Override
		public ItemStack removeItem(int slot, int amount) {
			ItemStack out = storage.extractItem(slot, amount);
			if (!out.isEmpty()) {
				persist();
			}
			return out;
		}

		@Override
		public ItemStack removeItemNoUpdate(int slot) {
			ItemStack out = storage.extractItem(slot, 64);
			if (!out.isEmpty()) {
				persist();
			}
			return out;
		}

		@Override
		public void setItem(int slot, ItemStack stack) {
			if (stack.isEmpty()) {
				storage.clearSlot(slot);
			} else {
				storage.insertItemInto(slot, stack);
			}
			persist();
		}

		@Override
		public void setChanged() {
		}

		@Override
		public boolean stillValid(Player player) {
			return true;
		}

		@Override
		public void clearContent() {
			for (int i = 0; i < BUCKET_SLOTS; i++) {
				storage.clearSlot(i);
			}
			persist();
		}
	}
}
