package com.transmutationconvenient.network;

import com.transmutationconvenient.TransmutationConvenientMod;
import com.transmutationconvenient.bucket.BlackHoleBucketItem;
import com.transmutationconvenient.bucket.BlackHoleBucketMenu;
import com.transmutationconvenient.client.ClientPacketHandlers;
import com.transmutationconvenient.compat.CurioActions;
import com.transmutationconvenient.util.FillService;
import com.transmutationconvenient.util.WorkbenchKind;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 網路通道。
 *
 * <p>客戶端 → 服務端：
 * <ul>
 *   <li>{@link FillRequestPacket}：用 EMC 幫我填好合成材料（原有功能）。</li>
 *   <li>{@link CycleModePacket}：切換手上黑洞之桶的模式。</li>
 *   <li>{@link ExtractFluidPacket}：游標拿著空桶時，從流體槽抽一桶出來。</li>
 *   <li>{@link CurioUsePacket}：空手右鍵時，使用 Curios 槽裡的大天使的懲戒。</li>
 *   <li>{@link CurioModePacket}：空手按 G 時，切換 Curios 槽裡的大天使的懲戒模式。</li>
 * </ul>
 *
 * <p>服務端 → 客戶端：
 * <ul>
 *   <li>{@link BucketDataPacket}：同步黑洞之桶每一格的實際總量與流體清單。</li>
 *   <li>{@link ProjectESimplificationPacket}：同步 ProjectE 精簡狀態，重建創造標籤。</li>
 * </ul>
 */
public final class NetworkHandler {

	private static final String PROTOCOL = "3";

	public static SimpleChannel CHANNEL;

	private NetworkHandler() {
	}

	public static void register() {
		CHANNEL = NetworkRegistry.newSimpleChannel(
				new ResourceLocation(TransmutationConvenientMod.MOD_ID, "main"),
				() -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
		CHANNEL.registerMessage(0, FillRequestPacket.class,
				FillRequestPacket::encode, FillRequestPacket::decode, FillRequestPacket::handle);
		CHANNEL.registerMessage(1, CycleModePacket.class,
				CycleModePacket::encode, CycleModePacket::decode, CycleModePacket::handle);
		CHANNEL.registerMessage(2, BucketDataPacket.class,
				BucketDataPacket::encode, BucketDataPacket::decode, BucketDataPacket::handle);
		CHANNEL.registerMessage(3, ExtractFluidPacket.class,
				ExtractFluidPacket::encode, ExtractFluidPacket::decode, ExtractFluidPacket::handle);
		CHANNEL.registerMessage(4, ProjectESimplificationPacket.class,
				ProjectESimplificationPacket::encode, ProjectESimplificationPacket::decode,
				ProjectESimplificationPacket::handle);
		CHANNEL.registerMessage(5, CurioUsePacket.class,
				CurioUsePacket::encode, CurioUsePacket::decode, CurioUsePacket::handle);
		CHANNEL.registerMessage(6, CurioModePacket.class,
				CurioModePacket::encode, CurioModePacket::decode, CurioModePacket::handle);
	}

	/** 伺服器 → 客戶端：同步 ProjectE 精簡狀態，讓創造標籤重建。 */
	public static void sendProjectESimplification(ServerPlayer player, boolean simplified) {
		CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
				new ProjectESimplificationPacket(simplified));
	}

	/** 客戶端 → 服務端：這個工作台、這些材料，請幫我填進合成格（缺的用 EMC 買）。 */
	public record FillRequestPacket(WorkbenchKind kind, List<ItemStack> required) {

		public void encode(FriendlyByteBuf buf) {
			buf.writeEnum(kind);
			buf.writeCollection(required, FriendlyByteBuf::writeItem);
		}

		public static FillRequestPacket decode(FriendlyByteBuf buf) {
			WorkbenchKind kind = buf.readEnum(WorkbenchKind.class);
			List<ItemStack> required = buf.readCollection(ArrayList::new, FriendlyByteBuf::readItem);
			return new FillRequestPacket(kind, required);
		}

		public void handle(Supplier<NetworkEvent.Context> ctx) {
			NetworkEvent.Context context = ctx.get();
			context.enqueueWork(() -> {
				ServerPlayer player = context.getSender();
				if (player == null) {
					return;
				}
				Component error = FillService.fill(player, player.containerMenu, kind, required);
				if (error != null) {
					player.sendSystemMessage(error);
				}
			});
			context.setPacketHandled(true);
		}
	}

	/** 客戶端 → 服務端：把主手／副手那桶的模式往下切一格。 */
	public record CycleModePacket() {

		public void encode(FriendlyByteBuf buf) {
		}

		public static CycleModePacket decode(FriendlyByteBuf buf) {
			return new CycleModePacket();
		}

