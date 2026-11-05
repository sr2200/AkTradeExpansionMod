package com.akf.tradeexpansion.data;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.GoodParams;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads goods and market profiles from data packs on server start and {@code /reload} (design D-2).
 * Existing markets keep their goods; definitions (prices, tau, ...) take effect immediately.
 */
public final class DataReloadListener {
	private DataReloadListener() {
	}

	public static void init() {
		ResourceLoader loader = ResourceLoader.get(PackType.SERVER_DATA);
		loader.registerReloadListener(TradeExpansion.id("goods"), new Goods());
		loader.registerReloadListener(TradeExpansion.id("market_profiles"), new Profiles());
		loader.addListenerOrdering(TradeExpansion.id("goods"), TradeExpansion.id("market_profiles"));
	}

	private static final class Goods extends SimpleJsonResourceReloadListener<GoodsDefinition> {
		Goods() {
			super(GoodsDefinition.CODEC, FileToIdConverter.json("ak_trade_expansion/goods"));
		}

		@Override
		protected void apply(Map<Identifier, GoodsDefinition> loaded, ResourceManager manager, ProfilerFiller profiler) {
			Map<Identifier, GoodsDefinition> valid = new LinkedHashMap<>();
			TradeExpansionConfig config = TradeExpansionConfig.get();
			loaded.forEach((id, def) -> {
				if (!BuiltInRegistries.ITEM.containsKey(def.item())) {
					TradeExpansion.LOGGER.warn("Goods {}: unknown item {}, skipped", id, def.item());
					return;
				}
				// B-6: warn when tau had to be raised so that a sell-then-buy round trip cannot profit.
				GoodParams raw = def.params(1.0, 1.0, config);
				double tau = def.elasticity().orElse(config.price.defaultElasticity);
				if (def.buy() && def.sell() && raw.elasticity() != tau) {
					TradeExpansion.LOGGER.warn("Goods {}: elasticity {} is exploitable with spread {}, using {}",
							id, tau, raw.spread(), raw.elasticity());
				}
				valid.put(id, def);
			});
			GoodsCatalog.replace(valid);
			TradeExpansion.LOGGER.info("Loaded {} goods", valid.size());
		}
	}

	private static final class Profiles extends SimpleJsonResourceReloadListener<MarketProfile> {
		Profiles() {
			super(MarketProfile.CODEC, FileToIdConverter.json("ak_trade_expansion/market_profile"));
		}

		@Override
		protected void apply(Map<Identifier, MarketProfile> loaded, ResourceManager manager, ProfilerFiller profiler) {
			loaded.forEach((id, profile) -> profile.pools().forEach(pool -> pool.entries().forEach(e -> {
				if (GoodsCatalog.get(e.goods()) == null) {
					TradeExpansion.LOGGER.warn("Market profile {}: unknown goods {}", id, e.goods());
				}
			})));
			MarketProfiles.replace(loaded);
			TradeExpansion.LOGGER.info("Loaded {} market profiles", loaded.size());
		}
	}
}
