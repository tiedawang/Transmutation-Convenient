package com.transmutationconvenient.registry;

import com.transmutationconvenient.TransmutationConvenientMod;
import com.transmutationconvenient.bucket.BlackHoleBucketMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** 容器選單類型註冊。 */
public final class ModMenus {

	public static final DeferredRegister<MenuType<?>> MENUS =
			DeferredRegister.create(Registries.MENU, TransmutationConvenientMod.MOD_ID);

	public static final RegistryObject<MenuType<BlackHoleBucketMenu>> BLACK_HOLE_BUCKET =
			MENUS.register("black_hole_bucket", () -> IForgeMenuType.create(BlackHoleBucketMenu::new));

	private ModMenus() {
	}

	public static void register(IEventBus modEventBus) {
		MENUS.register(modEventBus);
	}
}
