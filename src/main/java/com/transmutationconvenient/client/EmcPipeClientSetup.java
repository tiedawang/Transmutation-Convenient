package com.transmutationconvenient.client;

import com.transmutationconvenient.TransmutationConvenientMod;
import com.transmutationconvenient.registry.ModBlocks;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * EMC 管道的客戶端渲染設定（只會在客戶端載入）。
 *
 * <p>管身的連接段模型用了原版玻璃貼圖與帶透明像素的質粒動畫貼圖，
 * 必須放在 {@code cutout_mipped} 渲染層；放在預設的 solid 層時，
 * 透明像素會被畫成不透明的黑／白，看起來就像「沒有貼圖」。
 *
 * <p>方塊模型 JSON 裡已經寫了 {@code "render_type": "minecraft:cutout_mipped"}；
 * 這裡再走一次 {@link ItemBlockRenderTypes#setRenderLayer} 當後備
 * （多方塊的 multipart 模型若沒有把 render type 帶下來，就是靠這一行）。
 */
@Mod.EventBusSubscriber(modid = TransmutationConvenientMod.MOD_ID, value = Dist.CLIENT,
		bus = Mod.EventBusSubscriber.Bus.MOD)
public final class EmcPipeClientSetup {

	private EmcPipeClientSetup() {
	}

	@SubscribeEvent
	public static void onClientSetup(FMLClientSetupEvent event) {
		event.enqueueWork(() -> ItemBlockRenderTypes.setRenderLayer(
				ModBlocks.EMC_PIPE.get(), RenderType.cutoutMipped()));
	}
}
