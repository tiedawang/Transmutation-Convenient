package com.transmutationconvenient.client;

import com.transmutationconvenient.bucket.BlackHoleBucketMenu;
import com.transmutationconvenient.projecte.ProjectESimplificationClientState;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fluids.FluidStack;

import java.util.List;

/** 客戶端封包處理（只會在客戶端載入）。 */
public final class ClientPacketHandlers {

	private ClientPacketHandlers() {
	}

	public static void applyBucketData(int containerId, long[] counts, List<FluidStack> fluids) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null && minecraft.player.containerMenu instanceof BlackHoleBucketMenu menu) {
			menu.applyClientData(containerId, counts, fluids);
		}
	}

	public static void applyProjectESimplification(boolean simplified) {
		ProjectESimplificationClientState.setEnabled(simplified);
		ClientCreativeTabs.rebuild();
	}
}
