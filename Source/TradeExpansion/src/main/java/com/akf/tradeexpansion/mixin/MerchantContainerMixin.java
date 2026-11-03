package com.akf.tradeexpansion.mixin;

import com.akf.tradeexpansion.currency.WalletService;
import com.akf.tradeexpansion.villager.VillagerCoins;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stage 14a (design H): the result slot of a coin trade is filled when the player can pay the coin price from wallet
 * and coins and has the non-emerald part of the cost in the payment slots. Runs on both sides (the client predicts
 * with the prices the server sent). Emeralds never pay for a coin trade.
 */
@Mixin(MerchantContainer.class)
public abstract class MerchantContainerMixin {
	@Shadow @Final private Merchant merchant;
	@Shadow @Final private NonNullList<ItemStack> itemStacks;
	@Shadow private MerchantOffer activeOffer;
	@Shadow private int selectionHint;
	@Shadow private int futureXp;

	@Shadow public abstract void setItem(int slot, ItemStack stack);

	@Inject(method = "updateSellItem", at = @At("HEAD"), cancellable = true)
	private void tradeExpansion$coinOffer(CallbackInfo ci) {
		Player player = merchant.getTradingPlayer();
		if (player == null || !VillagerCoins.active(player)) return;
		MerchantOffers offers = merchant.getOffers();
		if (selectionHint < 0 || selectionHint >= offers.size()) return;
		long price = VillagerCoins.price(player, selectionHint);
		if (price < 0) return; // not paid in emeralds: vanilla
		ci.cancel();
		MerchantOffer offer = offers.get(selectionHint);
		activeOffer = null;
		futureXp = 0;
		boolean otherOk = VillagerCoins.otherCostPaid(offer, itemStacks.get(0), itemStacks.get(1));
		if (offer.isOutOfStock() || !otherOk || !WalletService.canPay(player, price)) {
			setItem(2, ItemStack.EMPTY);
			return;
		}
		activeOffer = offer;
		setItem(2, offer.assemble());
		futureXp = offer.getXp();
	}

	/** Vanilla matched an offer from the payment slots: a coin offer must not be bought with emeralds. */
	@Inject(method = "updateSellItem", at = @At("TAIL"))
	private void tradeExpansion$noEmeralds(CallbackInfo ci) {
		Player player = merchant.getTradingPlayer();
		if (activeOffer == null || player == null || !VillagerCoins.active(player)) return;
		if (VillagerCoins.price(player, merchant.getOffers().indexOf(activeOffer)) >= 0 && merchant.getOffers().indexOf(activeOffer) != selectionHint) {
			activeOffer = null;
			futureXp = 0;
			setItem(2, ItemStack.EMPTY);
		}
	}
}
