package com.akf.tradeexpansion;

import com.akf.tradeexpansion.command.TradeExpansionCommand;
import com.akf.tradeexpansion.network.ModPayloads;
import com.akf.tradeexpansion.registry.ModBlockEntities;
import com.akf.tradeexpansion.registry.ModBlocks;
import com.akf.tradeexpansion.registry.ModCreativeTab;
import com.akf.tradeexpansion.registry.ModItems;
import com.akf.tradeexpansion.registry.ModMenus;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TradeExpansion implements ModInitializer {
	public static final String MOD_ID = "trade_expansion";

	/** Log lines are prefixed with [TradeExpansion]. */
	public static final Logger LOGGER = LoggerFactory.getLogger("TradeExpansion");

	@Override
	public void onInitialize() {
		ModItems.init();
		ModBlocks.init();
		ModBlockEntities.init();
		ModMenus.init();
		ModCreativeTab.init();
		ModPayloads.init();
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> TradeExpansionCommand.register(dispatcher));

		LOGGER.info("Trade Expansion initialized");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
