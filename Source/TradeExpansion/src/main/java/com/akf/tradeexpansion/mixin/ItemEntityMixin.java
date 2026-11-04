package com.akf.tradeexpansion.mixin;

import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.currency.WalletService;
import com.akf.tradeexpansion.item.CoinItem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Wallet auto-collect (design E-6): coins a player picks up go straight into their wallet.
 * Respects the pickup delay and the item's target player like vanilla; what does not fit is picked up normally.
 */
@Mixin(value = ItemEntity.class, remap = false)
public abstract class ItemEntityMixin {
	@Shadow
	private UUID target;

	@Shadow
	public abstract ItemStack getItem();

	@Shadow
	public abstract boolean hasPickUpDelay();

	@Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
	private void tradeExpansion$collectIntoWallet(Player player, CallbackInfo ci) {
		ItemEntity self = (ItemEntity) (Object) this;
		if (self.level().isClientSide() || !TradeExpansionConfig.get().wallet.autoCollect) return;
		ItemStack stack = getItem();
		if (!(stack.getItem() instanceof CoinItem) || hasPickUpDelay() || (target != null && !target.equals(player.getUUID()))) return;

		int stored = WalletService.collectPickup(player, stack);
		if (stored == 0) return;
		player.take(self, stored);
		if (stack.isEmpty()) {
			self.discard();
			ci.cancel();
		}
	}
}
