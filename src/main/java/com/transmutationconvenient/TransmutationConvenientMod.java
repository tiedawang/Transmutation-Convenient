package com.transmutationconvenient;

import com.transmutationconvenient.network.NetworkHandler;
import com.transmutationconvenient.registry.ModBlockEntities;
import com.transmutationconvenient.registry.ModBlocks;
import com.transmutationconvenient.registry.ModItems;
import moze_intel.projecte.api.ProjectEAPI;
import moze_intel.projecte.api.imc.CustomEMCRegistration;
import moze_intel.projecte.api.imc.IMCMethods;
import moze_intel.projecte.api.nss.NSSItem;
import net.minecraft.world.item.Items;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.InterModComms;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transmutation Convenient —— 便捷轉化。
 *
 * <p>在 JEI 的「快速填入」裡：玩家缺少的材料，若已在等價交換（ProjectE）學習過轉化配方、
 * 而且 EMC 足夠，就視為已有 —— 直接從轉化桌以 EMC 買下缺的材料放進合成格。
 *
 * <p>支援的工作台：原版工作台、無盡重生（Re:Avaritia）的伏聆／煉獄／終末／終焉工作台。
 * 四個工作台只靠選單註冊名辨識，不依賴 Re:Avaritia 的類。
 */
@Mod(TransmutationConvenientMod.MOD_ID)
public class TransmutationConvenientMod {

	public static final String MOD_ID = "transmutationconvenient";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** 原版鞘翅的 EMC 值（使用者指定）。 */
	public static final long ELYTRA_EMC = 5_000L;

	public TransmutationConvenientMod(FMLJavaModLoadingContext context) {
		IEventBus modEventBus = context.getModEventBus();

		// 註冊順序不可調換：方塊 → 方塊實體 → 物品（物品要為方塊建立 BlockItem）
		ModBlocks.register(modEventBus);
		ModBlockEntities.register(modEventBus);
		ModItems.register(modEventBus);

		modEventBus.addListener(this::commonSetup);
		LOGGER.info("[便捷轉化] 正在初始化……");
	}

	private void commonSetup(FMLCommonSetupEvent event) {
		event.enqueueWork(() -> {
			NetworkHandler.register();
			registerElytraEmc();
		});
	}

	/**
	 * 把鞘翅的基礎 EMC 值註冊進 ProjectE 的 EMC 表（無 NBT 的鞘翅 = 5000）。
	 *
	 * <p>只靠運行期的 {@code NBTManager} mixin 不夠：ProjectE 自己在啟動時會用配方圖
	 * 推算所有物品的 EMC 值，那一步讀的是「EMC 表」而不是 {@code IEMCProxy}。鞘翅不在表裡，
	 * 任何「拿鞘翅當材料」的配方（例如無盡貪婪的無限鞘翅）就會推算失敗、成品沒有 EMC 值。
	 * 所以在 EMC 表建好之前用 ProjectE 的 IMC 介面把鞘翅放進去。
	 *
	 * <p>表裡記的是「無 NBT 的鞘翅」；帶耐久／附魔的變體由 {@code NBTManagerMixin} 在
	 * 查值時一律回報 5000，兩邊合起來才是完整的「鞘翅 EMC 恆為 5000」。
	 */
	private static void registerElytraEmc() {
		InterModComms.sendTo(
				ProjectEAPI.PROJECTE_MODID,
				IMCMethods.REGISTER_CUSTOM_EMC,
				() -> new CustomEMCRegistration(NSSItem.createItem(Items.ELYTRA), ELYTRA_EMC));
	}
}