		public void handle(Supplier<NetworkEvent.Context> ctx) {
			NetworkEvent.Context context = ctx.get();
			context.enqueueWork(() -> {
				ServerPlayer player = context.getSender();
				if (player == null) {
					return;
				}
				ItemStack stack = player.getMainHandItem();
				if (!(stack.getItem() instanceof BlackHoleBucketItem)) {
					stack = player.getOffhandItem();
				}
				if (!(stack.getItem() instanceof BlackHoleBucketItem)) {
					return;
				}
				BlackHoleBucketItem.setMode(stack, BlackHoleBucketItem.getMode(stack).next());
				player.inventoryMenu.broadcastChanges();
			});
			context.setPacketHandled(true);
		}
	}

	/** 客戶端 → 服務端：空手右鍵，使用 Curios 槽裡的大天使的懲戒。 */
	public record CurioUsePacket() {

		public void encode(FriendlyByteBuf buf) {
		}

		public static CurioUsePacket decode(FriendlyByteBuf buf) {
			return new CurioUsePacket();
		}

		public void handle(Supplier<NetworkEvent.Context> ctx) {
			NetworkEvent.Context context = ctx.get();
			context.enqueueWork(() -> {
				ServerPlayer player = context.getSender();
				if (player != null) {
					CurioActions.fireArchangel(player);
				}
			});
			context.setPacketHandled(true);
		}
	}

	/** 客戶端 → 服務端：空手按 G，切換 Curios 槽裡的大天使的懲戒模式。 */
	public record CurioModePacket() {

		public void encode(FriendlyByteBuf buf) {
		}

		public static CurioModePacket decode(FriendlyByteBuf buf) {
			return new CurioModePacket();
		}

		public void handle(Supplier<NetworkEvent.Context> ctx) {
			NetworkEvent.Context context = ctx.get();
			context.enqueueWork(() -> {
				ServerPlayer player = context.getSender();
				if (player != null) {
					CurioActions.toggleArchangel(player);
				}
			});
			context.setPacketHandled(true);
		}
	}

	/** 客戶端 → 服務端：從第 index 個流體槽抽一桶（游標必須拿著空桶）。 */
	public record ExtractFluidPacket(int index) {

		public void encode(FriendlyByteBuf buf) {
			buf.writeVarInt(index);
		}

		public static ExtractFluidPacket decode(FriendlyByteBuf buf) {
			return new ExtractFluidPacket(buf.readVarInt());
		}

		public void handle(Supplier<NetworkEvent.Context> ctx) {
			NetworkEvent.Context context = ctx.get();
			context.enqueueWork(() -> {
				ServerPlayer player = context.getSender();
				if (player == null) {
					return;
				}
				if (player.containerMenu instanceof BlackHoleBucketMenu menu) {
					menu.extractFluidToCursor(index);
				}
			});
			context.setPacketHandled(true);
		}
	}

	/** 服務端 → 客戶端：黑洞之桶的每格總量與流體清單。 */
	public record BucketDataPacket(int containerId, long[] counts, List<FluidStack> fluids) {

		public void encode(FriendlyByteBuf buf) {
			buf.writeVarInt(containerId);
			buf.writeVarInt(counts.length);
			for (long count : counts) {
				buf.writeLong(count);
			}
			buf.writeVarInt(fluids.size());
			for (FluidStack fluid : fluids) {
				fluid.writeToPacket(buf);
			}
		}

		public static BucketDataPacket decode(FriendlyByteBuf buf) {
			int containerId = buf.readVarInt();
			long[] counts = new long[buf.readVarInt()];
			for (int i = 0; i < counts.length; i++) {
				counts[i] = buf.readLong();
			}
			int fluidCount = buf.readVarInt();
			List<FluidStack> fluids = new ArrayList<>(fluidCount);
			for (int i = 0; i < fluidCount; i++) {
				fluids.add(FluidStack.readFromPacket(buf));
			}
			return new BucketDataPacket(containerId, counts, fluids);
		}

		public void handle(Supplier<NetworkEvent.Context> ctx) {
			NetworkEvent.Context context = ctx.get();
			context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
					() -> () -> ClientPacketHandlers.applyBucketData(containerId, counts, fluids)));
			context.setPacketHandled(true);
		}
	}

	/** 伺服器 → 客戶端：ProjectE 精簡開關；客戶端收到後重建創造標籤。 */
	public record ProjectESimplificationPacket(boolean simplified) {

		public void encode(FriendlyByteBuf buf) {
			buf.writeBoolean(simplified);
		}

		public static ProjectESimplificationPacket decode(FriendlyByteBuf buf) {
			return new ProjectESimplificationPacket(buf.readBoolean());
		}

		public void handle(Supplier<NetworkEvent.Context> ctx) {
			NetworkEvent.Context context = ctx.get();
			context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
					() -> () -> ClientPacketHandlers.applyProjectESimplification(simplified)));
			context.setPacketHandled(true);
		}
	}
}
