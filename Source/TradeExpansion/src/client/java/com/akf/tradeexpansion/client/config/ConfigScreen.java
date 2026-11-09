package com.akf.tradeexpansion.client.config;

import com.akf.tradeexpansion.config.ConfigManager;
import com.akf.tradeexpansion.config.ServerConfig;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.DoubleFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.LongFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * YACL settings screen opened from Mod Menu (design G, Q35). Edits a copy of {@code config/ak_trade_expansion.json};
 * while connected to a remote server only the client settings can be changed.
 */
public final class ConfigScreen {
	private static final String KEY = "config.ak_trade_expansion.";

	private final TradeExpansionConfig edit;
	/** Connected to a server we do not host: server-side options are read-only. */
	private final boolean serverLocked;

	private ConfigScreen() {
		this.edit = ConfigManager.read(ConfigManager.path());
		Minecraft mc = Minecraft.getInstance();
		this.serverLocked = mc.level != null && mc.getSingleplayerServer() == null;
	}

	public static Screen create(Screen parent) {
		return new ConfigScreen().build().generateScreen(parent);
	}

	private YetAnotherConfigLib build() {
		TradeExpansionConfig d = TradeExpansionConfig.defaults();
		TradeExpansionConfig.Currency cur = edit.currency;
		TradeExpansionConfig.Price price = edit.price;
		TradeExpansionConfig.Recovery rec = edit.recovery;
		TradeExpansionConfig.Distance dist = edit.distance;
		TradeExpansionConfig.Market market = edit.market;
		TradeExpansionConfig.Trade trade = edit.trade;

		ConfigCategory.Builder currency = category("currency");
		for (int i = 0; i < 4; i++) {
			int index = i;
			currency.option(intField("currency.ratios." + i, d.currency.ratios.get(i), () -> cur.ratios.get(index), v -> cur.ratios.set(index, v), 2, 100));
		}
		currency.option(bool("currency.ratioLocked", d.currency.ratioLocked, () -> cur.ratioLocked, v -> cur.ratioLocked = v))
				.option(intField("currency.emeraldCoinsPerBlock", d.currency.emeraldCoinsPerBlock, () -> cur.emeraldCoinsPerBlock, v -> cur.emeraldCoinsPerBlock = v, 0, 64))
				.option(bool("currency.sneakUseExchange", d.currency.sneakUseExchange, () -> cur.sneakUseExchange, v -> cur.sneakUseExchange = v))
				.option(bool("currency.allowMinting", d.currency.allowMinting, () -> cur.allowMinting, v -> cur.allowMinting = v))
				.option(bool("currency.allowIronMinting", d.currency.allowIronMinting, () -> cur.allowIronMinting, v -> cur.allowIronMinting = v))
				.option(bool("currency.changeOwnerOnly", d.currency.changeOwnerOnly, () -> cur.changeOwnerOnly, v -> cur.changeOwnerOnly = v))
				.option(bool("currency.emeraldCoinFixedPrice", d.currency.emeraldCoinFixedPrice, () -> cur.emeraldCoinFixedPrice, v -> cur.emeraldCoinFixedPrice = v))
				.option(bool("currency.emeraldCoinUnlimited", d.currency.emeraldCoinUnlimited, () -> cur.emeraldCoinUnlimited, v -> cur.emeraldCoinUnlimited = v))
				.option(doubleField("currency.exchangeFee", d.currency.exchangeFee, () -> cur.exchangeFee, v -> cur.exchangeFee = v, 0, 0.5))
				.option(longField("currency.startingMoney", d.currency.startingMoney, () -> cur.startingMoney, v -> cur.startingMoney = v, 0, 1_000_000_000L))
				.option(bool("currency.mobDropCoins", d.currency.mobDropCoins, () -> cur.mobDropCoins, v -> cur.mobDropCoins = v));

		ConfigCategory.Builder priceCat = category("price")
				.option(doubleField("price.defaultSpread", d.price.defaultSpread, () -> price.defaultSpread, v -> price.defaultSpread = v, 0.05, 0.9))
				.option(doubleField("price.defaultElasticity", d.price.defaultElasticity, () -> price.defaultElasticity, v -> price.defaultElasticity = v, 3, 10000))
				.option(doubleField("price.minMultiplier", d.price.minMultiplier, () -> price.minMultiplier, v -> price.minMultiplier = v, 0.01, 1))
				.option(doubleField("price.maxMultiplier", d.price.maxMultiplier, () -> price.maxMultiplier, v -> price.maxMultiplier = v, 1, 100))
				.option(doubleField("price.jitter", d.price.jitter, () -> price.jitter, v -> price.jitter = v, 0, 0.5))
				.option(doubleField("price.defaultMaxStockMultiplier", d.price.defaultMaxStockMultiplier, () -> price.defaultMaxStockMultiplier, v -> price.defaultMaxStockMultiplier = v, 1, 10))
				.option(doubleField("price.globalMultiplier", d.price.globalMultiplier, () -> price.globalMultiplier, v -> price.globalMultiplier = v, 0.01, 100));

		ConfigCategory.Builder recovery = category("recovery")
				.option(doubleField("recovery.halfLifeDays", d.recovery.halfLifeDays, () -> rec.halfLifeDays, v -> rec.halfLifeDays = v, 0.05, 100))
				.option(doubleField("recovery.restockMultiplier", d.recovery.restockMultiplier, () -> rec.restockMultiplier, v -> rec.restockMultiplier = v, 0, 100))
				.option(doubleField("recovery.consumableMultiplier", d.recovery.consumableMultiplier, () -> rec.consumableMultiplier, v -> rec.consumableMultiplier = v, 0, 10))
				.option(doubleField("recovery.consumableHalfLifeDays", d.recovery.consumableHalfLifeDays, () -> rec.consumableHalfLifeDays, v -> rec.consumableHalfLifeDays = v, 0.05, 100))
				.option(server(Option.<TradeExpansionConfig.RecoveryClock>createBuilder()
						.name(Component.translatable(KEY + "recovery.clock"))
						.description(description("recovery.clock"))
						.binding(d.recovery.clock, () -> rec.clock, v -> rec.clock = v)
						.controller(o -> EnumControllerBuilder.create(o).enumClass(TradeExpansionConfig.RecoveryClock.class))));

		// v0.27: goods added by the config file and the consumables lists (B-3) are picked from icons on their own screen.
		ConfigCategory.Builder goods = category("goods")
				.option(ButtonOption.createBuilder()
						.name(Component.translatable(KEY + "goods_manager.open"))
						.text(Component.translatable(KEY + "goods_manager.open.button"))
						.description(description("goods_manager.open"))
						.action((screen, option) -> Minecraft.getInstance().gui.setScreen(new GoodsManagerScreen(screen, edit, this::saveGoodsLists)))
						.available(!serverLocked)
						.build());

		ConfigCategory.Builder distance = category("distance")
				.option(doubleField("distance.mergeRadius", d.distance.mergeRadius, () -> dist.mergeRadius, v -> dist.mergeRadius = v, 8, 512))
				.option(doubleField("distance.nearRadius", d.distance.nearRadius, () -> dist.nearRadius, v -> dist.nearRadius = v, 8, 100000))
				.option(doubleField("distance.farRadius", d.distance.farRadius, () -> dist.farRadius, v -> dist.farRadius = v, 9, 100000))
				.option(doubleField("distance.falloffExponent", d.distance.falloffExponent, () -> dist.falloffExponent, v -> dist.falloffExponent = v, 0.5, 5))
				.option(doubleField("distance.minNewMarketDistance", d.distance.minNewMarketDistance, () -> dist.minNewMarketDistance, v -> dist.minNewMarketDistance = v, 0, 10000))
				.option(bool("distance.linkNetherToOverworld", d.distance.linkNetherToOverworld, () -> dist.linkNetherToOverworld, v -> dist.linkNetherToOverworld = v));

		ConfigCategory.Builder marketCat = category("market")
				.option(intField("market.minGoods", d.market.minGoods, () -> market.minGoods, v -> market.minGoods = v, 1, 100))
				.option(intField("market.maxGoods", d.market.maxGoods, () -> market.maxGoods, v -> market.maxGoods = v, 1, 100));
		thresholds(marketCat, "market.levelThresholds.", d.market.levelThresholds, market.levelThresholds);
		thresholds(marketCat, "market.villagerThresholds.", d.market.villagerThresholds, market.villagerThresholds);
		marketCat.option(bool("market.levelUsesVillagers", d.market.levelUsesVillagers, () -> market.levelUsesVillagers, v -> market.levelUsesVillagers = v));
		marketCat.option(server(Option.<TradeExpansionConfig.RenamePermission>createBuilder()
				.name(Component.translatable(KEY + "market.renamePermission"))
				.description(description("market.renamePermission"))
				.binding(d.market.renamePermission, () -> market.renamePermission, v -> market.renamePermission = v)
				.controller(o -> EnumControllerBuilder.create(o).enumClass(TradeExpansionConfig.RenamePermission.class)
						.formatValue(v -> Component.translatable(KEY + "market.renamePermission." + v.getSerializedName().toLowerCase(Locale.ROOT))))));
		marketCat.option(server(Option.<TradeExpansionConfig.NewMarketRequires>createBuilder()
				.name(Component.translatable(KEY + "market.newMarketRequires"))
				.description(description("market.newMarketRequires"))
				.binding(d.market.newMarketRequires, () -> market.newMarketRequires, v -> market.newMarketRequires = v)
				.controller(o -> EnumControllerBuilder.create(o).enumClass(TradeExpansionConfig.NewMarketRequires.class)
						.formatValue(v -> Component.translatable(KEY + "market.newMarketRequires." + v.getSerializedName().toLowerCase(Locale.ROOT))))))
				.option(intField("market.newMarketMinBeds", d.market.newMarketMinBeds, () -> market.newMarketMinBeds, v -> market.newMarketMinBeds = v, 0, 1000))
				.option(intField("market.newMarketMinVillagers", d.market.newMarketMinVillagers, () -> market.newMarketMinVillagers, v -> market.newMarketMinVillagers = v, 0, 1000))
				.option(intField("market.newMarketEnoughBeds", d.market.newMarketEnoughBeds, () -> market.newMarketEnoughBeds, v -> market.newMarketEnoughBeds = v, 0, 1000));
		marketCat.option(intField("market.levelRadius", d.market.levelRadius, () -> market.levelRadius, v -> market.levelRadius = v, 16, 256))
				.option(doubleField("market.levelCheckIntervalDays", d.market.levelCheckIntervalDays, () -> market.levelCheckIntervalDays, v -> market.levelCheckIntervalDays = v, 0, 100))
				.option(bool("market.shrinkOnLevelDown", d.market.shrinkOnLevelDown, () -> market.shrinkOnLevelDown, v -> market.shrinkOnLevelDown = v));
		// D-5: today's specials per level, price and stock.
		for (int i = 0; i < 5; i++) {
			int index = i;
			marketCat.option(intField("market.specialGoods." + i, d.market.specialGoods.get(i), () -> market.specialGoods.get(index), v -> market.specialGoods.set(index, v), 0, 10));
		}
		marketCat.option(doubleField("market.specialPriceMultiplier", d.market.specialPriceMultiplier, () -> market.specialPriceMultiplier, v -> market.specialPriceMultiplier = v, 0.5, 5))
				.option(doubleField("market.specialStockRatio", d.market.specialStockRatio, () -> market.specialStockRatio, v -> market.specialStockRatio = v, 0.01, 1));

		ConfigCategory.Builder wallet = category("wallet")
				.option(longField("wallet.capacity", d.wallet.capacity, () -> edit.wallet.capacity, v -> edit.wallet.capacity = v, 1000, 1_000_000_000L))
				.option(bool("wallet.autoCollect", d.wallet.autoCollect, () -> edit.wallet.autoCollect, v -> edit.wallet.autoCollect = v));

		ConfigCategory.Builder bank = category("bank")
				.option(intField("bank.minMarketLevel", d.bank.minMarketLevel, () -> edit.bank.minMarketLevel, v -> edit.bank.minMarketLevel = v, 1, 5))
				.option(longField("bank.cardFee", d.bank.cardFee, () -> edit.bank.cardFee, v -> edit.bank.cardFee = v, 0, 1_000_000L))
				.option(doubleField("bank.cardTransactionFee", d.bank.cardTransactionFee, () -> edit.bank.cardTransactionFee, v -> edit.bank.cardTransactionFee = v, 0, 0.1))
				.option(doubleField("bank.interestRatePerDay", d.bank.interestRatePerDay, () -> edit.bank.interestRatePerDay, v -> edit.bank.interestRatePerDay = v, 0, 0.01))
				.option(longField("bank.interestMaxBalance", d.bank.interestMaxBalance, () -> edit.bank.interestMaxBalance, v -> edit.bank.interestMaxBalance = v, 0, 1_000_000_000_000L));

		ConfigCategory.Builder tradeCat = category("trade")
				.option(doubleField("trade.maxDistance", d.trade.maxDistance, () -> trade.maxDistance, v -> trade.maxDistance = v, 2, 32))
				.option(intField("trade.maxPerTransaction", d.trade.maxPerTransaction, () -> trade.maxPerTransaction, v -> trade.maxPerTransaction = v, 1, 2304))
				.option(doubleField("trade.priceTolerance", d.trade.priceTolerance, () -> trade.priceTolerance, v -> trade.priceTolerance = v, 0, 0.2))
				.option(bool("trade.allowCreative", d.trade.allowCreative, () -> trade.allowCreative, v -> trade.allowCreative = v))
				.option(bool("trade.showPlacementHint", d.trade.showPlacementHint, () -> trade.showPlacementHint, v -> trade.showPlacementHint = v))
				.option(bool("trade.allowShiftAll", d.trade.allowShiftAll, () -> trade.allowShiftAll, v -> trade.allowShiftAll = v))
				.option(bool("trade.acceptDamaged", d.trade.acceptDamaged, () -> trade.acceptDamaged, v -> trade.acceptDamaged = v));
		// B-5: bulk discount steps (as many as the file has; add or remove steps in the file).
		for (int i = 0; i < trade.bulkDiscounts.size(); i++) {
			int index = i;
			TradeExpansionConfig.BulkTier def = index < d.trade.bulkDiscounts.size() ? d.trade.bulkDiscounts.get(index) : trade.bulkDiscounts.get(index);
			tradeCat.option(intField("trade.bulkDiscounts.minQuantity", def.minQuantity(), () -> trade.bulkDiscounts.get(index).minQuantity(),
							v -> trade.bulkDiscounts.set(index, new TradeExpansionConfig.BulkTier(v, trade.bulkDiscounts.get(index).rate())), 2, 2304))
					.option(doubleField("trade.bulkDiscounts.rate", def.rate(), () -> trade.bulkDiscounts.get(index).rate(),
							v -> trade.bulkDiscounts.set(index, new TradeExpansionConfig.BulkTier(trade.bulkDiscounts.get(index).minQuantity(), v)), 0, 0.5));
		}

		ConfigCategory.Builder other = category("other")
				.option(bool("log.trades", d.log.trades, () -> edit.log.trades, v -> edit.log.trades = v))
				.option(intField("command.permissionLevel", d.command.permissionLevel, () -> edit.command.permissionLevel, v -> edit.command.permissionLevel = v, 0, 4))
				.option(bool("worldgen.spawnStallsInVillages", d.worldgen.spawnStallsInVillages, () -> edit.worldgen.spawnStallsInVillages, v -> edit.worldgen.spawnStallsInVillages = v))
				.option(bool("worldgen.protectNaturalStalls", d.worldgen.protectNaturalStalls, () -> edit.worldgen.protectNaturalStalls, v -> edit.worldgen.protectNaturalStalls = v))
				.option(bool("worldgen.spawnTraderNpc", d.worldgen.spawnTraderNpc, () -> edit.worldgen.spawnTraderNpc, v -> edit.worldgen.spawnTraderNpc = v))
				.option(bool("vanilla.disableVillagerTrades", d.vanilla.disableVillagerTrades, () -> edit.vanilla.disableVillagerTrades, v -> edit.vanilla.disableVillagerTrades = v));

		TradeExpansionConfig.Quest quest = edit.quest;
		TradeExpansionConfig.Demand demand = edit.demand;
		ConfigCategory.Builder village = category("village")
				.option(intField("board.radius", d.board.radius, () -> edit.board.radius, v -> edit.board.radius = v, 64, 4096))
				.option(intField("board.maxMarkets", d.board.maxMarkets, () -> edit.board.maxMarkets, v -> edit.board.maxMarkets = v, 1, 100))
				.option(doubleField("board.dealThreshold", d.board.dealThreshold, () -> edit.board.dealThreshold, v -> edit.board.dealThreshold = v, 0.01, 1))
				.option(intField("quest.maxPerMarket", d.quest.maxPerMarket, () -> quest.maxPerMarket, v -> quest.maxPerMarket = v, 0, 10))
				.option(doubleField("quest.refreshDays", d.quest.refreshDays, () -> quest.refreshDays, v -> quest.refreshDays = v, 0.1, 30))
				.option(doubleField("quest.durationDays", d.quest.durationDays, () -> quest.durationDays, v -> quest.durationDays = v, 0.5, 30));
		for (int i = 0; i < 3; i++) {
			int index = i;
			village.option(doubleField("quest.rewardMultipliers." + i, d.quest.rewardMultipliers.get(i),
					() -> quest.rewardMultipliers.get(index), v -> quest.rewardMultipliers.set(index, v), 0.1, 10));
		}
		for (int i = 0; i < 2; i++) {
			int index = i;
			village.option(intField("quest.difficultyThresholds." + i, d.quest.difficultyThresholds.get(i),
					() -> quest.difficultyThresholds.get(index), v -> quest.difficultyThresholds.set(index, v), 0, 1_000_000_000));
		}
		// J-2 (v0.35): transport requests
		village.option(intField("quest.transportPerMarket", d.quest.transportPerMarket, () -> quest.transportPerMarket, v -> quest.transportPerMarket = v, 0, 5))
				.option(doubleField("quest.transportMinDistance", d.quest.transportMinDistance, () -> quest.transportMinDistance, v -> quest.transportMinDistance = v, 0, 100_000))
				.option(doubleField("quest.transportMaxDistance", d.quest.transportMaxDistance, () -> quest.transportMaxDistance, v -> quest.transportMaxDistance = v, 0, 100_000))
				.option(doubleField("quest.transportBaseMultiplier", d.quest.transportBaseMultiplier, () -> quest.transportBaseMultiplier, v -> quest.transportBaseMultiplier = v, 0, 10))
				.option(doubleField("quest.transportPerThousandBlocks", d.quest.transportPerThousandBlocks, () -> quest.transportPerThousandBlocks, v -> quest.transportPerThousandBlocks = v, 0, 10))
				.option(doubleField("quest.transportMaxMultiplier", d.quest.transportMaxMultiplier, () -> quest.transportMaxMultiplier, v -> quest.transportMaxMultiplier = v, 0, 10));
		village.option(intField("demand.maxShortages", d.demand.maxShortages, () -> demand.maxShortages, v -> demand.maxShortages = v, 0, 10))
				.option(doubleField("demand.durationDays", d.demand.durationDays, () -> demand.durationDays, v -> demand.durationDays = v, 0.5, 30))
				.option(doubleField("demand.minMultiplier", d.demand.minMultiplier, () -> demand.minMultiplier, v -> demand.minMultiplier = v, 1, 5))
				.option(doubleField("demand.maxMultiplier", d.demand.maxMultiplier, () -> demand.maxMultiplier, v -> demand.maxMultiplier = v, 1, 5))
				// O (v0.46): rival guilds
				.option(bool("rivals.enabled", d.rivals.enabled, () -> edit.rivals.enabled, v -> edit.rivals.enabled = v))
				.option(doubleField("rivals.chancePerDay", d.rivals.chancePerDay, () -> edit.rivals.chancePerDay, v -> edit.rivals.chancePerDay = v, 0, 1))
				.option(intField("rivals.maxActive", d.rivals.maxActive, () -> edit.rivals.maxActive, v -> edit.rivals.maxActive = v, 0, 20))
				.option(intField("rivals.minDays", d.rivals.minDays, () -> edit.rivals.minDays, v -> edit.rivals.minDays = v, 1, 30))
				.option(intField("rivals.maxDays", d.rivals.maxDays, () -> edit.rivals.maxDays, v -> edit.rivals.maxDays = v, 1, 30))
				.option(doubleField("rivals.minImpact", d.rivals.minImpact, () -> edit.rivals.minImpact, v -> edit.rivals.minImpact = v, 0, 2))
				.option(doubleField("rivals.maxImpact", d.rivals.maxImpact, () -> edit.rivals.maxImpact, v -> edit.rivals.maxImpact = v, 0, 2))
				.option(doubleField("rivals.nearRadius", d.rivals.nearRadius, () -> edit.rivals.nearRadius, v -> edit.rivals.nearRadius = v, 0, 100_000))
				.option(doubleField("rivals.farRadius", d.rivals.farRadius, () -> edit.rivals.farRadius, v -> edit.rivals.farRadius = v, 0, 100_000))
				// P (v0.46): village trends
				.option(doubleField("trend.durationDays", d.trend.durationDays, () -> edit.trend.durationDays, v -> edit.trend.durationDays = v, 0.5, 60))
				.option(intField("trend.popularCount", d.trend.popularCount, () -> edit.trend.popularCount, v -> edit.trend.popularCount = v, 0, 5))
				.option(intField("trend.unpopularCount", d.trend.unpopularCount, () -> edit.trend.unpopularCount, v -> edit.trend.unpopularCount = v, 0, 5))
				.option(doubleField("trend.popularMin", d.trend.popularMin, () -> edit.trend.popularMin, v -> edit.trend.popularMin = v, 1, 5))
				.option(doubleField("trend.popularMax", d.trend.popularMax, () -> edit.trend.popularMax, v -> edit.trend.popularMax = v, 1, 5))
				.option(doubleField("trend.unpopularMin", d.trend.unpopularMin, () -> edit.trend.unpopularMin, v -> edit.trend.unpopularMin = v, 0.1, 1))
				.option(doubleField("trend.unpopularMax", d.trend.unpopularMax, () -> edit.trend.unpopularMax, v -> edit.trend.unpopularMax = v, 0.1, 1))
				.option(intField("trend.minGoods", d.trend.minGoods, () -> edit.trend.minGoods, v -> edit.trend.minGoods = v, 1, 20))
				.option(bool("trader.spawnOnOpen", d.trader.spawnOnOpen, () -> edit.trader.spawnOnOpen, v -> edit.trader.spawnOnOpen = v))
				.option(bool("villager.coinTrades", d.villager.coinTrades, () -> edit.villager.coinTrades, v -> edit.villager.coinTrades = v))
				.option(bool("villager.wanderingTraderCoins", d.villager.wanderingTraderCoins, () -> edit.villager.wanderingTraderCoins, v -> edit.villager.wanderingTraderCoins = v))
				.option(intField("villager.emeraldPrice", d.villager.emeraldPrice, () -> edit.villager.emeraldPrice, v -> edit.villager.emeraldPrice = v, 1, 10000))
				.option(doubleField("villager.minGoodsPriceMultiplier", d.villager.minGoodsPriceMultiplier, () -> edit.villager.minGoodsPriceMultiplier, v -> edit.villager.minGoodsPriceMultiplier = v, 0, 10));

		// M (v0.29): reputation of each player with each market.
		TradeExpansionConfig.Reputation rep = edit.reputation;
		ConfigCategory.Builder reputation = category("reputation")
				.option(bool("reputation.enabled", d.reputation.enabled, () -> rep.enabled, v -> rep.enabled = v));
		for (int i = 0; i < 5; i++) {
			int index = i;
			reputation.option(intField("reputation.levelThresholds." + i, d.reputation.levelThresholds.get(i),
					() -> rep.levelThresholds.get(index), v -> rep.levelThresholds.set(index, v), 1, 1_000_000));
		}
		reputation.option(doubleField("reputation.halfLifeDays", d.reputation.halfLifeDays, () -> rep.halfLifeDays, v -> rep.halfLifeDays = v, 0, 1000))
				.option(doubleField("reputation.pointsPerCoin", d.reputation.pointsPerCoin, () -> rep.pointsPerCoin, v -> rep.pointsPerCoin = v, 0, 10))
				.option(doubleField("reputation.shortageMultiplier", d.reputation.shortageMultiplier, () -> rep.shortageMultiplier, v -> rep.shortageMultiplier = v, 1, 10));
		for (int i = 0; i < 3; i++) {
			int index = i;
			reputation.option(intField("reputation.questPoints." + i, d.reputation.questPoints.get(i),
					() -> rep.questPoints.get(index), v -> rep.questPoints.set(index, v), 0, 100_000));
		}
		reputation.option(doubleField("reputation.acceptedQuestMultiplier", d.reputation.acceptedQuestMultiplier, () -> rep.acceptedQuestMultiplier, v -> rep.acceptedQuestMultiplier = v, 1, 10))
				.option(doubleField("reputation.villagerTradePoints", d.reputation.villagerTradePoints, () -> rep.villagerTradePoints, v -> rep.villagerTradePoints = v, 0, 1000))
				.option(doubleField("reputation.attackPenalty", d.reputation.attackPenalty, () -> rep.attackPenalty, v -> rep.attackPenalty = v, 0, 100_000))
				.option(doubleField("reputation.killPenalty", d.reputation.killPenalty, () -> rep.killPenalty, v -> rep.killPenalty = v, 0, 100_000))
				.option(doubleField("reputation.questFailPenalty", d.reputation.questFailPenalty, () -> rep.questFailPenalty, v -> rep.questFailPenalty = v, 0, 100_000))
				.option(doubleField("reputation.priceBonusPerLevel", d.reputation.priceBonusPerLevel, () -> rep.priceBonusPerLevel, v -> rep.priceBonusPerLevel = v, 0, 0.05))
				.option(doubleField("reputation.rewardBonusPerLevel", d.reputation.rewardBonusPerLevel, () -> rep.rewardBonusPerLevel, v -> rep.rewardBonusPerLevel = v, 0, 1))
				.option(intField("reputation.hardQuestLevel", d.reputation.hardQuestLevel, () -> rep.hardQuestLevel, v -> rep.hardQuestLevel = v, 0, 5))
				.option(intField("reputation.specialBonusLevel", d.reputation.specialBonusLevel, () -> rep.specialBonusLevel, v -> rep.specialBonusLevel = v, 0, 5))
				.option(intField("reputation.specialBonusCount", d.reputation.specialBonusCount, () -> rep.specialBonusCount, v -> rep.specialBonusCount = v, 0, 5));

		// N (v0.36): village development.
		TradeExpansionConfig.Development dev = edit.development;
		TradeExpansionConfig.Development dd = d.development;
		ConfigCategory.Builder development = category("development")
				.option(bool("development.enabled", dd.enabled, () -> dev.enabled, v -> dev.enabled = v))
				.option(stringField("development.dimensions", String.join(", ", dd.dimensions), () -> String.join(", ", dev.dimensions),
						v -> dev.dimensions = splitList(v)))
				.option(doubleField("development.pointsPerCoin", dd.pointsPerCoin, () -> dev.pointsPerCoin, v -> dev.pointsPerCoin = v, 0, 100))
				.option(doubleField("development.materialMultiplier", dd.materialMultiplier, () -> dev.materialMultiplier, v -> dev.materialMultiplier = v, 0, 10))
				.option(bool("development.requireMaterials", dd.requireMaterials, () -> dev.requireMaterials, v -> dev.requireMaterials = v))
				.option(doubleField("development.materialCountMultiplier", dd.materialCountMultiplier, () -> dev.materialCountMultiplier, v -> dev.materialCountMultiplier = v, 0.1, 100))
				.option(doubleField("development.questRewardRatio", dd.questRewardRatio, () -> dev.questRewardRatio, v -> dev.questRewardRatio = v, 0, 10))
				.option(doubleField("development.tradePointsPerCoin", dd.tradePointsPerCoin, () -> dev.tradePointsPerCoin, v -> dev.tradePointsPerCoin = v, 0, 1))
				.option(doubleField("development.halfLifeDays", dd.halfLifeDays, () -> dev.halfLifeDays, v -> dev.halfLifeDays = v, 0, 1000))
				.option(doubleField("development.reputationPerPoint", dd.reputationPerPoint, () -> dev.reputationPerPoint, v -> dev.reputationPerPoint = v, 0, 10))
				.option(stringField("development.levelBonusThresholds", joinInts(dd.levelBonusThresholds), () -> joinInts(dev.levelBonusThresholds),
						v -> dev.levelBonusThresholds = splitInts(v)))
				.option(intField("development.buildReputationLevel", dd.buildReputationLevel, () -> dev.buildReputationLevel, v -> dev.buildReputationLevel = v, 0, 5))
				.option(intField("development.maxConcurrentBuilds", dd.maxConcurrentBuilds, () -> dev.maxConcurrentBuilds, v -> dev.maxConcurrentBuilds = v, 1, 4))
				.option(doubleField("development.buildDays", dd.buildDays, () -> dev.buildDays, v -> dev.buildDays = v, 0, 100))
				.option(intField("development.siteRadius", dd.siteRadius, () -> dev.siteRadius, v -> dev.siteRadius = v, 8, 64))
				.option(intField("development.siteSearchLimit", dd.siteSearchLimit, () -> dev.siteSearchLimit, v -> dev.siteSearchLimit = v, 16, 4096))
				.option(doubleField("development.rebuildCostRatio", dd.rebuildCostRatio, () -> dev.rebuildCostRatio, v -> dev.rebuildCostRatio = v, 0, 1))
				.option(bool("development.protectFacilities", dd.protectFacilities, () -> dev.protectFacilities, v -> dev.protectFacilities = v))
				.option(bool("development.constructionEffects", dd.constructionEffects, () -> dev.constructionEffects, v -> dev.constructionEffects = v))
				.option(doubleField("development.warehouseStockMultiplier", dd.warehouseStockMultiplier, () -> dev.warehouseStockMultiplier, v -> dev.warehouseStockMultiplier = v, 1, 10))
				.option(doubleField("development.warehouseRestockMultiplier", dd.warehouseRestockMultiplier, () -> dev.warehouseRestockMultiplier, v -> dev.warehouseRestockMultiplier = v, 1, 10))
				.option(intField("development.questHallExtraQuests", dd.questHallExtraQuests, () -> dev.questHallExtraQuests, v -> dev.questHallExtraQuests = v, 0, 10))
				.option(intField("development.questHallExtraTransport", dd.questHallExtraTransport, () -> dev.questHallExtraTransport, v -> dev.questHallExtraTransport = v, 0, 5))
				.option(intField("development.innVillagers", dd.innVillagers, () -> dev.innVillagers, v -> dev.innVillagers = v, 0, 10))
				.option(doubleField("development.bankInterestMaxMultiplier", dd.bankInterestMaxMultiplier, () -> dev.bankInterestMaxMultiplier, v -> dev.bankInterestMaxMultiplier = v, 1, 100))
				.option(doubleField("development.fountainReputationMultiplier", dd.fountainReputationMultiplier, () -> dev.fountainReputationMultiplier, v -> dev.fountainReputationMultiplier = v, 1, 10))
				.option(doubleField("development.parkPointsMultiplier", dd.parkPointsMultiplier, () -> dev.parkPointsMultiplier, v -> dev.parkPointsMultiplier = v, 1, 10));

		// Q (v0.48): hired staff.
		TradeExpansionConfig.Staff st = edit.staff;
		TradeExpansionConfig.Staff sd = d.staff;
		ConfigCategory.Builder staff = category("staff")
				.option(bool("staff.enabled", sd.enabled, () -> st.enabled, v -> st.enabled = v))
				.option(intField("staff.maxPerPlayer", sd.maxPerPlayer, () -> st.maxPerPlayer, v -> st.maxPerPlayer = v, 0, 50))
				.option(intField("staff.minReputationLevel", sd.minReputationLevel, () -> st.minReputationLevel, v -> st.minReputationLevel = v, 0, 5))
				.option(longField("staff.carrierHireFee", sd.carrierHireFee, () -> st.carrierHireFee, v -> st.carrierHireFee = v, 0, 100_000_000L))
				.option(longField("staff.clerkHireFee", sd.clerkHireFee, () -> st.clerkHireFee, v -> st.clerkHireFee = v, 0, 100_000_000L))
				.option(longField("staff.carrierWagePerDay", sd.carrierWagePerDay, () -> st.carrierWagePerDay, v -> st.carrierWagePerDay = v, 0, 100_000_000L))
				.option(longField("staff.clerkWagePerDay", sd.clerkWagePerDay, () -> st.clerkWagePerDay, v -> st.clerkWagePerDay = v, 0, 100_000_000L))
				.option(intField("staff.carrierCapacity", sd.carrierCapacity, () -> st.carrierCapacity, v -> st.carrierCapacity = v, 1, 2304))
				.option(intField("staff.clerkCapacity", sd.clerkCapacity, () -> st.clerkCapacity, v -> st.clerkCapacity = v, 1, 100_000))
				.option(doubleField("staff.carrierBlocksPerDay", sd.carrierBlocksPerDay, () -> st.carrierBlocksPerDay, v -> st.carrierBlocksPerDay = v, 10, 1_000_000))
				.option(doubleField("staff.carrierMaxDistance", sd.carrierMaxDistance, () -> st.carrierMaxDistance, v -> st.carrierMaxDistance = v, 0, 1_000_000))
				.option(doubleField("staff.carrierCommission", sd.carrierCommission, () -> st.carrierCommission, v -> st.carrierCommission = v, 0, 1))
				.option(doubleField("staff.carrierSpreadMultiplier", sd.carrierSpreadMultiplier, () -> st.carrierSpreadMultiplier, v -> st.carrierSpreadMultiplier = v, 0, 1))
				.option(doubleField("staff.carrierPriceImpact", sd.carrierPriceImpact, () -> st.carrierPriceImpact, v -> st.carrierPriceImpact = v, 0.05, 1))
				.option(doubleField("staff.carrierMaxWaitDays", sd.carrierMaxWaitDays, () -> st.carrierMaxWaitDays, v -> st.carrierMaxWaitDays = v, 0, 30))
				.option(doubleField("staff.accidentBase", sd.accidentBase, () -> st.accidentBase, v -> st.accidentBase = v, 0, 1))
				.option(doubleField("staff.accidentPerThousandBlocks", sd.accidentPerThousandBlocks, () -> st.accidentPerThousandBlocks, v -> st.accidentPerThousandBlocks = v, 0, 1))
				.option(doubleField("staff.accidentMax", sd.accidentMax, () -> st.accidentMax, v -> st.accidentMax = v, 0, 1))
				.option(doubleField("staff.accidentLossMin", sd.accidentLossMin, () -> st.accidentLossMin, v -> st.accidentLossMin = v, 0, 1))
				.option(doubleField("staff.accidentLossMax", sd.accidentLossMax, () -> st.accidentLossMax, v -> st.accidentLossMax = v, 0, 1))
				.option(doubleField("staff.accidentRestDays", sd.accidentRestDays, () -> st.accidentRestDays, v -> st.accidentRestDays = v, 0, 30))
				.option(doubleField("staff.quitChance", sd.quitChance, () -> st.quitChance, v -> st.quitChance = v, 0, 1))
				.option(bool("staff.notifyChat", sd.notifyChat, () -> st.notifyChat, v -> st.notifyChat = v));

		// Client settings are always editable.
		ConfigCategory.Builder client = category("client")
				.option(Option.<Boolean>createBuilder()
						.name(Component.translatable(KEY + "client.showDebugTooltip"))
						.description(OptionDescription.of(Component.translatable(KEY + "client.showDebugTooltip.desc")))
						.binding(d.client.showDebugTooltip, () -> edit.client.showDebugTooltip, v -> edit.client.showDebugTooltip = v)
						.controller(TickBoxControllerBuilder::create)
						.build())
				.option(Option.<TradeExpansionConfig.BalancePosition>createBuilder()
						.name(Component.translatable(KEY + "client.balancePosition"))
						.description(OptionDescription.of(Component.translatable(KEY + "client.balancePosition.desc")))
						.binding(d.client.balancePosition, () -> edit.client.balancePosition, v -> edit.client.balancePosition = v)
						.controller(o -> EnumControllerBuilder.create(o).enumClass(TradeExpansionConfig.BalancePosition.class)
								.formatValue(v -> Component.translatable(KEY + "client.balancePosition." + v.getSerializedName().toLowerCase(Locale.ROOT))))
						.build());

		return YetAnotherConfigLib.createBuilder()
				.title(Component.translatable(KEY + "title"))
				.categories(List.of(currency.build(), priceCat.build(), goods.build(), recovery.build(), distance.build(), marketCat.build(),
						wallet.build(), bank.build(), tradeCat.build(), village.build(), reputation.build(), development.build(), staff.build(), other.build(),
						client.build()))
				.save(this::save)
				.build();
	}

