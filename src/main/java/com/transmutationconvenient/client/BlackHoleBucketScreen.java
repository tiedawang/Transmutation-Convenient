package com.transmutationconvenient.client;

import com.transmutationconvenient.bucket.BlackHoleBucketMenu;
import com.transmutationconvenient.bucket.BucketStorage;
import com.transmutationconvenient.network.NetworkHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

import java.util.List;

/**
 * 黑洞之桶的儲存空間介面。
 *
 * <p>版面：左邊是流體側邊欄（流體槽的數量＝目前存了幾種流體，槽平分側邊欄高度、槽與槽之間留 2 像素），
 * 右邊是原版大箱子的 54 格物品區。物品格的實際總量由 {@link #renderLabels} 自己畫在原本顯示數量的位置。
 *
 * <p>流體槽的填滿程度＝該流體存量 ÷ 每種流體各自的 1000 桶上限；用該流體的靜止貼圖從槽底往上覆蓋。
 * 滑鼠停在流體槽上會顯示「流體名稱：X 桶」；游標拿著空桶時點流體槽會抽出一桶。
 */
public class BlackHoleBucketScreen extends AbstractContainerScreen<BlackHoleBucketMenu> {

	private static final ResourceLocation CHEST_TEXTURE =
			new ResourceLocation("textures/gui/container/generic_54.png");

	/** 流體貼圖的原生尺寸（維持原比例，不拉伸）。 */
	private static final int FLUID_TILE_SIZE = 16;

	private static final int PANEL_COLOR = 0xFFC6C6C6;
	private static final int PANEL_HIGHLIGHT = 0xFFFFFFFF;
	private static final int PANEL_SHADOW = 0xFF555555;
	private static final int SLOT_COLOR = 0xFF8B8B8B;
	private static final int SLOT_SHADOW = 0xFF373737;
	private static final int SLOT_HIGHLIGHT = 0xFFFFFFFF;

