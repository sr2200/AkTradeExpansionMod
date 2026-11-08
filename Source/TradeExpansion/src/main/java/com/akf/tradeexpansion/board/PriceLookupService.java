package com.akf.tradeexpansion.board;

import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.GoodParams;
import com.akf.tradeexpansion.core.GoodState;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.GoodsDefinition;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketNames;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.market.MarketUpkeep;
import com.akf.tradeexpansion.market.Reputation;
import com.akf.tradeexpansion.network.PriceLookupC2S;
import com.akf.tradeexpansion.network.PriceLookupS2C;
import com.akf.tradeexpansion.network.TradableItemsS2C;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

/**
 * I (v0.47): prices of an item at the nearest markets, for the recipe viewer pages (JEI / REI). Like the bulletin board,
 * only markets within {@code board.radius} of the player are listed (farther markets have to be visited), with the
 * player's reputation applied and the latest prices.
 */
public final class PriceLookupService {
	/** Markets listed on a page. */
	public static final int MAX_MARKETS = 5;
	/** Ignore requests that come faster than this (game ticks): the client asks again when a page stays open. */
	private static final long MIN_INTERVAL_TICKS = 2;

	private static final Map<UUID, Long> LAST_REQUEST = new HashMap<>();

	private PriceLookupService() {
	}

	public static void handle(ServerPlayer player, PriceLookupC2S request) {
		long tick = player.level().getGameTime();
		Long last = LAST_REQUEST.put(player.getUUID(), tick);
		if (last != null && tick >= last && tick - last < MIN_INTERVAL_TICKS) return;

		List<Identifier> goodsIds = new ArrayList<>();
		GoodsCatalog.all().forEach((goodsId, def) -> {
			if (def.item().equals(request.item()) && (def.buy() || def.sell())) goodsIds.add(goodsId);
		});
		double radius = TradeExpansionConfig.get().board.radius;
		List<PriceLookupS2C.Entry> entries = new ArrayList<>();
		if (!goodsIds.isEmpty()) {
			BlockPos pos = player.blockPosition();
			List<Market> candidates = MarketRegistry.get(player.level().getServer()).inDimension(player.level().dimension()).stream()
					.filter(m -> m.horizontalDistanceTo(pos) <= radius && goodsIds.stream().anyMatch(id -> m.good(id) != null))
					.sorted(Comparator.comparingDouble(m -> m.horizontalDistanceTo(pos)))
					.toList();
			UUID uuid = player.getUUID();
			int markets = 0;
			for (Market m : candidates) {
				if (markets >= MAX_MARKETS) break;
				long now = MarketUpkeep.touch(player.level(), m); // latest prices (I)
				boolean listed = false;
				for (Identifier goodsId : goodsIds) {
					PriceLookupS2C.Entry entry = entry(m, uuid, now, goodsId, pos);
					if (entry == null) continue;
					entries.add(entry);
					listed = true;
				}
				if (listed) markets++;
			}
		}
		ServerPlayNetworking.send(player, new PriceLookupS2C(request.item(), (int) Math.round(radius), entries));
	}

	private static PriceLookupS2C.Entry entry(Market m, UUID uuid, long now, Identifier goodsId, BlockPos pos) {
		GoodState state = m.good(goodsId);
		GoodsDefinition def = GoodsCatalog.get(goodsId);
		if (state == null || def == null || !Reputation.canSee(m, uuid, goodsId, now)) return null; // M: bonus specials
		GoodParams p = Reputation.params(m, uuid, goodsId, now); // M: prices as this player would get them
		if (p == null) return null;
		long buy = def.buy() && PriceModel.canBuy(state) ? PriceModel.buyPrice(p, state.pressure()) : -1;
		long sell = def.sell() && PriceModel.canSell(p, state) ? PriceModel.sellPrice(p, state.pressure()) : -1;
		BlockPos center = m.center();
		return new PriceLookupS2C.Entry(m.id(), MarketNames.title(m), (int) Math.round(m.horizontalDistanceTo(pos)),
				center.getX() - pos.getX(), center.getZ() - pos.getZ(), def.unit(), buy, sell, (int) Math.floor(state.stock()),
				m.shortages().containsKey(goodsId));
	}

	/** The items that some goods definition buys or sells (sorted, no duplicates). */
	public static TradableItemsS2C tradableItems() {
		TreeSet<Identifier> items = new TreeSet<>();
		GoodsCatalog.all().values().forEach(def -> {
			if (def.buy() || def.sell()) items.add(def.item());
		});
		return new TradableItemsS2C(List.copyOf(items));
	}

	/** Sends the tradable items to every player (after a data pack or config reload). */
	public static void broadcastTradableItems(MinecraftServer server) {
		TradableItemsS2C payload = tradableItems();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	public static void forget(UUID player) {
		LAST_REQUEST.remove(player);
	}
}
