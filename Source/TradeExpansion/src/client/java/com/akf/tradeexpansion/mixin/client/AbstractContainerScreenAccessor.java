package com.akf.tradeexpansion.mixin.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A-4 (v0.77): the trade screen shrinks when the window gets too small for it. */
@Mixin(value = AbstractContainerScreen.class, remap = false)
public interface AbstractContainerScreenAccessor {
	@Mutable
	@Accessor("imageWidth")
	void tradeExpansion$setImageWidth(int width);

	@Mutable
	@Accessor("imageHeight")
	void tradeExpansion$setImageHeight(int height);
}
