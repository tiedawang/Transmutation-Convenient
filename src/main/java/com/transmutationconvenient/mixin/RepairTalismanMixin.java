package com.transmutationconvenient.mixin;

import moze_intel.projecte.gameObjs.items.RepairTalisman;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 讓 ProjectE 的修復護符也能修 Colytra 附著在胸甲上的鞘翅。
 *
 * <p>Colytra 把鞘翅的 {@link ItemStack} 序列化在胸甲 NBT 的
 * {@code colytra:ElytraUpgrade} 標籤裡，鞘翅本身不在玩家的物品欄中。
 * ProjectE 的修復護符（{@link RepairTalisman}）只走玩家物品欄、對每個
 * {@code ItemStack} 呼叫 {@code setDamageValue}，所以永遠碰不到那個內層鞘翅。
 *
 * <p>這裡不動 ProjectE 的任何修復邏輯或冷卻：只注入它每次冷卻觸發的
 * {@code repairAllItems(Player)} 尾端，照同樣的節奏（每次冷卻修一點耐久）
 * 把玩家背包、盔甲槽、副手裡所有帶 Colytra 附著的胸甲內層鞘翅各修一點。
 *
 * <p>用 NBT 標籤字串直接讀寫，不依賴 Colytra 的類別：沒有安裝 Colytra 時
 * 這些標籤不存在，整段就是空操作。
 *
 * <p>這裡刻意不對 ProjectE 提版本要求：{@code require = 0} 讓注入點在舊版 ProjectE
 * 找不到時靜默跳過、模組照常啟動（代價只是這項修正失效），不把版本差異變成啟動崩潰。
 */
@Mixin(RepairTalisman.class)
public abstract class RepairTalismanMixin {

	/** Colytra 存放附著鞘翅的 NBT 標籤（ColytraConstants.MOD_ID + ":ElytraUpgrade"）。 */
	private static final String COLYTRA_ELYTRA_TAG = "colytra:ElytraUpgrade";

	@Inject(
			method = "repairAllItems(Lnet/minecraft/world/entity/player/Player;)V",
			at = @At("TAIL"),
			require = 0,
			remap = false)
	private static void transmutationconvenient$repairAttachedElytra(Player player, CallbackInfo ci) {
		for (ItemStack stack : player.getInventory().items) {
			transmutationconvenient$repairOne(stack);
		}
		for (ItemStack stack : player.getInventory().armor) {
			transmutationconvenient$repairOne(stack);
		}
		for (ItemStack stack : player.getInventory().offhand) {
			transmutationconvenient$repairOne(stack);
		}
	}

	/** 胸甲夾層裡的鞘翅：有損壞就修一點耐久，寫回胸甲 NBT。 */
	private static void transmutationconvenient$repairOne(ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		CompoundTag elytraTag = stack.getTagElement(COLYTRA_ELYTRA_TAG);
		if (elytraTag == null) {
			return;
		}
		ItemStack elytra = ItemStack.of(elytraTag);
		if (elytra.isEmpty() || !elytra.isDamageableItem() || elytra.getDamageValue() <= 0) {
			return;
		}
		elytra.setDamageValue(elytra.getDamageValue() - 1);
		stack.getOrCreateTag().put(COLYTRA_ELYTRA_TAG, elytra.save(new CompoundTag()));
	}
}
