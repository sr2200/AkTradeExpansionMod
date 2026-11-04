package com.akf.tradeexpansion.mixin;

import com.akf.tradeexpansion.villager.VillagerCoins;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stage 14 (design H): sends the coin prices right after a villager's or wandering trader's offers (screen open / refresh). */
@Mixin(value = ServerPlayer.class, remap = false)
public abstract class ServerPlayerMixin {
	@Inject(method = "sendMerchantOffers", at = @At("TAIL"))
	private void tradeExpansion$sendCoinPrices(int containerId, MerchantOffers offers, int level, int xp, boolean showProgress,
			boolean canRestock, CallbackInfo ci) {
		ServerPlayer self = (ServerPlayer) (Object) this;
		if (self.containerMenu instanceof MerchantMenu menu && menu.containerId == containerId) {
			VillagerCoins.sendPrices(self, containerId, offers, ((MerchantMenuAccessor) menu).tradeExpansion$trader());
		} else {
			VillagerCoins.clear(self.getUUID());
		}
	}
}
