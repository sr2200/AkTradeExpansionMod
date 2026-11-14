package com.akf.tradeexpansion.config;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.board.PriceLookupService;
import com.akf.tradeexpansion.currency.CoinType;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.network.ConfigSyncS2C;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;

/**
 * Server-side handling of the settings: coin ratio lock (E-4), client sync (G), {@code /aktradeex reload}.
 */
public final class ServerConfig {
	private ServerConfig() {
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STARTED.register(ServerConfig::applyRatioLock);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> GoodsCatalog.setRegistries(server.registryAccess()));
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
			GoodsCatalog.setRegistries(server.registryAccess());
			PriceLookupService.broadcastTradableItems(server); // I (v0.47): recipe viewer pages
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			sender.sendPacket(ConfigSyncS2C.fromServer(server));
			sender.sendPacket(PriceLookupService.tradableItems());
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PriceLookupService.forget(handler.getPlayer().getUUID()));
	}

	/** Re-reads the config file, applies it and pushes the synced values to every player. */
	public static void reload(MinecraftServer server) {
		ConfigManager.load();
		applyRatioLock(server);
		sync(server);
		PriceLookupService.broadcastTradableItems(server); // goods overrides of the config file may have changed
		TradeExpansion.LOGGER.info("Reloaded {}", ConfigManager.path());
	}

	/** Pushes the synced values (settings, clock offset) to every player. */
	public static void sync(MinecraftServer server) {
		ConfigSyncS2C sync = ConfigSyncS2C.fromServer(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerPlayNetworking.send(player, sync);
		}
	}

	/**
	 * E-4: the world remembers the ratios it was created with. Locked → the world's ratios win;
	 * unlocked → the world adopts the config's ratios. Either way a change is logged.
	 */
	private static void applyRatioLock(MinecraftServer server) {
		MarketRegistry registry = MarketRegistry.get(server);
		int[] saved = registry.currencyRatios();
		int[] configured = TradeExpansionConfig.get().currency.ratioArray();
		if (Arrays.equals(saved, configured)) return;
		if (TradeExpansionConfig.get().currency.ratioLocked) {
			TradeExpansion.LOGGER.warn("currency.ratios {} differs from this world's {}; ratioLocked is on, keeping the world's ratios",
					Arrays.toString(configured), Arrays.toString(saved));
			CoinType.setRatios(saved);
		} else {
			TradeExpansion.LOGGER.warn("currency.ratios changed from {} to {}; coins already held change their value",
					Arrays.toString(saved), Arrays.toString(configured));
			registry.setCurrencyRatios(configured);
		}
	}
}
