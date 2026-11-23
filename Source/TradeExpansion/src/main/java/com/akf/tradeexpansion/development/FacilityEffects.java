package com.akf.tradeexpansion.development;

import com.akf.tradeexpansion.core.FacilityLevelMath;
import com.akf.tradeexpansion.market.Market;

/**
 * N-11 (v0.86): how strong the effects of a market's completed buildings are at their level and in the direction chosen
 * at level 2. Every place that applies a building's effect asks here for its value; at level 1 the values are those of
 * the config, as before the levels.
 */
public final class FacilityEffects {
	private FacilityEffects() {
	}

	/** The completed building of {@code kind} at {@code market}, or {@code null}. */
	public static Facility built(Market market, FacilityKind kind) {
		if (market == null) return null;
		for (Facility f : market.facilities().values()) {
			if (f.kind() == kind && f.complete()) return f;
		}
		return null;
	}

	/** Level of the completed building of {@code kind} (0 when there is none). */
	public static int level(Market market, FacilityKind kind) {
		Facility f = built(market, kind);
		return f == null ? 0 : f.level();
	}

	/** Strength of effect {@code effect} (0 = the first) of the building of {@code kind}: 1 at level 1 or without one. */
	public static double strength(Market market, FacilityKind kind, int effect) {
		Facility f = built(market, kind);
		if (f == null) return 1.0;
		return strength(kind, f.level(), f.branch(), effect);
	}

	/** Strength of an effect at {@code level} in direction {@code branch} (also for the screen). */
	public static double strength(FacilityKind kind, int level, String branch, int effect) {
		if (!Development.config().upgradeEnabled) return 1.0;
		return FacilityLevelMath.strength(level, kind.role(branch, effect), Development.config().upgradeEffectSteps);
	}

	/** A multiplier of the config ({@code base}) at the building's strength ({@link FacilityLevelMath#raise}). */
	public static double raise(Market market, FacilityKind kind, int effect, double base) {
		return FacilityLevelMath.raise(base, strength(market, kind, effect));
	}

	/** An added count of the config ({@code base}) at the building's strength ({@link FacilityLevelMath#count}). */
	public static int count(Market market, FacilityKind kind, int effect, int base) {
		return FacilityLevelMath.count(base, strength(market, kind, effect));
	}

	/**
	 * Multiplier of a side bonus at {@code market}: the product over its completed buildings whose direction adds {@code
	 * perk} ({@link FacilityKind#perk}); 1 when none does.
	 */
	public static double perk(Market market, FacilityKind.Perk perk) {
		if (market == null || perk == FacilityKind.Perk.NONE || !Development.config().upgradeEnabled) return 1.0;
		double m = 1.0;
		for (Facility f : market.facilities().values()) {
			if (f.complete() && f.kind().perk(f.branch()) == perk) m *= FacilityLevelMath.perk(f.level(), Development.config().upgradePerkBonus);
		}
		return m;
	}
}
