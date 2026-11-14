package com.akf.tradeexpansion.core;

/**
 * C-1 {@code market.newMarketRequires}: whether the beds and villagers counted around a crafted stall are enough to found
 * a new market (the village mode is a structure check and not part of this). Minecraft-independent.
 */
public final class SettlementMath {
	private SettlementMath() {
	}

	public enum Mode { BEDS, VILLAGERS, BEDS_AND_VILLAGERS }

	/**
	 * @param minBeds      {@code market.newMarketMinBeds}
	 * @param minVillagers {@code market.newMarketMinVillagers}
	 * @param enoughBeds   {@code market.newMarketEnoughBeds} (v0.27.1: this many beds are enough whatever the villager
	 *                     count; 0 = off)
	 */
	public record Requirement(Mode mode, int minBeds, int minVillagers, int enoughBeds) {
	}

	/**
	 * @param noBeds    beds cannot be used here (Nether, End): only villagers count
	 * @param beds      beds within {@code market.levelRadius} (ignored when {@code noBeds})
	 * @param villagers villagers within {@code market.levelRadius} (not needed in BEDS mode when beds can be used)
	 */
	public static boolean allows(Requirement r, boolean noBeds, long beds, long villagers) {
		return switch (r.mode()) {
			case BEDS -> noBeds ? villagers >= r.minVillagers() : beds >= r.minBeds();
			case VILLAGERS -> villagers >= r.minVillagers();
			case BEDS_AND_VILLAGERS -> villagers >= r.minVillagers()
					&& (noBeds || beds >= r.minBeds() && (beds >= villagers || enoughBeds(r, beds)));
		};
	}

	/** Whether {@link #allows} needs the villager count (counting villagers is the costly part). */
	public static boolean needsVillagers(Mode mode, boolean noBeds) {
		return mode != Mode.BEDS || noBeds;
	}

	private static boolean enoughBeds(Requirement r, long beds) {
		return r.enoughBeds() > 0 && beds >= r.enoughBeds();
	}
}
