package com.akf.tradeexpansion.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Rival guilds loaded from {@code data/<ns>/ak_trade_expansion/rival_guild/*.json} (design O-2), in id order. */
public final class RivalGuilds {
	/**
	 * @param groups goods groups (O-1) the guild deals in
	 * @param weight how often it is chosen (0 = never)
	 */
	public record Guild(List<Identifier> groups, int weight) {
		public static final Codec<Guild> CODEC = RecordCodecBuilder.create(i -> i.group(
				Identifier.CODEC.listOf().fieldOf("groups").forGetter(Guild::groups),
				Codec.intRange(0, 1000).optionalFieldOf("weight", 1).forGetter(Guild::weight)
		).apply(i, Guild::new));

		/** The guild deals in this good (it is in one of its groups). */
		public boolean dealsIn(Identifier goodsId) {
			for (Identifier group : groups) {
				if (GoodsGroups.contains(group, goodsId)) return true;
			}
			return false;
		}
	}

	private static Map<Identifier, Guild> guilds = Map.of();

	private RivalGuilds() {
	}

	public static Map<Identifier, Guild> all() {
		return guilds;
	}

	public static Guild get(Identifier id) {
		return id == null ? null : guilds.get(id);
	}

	/** Display name: {@code rival_guild.<ns>.<path>}. */
	public static Component name(Identifier guild) {
		return Component.translatable("rival_guild." + guild.getNamespace() + "." + guild.getPath().replace('/', '.'));
	}

	static void replace(Map<Identifier, Guild> loaded) {
		Map<Identifier, Guild> sorted = new LinkedHashMap<>();
		loaded.keySet().stream().sorted().forEach(id -> sorted.put(id, loaded.get(id)));
		guilds = Collections.unmodifiableMap(sorted);
	}
}
