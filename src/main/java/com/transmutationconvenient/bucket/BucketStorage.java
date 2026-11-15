package com.transmutationconvenient.bucket;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 黑洞之桶的內部儲存。
 *
 * <p>物品區：54 格，與原版大箱子同容量。每一格存的是「物品模板（數量固定為 1）＋ 一個 64 位元長整數的總量」，
 * 所以同一格可以無限堆疊（兩把鑽石劍、兩組泥土都可以放同一格）；取出時一次最多只取「該物品的原版堆疊上限」，
 * 這條規則由 {@link #extractItem(int, int)} 依物品的 {@code getMaxStackSize()} 做上限裁切。
 *
 * <p>流體區：以 {@link FluidStack} 記錄每一種流體與其存量，<b>每一種流體各自</b>可以存到
 * {@link #FLUID_CAPACITY}（1000 桶 = 1000000 mB），互不共用上限。流體種類不設限，
 * GUI 會依種類數把側邊欄切給它們。
 *
 * <p>資料直接寫在物品的 NBT 裡，欄位是 {@code Items}（清單，每項 {@code Slot}/{@code Item}/{@code Count}）
 * 與 {@code Fluids}（清單，每項就是 {@link FluidStack} 的 NBT）。{@link #getVersion()} 在每次變動時遞增，
 * 容器選單用它判斷要不要把新資料同步給客戶端。
 */
public final class BucketStorage {

	/** 物品槽數（與原版大箱子相同）。 */
	public static final int SLOT_COUNT = 54;

	/** 每一種流體各自的上限（1000 桶）。 */
	public static final long FLUID_CAPACITY = 1_000_000L;

	private static final String TAG_ITEMS = "Items";
	private static final String TAG_FLUIDS = "Fluids";
	private static final String TAG_SLOT = "Slot";
	private static final String TAG_ITEM = "Item";
	private static final String TAG_COUNT = "Count";

	private final ItemStack[] itemTemplates = new ItemStack[SLOT_COUNT];
	private final long[] counts = new long[SLOT_COUNT];
	private final List<FluidStack> fluids = new ArrayList<>();

	private int version;

	public BucketStorage() {
		for (int i = 0; i < SLOT_COUNT; i++) {
			itemTemplates[i] = ItemStack.EMPTY;
		}
	}

	/** 從物品 NBT 讀出储存；沒有 NBT 就是空桶。 */
	public static BucketStorage fromStack(ItemStack stack) {
		return load(stack.getOrCreateTag());
	}

	/** 把儲存寫回物品 NBT。 */
	public void saveTo(ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		write(stack.getOrCreateTag());
	}

	public static BucketStorage load(CompoundTag tag) {
		BucketStorage storage = new BucketStorage();
		ListTag items = tag.getList(TAG_ITEMS, Tag.TAG_COMPOUND);
		for (int i = 0; i < items.size(); i++) {
			CompoundTag entry = items.getCompound(i);
			int slot = entry.getByte(TAG_SLOT) & 0xFF;
			if (slot >= SLOT_COUNT) {
				continue;
			}
			ItemStack item = ItemStack.of(entry.getCompound(TAG_ITEM));
			long count = entry.getLong(TAG_COUNT);
			if (item.isEmpty() || count <= 0L) {
				continue;
			}
			storage.itemTemplates[slot] = item.copyWithCount(1);
			storage.counts[slot] = count;
		}
		ListTag fluidList = tag.getList(TAG_FLUIDS, Tag.TAG_COMPOUND);
		for (int i = 0; i < fluidList.size(); i++) {
			FluidStack stack = FluidStack.loadFluidStackFromNBT(fluidList.getCompound(i));
			if (!stack.isEmpty()) {
				// 走 insertFluid 讓舊存檔裡「水源 + 流水」兩筆同種流體自動併成一筆
				storage.insertFluid(stack);
			}
		}
		return storage;
	}

	public void write(CompoundTag tag) {
		ListTag items = new ListTag();
		for (int i = 0; i < SLOT_COUNT; i++) {
			if (counts[i] <= 0L) {
				continue;
			}
			CompoundTag entry = new CompoundTag();
			entry.putByte(TAG_SLOT, (byte) i);
			CompoundTag itemTag = new CompoundTag();
			itemTemplates[i].copyWithCount(1).save(itemTag);
			entry.put(TAG_ITEM, itemTag);
			entry.putLong(TAG_COUNT, counts[i]);
			items.add(entry);
		}
		tag.put(TAG_ITEMS, items);

		ListTag fluidList = new ListTag();
		for (FluidStack fluid : fluids) {
			fluidList.add(fluid.writeToNBT(new CompoundTag()));
		}
		tag.put(TAG_FLUIDS, fluidList);
	}

	public int getVersion() {
		return version;
	}

	public long getCount(int slot) {
		return slot >= 0 && slot < SLOT_COUNT ? counts[slot] : 0L;
	}

	/** 給 GUI 顯示用的物品（數量固定 1，總量由 GUI 另外畫）。 */
	public ItemStack getDisplayStack(int slot) {
		if (slot < 0 || slot >= SLOT_COUNT || counts[slot] <= 0L) {
			return ItemStack.EMPTY;
		}
		return itemTemplates[slot].copyWithCount(1);
	}

	public ItemStack getTemplate(int slot) {
		return slot >= 0 && slot < SLOT_COUNT ? itemTemplates[slot] : ItemStack.EMPTY;
	}

	/** 把一整疊物品收進儲存，回傳收不下的部分（全收下就是空）。 */
	public ItemStack insertItem(ItemStack stack) {
		if (stack.isEmpty()) {
			return ItemStack.EMPTY;
		}
		int slot = findSlotFor(stack);
		if (slot < 0) {
			return stack;
		}
		return insertItemInto(slot, stack);
	}

	/** 把一整疊物品收進指定格；該格必須是空的或同一種物品。 */
	public ItemStack insertItemInto(int slot, ItemStack stack) {
		if (stack.isEmpty() || slot < 0 || slot >= SLOT_COUNT) {
			return stack;
		}
		if (counts[slot] > 0L && !ItemStack.isSameItemSameTags(itemTemplates[slot], stack)) {
			return stack;
		}
		if (counts[slot] <= 0L) {
			itemTemplates[slot] = stack.copyWithCount(1);
		}
		counts[slot] += stack.getCount();
		version++;
		return ItemStack.EMPTY;
	}

	private int findSlotFor(ItemStack stack) {
		for (int i = 0; i < SLOT_COUNT; i++) {
			if (counts[i] > 0L && ItemStack.isSameItemSameTags(itemTemplates[i], stack)) {
				return i;
			}
		}
		for (int i = 0; i < SLOT_COUNT; i++) {
			if (counts[i] <= 0L) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * 從指定格取出一組物品。
	 *
	 * <p>一次最多取「該物品的原版堆疊上限」：鑽石劍只取 1 把、泥土最多取 64 個，
	 * 就算那一格堆了兩把劍或 128 個泥土也一樣。
	 */
	public ItemStack extractItem(int slot, int maxAmount) {
		if (slot < 0 || slot >= SLOT_COUNT || counts[slot] <= 0L || maxAmount <= 0) {
			return ItemStack.EMPTY;
		}
		int perStack = Math.max(1, itemTemplates[slot].getMaxStackSize());
		int take = (int) Math.min(Math.min(maxAmount, perStack), counts[slot]);
		if (take <= 0) {
			return ItemStack.EMPTY;
		}
		ItemStack out = itemTemplates[slot].copyWithCount(take);
		counts[slot] -= take;
		if (counts[slot] <= 0L) {
			counts[slot] = 0L;
			itemTemplates[slot] = ItemStack.EMPTY;
		}
		version++;
		return out;
	}

	public void clearSlot(int slot) {
		if (slot < 0 || slot >= SLOT_COUNT) {
			return;
		}
		counts[slot] = 0L;
		itemTemplates[slot] = ItemStack.EMPTY;
		version++;
	}

	public boolean isEmpty() {
		for (int i = 0; i < SLOT_COUNT; i++) {
			if (counts[i] > 0L) {
				return false;
			}
		}
		return fluids.isEmpty();
	}

	// ------------------------------------------------------------------ 流體

	/** 指定流體目前的存量（自動把流動流體換算回來源流體）。 */
	public long getFluidAmount(Fluid fluid) {
		Fluid source = toSourceFluid(fluid);
		long amount = 0L;
		for (FluidStack existing : fluids) {
			if (existing.getFluid() == source) {
				amount += existing.getAmount();
			}
		}
		return amount;
	}

	/**
	 * 收流體，回傳實際收下的 mB 數（該種流體自己滿了就只收到上限）。
	 *
	 * <p>只收「整桶」：不足 1000 mB 的零頭固定向下取整丟棄，所以桶內存量永遠是整數桶，
	 * 不會出現 0.6 桶這種數字。
	 */
	public int insertFluid(FluidStack stack) {
		if (stack.isEmpty()) {
			return 0;
		}
		// 流動水／流動岩漿的 Fluid 是 flowing_water／flowing_lava，跟水源不是同一個物件；
		// 一律換算回水源再存，否則同一種液體會在側邊欄被拆成兩個槽。
		Fluid fluid = toSourceFluid(stack.getFluid());
		int wholeMilliBuckets = stack.getAmount() / 1000 * 1000;
		if (wholeMilliBuckets <= 0) {
			return 0;
		}
		long room = FLUID_CAPACITY - getFluidAmount(fluid);
		if (room <= 0L) {
			return 0;
		}
		int accepted = (int) Math.min(wholeMilliBuckets, room);
		if (accepted <= 0) {
			return 0;
		}
		for (FluidStack existing : fluids) {
			if (existing.getFluid() == fluid && Objects.equals(existing.getTag(), stack.getTag())) {
				existing.setAmount(existing.getAmount() + accepted);
				version++;
				return accepted;
			}
		}
		fluids.add(new FluidStack(fluid, accepted, stack.getTag()));
		version++;
		return accepted;
	}

	/** 把流動流體換算成它的來源流體（水／岩漿等）；其他流體原樣回傳。 */
	public static Fluid toSourceFluid(Fluid fluid) {
		if (fluid instanceof FlowingFluid flowing) {
			Fluid source = flowing.getSource();
			if (source != null) {
				return source;
			}
		}
		return fluid;
	}

	public int getFluidCount() {
		return fluids.size();
	}

	public FluidStack getFluid(int index) {
		return index >= 0 && index < fluids.size() ? fluids.get(index) : FluidStack.EMPTY;
	}

	/** 回傳一份複本（GUI 用，避免外部改到內部資料）。 */
	public List<FluidStack> getFluids() {
		List<FluidStack> copy = new ArrayList<>(fluids.size());
		for (FluidStack fluid : fluids) {
			copy.add(fluid.copy());
		}
		return copy;
	}

	/** 取出指定流體，成功回傳 true；數量不足就不動。 */
	public boolean extractFluid(int index, int amount) {
		if (index < 0 || index >= fluids.size() || amount <= 0) {
			return false;
		}
		FluidStack fluid = fluids.get(index);
		if (fluid.getAmount() < amount) {
			return false;
		}
		fluid.setAmount(fluid.getAmount() - amount);
		if (fluid.getAmount() <= 0) {
			fluids.remove(index);
		}
		version++;
		return true;
	}

	public long[] getCounts() {
		return counts.clone();
	}

	// ------------------------------------------------------------ 客戶端同步

	/** 開啟 GUI 時把完整資料寫給客戶端。 */
	public void writeClientData(FriendlyByteBuf buf) {
		for (int i = 0; i < SLOT_COUNT; i++) {
			buf.writeLong(counts[i]);
		}
		buf.writeVarInt(fluids.size());
		for (FluidStack fluid : fluids) {
			fluid.writeToPacket(buf);
		}
	}

	/** 客戶端建立選單時讀取完整資料。 */
	public static void readClientData(FriendlyByteBuf buf, long[] counts, List<FluidStack> fluids) {
		for (int i = 0; i < SLOT_COUNT; i++) {
			counts[i] = buf.readLong();
		}
		int size = buf.readVarInt();
		for (int i = 0; i < size; i++) {
			fluids.add(FluidStack.readFromPacket(buf));
		}
	}
}
