package com.akf.tradeexpansion.quest;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.bank.BankAccounts;
import com.akf.tradeexpansion.bank.BankService;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.core.QuestMath;
import com.akf.tradeexpansion.currency.WalletService;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.GoodsDefinition;
import com.akf.tradeexpansion.item.CoinItem;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketClock;
import com.akf.tradeexpansion.market.MarketFactory;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.menu.MarketMenu;
import com.akf.tradeexpansion.network.QuestDeliverC2S;
import com.akf.tradeexpansion.network.QuestResultS2C;
import com.akf.tradeexpansion.trade.InventoryUtil;
import com.akf.tradeexpansion.trade.TradeResult;
import com.akf.tradeexpansion.trade.TradeService;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Delivery requests (design J): markets post up to {@code quest.maxPerMarket} requests, refilled every
 * {@code quest.refreshDays}; anyone may deliver (first come). Rewards are new money, tuned by the multipliers.
 */
public final class QuestService {
	private QuestService() {
	}

	/** Drops expired requests and refills the free slots when the refresh interval has passed. @return changed */
	public static boolean refresh(ServerLevel level, Market market, long now) {
		TradeExpansionConfig.Quest config = TradeExpansionConfig.get().quest;
		boolean changed = market.requests().removeIf(r -> r.deadline() < now);
		long interval = Math.round(config.refreshDays * PriceModel.TICKS_PER_DAY);
		if (market.lastRequestRefresh() != Long.MIN_VALUE && now - market.lastRequestRefresh() < interval) return changed;
		market.setLastRequestRefresh(now);
		Random random = new Random(MarketFactory.seed(level, market.center()) ^ now);
		while (market.requests().size() < config.maxPerMarket) {
			Request request = create(market, random, now);
			if (request == null) break;
			market.requests().add(request);
			changed = true;
		}
		return changed;
	}

	/** Replaces every request (used by {@code market reroll}, design K). */
	public static void reroll(ServerLevel level, Market market, long now) {
		market.requests().clear();
		market.setLastRequestRefresh(Long.MIN_VALUE);
		refresh(level, market, now);
	}

	private static Request create(Market market, Random random, long now) {
		TradeExpansionConfig.Quest config = TradeExpansionConfig.get().quest;
		Set<Identifier> taken = new HashSet<>();
		market.requests().forEach(r -> taken.add(r.goods()));
		// Prefer goods this market does not sell to players, so buying here and delivering here cannot profit.
		List<Identifier> preferred = new ArrayList<>();
		List<Identifier> others = new ArrayList<>();
		for (Map.Entry<Identifier, GoodsDefinition> e : GoodsCatalog.all().entrySet()) {
			GoodsDefinition def = e.getValue();
			// No tokens (unit / auto_add) and only plain items (J: deliveries take items without components).
			if (taken.contains(e.getKey()) || def.unit() != 1 || def.autoAdd().isPresent() || def.components().isPresent()) continue;
			boolean soldHere = market.good(e.getKey()) != null && def.buy();
			(soldHere ? others : preferred).add(e.getKey());
		}
		List<Identifier> pool = preferred.isEmpty() ? others : preferred;
		if (pool.isEmpty()) return null;
		Identifier goodsId = pool.get(random.nextInt(pool.size()));
		GoodsDefinition def = GoodsCatalog.get(goodsId);
		int quantity = 1 + random.nextInt(Math.max(1, (int) Math.round(def.targetStock())));
		double value = market.basePriceFor(goodsId) * quantity;
		int[] thresholds = config.difficultyThresholds.stream().mapToInt(Integer::intValue).toArray();
		double[] multipliers = config.rewardMultipliers.stream().mapToDouble(Double::doubleValue).toArray();
		int difficulty = QuestMath.difficulty(value, thresholds);
		long reward = Math.max(1, QuestMath.reward(value, difficulty, multipliers));
		long deadline = now + Math.round(config.durationDays * PriceModel.TICKS_PER_DAY);
		return new Request(market.takeRequestId(), goodsId, quantity, reward, deadline, difficulty);
	}

	public static void deliver(ServerPlayer player, QuestDeliverC2S request) {
		MarketRegistry registry = MarketRegistry.get(player.level().getServer());
		Market market = registry.get(request.marketId());
		long reward = 0;
		TradeResult result;
		if (market == null || !(player.containerMenu instanceof MarketMenu menu) || menu.marketId() != market.id()) {
			result = TradeResult.INVALID_REQUEST;
		} else if (!menu.stillValid(player)) {
			result = TradeResult.TOO_FAR;
		} else if (player.isCreative() && !TradeExpansionConfig.get().trade.allowCreative) {
			result = TradeResult.CREATIVE_NOT_ALLOWED;
		} else if (request.useCard() && !BankService.hasValidCard(player)) {
			result = TradeResult.CARD_INVALID;
		} else {
			long now = MarketClock.now(player.level());
			refresh(player.level(), market, now);
			Request target = market.requests().stream().filter(r -> r.id() == request.requestId()).findFirst().orElse(null);
			GoodsDefinition def = target == null ? null : GoodsCatalog.get(target.goods());
			Item item = def == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(def.item());
			// Requests take exact items only (with the good's components), never damaged ones.
			InventoryUtil.Match match = def == null ? null : new InventoryUtil.Match(item, GoodsCatalog.patch(target.goods()), false);
			if (target == null || item == Items.AIR) {
				result = TradeResult.NOT_AVAILABLE; // expired or delivered by someone else
			} else if (InventoryUtil.count(player, match, menu.sellSlot()) < target.quantity()) {
				result = TradeResult.INSUFFICIENT_ITEMS;
			} else {
				InventoryUtil.remove(player, match, menu.sellSlot(), target.quantity());
				if (request.useCard()) {
					BankAccounts.get(player.level().getServer()).add(player.getUUID(), target.reward());
				} else {
					WalletService.give(player, target.reward());
				}
				market.requests().remove(target);
				registry.setDirty();
				reward = target.reward();
				result = TradeResult.SUCCESS;
				if (TradeExpansionConfig.get().log.trades) {
					TradeExpansion.LOGGER.info("{} DELIVER {} x{} @market#{} reward={}{}", player.getPlainTextName(), def.item(),
							target.quantity(), market.id(), CoinItem.formatCopper(reward), request.useCard() ? " (card)" : "");
				}
				TradeService.broadcast(player.level().getServer(), registry, Set.of(market.id()), now);
			}
		}
		ServerPlayNetworking.send(player, new QuestResultS2C(result, reward));
		BankService.sendAccount(player);
	}
}
