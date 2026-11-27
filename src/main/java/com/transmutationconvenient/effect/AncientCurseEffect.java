package com.transmutationconvenient.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * 遠古版本的詛咒。
 *
 * <p>這個效果本身不改攻擊力、不改移動速度，唯一作用是每刻把受影響生物的
 * {@code invulnerableTime} 歸零，讓牠們不再享有受擊後的無敵時間。
 * 效果只有 3 秒，離開效果後原版的受擊冷卻會自然恢復。
 */
public class AncientCurseEffect extends MobEffect {

	/** 粒子與藥水顏色：紫色。 */
	public static final int COLOR = 0x8A2BE2;

	public AncientCurseEffect() {
		super(MobEffectCategory.HARMFUL, COLOR);
	}

	@Override
	public void applyEffectTick(LivingEntity entity, int amplifier) {
		// 原版受傷後會把 invulnerableTime 設成 20；每刻歸零就能讓下一次攻擊立即生效。
		entity.invulnerableTime = 0;
	}

	@Override
	public boolean isDurationEffectTick(int duration, int amplifier) {
		// 每刻都要執行，不能像回復或中毒那樣間隔執行。
		return true;
	}
}
