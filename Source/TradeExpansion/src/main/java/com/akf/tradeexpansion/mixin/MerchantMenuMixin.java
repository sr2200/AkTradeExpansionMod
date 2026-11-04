package com.akf.tradeexpansion.mixin;

import com.akf.tradeexpansion.villager.VillagerCoins;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.Merchant;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stage 14a (design H): choosing a coin trade in the list does not move emeralds into the payment slots (only the
 * non-emerald part of the cost), and the result is recomputed even when nothing was moved.
 */
@Mixin(value = MerchantMenu.class, remap = false)
public abstract class MerchantMenuMixin {
	@Shadow @Final private Merchant trader;
	@Shadow @Final private MerchantContainer tradeContainer;

	@Inject(method = "moveFromInventoryToPaymentSlot", at = @At("HEAD"), cancellable = true)
	private void tradeExpansion$skipEmeralds(int slot, ItemCost cost, CallbackInfo ci) {
		Player player = trader.getTradingPlayer();
		if (player != null && VillagerCoins.active(player) && VillagerCoins.isEmerald(cost)) ci.cancel();
	}

	@Inject(method = "tryMoveItems", at = @At("TAIL"))
	private void tradeExpansion$refresh(int index, CallbackInfo ci) {
		Player player = trader.getTradingPlayer();
		if (player != null && VillagerCoins.active(player)) tradeContainer.updateSellItem();
	}
}
