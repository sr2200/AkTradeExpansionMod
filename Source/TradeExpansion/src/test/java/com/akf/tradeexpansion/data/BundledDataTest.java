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
		assertEquals(24, goods.get("diamond_sword").maxStock().orElseThrow());
		assertFalse(goods.get("rotten_flesh").buy());
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
