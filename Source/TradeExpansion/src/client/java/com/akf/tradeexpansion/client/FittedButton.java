package com.akf.tradeexpansion.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * A button whose label is shrunk (down to {@link #MIN_SCALE}) to fit instead of scrolling: small buttons such as the
 * staff tab's "+1,000c" or "Change" would otherwise show only a moving part of their label.
 */
public final class FittedButton extends Button {
	/** Smallest scale of a label; a label still wider than that scrolls as usual. */
	public static final float MIN_SCALE = 0.6f;
	private static final int COLOR_ACTIVE = 0xFFFFFFFF;
	private static final int COLOR_INACTIVE = 0xFFA0A0A0;

	private FittedButton(int x, int y, int width, int height, Component message, OnPress onPress) {
		super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
	}

	public static FittedButton create(Component message, OnPress onPress, int x, int y, int width, int height) {
		return new FittedButton(x, y, width, height, message, onPress);
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		extractDefaultSprite(g);
		Font font = Minecraft.getInstance().font;
		int room = getWidth() - 2 * TEXT_MARGIN;
		int width = font.width(getMessage());
		float scale = room / (float) width;
		if (width <= room || scale < MIN_SCALE) {
			extractDefaultLabel(g.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
			return;
		}
		g.pose().pushMatrix();
		g.pose().translate(getX() + getWidth() / 2.0f, getY() + (getHeight() - font.lineHeight * scale) / 2.0f + 0.5f);
		g.pose().scale(scale, scale);
		g.text(font, getMessage(), -width / 2, 0, active ? COLOR_ACTIVE : COLOR_INACTIVE, true);
		g.pose().popMatrix();
	}
}
