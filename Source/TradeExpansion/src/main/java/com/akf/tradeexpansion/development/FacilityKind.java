package com.akf.tradeexpansion.development;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** The facilities of design N-4. Each market can have one of each; the effects are fixed per kind. */
public enum FacilityKind implements StringRepresentable {
	/** More request slots (J / J-2). */
	QUEST_HALL("quest_hall", 2, Perk.NONE),
	/** Higher stock cap and faster restocking (B-1). */
	WAREHOUSE("warehouse", 2, Perk.NONE),
	/** Beds counted by D-4; villagers arrive when first completed. */
	INN("inn", 1, Perk.REPUTATION),
	/** Bank tab regardless of the level, no card fee, higher interest cap (E-7 / E-8). */
	BANK("bank", 1, Perk.REPUTATION),
	/** Leisure (v0.50): more reputation gained at the market (M). */
	FOUNTAIN("fountain", 1, Perk.DEVELOPMENT),
	/** Leisure (v0.50): more development points gained by the market (N-2). */
	PARK("park", 1, Perk.REPUTATION),
	/** v0.51: prices recover faster at the market (B-3). */
	CLOCK_TOWER("clock_tower", 1, Perk.REPUTATION),
	/** v0.51: fewer carrier accidents on trips from / to the market (Q), weaker rival guild pushes (O). */
	WATCHTOWER("watchtower", 2, Perk.NONE),
	/** v0.51: the bulletin board reaches farther (I). */
	TAVERN("tavern", 1, Perk.REPUTATION),
	/** v0.51: the market deals in more goods (D-4). */
	MARKET_HALL("market_hall", 1, Perk.DEVELOPMENT),
	/** v0.51: higher transport request rewards (J-2) and faster carriers (Q) to / from the market; built at the water. */
	HARBOR("harbor", 2, Perk.NONE),
	/** v0.51: trends in fashion raise prices more, trends out of fashion lower them less (P). */
	THEATER("theater", 1, Perk.REPUTATION),
	/** v0.51: carriers hired here rest less after an accident and quit less often (Q). */
	HOT_SPRING("hot_spring", 2, Perk.NONE),
	/** v0.51: every few days a festival day with more reputation and development points. */
	FESTIVAL("festival", 2, Perk.NONE),
	/** Mint: copper and iron blocks are minted into more coins than by the recipe, on the exchange tab (E-2). */
	MINT("mint", 1, Perk.DEVELOPMENT),
	/** v0.79: more trade routes for the market and a stronger link on its routes (C-5). */
	STATION("station", 2, Perk.NONE),
	/** v0.79: one more of today's specials, sold without the special surcharge (D-5). */
	APPRAISER("appraiser", 1, Perk.REPUTATION),
	/** v0.79: one more shortage slot and more development points for fulfilling one (K / K-2). */
	WHOLESALER("wholesaler", 2, Perk.NONE),
	/** v0.79: clerks hired at the market sell faster (their sales move the price less) for a lower wage (Q-2). */
	GUILD_HALL("guild_hall", 2, Perk.NONE),
	/** v0.79: reputation with the market fades more slowly (M). */
	CHAPEL("chapel", 1, Perk.DEVELOPMENT),
	/** v0.79: the market's other facilities need fewer materials (N-2). */
	TOWN_HALL("town_hall", 1, Perk.REPUTATION),
	/** v0.79: villagers near the market charge fewer coins (H). */
	WORKSHOP("workshop", 1, Perk.REPUTATION),
	/** v0.79: the trade screen tells how many days shortages, trends and rival campaigns still last (outlook). */
	SCHOOL("school", 0, Perk.NONE);

	public static final Codec<FacilityKind> CODEC = StringRepresentable.fromEnum(FacilityKind::values);

	private final String name;
	private final int effects;
	private final Perk perk;

	/**
	 * @param effects N-11 (v0.86): how many of its effects grow with the level (0..2); with 2, direction a strengthens the
	 *                first and b the second, with 1 direction a strengthens it and b adds {@code perk}
	 * @param perk    the side bonus of direction b (with 0 effects: a gives more reputation, b more development points)
	 */
	FacilityKind(String name, int effects, Perk perk) {
		this.name = name;
		this.effects = effects;
		this.perk = perk;
	}

	/** N-11: a side bonus a direction can add at the market (× {@code 1 + development.upgradePerkBonus} per level above 1). */
	public enum Perk {
		NONE, REPUTATION, DEVELOPMENT
	}

	/** N-11: effects that grow with the level (see the constructor). */
	public int effects() {
		return effects;
	}

	/** N-11: how direction {@code branch} ({@code a} / {@code b}, empty before level 2) treats effect {@code effect}. */
	public com.akf.tradeexpansion.core.FacilityLevelMath.Role role(String branch, int effect) {
		if (branch == null || branch.isEmpty() || effect >= effects) return com.akf.tradeexpansion.core.FacilityLevelMath.Role.NEUTRAL;
		boolean a = branch.equals("a");
		boolean strong = effect == 0 ? a : !a;
		return strong ? com.akf.tradeexpansion.core.FacilityLevelMath.Role.STRONG : com.akf.tradeexpansion.core.FacilityLevelMath.Role.WEAK;
	}

	/** N-11: the side bonus direction {@code branch} adds, or {@link Perk#NONE}. */
	public Perk perk(String branch) {
		if (branch == null || branch.isEmpty()) return Perk.NONE;
		if (effects == 0) return branch.equals("a") ? Perk.REPUTATION : Perk.DEVELOPMENT;
		return effects == 1 && branch.equals("b") ? perk : Perk.NONE;
	}

	@Override
	public String getSerializedName() {
		return name;
	}

	/** Tab of the trade screen the facility's sign opens ({@code MarketScreen} tab keys). */
	public String tab() {
		return switch (this) {
			case QUEST_HALL -> "quest";
			case BANK -> "bank";
			case MINT -> "exchange";
			default -> "town";
		};
	}
}
