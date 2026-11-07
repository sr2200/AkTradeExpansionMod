package com.akf.tradeexpansion.development;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** The four facilities of design N-4. Each market can have one of each; the effects are fixed per kind. */
public enum FacilityKind implements StringRepresentable {
	/** More request slots (J / J-2). */
	QUEST_HALL("quest_hall"),
	/** Higher stock cap and faster restocking (B-1). */
	WAREHOUSE("warehouse"),
	/** Beds counted by D-4; villagers arrive when first completed. */
	INN("inn"),
	/** Bank tab regardless of the level, no card fee, higher interest cap (E-7 / E-8). */
	BANK("bank");

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
			default -> "town";
		};
	}
}
