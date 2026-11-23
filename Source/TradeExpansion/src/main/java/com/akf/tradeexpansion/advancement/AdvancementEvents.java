package com.akf.tradeexpansion.advancement;

import java.util.Set;

/**
 * Event names of {@link TradeEventTrigger} (design R), used by data/ak_trade_expansion/advancement/*.json. Kept free of
 * Minecraft classes so the tests can check the bundled advancements against them.
 */
public interface AdvancementEvents {
	/** A buy or sell; value: the total in copper. */
	String TRADE = "trade";
	/** A buy; value: trading units bought at once. */
	String BULK_BUY = "bulk_buy";
	/** A sale filled the market's stock (it stops buying the good). */
	String SELL_STOPPED = "sell_stopped";
	/** Sold a good the market is short of. */
	String SHORTAGE_SOLD = "shortage_sold";
	/** Sold a good a rival guild is buying up around this market. */
	String RIVAL_SOLD = "rival_sold";
	/** Value: number of markets the player has traded at. */
	String MARKETS = "markets";
	String TRADE_NETHER = "trade_nether";
	String TRADE_END = "trade_end";
	/** Value: cash plus the bank account, in copper. */
	String WEALTH = "wealth";
	/** Value: the amount deposited. */
	String BANK_DEPOSIT = "bank_deposit";
	String CARD_ISSUED = "card_issued";
	/** Value: the stars of the delivered request. */
	String QUEST_DELIVERED = "quest_delivered";
	String CARGO_DELIVERED = "cargo_delivered";
	/** Value: the reputation level with a market. */
	String REPUTATION = "reputation";
	String DONATED = "donated";
	/** Value: completed buildings of a market the player helped develop. */
	String FACILITIES = "facilities";
	/** Value: kinds of completed buildings of a market the player helped develop. */
	String FACILITY_KINDS = "facility_kinds";
	/** v0.87 (N-11): value: the highest level of a completed building of a market the player helped develop. */
	String FACILITY_LEVEL = "facility_level";
	/** v0.87 (N-11): a building of a market the player helped develop is a tall level 3 (second storey or higher shape). */
	String FACILITY_TALL = "facility_tall";
	/** v0.87 (N-11): value: completed buildings of a market the player helped develop when all of them are level 3, else 0. */
	String FACILITIES_MAX_LEVEL = "facilities_max_level";
	String STAFF_HIRED = "staff_hired";
	/** Value: a carrier's profit on one round trip, after its pay. */
	String STAFF_PROFIT = "staff_profit";

	Set<String> ALL = Set.of(TRADE, BULK_BUY, SELL_STOPPED, SHORTAGE_SOLD, RIVAL_SOLD, MARKETS, TRADE_NETHER, TRADE_END, WEALTH,
			BANK_DEPOSIT, CARD_ISSUED, QUEST_DELIVERED, CARGO_DELIVERED, REPUTATION, DONATED, FACILITIES, FACILITY_KINDS,
			FACILITY_LEVEL, FACILITY_TALL, FACILITIES_MAX_LEVEL, STAFF_HIRED, STAFF_PROFIT);
}
