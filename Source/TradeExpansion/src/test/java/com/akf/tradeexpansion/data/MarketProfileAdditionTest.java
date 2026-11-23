package com.akf.tradeexpansion.data;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** S-4: market profile additions and price modifiers by goods id. */
class MarketProfileAdditionTest {
	@BeforeAll
	static void boot() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	private static <T> T parse(Codec<T> codec, String json) {
		return codec.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow(AssertionError::new);
	}

	private static final Identifier BREAD = Identifier.parse("ak_trade_expansion:bread");
	private static final Identifier FLOWER = Identifier.parse("ak_garden:cut_flower_sunset");

	@Test
	void additionAppendsPoolsAlwaysAndModifiers() {
		MarketProfile base = parse(MarketProfile.CODEC, """
				{"pools": [{"rolls": 1, "entries": [{"goods": "ak_trade_expansion:bread"}]}],
				 "always": ["ak_trade_expansion:bread"],
				 "price_modifiers": [{"item": "minecraft:bread", "multiplier": 2}]}
				""");
		MarketProfile.Addition addition = parse(MarketProfile.Addition.CODEC, """
				{"profiles": ["ak_trade_expansion:snowy_village"],
				 "pools": [{"rolls": 2, "entries": [{"goods": "ak_garden:cut_flower_sunset", "base_price_multiplier": 1.5}]}],
				 "always": ["ak_trade_expansion:bread", "ak_garden:cut_flower_sunset"],
				 "price_modifiers": [{"goods": "ak_garden:cut_flower_sunset", "multiplier": 3}]}
				""");
		MarketProfile merged = base.with(addition);

		assertEquals(2, merged.pools().size());
		assertEquals(2, merged.pools().get(1).rolls().min());
		assertEquals(List.of(BREAD, FLOWER), merged.always(), "always goods are added without duplicates");
		assertEquals(2, merged.priceModifiers().size());
		assertEquals(2.0, merged.multiplier(BREAD, Identifier.parse("minecraft:bread")), 1e-9);
		// base_price_multiplier of the added pool × the goods price modifier
		assertEquals(4.5, merged.multiplier(FLOWER, Identifier.parse("ak_garden:cut_flower")), 1e-9);
		assertEquals(1, base.pools().size(), "the original profile is unchanged");
	}

	@Test
	void goodsModifierMatchesOnlyThatGoods() {
		MarketProfile profile = parse(MarketProfile.CODEC, """
				{"price_modifiers": [{"goods": "ak_garden:cut_flower_sunset", "multiplier": 2}]}
				""");
		Identifier item = Identifier.parse("minecraft:poppy");
		assertEquals(2.0, profile.multiplier(FLOWER, item), 1e-9);
		assertEquals(1.0, profile.multiplier(Identifier.parse("ak_garden:cut_flower_snow"), item), 1e-9, "same item, other goods");
	}

	@Test
	void modifierNeedsExactlyOneTarget() {
		assertTrue(MarketProfile.PriceModifier.CODEC.parse(JsonOps.INSTANCE,
				JsonParser.parseString("{\"item\": \"minecraft:bread\", \"goods\": \"ak_trade_expansion:bread\", \"multiplier\": 2}")).isError());
		assertTrue(MarketProfile.PriceModifier.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"multiplier\": 2}")).isError());
	}
}
