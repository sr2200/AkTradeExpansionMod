package com.akf.tradeexpansion.test.mixin.client;

import com.akf.tradeexpansion.client.MarketScreen;
import com.akf.tradeexpansion.test.client.TextRecorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The "sell all" box is drawn over the goods list in the same layer: {@link TextRecorder} marks where it starts, so the
 * layout check can leave out the list texts the box covers.
 */
@Mixin(MarketScreen.class)
public class MarketScreenMixin {
	@Inject(method = "extractSellAll", at = @At("HEAD"))
	private void ak_trade_expansion_test$markSellAll(CallbackInfo ci) {
		TextRecorder.onMark();
	}
}
