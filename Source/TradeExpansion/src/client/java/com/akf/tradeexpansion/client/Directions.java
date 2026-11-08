package com.akf.tradeexpansion.client;

import net.minecraft.network.chat.Component;

/** Compass direction of an offset ("north-east" ...), for the bulletin board and the recipe viewer pages. */
public final class Directions {
	private static final String[] KEYS = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};

	private Directions() {
	}

	public static String of(int dx, int dz) {
		if (dx == 0 && dz == 0) return Component.translatable("direction.ak_trade_expansion.here").getString();
		double angle = Math.toDegrees(Math.atan2(dx, -dz)); // 0 = north, clockwise
		int index = (int) Math.round(((angle % 360) + 360) % 360 / 45.0) % 8;
		return Component.translatable("direction.ak_trade_expansion." + KEYS[index]).getString();
	}
}
