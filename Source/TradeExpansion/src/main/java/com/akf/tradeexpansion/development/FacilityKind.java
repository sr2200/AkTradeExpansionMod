package com.akf.tradeexpansion.development;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** The facilities of design N-4. Each market can have one of each; the effects are fixed per kind. */
public enum FacilityKind implements StringRepresentable {
	/** More request slots (J / J-2). */
	QUEST_HALL("quest_hall"),
	/** Higher stock cap and faster restocking (B-1). */
	WAREHOUSE("warehouse"),
	/** Beds counted by D-4; villagers arrive when first completed. */
	INN("inn"),
	/** Bank tab regardless of the level, no card fee, higher interest cap (E-7 / E-8). */
	BANK("bank"),
	/** Leisure (v0.50): more reputation gained at the market (M). */
	FOUNTAIN("fountain"),
	/** Leisure (v0.50): more development points gained by the market (N-2). */
	PARK("park"),
	/** v0.51: prices recover faster at the market (B-3). */
	CLOCK_TOWER("clock_tower"),
	/** v0.51: fewer carrier accidents on trips from / to the market (Q), weaker rival guild pushes (O). */
	WATCHTOWER("watchtower"),
	/** v0.51: the bulletin board reaches farther (I). */
	TAVERN("tavern"),
	/** v0.51: the market deals in more goods (D-4). */
	MARKET_HALL("market_hall"),
	/** v0.51: higher transport request rewards (J-2) and faster carriers (Q) to / from the market; built at the water. */
	HARBOR("harbor"),
	/** v0.51: trends in fashion raise prices more, trends out of fashion lower them less (P). */
	THEATER("theater"),
	/** v0.51: carriers hired here rest less after an accident and quit less often (Q). */
	HOT_SPRING("hot_spring"),
	/** v0.51: every few days a festival day with more reputation and development points. */
	FESTIVAL("festival"),
	/** Mint: copper and iron blocks are minted into more coins than by the recipe, on the exchange tab (E-2). */
	MINT("mint"),
	/** v0.79: more trade routes for the market and a stronger link on its routes (C-5). */
	STATION("station"),
	/** v0.79: one more of today's specials, sold without the special surcharge (D-5). */
	APPRAISER("appraiser"),
	/** v0.79: one more shortage slot and more development points for fulfilling one (K / K-2). */
	WHOLESALER("wholesaler"),
	/** v0.79: clerks hired at the market sell faster (their sales move the price less) for a lower wage (Q-2). */
	GUILD_HALL("guild_hall"),
	/** v0.79: reputation with the market fades more slowly (M). */
	CHAPEL("chapel"),
	/** v0.79: the market's other facilities need fewer materials (N-2). */
	TOWN_HALL("town_hall"),
	/** v0.79: villagers near the market charge fewer coins (H). */
	WORKSHOP("workshop"),
	/** v0.79: the trade screen tells how many days shortages, trends and rival campaigns still last (outlook). */
	SCHOOL("school");

	public static final Codec<FacilityKind> CODEC = StringRepresentable.fromEnum(FacilityKind::values);

	private final String name;

	FacilityKind(String name) {
		this.name = name;
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
