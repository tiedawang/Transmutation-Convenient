package com.transmutationconvenient.registry;

import com.transmutationconvenient.TransmutationConvenientMod;
import com.transmutationconvenient.effect.AncientCurseEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 狀態效果註冊。 */
public final class ModEffects {

	public static final DeferredRegister<MobEffect> EFFECTS =
			DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, TransmutationConvenientMod.MOD_ID);

	/** 遠古版本的詛咒：受擊冷卻歸零，粒子紫色。 */
	public static final RegistryObject<MobEffect> ANCIENT_CURSE =
			EFFECTS.register("ancient_curse", AncientCurseEffect::new);

	private ModEffects() {
	}

	public static void register(IEventBus modEventBus) {
		EFFECTS.register(modEventBus);
	}
}
