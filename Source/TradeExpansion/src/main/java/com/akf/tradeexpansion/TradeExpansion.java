package com.akf.tradeexpansion;

import com.akf.tradeexpansion.command.TradeExpansionCommand;
import com.akf.tradeexpansion.config.ConfigManager;
import com.akf.tradeexpansion.config.ServerConfig;
import com.akf.tradeexpansion.crafting.EmeraldCoinRecipe;
import com.akf.tradeexpansion.crafting.MintingRecipe;
import com.akf.tradeexpansion.loot.CoinDrops;
import com.akf.tradeexpansion.data.DataReloadListener;
import com.akf.tradeexpansion.network.ModPayloads;
import com.akf.tradeexpansion.registry.ModBlockEntities;
import com.akf.tradeexpansion.registry.ModBlocks;
import com.akf.tradeexpansion.registry.ModSounds;
import com.akf.tradeexpansion.registry.ModDataComponents;
import com.akf.tradeexpansion.registry.ModEntities;
import com.akf.tradeexpansion.registry.ModCreativeTab;
import com.akf.tradeexpansion.registry.ModItems;
import com.akf.tradeexpansion.registry.ModMenus;
import com.akf.tradeexpansion.worldgen.VillageStalls;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TradeExpansion implements ModInitializer {
	public static final String MOD_ID = "ak_trade_expansion";

	/** Log lines are prefixed with [TradeExpansion]. */
	public static final Logger LOGGER = LoggerFactory.getLogger("TradeExpansion");

	@Override
	public void onInitialize() {
		ConfigManager.load();
		ModDataComponents.init();
		ModItems.init();
		ModEntities.init();
		ModBlocks.init();
		ModSounds.init();
		ModBlockEntities.init();
		ModMenus.init();
		ModCreativeTab.init();
		ModPayloads.init();
		com.akf.tradeexpansion.currency.StartingMoney.register(); // E-2: currency.startingMoney on the first join
		com.akf.tradeexpansion.market.Reputation.register(); // M: hurting villagers / traders lowers the reputation
		com.akf.tradeexpansion.development.FacilityBuilder.register(); // N-4 / N-5: constructions, signs, protection
		EmeraldCoinRecipe.init();
		MintingRecipe.init();
		CoinDrops.init();
		VillagerTradeToggle.init();
		VillageStalls.init();
		com.akf.tradeexpansion.market.PlacementGuide.init();
		DataReloadListener.init();
		ServerConfig.init();
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> TradeExpansionCommand.register(dispatcher));

		LOGGER.info("AK Trade Expansion initialized");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