	/** Singleplayer: the integrated server reloads the file (same as {@code /aktradeex reload}). Remote: only client settings. */
	private void save() {
		if (serverLocked) {
			TradeExpansionConfig file = ConfigManager.read(ConfigManager.path());
			file.client = edit.client;
			ConfigManager.write(ConfigManager.path(), file);
			TradeExpansionConfig live = ConfigManager.copy(TradeExpansionConfig.get());
			live.client = edit.client;
			TradeExpansionConfig.set(live);
			return;
		}
		ConfigManager.save(edit);
		IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
		if (server != null) server.execute(() -> ServerConfig.reload(server));
	}

	/** Goods manager "Done": writes only the goods and consumables lists to the file and reloads (like {@link #save}). */
	private void saveGoodsLists() {
		TradeExpansionConfig file = ConfigManager.read(ConfigManager.path());
		file.goods = new TreeMap<>(edit.goods);
		file.consumables = new TradeExpansionConfig.Consumables();
		file.consumables.add = new ArrayList<>(edit.consumables.add);
		file.consumables.remove = new ArrayList<>(edit.consumables.remove);
		ConfigManager.save(file);
		IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
		if (server != null) server.execute(() -> ServerConfig.reload(server));
	}

	// ---- option helpers ----

	private static ConfigCategory.Builder category(String name) {
		return ConfigCategory.createBuilder().name(Component.translatable(KEY + "category." + name));
	}

