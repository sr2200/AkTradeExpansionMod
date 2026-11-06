package com.akf.tradeexpansion.board;

import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.GoodParams;
import com.akf.tradeexpansion.core.GoodState;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.GoodsDefinition;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.market.MarketUpkeep;
import com.akf.tradeexpansion.market.Reputation;
import com.akf.tradeexpansion.menu.MarketMenu;
import com.akf.tradeexpansion.network.BoardRequestC2S;
import com.akf.tradeexpansion.network.BoardS2C;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Bulletin board (design I): latest prices of the other markets within {@code board.radius}, with deals (v0.24): goods at
 * least {@code board.dealThreshold} cheaper to buy, or paid that much more, than at the current market.
 */
public final class BoardService {
	private BoardService() {
	}

	public static void handle(ServerPlayer player, BoardRequestC2S request) {
		MarketRegistry registry = MarketRegistry.get(player.level().getServer());
		Market here = registry.get(request.marketId());
		if (here == null || !(player.containerMenu instanceof MarketMenu menu) || menu.marketId() != here.id() || !menu.stillValid(player)) {
			return;
		}
		TradeExpansionConfig.Board config = TradeExpansionConfig.get().board;
		List<Market> nearby = new ArrayList<>(registry.inDimension(here.dimension()).stream()
				.filter(m -> m != here && m.horizontalDistanceTo(here.center()) <= config.radius)
				.sorted(Comparator.comparingDouble(m -> m.horizontalDistanceTo(here.center())))
				.limit(config.maxMarkets)
				.toList());
		long now = MarketUpkeep.touch(player.level(), here);
		java.util.UUID uuid = player.getUUID();
		double threshold = config.dealThreshold;
		List<BoardS2C.Entry> entries = new ArrayList<>();
		for (Market m : nearby) {
			MarketUpkeep.touch(player.level(), m); // latest prices (I)
			List<BoardS2C.Good> goods = new ArrayList<>();
			m.goods().forEach((goodsId, state) -> {
				if (!Reputation.canSee(m, uuid, goodsId, now)) return; // M: bonus specials
				GoodParams p = Reputation.params(m, uuid, goodsId, now); // M: prices as this player would get them
				GoodsDefinition def = GoodsCatalog.get(goodsId);
				if (p == null || def == null) return;
				goods.add(good(here, uuid, now, goodsId, def, p, state, m.shortages().containsKey(goodsId), threshold));
			});
			entries.add(new BoardS2C.Entry(m.id(), com.akf.tradeexpansion.market.MarketNames.title(m), m.center().getX() - here.center().getX(),
					m.center().getZ() - here.center().getZ(), goods));
		}
		ServerPlayNetworking.send(player, new BoardS2C(here.id(), entries));
	}

	private static BoardS2C.Good good(Market here, java.util.UUID uuid, long now, net.minecraft.resources.Identifier goodsId, GoodsDefinition def, GoodParams p, GoodState state,
			boolean shortage, double threshold) {
		long buy = def.buy() && PriceModel.canBuy(state) ? PriceModel.buyPrice(p, state.pressure()) : -1;
		long sell = def.sell() && PriceModel.canSell(p, state) ? PriceModel.sellPrice(p, state.pressure()) : -1;
		// I (v0.24): compared with the current market, or with the standard price when the current market does not deal in it.
		GoodParams refParams = Reputation.params(here, uuid, goodsId, now);
		GoodState refState = here.good(goodsId);
		if (refParams == null || refState == null) {
			// M: the player's reputation with this market applies to the reference price too
			refParams = com.akf.tradeexpansion.core.ReputationMath.withBonus(def.params(1.0, 1.0, TradeExpansionConfig.get()),
					Reputation.priceBonus(Reputation.level(here, uuid, now)));
			refState = null;
		}
		double refX = refState == null ? 0 : refState.pressure();
		long refBuy = PriceModel.buyPrice(refParams, refX);
		long refSell = PriceModel.sellPrice(refParams, refX);
		int buyDeal = buy > 0 && refBuy > 0 && buy <= refBuy * (1 - threshold) ? (int) Math.round((1 - (double) buy / refBuy) * 100) : 0;
		int sellDeal = sell > 0 && refSell > 0 && sell >= refSell * (1 + threshold) ? (int) Math.round(((double) sell / refSell - 1) * 100) : 0;
		return new BoardS2C.Good(def.item(), def.unit(), buy, sell, (int) Math.floor(state.stock()), shortage, buyDeal, sellDeal);
	}
}
