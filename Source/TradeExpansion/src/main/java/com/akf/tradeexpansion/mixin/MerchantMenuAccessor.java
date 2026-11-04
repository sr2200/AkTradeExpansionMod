package com.akf.tradeexpansion.mixin;

import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.Merchant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The merchant behind a trade screen (stage 14a: only villagers trade in coins for now). */
@Mixin(value = MerchantMenu.class, remap = false)
public interface MerchantMenuAccessor {
	@Accessor("trader")
	Merchant tradeExpansion$trader();
}
