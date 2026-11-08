package com.transmutationconvenient.mixin;

import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.emc.nbt.NBTManager;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 把原版鞘翅的 EMC 值固定成 5000。
 *
 * <p>ProjectE 預設沒有鞘翅的 EMC 值（鞘翅只能從要塞戰利品取得、沒有合成配方，
 * 自動推算算不到它）。用 {@code /projecte setemc 5000 minecraft:elytra} 之後也只解決
 * 「完全沒有 NBT」的那一種：ProjectE 的 {@link NBTManager#getEmcValue(ItemInfo)}
 * 會對帶 NBT 的物品先找無 NBT 的基礎值、再交給 {@code DamageProcessor} 按耐久折算，
 * 於是出現三種症狀：
 * <ul>
 *   <li>滿耐久的鞘翅多出 {@code +1} 點底數：{@code 5000 * (432 + 1) / 432 = 5011}（數字偏差）；</li>
 *   <li>耐久不滿的鞘翅被按比例折算，值不再是 5000；</li>
 *   <li>有附魔的鞘翅帶著 {@code Enchantments} NBT，走的也是折算法，結果同樣不是 5000
 *       （若啟用了附魔處理器還會再被加值）。</li>
 * </ul>
 *
 * <p>因此在 EMC 查詢的最底層攔截：只要是鞘翅，不管耐久、附魔、其他 NBT，
 * 一律回傳 5000。所有讀值路徑（{@code IEMCProxy#getValue}、{@code getSellValue}、
 * 轉化桌的買賣與顯示、其他模組查 EMC）都經過這個方法，所以一處就夠。
 *
 * <p>這裡刻意不對 ProjectE 提版本要求：{@code require = 0} 讓注入點在舊版 ProjectE
 * 找不到時靜默跳過、模組照常啟動（代價只是這項修正失效），不把版本差異變成啟動崩潰。
 */
@Mixin(NBTManager.class)
public abstract class NBTManagerMixin {

	/** 鞘翅的 EMC 值（使用者指定）。 */
	private static final long ELYTRA_EMC = 5_000L;

	@Inject(
			method = "getEmcValue(Lmoze_intel/projecte/api/ItemInfo;)J",
			at = @At("HEAD"),
			cancellable = true,
			require = 0,
			remap = false)
	private static void transmutationconvenient$fixedElytraEmc(ItemInfo info,
			CallbackInfoReturnable<Long> cir) {
		if (info.getItem() == Items.ELYTRA) {
			cir.setReturnValue(ELYTRA_EMC);
		}
	}
}
