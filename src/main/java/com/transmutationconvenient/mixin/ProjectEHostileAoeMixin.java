package com.transmutationconvenient.mixin;

import moze_intel.projecte.utils.ToolHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Predicate;

/**
 * 讓 ProjectE 的群體傷害「虐殺敵對生物」模式把野生的冰火傳說龍視為敵對目標。
 *
 * <p>ProjectE 1.0.1 的 {@link ToolHelper#attackAOE} 會依模式選一個實體過濾器：
 * 敵對模式只接受 {@code Enemy}，全體模式接受 {@code LivingEntity}。冰火社區版的龍
 * 繼承 {@code TamableAnimal}，沒有實作 {@code Enemy}；而且野生與已馴服龍是同一個
 * 類別，差別只在 {@code TamableAnimal#isTame()} 的資料，所以 ProjectE 原本的
 * {@code instanceof Enemy} 判斷會同時漏掉兩種龍。
 *
 * <p>這裡不把 {@code Enemy} 加進龍的類別，也不改全體模式；只包裝傳給
 * {@code Level#getEntities} 的過濾器：原本的過濾器通過，或這是一隻「不是已馴服」
 * 的冰火龍，就選中。全體模式原本就會接受所有 {@code LivingEntity}，所以包裝後
 * 行為不變；敵對模式則會多出野生龍。
 *
 * <p>這個過濾器只在伺服器端的群體傷害掃描中使用；直接左鍵攻擊、龍的 AI、
 * 掉落或其他系統都不受影響。龍的辨識只用實體註冊名稱與原版
 * {@code TamableAnimal}，不新增冰火傳說或社區版的編譯依賴。
 */
@Mixin(ToolHelper.class)
public abstract class ProjectEHostileAoeMixin {

	@ModifyArg(
			method = "attackAOE",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"),
			index = 2,
			require = 0)
	private static Predicate<Entity> transmutationconvenient$includeWildDragons(Predicate<Entity> original) {
		return entity -> original.test(entity) || transmutationconvenient$isWildIceAndFireDragon(entity);
	}

	/**
	 * 判斷是不是「野生的冰火龍」。
	 *
	 * <p>先看實體註冊名稱，避免對所有實體做 {@code TamableAnimal} 轉型；再用
	 * {@code isTame()} 排除已馴服龍。野生龍與已馴服龍是同一個類別，差別只在
	 * 這個資料值，所以必須在每次掃描時讀取，不能靠類別或固定標記。
	 */
	@Unique
	private static boolean transmutationconvenient$isWildIceAndFireDragon(Entity entity) {
		if (entity.isSpectator()) {
			return false;
		}
		ResourceLocation id = EntityType.getKey(entity.getType());
		if (id == null
				|| !"iceandfire".equals(id.getNamespace())
				|| !id.getPath().contains("dragon")) {
			return false;
		}
		return entity instanceof TamableAnimal dragon && !dragon.isTame();
	}
}
