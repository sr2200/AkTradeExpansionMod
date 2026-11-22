package com.akf.tradeexpansion.mixin;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A-4 (v0.77): moves the trade screen's slots when the screen is fitted to a smaller window (client only). */
@Mixin(value = Slot.class, remap = false)
public interface SlotAccessor {
	@Mutable
	@Accessor("x")
	void tradeExpansion$setX(int x);

	@Mutable
	@Accessor("y")
	void tradeExpansion$setY(int y);
}
