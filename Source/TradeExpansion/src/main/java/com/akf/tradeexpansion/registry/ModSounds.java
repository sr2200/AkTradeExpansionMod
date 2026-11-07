package com.akf.tradeexpansion.registry;

import com.akf.tradeexpansion.TradeExpansion;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * Sound events of the mod. They reuse vanilla sound files ({@code assets/ak_trade_expansion/sounds.json}) but have their
 * own subtitles.
 */
public final class ModSounds {
	/** N-5 (v0.45): hammering at a site under construction (subtitle "Construction work"). */
	public static final SoundEvent CONSTRUCTION = register("facility.construction");

	private ModSounds() {
	}

	private static SoundEvent register(String name) {
		Identifier id = TradeExpansion.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	public static void init() {
	}
}
