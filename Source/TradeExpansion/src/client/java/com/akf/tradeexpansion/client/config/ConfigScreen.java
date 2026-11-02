package com.akf.tradeexpansion.client.config;

import com.akf.tradeexpansion.config.ConfigManager;
import com.akf.tradeexpansion.config.ServerConfig;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.DoubleFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.LongFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * YACL settings screen opened from Mod Menu (design G, Q35). Edits a copy of {@code config/trade_expansion.json};
 * while connected to a remote server only the client settings can be changed.
 */
public final class ConfigScreen {
	private static final String KEY = "config.trade_expansion.";

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
				.option(server(Option.<TradeExpansionConfig.RecoveryClock>createBuilder()
						.name(Component.translatable(KEY + "recovery.clock"))
						.description(description("recovery.clock"))
						.binding(d.recovery.clock, () -> rec.clock, v -> rec.clock = v)
						.controller(o -> EnumControllerBuilder.create(o).enumClass(TradeExpansionConfig.RecoveryClock.class))));

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
		marketCat.option(intField("market.levelRadius", d.market.levelRadius, () -> market.levelRadius, v -> market.levelRadius = v, 16, 256))
				.option(doubleField("market.levelCheckIntervalDays", d.market.levelCheckIntervalDays, () -> market.levelCheckIntervalDays, v -> market.levelCheckIntervalDays = v, 0, 100));

		ConfigCategory.Builder wallet = category("wallet")
				.option(longField("wallet.capacity", d.wallet.capacity, () -> edit.wallet.capacity, v -> edit.wallet.capacity = v, 1000, 1_000_000_000L))
				.option(bool("wallet.autoCollect", d.wallet.autoCollect, () -> edit.wallet.autoCollect, v -> edit.wallet.autoCollect = v));

		ConfigCategory.Builder bank = category("bank")
				.option(intField("bank.minMarketLevel", d.bank.minMarketLevel, () -> edit.bank.minMarketLevel, v -> edit.bank.minMarketLevel = v, 1, 5))
				.option(longField("bank.cardFee", d.bank.cardFee, () -> edit.bank.cardFee, v -> edit.bank.cardFee = v, 0, 1_000_000L))
				.option(doubleField("bank.cardTransactionFee", d.bank.cardTransactionFee, () -> edit.bank.cardTransactionFee, v -> edit.bank.cardTransactionFee = v, 0, 0.1))
				.option(doubleField("bank.interestRatePerDay", d.bank.interestRatePerDay, () -> edit.bank.interestRatePerDay, v -> edit.bank.interestRatePerDay = v, 0, 0.01));

		ConfigCategory.Builder tradeCat = category("trade")
				.option(doubleField("trade.maxDistance", d.trade.maxDistance, () -> trade.maxDistance, v -> trade.maxDistance = v, 2, 32))
				.option(intField("trade.maxPerTransaction", d.trade.maxPerTransaction, () -> trade.maxPerTransaction, v -> trade.maxPerTransaction = v, 1, 2304))
				.option(doubleField("trade.priceTolerance", d.trade.priceTolerance, () -> trade.priceTolerance, v -> trade.priceTolerance = v, 0, 0.2))
				.option(bool("trade.allowCreative", d.trade.allowCreative, () -> trade.allowCreative, v -> trade.allowCreative = v))
				.option(bool("trade.allowShiftAll", d.trade.allowShiftAll, () -> trade.allowShiftAll, v -> trade.allowShiftAll = v))
				.option(bool("trade.acceptDamaged", d.trade.acceptDamaged, () -> trade.acceptDamaged, v -> trade.acceptDamaged = v));

		ConfigCategory.Builder other = category("other")
				.option(bool("log.trades", d.log.trades, () -> edit.log.trades, v -> edit.log.trades = v))
				.option(intField("command.permissionLevel", d.command.permissionLevel, () -> edit.command.permissionLevel, v -> edit.command.permissionLevel = v, 0, 4))
				.option(bool("worldgen.spawnStallsInVillages", d.worldgen.spawnStallsInVillages, () -> edit.worldgen.spawnStallsInVillages, v -> edit.worldgen.spawnStallsInVillages = v))
				.option(bool("worldgen.protectNaturalStalls", d.worldgen.protectNaturalStalls, () -> edit.worldgen.protectNaturalStalls, v -> edit.worldgen.protectNaturalStalls = v))
				.option(bool("worldgen.spawnTraderNpc", d.worldgen.spawnTraderNpc, () -> edit.worldgen.spawnTraderNpc, v -> edit.worldgen.spawnTraderNpc = v))
				.option(bool("vanilla.disableVillagerTrades", d.vanilla.disableVillagerTrades, () -> edit.vanilla.disableVillagerTrades, v -> edit.vanilla.disableVillagerTrades = v));

		// Client settings are always editable.
		ConfigCategory.Builder client = category("client")
				.option(Option.<Boolean>createBuilder()
						.name(Component.translatable(KEY + "client.showDebugTooltip"))
						.description(OptionDescription.of(Component.translatable(KEY + "client.showDebugTooltip.desc")))
						.binding(d.client.showDebugTooltip, () -> edit.client.showDebugTooltip, v -> edit.client.showDebugTooltip = v)
						.controller(TickBoxControllerBuilder::create)
						.build());

		return YetAnotherConfigLib.createBuilder()
				.title(Component.translatable(KEY + "title"))
				.categories(List.of(currency.build(), priceCat.build(), recovery.build(), distance.build(), marketCat.build(),
						wallet.build(), bank.build(), tradeCat.build(), other.build(), client.build()))
				.save(this::save)
				.build();
	}

	/** Singleplayer: the integrated server reloads the file (same as {@code /tradeex reload}). Remote: only client settings. */
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

	private Option<Double> doubleField(String key, double def, Supplier<Double> get, Consumer<Double> set, double min, double max) {
		return server(Option.<Double>createBuilder()
				.name(Component.translatable(KEY + key))
				.description(description(key))
				.binding(def, get, set)
				.controller(o -> DoubleFieldControllerBuilder.create(o).range(min, max)));
	}
}
