package com.akf.tradeexpansion.client;

import com.akf.tradeexpansion.network.MarketSnapshotS2C;

/** Latest market snapshot received from the server (design 4-2). */
public final class ClientMarketCache {
	private static MarketSnapshotS2C snapshot;

	private ClientMarketCache() {
	}

	public static MarketSnapshotS2C get(long marketId) {
		return snapshot != null && snapshot.marketId() == marketId ? snapshot : null;
	}

	public static void put(MarketSnapshotS2C newSnapshot) {
		snapshot = newSnapshot;
	}

	public static void clear() {
		snapshot = null;
	}
}
