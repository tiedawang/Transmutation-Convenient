package com.transmutationconvenient.pipe;

import com.transmutationconvenient.registry.ModBlockEntities;
import com.transmutationconvenient.registry.ModItems;
import moze_intel.projecte.api.capabilities.PECapabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/**
 * EMC 管道 —— 把 EMC 從生產 EMC 的方塊（能量收集器、反物質繼電器……）引到任何地方。
 *
 * <p>整根管道的實作是從「無限科技」的 RT 傳輸線搬過來改的：外觀、連線判定、
 * 方塊實體的結構都相同，只把傳輸的介質從 RT 換成等價交換（ProjectE）的 EMC。
 *
 * <p>相連的管道組成常駐的網路（{@link EmcNetwork}），網路持有共享緩衝並在每個遊戲刻
 * 統一發放一次；管道本身不跑分配邏輯，只負責把機器推進來的 EMC 併入緩衝、以及顯示外觀。
 * 因此傳輸沒有損耗、沒有速度限制、也沒有距離限制 —— 連通即可用。
 *
 * <p>六個面各自獨立判斷是否連接：鄰塊是同款管道、轉化桌，或帶有 ProjectE
 * 的 EMC 儲存能力（{@code IEmcStorage}）的方塊。
 *
 * <p>{@link #FLOWING} 表示「管網裡此刻有 EMC」：為 true 時連接段的玻璃管裡
 * 會亮起金紅色質粒，沒有能量時則為空玻璃管。沒有端點、或端點全都沒有優先級時，
 * 一律不顯示流動。
 */
public class EmcPipeBlock extends BaseEntityBlock {

	public static final BooleanProperty NORTH = BooleanProperty.create("north");
	public static final BooleanProperty EAST = BooleanProperty.create("east");
	public static final BooleanProperty SOUTH = BooleanProperty.create("south");
	public static final BooleanProperty WEST = BooleanProperty.create("west");
	public static final BooleanProperty UP = BooleanProperty.create("up");
	public static final BooleanProperty DOWN = BooleanProperty.create("down");

	/** 管網當前是否有 EMC（決定連接段採用「空管」還是「亮著質粒」的管）。 */
	public static final BooleanProperty FLOWING = BooleanProperty.create("flowing");

	public static final Map<Direction, BooleanProperty> PROPERTY_BY_DIRECTION = Map.of(
			Direction.NORTH, NORTH,
			Direction.EAST, EAST,
			Direction.SOUTH, SOUTH,
			Direction.WEST, WEST,
			Direction.UP, UP,
			Direction.DOWN, DOWN
	);

	/** 物理碰撞箱：只有中心一小節，玩家走在管道上不會被絆住。 */
	private static final VoxelShape CORE_SHAPE = Block.box(5.0, 5.0, 5.0, 11.0, 11.0, 11.0);

	/** 模型外形：中心＋各連接方向伸出的臂（不含整格四周的空白角落）。 */
	private static final VoxelShape[] SHAPES = new VoxelShape[64];

	static {
		for (int mask = 0; mask < SHAPES.length; mask++) {
			VoxelShape shape = CORE_SHAPE;
			for (Direction direction : Direction.values()) {
				if ((mask & (1 << direction.ordinal())) != 0) {
					shape = Shapes.or(shape, armShape(direction));
				}
			}
			SHAPES[mask] = shape;
		}
	}

