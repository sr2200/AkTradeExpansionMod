package com.akf.tradeexpansion.test.mixin.client;

import net.minecraft.client.resources.language.ClientLanguage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minecraft shows a missing translation as its key without a word in the log: a key of this mod looked up and missing
 * in the language now in use is logged once as a warning, which tools/checklist/record.js counts as a problem of the run.
 */
@Mixin(ClientLanguage.class)
public class ClientLanguageMixin {
	@Unique
	private static final Logger ak_trade_expansion_test$LOGGER = LoggerFactory.getLogger("AKTradeExpansion/missing");
	@Unique
	private static final Set<String> ak_trade_expansion_test$reported = ConcurrentHashMap.newKeySet();

	@Shadow
	@Final
	private Map<String, String> storage;

	@Inject(method = "getOrDefault", at = @At("HEAD"))
	private void ak_trade_expansion_test$reportMissing(String key, String defaultValue, CallbackInfoReturnable<String> cir) {
		if (key.contains("ak_trade_expansion") && !key.contains("ak_trade_expansion_test") && !storage.containsKey(key)
				&& ak_trade_expansion_test$reported.add(key)) {
			ak_trade_expansion_test$LOGGER.warn("Missing translation: {}", key);
		}
	}
}
