package com.akf.tradeexpansion.data;

import com.akf.tradeexpansion.development.FacilityKind;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Facilities loaded from {@code data/<ns>/ak_trade_expansion/facility/*.json} (design N-4), in tab order. Only the first
 * definition of each kind is used.
 */
public final class FacilityCatalog {
	private static Map<Identifier, FacilityDefinition> facilities = Map.of();

	private FacilityCatalog() {
	}

	public static FacilityDefinition get(Identifier id) {
		return id == null ? null : facilities.get(id);
	}

	/** In tab order. */
	public static Map<Identifier, FacilityDefinition> all() {
		return facilities;
	}

	public static Identifier idOf(FacilityKind kind) {
		for (Map.Entry<Identifier, FacilityDefinition> e : facilities.entrySet()) {
			if (e.getValue().kind() == kind) return e.getKey();
		}
		return null;
	}

	/** Keeps one definition per kind (lowest order, then id) and sorts them by order. */
	public static void replace(Map<Identifier, FacilityDefinition> loaded) {
		List<Map.Entry<Identifier, FacilityDefinition>> sorted = loaded.entrySet().stream()
				.sorted(Comparator.comparingInt((Map.Entry<Identifier, FacilityDefinition> e) -> e.getValue().order())
						.thenComparing(e -> e.getKey().toString()))
				.toList();
		Map<Identifier, FacilityDefinition> result = new LinkedHashMap<>();
		java.util.Set<FacilityKind> kinds = java.util.EnumSet.noneOf(FacilityKind.class);
		for (Map.Entry<Identifier, FacilityDefinition> e : sorted) {
			if (kinds.add(e.getValue().kind())) result.put(e.getKey(), e.getValue());
		}
		facilities = Collections.unmodifiableMap(result);
	}
}
