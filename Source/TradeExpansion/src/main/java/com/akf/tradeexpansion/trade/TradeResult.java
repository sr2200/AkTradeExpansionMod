package com.akf.tradeexpansion.trade;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Locale;

/** Result codes of a trade (design A-4 / B-5). */
public enum TradeResult {
	SUCCESS,
	INVALID_REQUEST,
	TOO_FAR,
	CREATIVE_NOT_ALLOWED,
	NOT_AVAILABLE,
	INSUFFICIENT_STOCK,
	INSUFFICIENT_FUNDS,
	INSUFFICIENT_ITEMS,
	INVENTORY_FULL,
	STOCK_FULL,
	PRICE_CHANGED,
	/** Card payment chosen without a valid card (E-8). Appended last: ordinals are sent over the network. */
	CARD_INVALID,
	/** M (v0.29): a 3-star request needs a better reputation with the market. */
	REPUTATION_TOO_LOW,
	/** J-2 (v0.35): another player already took the cargo crate of the transport request. */
	CARGO_TAKEN,
	/** J-2: no cargo crate for this market in the inventory. */
	NO_CARGO,
	/** J-2: the only crates for this market are past their deadline. */
	CARGO_EXPIRED,
	/** N (v0.38): the market lacks the development points for the facility. */
	NOT_ENOUGH_POINTS,
	/** N-5: development.maxConcurrentBuilds facilities are already under construction. */
	BUILD_LIMIT,
	/** N-5: no free, flat place around the market. */
	NO_SITE,
	/** N-5: the building site stake stands where the building cannot be built. */
	MARKER_BLOCKED,
	/** N-4: the facility is already built or under construction. */
	ALREADY_BUILT,
	/** N-2: no wanted materials in the inventory (or the facility has all it needs). */
	NOTHING_TO_DONATE,
	/** N-5: the player already carries a building site stake. */
	HAS_MARKER,
	/** N-4: the building's structure file is missing from the data packs. */
	NO_TEMPLATE;

	public static final StreamCodec<ByteBuf, TradeResult> STREAM_CODEC =
			ByteBufCodecs.VAR_INT.map(i -> values()[i], TradeResult::ordinal);

	public String translationKey() {
		return "trade.ak_trade_expansion.result." + name().toLowerCase(Locale.ROOT);
	}
}
