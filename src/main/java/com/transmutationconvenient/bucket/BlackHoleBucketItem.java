package com.transmutationconvenient.bucket;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.transmutationconvenient.machine.AutoVendingMachineBlock;
import com.transmutationconvenient.machine.AutoVendingMachineBlockEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 黑洞之桶。
 *
 * <p>三種模式（切換順序見 {@link BucketMode}）：
 * <ul>
 *   <li><b>吸引物品</b>：持續把周圍掉落物吸進桶內儲存空間。範圍沿用 ProjectE 黑洞指環的判定
 *       （玩家碰撞箱向外膨脹 7 格）再乘以二，也就是膨脹 14 格。</li>
 *   <li><b>吸引液體</b>：持續把半徑 10 格內的液體方塊吸進桶內流體儲存。</li>
 *   <li><b>不可控吸引</b>：右鍵不開儲存空間，改為發射一枚無盡貪婪的終望珍珠。</li>
 * </ul>
 *
 * <p>吸引物品、吸引液體這兩種模式下，右鍵都會打開儲存空間（GUI 見 {@code BlackHoleBucketScreen}）。
 * 物品本身放在背包裡就會持續作用，不必拿在手上；模式切換則要拿在手中按回車鍵。
 */
public class BlackHoleBucketItem extends Item {

	public static final String TAG_MODE = "Mode";

	/** ProjectE 黑洞指環的吸引範圍（玩家碰撞箱膨脹 7 格）。 */
	private static final double BLACK_HOLE_BAND_RANGE = 7.0D;

	/** 黑洞之桶的吸引物品範圍 = 黑洞指環 × 2。 */
	private static final double ITEM_ATTRACT_RANGE = BLACK_HOLE_BAND_RANGE * 2.0D;

	/** 吸引液體的半徑（格）。 */
	private static final int FLUID_ATTRACT_RADIUS = 10;

	private static final int FLUID_ATTRACT_RADIUS_SQUARED = FLUID_ATTRACT_RADIUS * FLUID_ATTRACT_RADIUS;

	private static final String ENDEST_PEARL_TOOL_CLASS = "committee.nova.mods.avaritia.util.ToolUtils";

