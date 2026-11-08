package com.transmutationconvenient.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.resources.ResourceLocation;

/**
 * 只為拿 JEI 的 {@link IRecipeTransferHandlerHelper}（用來把「買不了的材料」標紅）。
 *
 * <p>不註冊任何轉移處理器 —— 無盡重生已經替它的工作台註冊過，
 * 同一個（選單, 配方類型）只能有一個 handler，再註冊會撞掉它。
 * 真正的攔截在 {@code RecipeTransferServiceMixin}。
 */
@JeiPlugin
public class TransmutationConvenientJeiPlugin implements IModPlugin {

	private static final ResourceLocation UID = new ResourceLocation("transmutationconvenient", "jei_plugin");

	/** 由 JEI 在初始化時填入；mixin 用它構造「只標紅部分槽」的錯誤。 */
	public static IRecipeTransferHandlerHelper HELPER;

	@Override
	public ResourceLocation getPluginUid() {
		return UID;
	}

	@Override
	public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
		HELPER = registration.getTransferHelper();
	}
}
