package com.transmutationconvenient.network;

import com.transmutationconvenient.TransmutationConvenientMod;
import com.transmutationconvenient.util.FillService;
import com.transmutationconvenient.util.WorkbenchKind;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 網路通道 —— 只有一個「請用 EMC 幫我填好材料」的請求包。
 *
 * <p>客戶端發出時帶著「配方材料清單」；服務端以自己算出的背包存量與 EMC 為準，
 * 不信任客戶端的任何數量。
 */
public final class NetworkHandler {

	private static final String PROTOCOL = "1";

	public static SimpleChannel CHANNEL;

	private NetworkHandler() {
	}

	public static void register() {
		CHANNEL = NetworkRegistry.newSimpleChannel(
				new ResourceLocation(TransmutationConvenientMod.MOD_ID, "main"),
				() -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
		CHANNEL.registerMessage(0, FillRequestPacket.class,
				FillRequestPacket::encode, FillRequestPacket::decode, FillRequestPacket::handle);
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
}