	public BlackHoleBucketScreen(BlackHoleBucketMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		this.imageWidth = BlackHoleBucketMenu.IMAGE_WIDTH;
		this.imageHeight = BlackHoleBucketMenu.IMAGE_HEIGHT;
		this.titleLabelX = BlackHoleBucketMenu.CHEST_SLOT_X;
		this.titleLabelY = 6;
		this.inventoryLabelX = BlackHoleBucketMenu.PLAYER_INV_X;
		this.inventoryLabelY = this.imageHeight - 94;
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		super.render(guiGraphics, mouseX, mouseY, partialTick);
		int fluidIndex = fluidSlotAt(mouseX, mouseY);
		if (fluidIndex >= 0) {
			guiGraphics.renderTooltip(this.font, fluidTooltip(fluidIndex), mouseX, mouseY);
		} else {
			this.renderTooltip(guiGraphics, mouseX, mouseY);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int fluidIndex = fluidSlotAt(mouseX, mouseY);
		if (button == 0 && fluidIndex >= 0 && this.menu.getCarried().is(Items.BUCKET)) {
			NetworkHandler.CHANNEL.sendToServer(new NetworkHandler.ExtractFluidPacket(fluidIndex));
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
		int x = this.leftPos;
		int y = this.topPos;
		drawSidebar(guiGraphics, x, y);
		guiGraphics.blit(CHEST_TEXTURE, x + BlackHoleBucketMenu.CHEST_X, y, 0, 0, 176, 222);
		drawFluidSlots(guiGraphics, x, y);
	}

	@Override
	protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		super.renderLabels(guiGraphics, mouseX, mouseY);
		for (int i = 0; i < BlackHoleBucketMenu.BUCKET_SLOTS; i++) {
			long count = this.menu.getStoredCount(i);
			if (count <= 1L) {
				continue;
			}
			Slot slot = this.menu.getSlot(i);
			String text = Long.toString(count);
			guiGraphics.drawString(this.font, text,
					slot.x + 19 - 2 - this.font.width(text), slot.y + 9, 0xFFFFFF, true);
		}
	}

	// ------------------------------------------------------------ 側邊欄

	private void drawSidebar(GuiGraphics guiGraphics, int x, int y) {
		int width = BlackHoleBucketMenu.SIDEBAR_WIDTH;
		int height = BlackHoleBucketMenu.SIDEBAR_PANEL_HEIGHT;
		guiGraphics.fill(x, y, x + width, y + height, PANEL_COLOR);
		guiGraphics.fill(x, y, x + width, y + 1, PANEL_HIGHLIGHT);
		guiGraphics.fill(x, y, x + 1, y + height, PANEL_HIGHLIGHT);
		guiGraphics.fill(x, y + height - 1, x + width, y + height, PANEL_SHADOW);
		guiGraphics.fill(x + width - 1, y, x + width, y + height, PANEL_SHADOW);
	}

	private void drawFluidSlots(GuiGraphics guiGraphics, int x, int y) {
		List<FluidStack> fluids = this.menu.getFluids();
		int count = fluids.size();
		if (count <= 0) {
			return;
		}
		int slotHeight = BlackHoleBucketMenu.fluidSlotHeight(count);
		int slotWidth = BlackHoleBucketMenu.FLUID_SLOT_WIDTH;
		for (int i = 0; i < count; i++) {
			int slotX = x + BlackHoleBucketMenu.FLUID_SLOT_X;
			int slotY = y + BlackHoleBucketMenu.fluidSlotY(i, count);
			guiGraphics.fill(slotX, slotY, slotX + slotWidth, slotY + slotHeight, SLOT_COLOR);
			guiGraphics.fill(slotX, slotY, slotX + slotWidth, slotY + 1, SLOT_SHADOW);
			guiGraphics.fill(slotX, slotY, slotX + 1, slotY + slotHeight, SLOT_SHADOW);
			guiGraphics.fill(slotX, slotY + slotHeight - 1, slotX + slotWidth, slotY + slotHeight, SLOT_HIGHLIGHT);
			guiGraphics.fill(slotX + slotWidth - 1, slotY, slotX + slotWidth, slotY + slotHeight, SLOT_HIGHLIGHT);

			FluidStack fluid = fluids.get(i);
			int innerX = slotX + 1;
			int innerY = slotY + 1;
			int innerWidth = slotWidth - 2;
			int innerHeight = slotHeight - 2;
			if (innerWidth <= 0 || innerHeight <= 0) {
				continue;
			}
			int fillHeight = (int) Math.round((double) innerHeight * fluid.getAmount() / BucketStorage.FLUID_CAPACITY);
			fillHeight = Mth.clamp(fillHeight, 0, innerHeight);
			if (fillHeight > 0) {
				renderFluid(guiGraphics, fluid, innerX, innerY + innerHeight - fillHeight, innerWidth, fillHeight);
			}
		}
	}

	/**
	 * 用流體貼圖填滿指定區塊。
	 *
	 * <p>貼圖維持原生 16×16 的比例，從左下方開始往右、往上平鋪；超出區塊的部分用剪裁切掉，
	 * 不做任何拉伸。區塊高度太小或寬度不是 16 的倍數時，邊緣那個不完整的貼圖就是被裁切的部分。
	 */
	private void renderFluid(GuiGraphics guiGraphics, FluidStack fluid, int x, int y, int width, int height) {
		IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(fluid.getFluid());
		ResourceLocation still = extensions.getStillTexture(fluid);
		if (still == null) {
			return;
		}
		TextureAtlasSprite sprite = this.minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(still);
		int tint = extensions.getTintColor(fluid);
		float alpha = ((tint >> 24) & 0xFF) / 255.0F;
		float red = ((tint >> 16) & 0xFF) / 255.0F;
		float green = ((tint >> 8) & 0xFF) / 255.0F;
		float blue = (tint & 0xFF) / 255.0F;
		if (alpha <= 0.0F) {
			alpha = 1.0F;
		}
		// x、y 是介面絕對座標（drawFluidSlots 已經加過 leftPos／topPos），這裡不能再加一次
		int screenX = x;
		int screenY = y;
		guiGraphics.enableScissor(screenX, screenY, screenX + width, screenY + height);
		guiGraphics.setColor(red, green, blue, alpha);
		int bottom = screenY + height;
		for (int tileY = bottom - FLUID_TILE_SIZE; tileY > screenY - FLUID_TILE_SIZE; tileY -= FLUID_TILE_SIZE) {
			for (int tileX = screenX; tileX < screenX + width; tileX += FLUID_TILE_SIZE) {
				guiGraphics.blit(tileX, tileY, 0, FLUID_TILE_SIZE, FLUID_TILE_SIZE, sprite);
			}
		}
		guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
		guiGraphics.disableScissor();
	}

	// ------------------------------------------------------------ 流體槽互動

	private int fluidSlotAt(double mouseX, double mouseY) {
		int count = this.menu.getFluids().size();
		if (count <= 0) {
			return -1;
		}
		double relativeX = mouseX - this.leftPos - BlackHoleBucketMenu.FLUID_SLOT_X;
		if (relativeX < 0.0D || relativeX >= BlackHoleBucketMenu.FLUID_SLOT_WIDTH) {
			return -1;
		}
		double relativeY = mouseY - this.topPos;
		int slotHeight = BlackHoleBucketMenu.fluidSlotHeight(count);
		for (int i = 0; i < count; i++) {
			int slotY = BlackHoleBucketMenu.fluidSlotY(i, count);
			if (relativeY >= slotY && relativeY < slotY + slotHeight) {
				return i;
			}
		}
		return -1;
	}

	private Component fluidTooltip(int index) {
		FluidStack fluid = this.menu.getFluids().get(index);
		return Component.translatable("gui.transmutationconvenient.black_hole_bucket.fluid",
				fluid.getDisplayName(), bucketsText(fluid.getAmount()));
	}

	private static String bucketsText(long milliBuckets) {
		return Long.toString(milliBuckets / 1000L);
	}
}
