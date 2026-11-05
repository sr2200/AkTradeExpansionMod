package com.akf.tradeexpansion.mixin;

import com.akf.tradeexpansion.market.Reputation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** M (v0.29): every completed villager trade (coins or emeralds) raises the reputation with the nearby market. */
@Mixin(value = AbstractVillager.class, remap = false)
public abstract class AbstractVillagerMixin {
	@Inject(method = "notifyTrade", at = @At("TAIL"))
	private void tradeExpansion$reputation(MerchantOffer offer, CallbackInfo ci) {
		AbstractVillager self = (AbstractVillager) (Object) this;
		if (self instanceof Villager villager && self.getTradingPlayer() instanceof ServerPlayer player) {
			Reputation.onVillagerTrade(player, villager);
		}
	}
}
