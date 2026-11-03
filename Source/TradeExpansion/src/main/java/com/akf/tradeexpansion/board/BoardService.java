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
import com.akf.tradeexpansion.menu.MarketMenu;
import com.akf.tradeexpansion.network.BoardRequestC2S;
import com.akf.tradeexpansion.network.BoardS2C;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Bulletin board (design I): latest prices of the other markets within {@code board.radius}.
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
		List<BoardS2C.Entry> entries = new ArrayList<>();
		for (Market m : nearby) {
			MarketUpkeep.touch(player.level(), m); // latest prices (I)
			List<BoardS2C.Good> goods = new ArrayList<>();
			m.goods().forEach((goodsId, state) -> {
				GoodParams p = m.params(goodsId);
				GoodsDefinition def = GoodsCatalog.get(goodsId);
				if (p == null || def == null) return;
				goods.add(good(def, p, state, m.shortages().containsKey(goodsId)));
			});
			entries.add(new BoardS2C.Entry(m.id(), m.level(), m.center().getX() - here.center().getX(),
					m.center().getZ() - here.center().getZ(), goods));
		}
		ServerPlayNetworking.send(player, new BoardS2C(here.id(), entries));
	}

	private static BoardS2C.Good good(GoodsDefinition def, GoodParams p, GoodState state, boolean shortage) {
		long buy = def.buy() && PriceModel.canBuy(state) ? PriceModel.buyPrice(p, state.pressure()) : -1;
		long sell = def.sell() && PriceModel.canSell(p, state) ? PriceModel.sellPrice(p, state.pressure()) : -1;
		return new BoardS2C.Good(def.item(), def.unit(), buy, sell, (int) Math.floor(state.stock()), shortage);
	}
}
