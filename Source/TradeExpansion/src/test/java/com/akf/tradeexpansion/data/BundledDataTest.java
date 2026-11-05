package com.akf.tradeexpansion.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Decodes every bundled goods / market_profile JSON with the real codecs (design D-2, goods-design.md). */
class BundledDataTest {
	private static final Path DATA = Path.of("src/main/resources/data/ak_trade_expansion/ak_trade_expansion");

	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	private static <T> Map<String, T> decodeAll(String dir, Codec<T> codec) throws IOException {
		Map<String, T> result = new HashMap<>();
		try (Stream<Path> files = Files.list(DATA.resolve(dir))) {
			for (Path file : files.toList()) {
				JsonElement json = JsonParser.parseString(Files.readString(file));
				T value = codec.parse(JsonOps.INSTANCE, json).getOrThrow(msg -> new AssertionError(file + ": " + msg));
				result.put(file.getFileName().toString().replace(".json", ""), value);
			}
		}
		return result;
	}

	@Test
	void goodsDecode() throws IOException {
		Map<String, GoodsDefinition> goods = decodeAll("goods", GoodsDefinition.CODEC);
		assertEquals(96, goods.size());
		GoodsDefinition emerald = goods.get("emerald_coin");
		assertEquals(10, emerald.unit());
		assertFalse(emerald.buy());
		assertTrue(emerald.autoAdd().isPresent());
		assertTrue(emerald.autoAdd().orElseThrow().isEmpty()); // v0.30: every market
		assertEquals(24, goods.get("diamond_sword").maxStock().orElseThrow());
		assertFalse(goods.get("rotten_flesh").buy());
	}

	/** E-5 (v0.30): with the default settings the emerald coin always sells at 4c per 10 and the market never stops buying. */
	@Test
	void emeraldCoinFixedAndUnlimited() throws IOException {
		GoodsDefinition emerald = decodeAll("goods", GoodsDefinition.CODEC).get("emerald_coin");
		com.akf.tradeexpansion.config.TradeExpansionConfig config = com.akf.tradeexpansion.config.TradeExpansionConfig.defaults();
		com.akf.tradeexpansion.core.GoodParams p = emerald.params(1.15, 1.0, config); // the price factor is ignored
		assertEquals(4, com.akf.tradeexpansion.core.PriceModel.sellPrice(p, 0));
		assertEquals(4 * 1000, com.akf.tradeexpansion.core.PriceModel.sumSell(p, 0, 1000));
		assertEquals(4, com.akf.tradeexpansion.core.PriceModel.sellPrice(p, 100_000));
		assertTrue(com.akf.tradeexpansion.core.PriceModel.sellCapacity(p, new com.akf.tradeexpansion.core.GoodState(1, 0, 1_000_000, 0)) > 1_000_000);
		// Off: the old behaviour (moving price, stock cap 64).
		config.currency.emeraldCoinFixedPrice = false;
		config.currency.emeraldCoinUnlimited = false;
		com.akf.tradeexpansion.core.GoodParams old = emerald.params(1.0, 1.0, config);
		assertEquals(64, old.maxStock());
		assertTrue(com.akf.tradeexpansion.core.PriceModel.sellPrice(old, 200) < 4);
	}

	@Test
	void profilesDecodeAndReferenceKnownGoods() throws IOException {
		Map<String, GoodsDefinition> goods = decodeAll("goods", GoodsDefinition.CODEC);
		Map<String, MarketProfile> profiles = decodeAll("market_profile", MarketProfile.CODEC);
		assertEquals(12, profiles.size());
		profiles.forEach((name, p) -> {
			Stream.concat(p.always().stream(), p.pools().stream().flatMap(pool -> pool.entries().stream()).map(MarketProfile.Entry::goods))
					.forEach(id -> assertTrue(goods.containsKey(id.getPath()), name + ": unknown goods " + id));
			long distinct = p.pools().stream().flatMap(pool -> pool.entries().stream()).map(MarketProfile.Entry::goods).distinct().count()
					+ p.always().stream().filter(a -> p.pools().stream().flatMap(pool -> pool.entries().stream()).noneMatch(e -> e.goods().equals(a))).count();
			assertTrue(distinct >= 20, name + " has only " + distinct + " candidates");
		});
		assertEquals(List.of(), profiles.get("default_overworld").conditions());
		assertEquals(10, profiles.get("plains_village").priority());
	}
}
