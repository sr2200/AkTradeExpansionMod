package com.akf.tradeexpansion.mixin;

import com.akf.tradeexpansion.villager.VillagerCoins;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stage 14a (design H): taking the result of a coin trade charges the coin price (wallet → coins, server side) and
 * uses up only the non-emerald part of the cost; the rest of the vanilla bookkeeping (uses, stats, experience)
 * stays the same.
 */
@Mixin(MerchantResultSlot.class)
public abstract class MerchantResultSlotMixin {
	@Shadow @Final private MerchantContainer slots;
	@Shadow @Final private Player player;
	@Shadow @Final private Merchant merchant;

	@Shadow protected abstract void checkTakeAchievements(ItemStack stack);

	/**
	 * Vanilla only lets the result be taken while the payment slots hold the full cost (emeralds included). A coin
	 * trade can be taken while the player can pay the coin price and the payment slots hold the non-emerald part.
	 */
	@Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
	private void tradeExpansion$canPay(Player taker, CallbackInfoReturnable<Boolean> cir) {
		MerchantOffer offer = slots.getActiveOffer();
		if (offer == null || !VillagerCoins.active(player)) return;
		long price = VillagerCoins.price(player, merchant.getOffers().indexOf(offer));
		if (price < 0) return;
		cir.setReturnValue(com.akf.tradeexpansion.currency.WalletService.canPay(player, price)
				&& VillagerCoins.otherCostPaid(offer, slots.getItem(0), slots.getItem(1)));
	}

	@Inject(method = "onTake", at = @At("HEAD"), cancellable = true)
	private void tradeExpansion$payWithCoins(Player taker, ItemStack stack, CallbackInfo ci) {
		MerchantOffer offer = slots.getActiveOffer();
		if (offer == null || !VillagerCoins.active(player)) return;
		long price = VillagerCoins.price(player, merchant.getOffers().indexOf(offer));
		if (price < 0) return;
		ci.cancel();
		checkTakeAchievements(stack);
		if (!player.level().isClientSide() && !VillagerCoins.charge(player, price, offer)) {
			return; // money spent elsewhere in the meantime: the item was already handed out by the slot, nothing more to do
		}
		ItemCost other = VillagerCoins.otherItemCost(offer);
		if (other != null) {
			int need = VillagerCoins.otherCost(offer).getCount();
			for (int slot = 0; slot < 2 && need > 0; slot++) {
				ItemStack paid = slots.getItem(slot);
				if (other.test(paid)) {
					int n = Math.min(need, paid.getCount());
					paid.shrink(n);
					need -= n;
					slots.setItem(slot, paid);
				}
			}
		}
		merchant.notifyTrade(offer);
		player.awardStat(Stats.TRADED_WITH_VILLAGER);
		merchant.overrideXp(merchant.getVillagerXp() + offer.getXp());
		slots.updateSellItem();
	}
}
