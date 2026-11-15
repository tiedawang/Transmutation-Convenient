package com.transmutationconvenient.mixin;

import com.transmutationconvenient.TransmutationConvenientMod;
import moze_intel.projecte.api.mapper.recipe.IRecipeTypeMapper;
import moze_intel.projecte.emc.mappers.recipe.CraftingMapper;
import net.minecraft.world.item.crafting.RecipeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.HashSet;
import java.util.Set;

/**
 * 保護 ProjectE 的配方映射迴圈：某個外部映射器如果因為缺少類別而爆炸，
 * 只跳過那一個映射器，不要讓整個 EMC 計算中斷。
 *
 * <p>這次的實際案例是 ProjectE Integration 7.2.5 的冰火龍爐映射器：
 * 它是為原版冰火編譯的，卻只看 {@code iceandfire} 這個 mod id。
 * 社區版（CE）保留同一個 mod id，但把 Java 類別搬到
 * {@code com.iafenvoy...}，所以舊映射器在 {@code canHandle} 裡找不到
 * {@code com.github.alexthe666...IafRecipeRegistry}，拋出
 * {@code NoClassDefFoundError}。ProjectE 只接一般異常，接不住這種
 * 「類別載入失敗」的錯誤，整個 EMC 計算因此直接結束。
 *
 * <p>這裡只攔 {@link LinkageError}（包含類別找不到、方法/欄位不存在），
 * 不攔一般的 {@link RuntimeException}，避免把真正的程式錯誤也吞掉。
 * 被跳過的映射器會留下警告，方便日後追查。
 */
@Mixin(CraftingMapper.class)
public abstract class CraftingMapperGuardMixin {

	/** 同一個映射器只警告一次；否則每個配方類型都會重印一次。 */
	@Unique
	private static final Set<String> transmutationconvenient$warnedMappers = new HashSet<>();

	@Redirect(
			method = "addMappings",
			at = @At(
					value = "INVOKE",
					target = "Lmoze_intel/projecte/api/mapper/recipe/IRecipeTypeMapper;canHandle(Lnet/minecraft/world/item/crafting/RecipeType;)Z",
					remap = false),
			remap = false)
	private boolean transmutationconvenient$guardCanHandle(IRecipeTypeMapper mapper, RecipeType<?> recipeType) {
		try {
			return mapper.canHandle(recipeType);
		} catch (LinkageError error) {
			String mapperName = mapper.getClass().getName();
			if (transmutationconvenient$warnedMappers.add(mapperName)) {
				TransmutationConvenientMod.LOGGER.warn(
						"[便捷轉化] 配方映射器 {} 缺少必要的類別，已跳過它，避免中斷整個 EMC 計算。",
						mapperName, error);
			}
			return false;
		}
	}
}
