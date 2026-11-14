package com.akf.tradeexpansion.test.mixin.client;

import com.akf.tradeexpansion.test.client.TextRecorder;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every text a screen draws goes through {@code addText}: the test's {@link TextRecorder} sees where it lands. */
@Mixin(GuiRenderState.class)
public class GuiRenderStateMixin {
	@Inject(method = "addText", at = @At("HEAD"))
	private void ak_trade_expansion_test$recordText(GuiTextRenderState text, CallbackInfo ci) {
		TextRecorder.onText(text);
	}

	@Inject(method = "nextStratum", at = @At("HEAD"))
	private void ak_trade_expansion_test$nextStratum(CallbackInfo ci) {
		TextRecorder.onNextStratum();
	}

	@Inject(method = "reset", at = @At("HEAD"))
	private void ak_trade_expansion_test$reset(CallbackInfo ci) {
		TextRecorder.onFrame();
	}
}
