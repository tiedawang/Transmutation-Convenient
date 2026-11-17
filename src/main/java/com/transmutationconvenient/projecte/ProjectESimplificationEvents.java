package com.transmutationconvenient.projecte;

import com.transmutationconvenient.TransmutationConvenientMod;
import com.transmutationconvenient.network.NetworkHandler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * ProjectE 精簡模式的指令與伺服器生命週期掛鉤。
 *
 * <p>指令刻意只有兩個固定形式：
 * <ul>
 *   <li>{@code /sePE true}：精簡。</li>
 *   <li>{@code /tcPE false}：解除精簡。</li>
 * </ul>
 * 兩個指令都只限管理員（權限等級 2），因為它們改的是整個世界的配方表。
 */
@Mod.EventBusSubscriber(modid = TransmutationConvenientMod.MOD_ID)
public final class ProjectESimplificationEvents {

	private ProjectESimplificationEvents() {
	}

	@SubscribeEvent
	public static void onRegisterCommands(RegisterCommandsEvent event) {
		event.getDispatcher().register(Commands.literal("sePE")
				.requires(source -> source.hasPermission(2))
				.then(Commands.literal("true")
						.executes(context -> setSimplified(context.getSource(), true))));

		event.getDispatcher().register(Commands.literal("tcPE")
				.requires(source -> source.hasPermission(2))
				.then(Commands.literal("false")
						.executes(context -> setSimplified(context.getSource(), false))));
	}

	@SubscribeEvent
	public static void onServerStarted(ServerStartedEvent event) {
		MinecraftServer server = event.getServer();
		ProjectESimplificationData data = ProjectESimplificationData.get(server);
		ProjectESimplificationService.initialize(server, data.isSimplified());
	}

	/**
	 * 玩家登入與 /reload 都會經過這裡。
	 *
	 * <p>OnDatapackSyncEvent 在 Forge 送出配方封包之前觸發；在這裡重新套用精簡，
	 * 伺服器接下來送給客戶端的就是過濾後的配方表。/reload 會把配方表重設成完整版，
	 * 所以必須在這裡補一次。
	 */
	@SubscribeEvent
	public static void onDatapackSync(OnDatapackSyncEvent event) {
		MinecraftServer server = event.getPlayerList().getServer();
		ProjectESimplificationData data = ProjectESimplificationData.get(server);

		if (data.isSimplified()) {
			if (ProjectESimplificationService.hasDisabledRecipes(server)) {
				ProjectESimplificationService.apply(server, true);
			}
		} else if (ProjectESimplificationService.isEnabled()) {
			ProjectESimplificationService.apply(server, false);
		}

		boolean simplified = data.isSimplified();
		for (ServerPlayer player : event.getPlayers()) {
			NetworkHandler.sendProjectESimplification(player, simplified);
		}
	}

	private static int setSimplified(CommandSourceStack source, boolean simplified) {
		MinecraftServer server = source.getServer();
		ProjectESimplificationData data = ProjectESimplificationData.get(server);

		ProjectESimplificationService.apply(server, simplified);
		data.setSimplified(simplified);
		ProjectESimplificationService.syncRecipes(server);

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			NetworkHandler.sendProjectESimplification(player, simplified);
		}

		source.sendSuccess(() -> Component.translatable(simplified
				? "transmutationconvenient.command.projecte.simplified"
				: "transmutationconvenient.command.projecte.restored"), false);
		return 1;
	}
}
