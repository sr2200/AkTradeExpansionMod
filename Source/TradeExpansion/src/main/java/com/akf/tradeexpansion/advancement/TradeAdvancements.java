package com.akf.tradeexpansion.advancement;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.bank.BankService;
import com.akf.tradeexpansion.currency.WalletService;
import com.akf.tradeexpansion.development.FacilityKind;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketClock;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.market.Reputation;
import com.akf.tradeexpansion.rival.Campaign;
import com.akf.tradeexpansion.rival.RivalService;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Advancements of the mod (design R). Every economy event the advancements look at goes through here as a
 * {@link TradeEventTrigger} event. States (wealth, reputation, buildings, markets traded in) are also checked when a
 * player joins or opens a trade screen, so progress made before the advancements existed counts too. Event names come
 * from {@link AdvancementEvents} (inherited, so callers write {@code TradeAdvancements.TRADE}).
 */
public final class TradeAdvancements implements AdvancementEvents {
	public static final TradeEventTrigger TRIGGER = Registry.register(BuiltInRegistries.TRIGGER_TYPES, TradeExpansion.id("trade_event"),
			new TradeEventTrigger());

	/** Ids of the markets the player has bought or sold at (for the "trade at N markets" advancements). */
	public static final AttachmentType<List<Long>> TRADED_MARKETS = AttachmentRegistry.create(TradeExpansion.id("traded_markets"),
			builder -> builder.persistent(Codec.LONG.listOf()).copyOnDeath());

	private TradeAdvancements() {
	}

	public static void init() {
		// Class loading registers the trigger and the attachment.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.getPlayer(), server));
	}

	public static void event(ServerPlayer player, String event, long value) {
		TRIGGER.trigger(player, event, value);
	}

	public static void event(ServerPlayer player, String event) {
		event(player, event, 1);
	}

	/**
	 * A buy or sell of {@code total} copper at {@code market} ({@code units}: trading units, for the bulk buy). Callers
	 * report sell-side events ({@link #SELL_STOPPED} etc.) with {@link #afterSale}.
	 */
	public static void afterTrade(ServerPlayer player, Market market, boolean buy, int units, long total, long now) {
		event(player, TRADE, total);
		if (buy) event(player, BULK_BUY, units);
		if (market.dimension().equals(Level.NETHER)) event(player, TRADE_NETHER);
		if (market.dimension().equals(Level.END)) event(player, TRADE_END);
		List<Long> traded = player.getAttachedOrElse(TRADED_MARKETS, List.of());
		if (!traded.contains(market.id())) {
			List<Long> updated = new ArrayList<>(traded);
			updated.add(market.id());
			traded = List.copyOf(updated);
			player.setAttached(TRADED_MARKETS, traded);
		}
		event(player, MARKETS, traded.size());
		checkMarket(player, market, now);
		checkWealth(player);
	}

	/** One good sold: shortage, rival buying, and whether the sale filled the market's stock. */
	public static void afterSale(ServerPlayer player, MarketRegistry registry, Market market, Identifier goodsId, boolean full, long now) {
		if (market.shortages().containsKey(goodsId)) event(player, SHORTAGE_SOLD);
		Campaign campaign = RivalService.strongest(registry, market, goodsId, now);
		if (campaign != null && campaign.buying()) event(player, RIVAL_SOLD);
		if (full) event(player, SELL_STOPPED);
	}

	/** Cash (wallets and coins) plus the bank account. */
	public static void checkWealth(ServerPlayer player) {
		long bank = BankService.accounts(player).balance(player.getUUID());
		event(player, WEALTH, WalletService.balance(player) + bank);
	}

	/** Reputation with {@code market} and its buildings (when the player has helped develop it). */
	public static void checkMarket(ServerPlayer player, Market market, long now) {
		event(player, REPUTATION, Reputation.level(market, player.getUUID(), now));
		if (market.contributors().containsKey(player.getUUID())) {
			event(player, FACILITIES, market.completedFacilities());
			int kinds = 0;
			for (FacilityKind kind : FacilityKind.values()) {
				if (market.hasFacility(kind)) kinds++;
			}
			event(player, FACILITY_KINDS, kinds);
		}
	}

	/** A building of {@code market} was completed: every online player who helped develop it. */
	public static void onFacilityCompleted(MinecraftServer server, Market market, long now) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (market.contributors().containsKey(player.getUUID())) checkMarket(player, market, now);
		}
	}

	private static void onJoin(ServerPlayer player, MinecraftServer server) {
		MarketRegistry registry = MarketRegistry.get(server);
		long now = MarketClock.now(player.level());
		int best = 0;
		for (Market market : registry.all()) {
			if (market.standing(player.getUUID()) != null) best = Math.max(best, Reputation.level(market, player.getUUID(), now));
			if (market.contributors().containsKey(player.getUUID())) checkMarket(player, market, now);
		}
		event(player, REPUTATION, best);
		event(player, MARKETS, player.getAttachedOrElse(TRADED_MARKETS, List.of()).size());
		checkWealth(player);
	}
}