	public BlackHoleBucketItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		BucketMode mode = getMode(stack);
		if (mode == BucketMode.UNCONTROLLED) {
			if (!level.isClientSide() && player instanceof ServerPlayer) {
				fireEndestPearl(level, player);
			}
			return InteractionResultHolder.success(stack);
		}
		if (player instanceof ServerPlayer serverPlayer) {
			BucketStorage storage = BucketStorage.fromStack(stack);
			NetworkHooks.openScreen(
					serverPlayer,
					new SimpleMenuProvider(
							(id, inventory, opener) -> new BlackHoleBucketMenu(id, inventory, stack),
							Component.translatable("container.transmutationconvenient.black_hole_bucket")),
					storage::writeClientData);
		}
		return InteractionResultHolder.success(stack);
	}

	/**
	 * 潛行＋右鍵自動販賣機：原版在潛行且手上有物品時會跳過方塊的 {@code use}，
	 * 直接走物品的 {@code useOn}，所以這條攔截放在物品這邊。
	 *
	 * <p>把桶裡所有可賣的物品與流體賣給那台機器；換到的 EMC 併進機器的暫存，
	 * 之後由機器自己的每刻入帳流程打進放置者帳戶（與機器賣自己物品是同一條路）。
	 */
	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (player == null || !player.isShiftKeyDown()) {
			return InteractionResult.PASS;
		}
		Level level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		if (!(level.getBlockState(pos).getBlock() instanceof AutoVendingMachineBlock)) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (level.getBlockEntity(pos) instanceof AutoVendingMachineBlockEntity machine) {
			machine.sellBucketContents(context.getItemInHand());
		}
		return InteractionResult.SUCCESS;
	}

	/**
	 * 背包裡每遊戲刻跑一次：依目前模式做事。
	 *
	 * <p>這一桶正被玩家打開（同一個桶的容器選單開著）時跳過：選單持有自己的一份儲存物件，
	 * 兩邊同時寫 NBT 會互相覆蓋，所以 GUI 開著時不做吸引。
	 */
	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
		if (level.isClientSide() || !(entity instanceof Player player)) {
			return;
		}
		if (player.containerMenu instanceof BlackHoleBucketMenu menu && menu.isOwnerStack(stack)) {
			return;
		}
		BucketMode mode = getMode(stack);
		switch (mode) {
			case ITEMS -> attractItems(level, player, stack);
			case FLUIDS -> attractFluids(level, player, stack);
			case UNCONTROLLED -> {
				// 不可控吸引模式只有右鍵發射珍珠，沒有被動效果
			}
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(getMode(stack).displayName());
	}

	public static BucketMode getMode(ItemStack stack) {
		CompoundTag tag = stack.getTag();
		if (tag == null || !tag.contains(TAG_MODE)) {
			return BucketMode.ITEMS;
		}
		return BucketMode.byId(tag.getString(TAG_MODE));
	}

	public static void setMode(ItemStack stack, BucketMode mode) {
		stack.getOrCreateTag().putString(TAG_MODE, mode.getId());
	}

	/** 玩家主手或副手是否拿著黑洞之桶（客戶端切換模式用）。 */
	public static boolean isHeldBy(Player player) {
		return player.getMainHandItem().getItem() instanceof BlackHoleBucketItem
				|| player.getOffhandItem().getItem() instanceof BlackHoleBucketItem;
	}

	// ------------------------------------------------------------ 吸引物品

	private static void attractItems(Level level, Player player, ItemStack bucketStack) {
		List<ItemEntity> entities = level.getEntitiesOfClass(
				ItemEntity.class,
				player.getBoundingBox().inflate(ITEM_ATTRACT_RANGE),
				entity -> !entity.isRemoved());
		if (entities.isEmpty()) {
			return;
		}
		BucketStorage storage = BucketStorage.fromStack(bucketStack);
		boolean changed = false;
		for (ItemEntity entity : entities) {
			ItemStack stack = entity.getItem();
			if (stack.isEmpty()) {
				continue;
			}
			ItemStack remainder = storage.insertItem(stack);
			if (remainder.getCount() == stack.getCount()) {
				continue;       // 54 格都被別種物品佔滿，這個收不進去
			}
			changed = true;
			if (remainder.isEmpty()) {
				entity.discard();
			} else {
				entity.setItem(remainder);
			}
		}
		if (changed) {
			storage.saveTo(bucketStack);
		}
	}

	// ------------------------------------------------------------ 吸引液體

	private static void attractFluids(Level level, Player player, ItemStack bucketStack) {
		BucketStorage storage = BucketStorage.fromStack(bucketStack);
		BlockPos center = player.blockPosition();
		int centerX = center.getX();
		int centerY = center.getY();
		int centerZ = center.getZ();
		int minY = level.getMinBuildHeight();
		int maxY = level.getMaxBuildHeight() - 1;
		Map<Long, LevelChunk> chunkCache = new HashMap<>();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		boolean changed = false;
		for (int dx = -FLUID_ATTRACT_RADIUS; dx <= FLUID_ATTRACT_RADIUS; dx++) {
			int x = centerX + dx;
			int chunkX = x >> 4;
			for (int dz = -FLUID_ATTRACT_RADIUS; dz <= FLUID_ATTRACT_RADIUS; dz++) {
				int remaining = FLUID_ATTRACT_RADIUS_SQUARED - dx * dx - dz * dz;
				if (remaining < 0) {
					continue;
				}
				int z = centerZ + dz;
				int chunkZ = z >> 4;
				long key = ChunkPos.asLong(chunkX, chunkZ);
				LevelChunk chunk = chunkCache.get(key);
				if (chunk == null) {
					chunk = level.getChunkSource().getChunk(chunkX, chunkZ, false);
					if (chunk == null) {
						continue;
					}
					chunkCache.put(key, chunk);
				}
				int maxDy = (int) Math.sqrt(remaining);
				int fromY = Math.max(minY, centerY - maxDy);
				int toY = Math.min(maxY, centerY + maxDy);
				for (int y = fromY; y <= toY; y++) {
					cursor.set(x, y, z);
					BlockState state = chunk.getBlockState(cursor);
					if (!(state.getBlock() instanceof LiquidBlock)) {
						continue;       // 只吸液體方塊，水草、含水方塊等不動
					}
					FluidState fluidState = state.getFluidState();
					if (fluidState.isEmpty()) {
						continue;
					}
					// 每個液體方塊都算完整的一桶（1000 mB）：水源是 1 桶，流動的液體也是 1 桶。
					// 這樣吸上來的量永遠是整數桶，不會出現「0.6 桶」這種零頭。
					int milliBuckets = 1000;
					Fluid sourceFluid = BucketStorage.toSourceFluid(fluidState.getType());
					if (BucketStorage.FLUID_CAPACITY - storage.getFluidAmount(sourceFluid) < milliBuckets) {
						continue;           // 這一種液體已經滿 1000 桶，跳過這一格（其他液體照收）
					}
					FluidStack fluidStack = new FluidStack(sourceFluid, milliBuckets);
					if (storage.insertFluid(fluidStack) != milliBuckets) {
						continue;
					}
					level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 3);
					changed = true;
				}
			}
		}
		if (changed) {
			storage.saveTo(bucketStack);
		}
	}

	// ------------------------------------------------------- 不可控吸引（珍珠）

	/**
	 * 發射一枚終望珍珠。
	 *
	 * <p>直接呼叫無盡貪婪自己的終望珍珠發射邏輯（與拿在手上右鍵那顆珍珠完全一致）；
	 * 無盡貪婪不在時退回原版終界珍珠，至少還是丟得出去。
	 */
	private static void fireEndestPearl(Level level, Player player) {
		if (ModList.get().isLoaded("avaritia")) {
			try {
				Item endestPearl = ForgeRegistries.ITEMS.getValue(new ResourceLocation("avaritia", "endest_pearl"));
				if (endestPearl != null) {
					Class<?> toolUtils = Class.forName(ENDEST_PEARL_TOOL_CLASS);
					Method pearlAttack = toolUtils.getMethod("pearlAttack", Player.class, ItemStack.class, Level.class);
					pearlAttack.invoke(null, player, new ItemStack(endestPearl), level);
					return;
				}
			} catch (ReflectiveOperationException | RuntimeException ignored) {
				// 終望珍珠那條路走不通時退回原版珍珠
			}
		}
		ThrownEnderpearl pearl = new ThrownEnderpearl(level, player);
		pearl.setItem(new ItemStack(Items.ENDER_PEARL));
		pearl.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, 1.0F);
		level.addFreshEntity(pearl);
	}
}
