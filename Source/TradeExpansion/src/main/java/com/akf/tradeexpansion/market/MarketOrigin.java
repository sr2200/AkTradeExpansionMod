package com.akf.tradeexpansion.market;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** How a market was created (D-1 condition {@code trade_expansion:origin}). */
public enum MarketOrigin implements StringRepresentable {
	NATURAL("natural"),
	CRAFTED("crafted");

	public static final Codec<MarketOrigin> CODEC = StringRepresentable.fromEnum(MarketOrigin::values);

	private final String name;

	MarketOrigin(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return name;
	}
}