	private OptionDescription description(String key) {
		Component text = Component.translatable(KEY + key + ".desc");
		if (serverLocked) {
			text = Component.empty().append(text).append("\n\n")
					.append(Component.translatable(KEY + "server_locked").withStyle(ChatFormatting.RED));
		}
		return OptionDescription.of(text);
	}

	/** Server-side options are grayed out while connected to a remote server. */
	private <T> Option<T> server(Option.Builder<T> builder) {
		return builder.available(!serverLocked).build();
	}

	private void thresholds(ConfigCategory.Builder category, String prefix, List<Integer> defaults, List<Integer> values) {
		for (int i = 0; i < 4; i++) {
			int index = i;
			category.option(intField(prefix + i, defaults.get(i), () -> values.get(index), v -> values.set(index, v), 0, 100000));
		}
	}

	private Option<Boolean> bool(String key, boolean def, Supplier<Boolean> get, Consumer<Boolean> set) {
		return server(Option.<Boolean>createBuilder()
				.name(Component.translatable(KEY + key))
				.description(description(key))
				.binding(def, get, set)
				.controller(TickBoxControllerBuilder::create));
	}

	private Option<Integer> intField(String key, int def, Supplier<Integer> get, Consumer<Integer> set, int min, int max) {
		return server(Option.<Integer>createBuilder()
				.name(Component.translatable(KEY + key))
				.description(description(key))
				.binding(def, get, set)
				.controller(o -> IntegerFieldControllerBuilder.create(o).range(min, max)));
	}

