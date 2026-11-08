package com.akf.tradeexpansion.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Goods groups loaded from {@code data/<ns>/ak_trade_expansion/goods_group/*.json} (design O-1): the fields of the rival
 * guilds (O-2) and of the village trends (P). A good may be in several groups.
 */
public final class GoodsGroups {
	/** One group file. */
	public record Group(List<Identifier> goods) {
		public static final Codec<Group> CODEC = RecordCodecBuilder.create(i -> i.group(
				Identifier.CODEC.listOf().fieldOf("goods").forGetter(Group::goods)
		).apply(i, Group::new));
	}

	private static Map<Identifier, Group> groups = Map.of();
	private static Map<Identifier, List<Identifier>> byGoods = Map.of();

	private GoodsGroups() {
	}

	/** In id order. */
	public static Map<Identifier, Group> all() {
		return groups;
	}

	public static Group get(Identifier id) {
		return id == null ? null : groups.get(id);
	}

	/** Groups the good is in, in id order (empty when none). */
	public static List<Identifier> groupsOf(Identifier goodsId) {
		return byGoods.getOrDefault(goodsId, List.of());
	}

	public static boolean contains(Identifier group, Identifier goodsId) {
		return groupsOf(goodsId).contains(group);
	}

	/** Display name: {@code goods_group.<ns>.<path>}. */
	public static Component name(Identifier group) {
		return Component.translatable("goods_group." + group.getNamespace() + "." + group.getPath().replace('/', '.'));
	}

	static void replace(Map<Identifier, Group> loaded) {
		Map<Identifier, Group> sorted = new LinkedHashMap<>();
		loaded.keySet().stream().sorted().forEach(id -> sorted.put(id, loaded.get(id)));
		Map<Identifier, List<Identifier>> index = new LinkedHashMap<>();
		sorted.forEach((id, group) -> group.goods().forEach(g -> {
			List<Identifier> list = index.computeIfAbsent(g, k -> new ArrayList<>());
			if (!list.contains(id)) list.add(id);
		}));
		index.replaceAll((k, v) -> List.copyOf(v));
		groups = Collections.unmodifiableMap(sorted);
		byGoods = Collections.unmodifiableMap(index);
	}
}