	public EmcPipeBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any()
				.setValue(NORTH, false)
				.setValue(EAST, false)
				.setValue(SOUTH, false)
				.setValue(WEST, false)
				.setValue(UP, false)
				.setValue(DOWN, false)
				.setValue(FLOWING, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN, FLOWING);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new EmcPipeBlockEntity(pos, state);
	}

	/** 流動外觀由服務端計算，客戶端僅跟隨方塊狀態變化。 */
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
			BlockEntityType<T> type) {
		if (level.isClientSide()) {
			return null;
		}
		return createTickerHelper(type, ModBlockEntities.EMC_PIPE.get(), EmcPipeBlockEntity::serverTick);
	}

	/**
	 * 準星瞄準用的形狀。
	 *
	 * <p><b>手裡拿著 EMC 管道時＝整格</b>：管身很細，若只給模型大小，十字準心會對不準，
	 * 也沒辦法在管上繼續接下一根 —— 鋪設時框選範圍必須與普通方塊一致。
	 *
	 * <p><b>沒拿管道時＝模型大小</b>（中心＋各連接方向的臂，見 {@link #SHAPES}）：
	 * 整格的框選範圍會讓旁邊的操作（挖方塊、放方塊、看後面的東西）一直被打斷，
	 * 因此只有真正在鋪管時才放寬。
	 *
	 * <p>物理碰撞箱不受影響，仍是中心的 {@code CORE_SHAPE}（見 {@link #getCollisionShape}）。
	 */
	@Override
	public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return context.isHoldingItem(ModItems.EMC_PIPE.get()) ? Shapes.block() : SHAPES[shapeIndex(state)];
	}

	/** 某一方向伸出的一段管子 —— 與 {@code emc_pipe_arm.json} 的模型元素一致。 */
	private static VoxelShape armShape(Direction direction) {
		return switch (direction) {
			case NORTH -> Block.box(6.0, 6.0, 0.0, 10.0, 10.0, 5.0);
			case SOUTH -> Block.box(6.0, 6.0, 11.0, 10.0, 10.0, 16.0);
			case WEST -> Block.box(0.0, 6.0, 6.0, 5.0, 10.0, 10.0);
			case EAST -> Block.box(11.0, 6.0, 6.0, 16.0, 10.0, 10.0);
			case DOWN -> Block.box(6.0, 0.0, 6.0, 10.0, 5.0, 10.0);
			case UP -> Block.box(6.0, 11.0, 6.0, 10.0, 16.0, 10.0);
		};
	}

	/** 把六個方向的連線狀態壓成 0~63 的索引（每個方向一位）。 */
	private static int shapeIndex(BlockState state) {
		int mask = 0;
		for (Direction direction : Direction.values()) {
			if (state.getValue(PROPERTY_BY_DIRECTION.get(direction))) {
				mask |= 1 << direction.ordinal();
			}
		}
		return mask;
	}

	/** 物理碰撞箱仍只有中心一小節，玩家走在管道上不會被絆住。 */
	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
			CollisionContext context) {
		return CORE_SHAPE;
	}

	/**
	 * 方塊本體需照常渲染。
	 *
	 * <p>1.20.1 的 {@link BaseEntityBlock} 預設 {@code getRenderShape} 回傳
	 * {@link RenderShape#INVISIBLE}（那是給「完全由方塊實體渲染」的方塊用的）；
	 * 不覆寫成 {@link RenderShape#MODEL} 的話，模型與貼圖整塊都不會被畫出來，
	 * 擺下去就是「沒有貼圖」。
	 *
	 * <p>26.x 的 {@code BaseEntityBlock} 沒有這個方法（預設就是 MODEL），
	 * 所以那邊的 RT 傳輸線看得到；1.20.1 移植時這一條要自己補。
	 */
	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.MODEL;
	}

	@Override
	public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		if (!oldState.is(state.getBlock())) {
			// 只有「這一格真的換成管道」才重算六向連線；單純的方塊狀態變化（例如流動外觀）
			// 不會影響連線，沒必要再掃一次鄰居。
			refreshConnections(level, pos, state);
		}
	}

	/**
	 * 管道被拆掉（或換成別的方塊）時把它從網路移出。
	 *
	 * <p>1.20.1 的 {@code onRemove} 在「方塊狀態改變」時也會被叫到（例如本模組自己更新
	 * {@link #FLOWING} 的外觀），那種情況新舊都是管道，不算被拆掉 —— 只有新方塊不是管道時
	 * 才需要動網路。
	 */
	@Override
	public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!(newState.getBlock() instanceof EmcPipeBlock) && level instanceof ServerLevel serverLevel) {
			EmcNetworkManager.of(serverLevel).onPipeRemoved(pos);
		}
		super.onRemove(state, level, pos, newState, movedByPiston);
	}

	@Override
	public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
			BlockPos fromPos, boolean movedByPiston) {
		super.neighborChanged(state, level, pos, neighborBlock, fromPos, movedByPiston);
		refreshConnections(level, pos, state);
		if (level instanceof ServerLevel serverLevel) {
			// 旁邊多了／少了機器或管道：這張網路的端點清單要重收
			EmcNetworkManager.of(serverLevel).markDirty(pos);
		}
	}

	/**
	 * 更新一根管道的流動外觀。
	 *
	 * <p>用 {@link Block#UPDATE_CLIENTS} 寫入：客戶端看得到貼圖切換，但不會再觸發一圈
	 * 鄰塊更新（流動狀態不影響連線，沒必要驚動鄰居）。
	 */
	public static void setFlowing(Level level, BlockPos pos, boolean flowing) {
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof EmcPipeBlock) || state.getValue(FLOWING) == flowing) {
			return;
		}
		level.setBlock(pos, state.setValue(FLOWING, flowing), Block.UPDATE_CLIENTS);
	}

	/**
	 * 重新判斷六個面。僅在實際變化時寫回，避免無謂的方塊更新風暴。
	 *
	 * <p><b>鄰塊所在區塊沒載入時直接跳過那個面</b>：{@link Level#getBlockState} 與
	 * {@link Level#getBlockEntity} 對沒載入的區塊會同步載入／生成（還會掛一張臨時票據），
	 * 長管網的邊界管道若每 10 tick 自檢一次，就會變成「載入 → 逾時卸載」的循環。
	 * 那個面的連線先保持原樣，等鄰塊載入後由事件或下一次自檢補上。
	 */
	public void refreshConnections(Level level, BlockPos pos, BlockState state) {
		BlockState updated = state;
		for (Direction direction : Direction.values()) {
			BlockPos neighbour = pos.relative(direction);
			if (!level.isLoaded(neighbour)) {
				continue;
			}
			updated = updated.setValue(PROPERTY_BY_DIRECTION.get(direction), canConnectTo(level, pos, direction));
		}
		if (updated != state) {
			level.setBlock(pos, updated, Block.UPDATE_ALL);
		}
	}

	/**
	 * 判斷該方向上的鄰塊是否屬於「能接管的方塊」。
	 *
	 * <p>三種情況可接：同款 EMC 管道、轉化桌方塊（本身沒有方塊實體，
	 * 只能靠註冊名辨認）、以及任何帶有 ProjectE {@code IEmcStorage}
	 * 能力的方塊實體（能量收集器、反物質繼電器、能量凝聚器都屬於此類）。
	 *
	 * @param direction 管道朝外的方向，即鄰居相對自身的方位
	 */
	public static boolean canConnectTo(Level level, BlockPos pos, Direction direction) {
		BlockPos neighbor = pos.relative(direction);
		if (!level.isLoaded(neighbor)) {
			return false;       // 不為了判斷連線去載入區塊
		}
		BlockState state = level.getBlockState(neighbor);
		if (state.getBlock() instanceof EmcPipeBlock) {
			return true;
		}
		if (EmcPriority.isTransmutationTable(state)) {
			return true;
		}
		BlockEntity blockEntity = level.getBlockEntity(neighbor);
		return blockEntity != null
				&& blockEntity.getCapability(PECapabilities.EMC_STORAGE_CAPABILITY, direction.getOpposite())
						.isPresent();
	}
}