	private Option<Long> longField(String key, long def, Supplier<Long> get, Consumer<Long> set, long min, long max) {
		return server(Option.<Long>createBuilder()
				.name(Component.translatable(KEY + key))
				.description(description(key))
				.binding(def, get, set)
				.controller(o -> LongFieldControllerBuilder.create(o).range(min, max)));
	}

	private Option<String> stringField(String key, String def, Supplier<String> get, Consumer<String> set) {
		return server(Option.<String>createBuilder()
				.name(Component.translatable(KEY + key))
				.description(description(key))
				.binding(def, get, set)
				.controller(StringControllerBuilder::create));
	}

	/** "a, b , c" → [a, b, c] (empty entries dropped). */
	private static List<String> splitList(String value) {
		List<String> list = new ArrayList<>();
		for (String part : value.split(",")) {
			if (!part.isBlank()) list.add(part.trim());
		}
		return list;
	}

	private static String joinInts(List<Integer> values) {
		return String.join(", ", values.stream().map(String::valueOf).toList());
	}

	/** "2, 4" → [2, 4]; entries that are not numbers are dropped (the validator corrects the rest). */
	private static List<Integer> splitInts(String value) {
		List<Integer> list = new ArrayList<>();
		for (String part : splitList(value)) {
			try {
				list.add(Integer.parseInt(part));
			} catch (NumberFormatException ignored) {
				// not a number
			}
		}
		return list;
	}

	private Option<Double> doubleField(String key, double def, Supplier<Double> get, Consumer<Double> set, double min, double max) {
		return server(Option.<Double>createBuilder()
				.name(Component.translatable(KEY + key))
				.description(description(key))
				.binding(def, get, set)
				.controller(o -> DoubleFieldControllerBuilder.create(o).range(min, max)));
	}
}
