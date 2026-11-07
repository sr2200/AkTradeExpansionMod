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

	/**
	 * N-5 (v0.43): the ground tag, resolved through the vanilla tags of the game jar, takes grass and the usual natural
	 * ground but not dirt paths or ice. Every referenced vanilla tag must exist (a missing one would empty the whole tag).
	 */
	@Test
	void facilityGroundTag() throws IOException {
		java.util.Set<String> blocks = new java.util.HashSet<>();
		resolveTag(Files.readString(Path.of("src/main/resources/data/ak_trade_expansion/tags/block/facility_ground.json")), blocks);
		for (String id : List.of("grass_block", "podzol", "mycelium", "dirt", "coarse_dirt", "sand", "red_sand", "stone", "snow_block", "gravel", "netherrack", "end_stone")) {
			assertTrue(blocks.contains("minecraft:" + id), id);
		}
		for (String id : List.of("dirt_path", "ice", "packed_ice", "water", "farmland")) assertFalse(blocks.contains("minecraft:" + id), id);
	}

	private static void resolveTag(String json, java.util.Set<String> blocks) throws IOException {
		for (JsonElement value : JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("values")) {
			String id = value.isJsonObject() ? value.getAsJsonObject().get("id").getAsString() : value.getAsString();
			if (!id.startsWith("#")) {
				blocks.add(id);
				continue;
			}
			net.minecraft.resources.Identifier tag = net.minecraft.resources.Identifier.parse(id.substring(1));
			String resource = "data/" + tag.getNamespace() + "/tags/block/" + tag.getPath() + ".json";
			try (java.io.InputStream in = BundledDataTest.class.getClassLoader().getResourceAsStream(resource)) {
				assertNotNull(in, "tag " + id + " does not exist");
				resolveTag(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8), blocks);
			}
		}
	}

	/** N-4 (v0.38): the four facilities, one per kind, each with its structure in every style it falls back to. */
	@Test
	void facilitiesDecode() throws IOException {
		Map<String, FacilityDefinition> facilities = decodeAll("facility", FacilityDefinition.CODEC);
		assertEquals(4, facilities.size());
		assertEquals(4, facilities.values().stream().map(FacilityDefinition::kind).distinct().count());
		for (Map.Entry<String, FacilityDefinition> e : facilities.entrySet()) {
			FacilityDefinition def = e.getValue();
			assertTrue(def.cost() > 0, e.getKey());
			assertFalse(def.wanted().isEmpty(), e.getKey());
			def.wanted().forEach(w -> assertTrue(w.item().isPresent() != w.tag().isPresent(), e.getKey() + ": item or tag"));
			assertTrue(Files.exists(Path.of("src/main/resources/data/ak_trade_expansion/structure/facility/plains/" + def.structure() + ".nbt")),
					e.getKey() + ": plains structure");
		}
		assertEquals(2500, facilities.get("warehouse").cost());
	}

	/**
	 * N-5 (v0.39): every style has every building; each holds exactly one facility sign of its own kind, uses only blocks
	 * that exist, and the Nether / End inns have no beds (they explode there).
	 */
	@Test
	void facilityStructures() throws IOException {
		Map<String, FacilityDefinition> facilities = decodeAll("facility", FacilityDefinition.CODEC);
		Path root = Path.of("src/main/resources/data/ak_trade_expansion/structure/facility");
		List<String> styles = List.of("plains", "desert", "savanna", "snowy", "taiga", "nether", "end");
		for (String style : styles) {
			for (FacilityDefinition def : facilities.values()) {
				Path file = root.resolve(style).resolve(def.structure() + ".nbt");
				net.minecraft.nbt.CompoundTag tag = net.minecraft.nbt.NbtIo.readCompressed(file, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
				net.minecraft.nbt.ListTag palette = tag.getListOrEmpty("palette");
				int signs = 0;
				for (int i = 0; i < palette.size(); i++) {
					net.minecraft.nbt.CompoundTag entry = palette.getCompoundOrEmpty(i);
					String id = entry.getStringOr("id", ""); // 26.x block state keys (Name / Properties are the legacy ones)
					// The mod's own blocks are not registered by the bootstrap; the sign is the only one used.
					if (id.startsWith("ak_trade_expansion:")) assertEquals("ak_trade_expansion:facility_sign", id, file.toString());
					else assertTrue(net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(net.minecraft.resources.Identifier.parse(id)), file + ": " + id);
					if (id.equals("ak_trade_expansion:facility_sign")) {
						signs++;
						assertEquals(def.kind().getSerializedName(), entry.getCompoundOrEmpty("properties").getStringOr("kind", ""), file + ": sign kind");
					}
					if ((style.equals("nether") || style.equals("end")) && id.endsWith("_bed")) fail(file + ": bed in " + style);
				}
				assertEquals(1, signs, file + ": one facility sign");
			}
		}
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
