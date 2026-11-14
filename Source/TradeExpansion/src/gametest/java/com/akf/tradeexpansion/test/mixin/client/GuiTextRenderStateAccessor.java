package com.akf.tradeexpansion.test.mixin.client;

import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GuiTextRenderState.class)
public interface GuiTextRenderStateAccessor {
	@Accessor("text")
	FormattedCharSequence ak_trade_expansion_test$text();

	@Accessor("color")
	int ak_trade_expansion_test$color();
}
