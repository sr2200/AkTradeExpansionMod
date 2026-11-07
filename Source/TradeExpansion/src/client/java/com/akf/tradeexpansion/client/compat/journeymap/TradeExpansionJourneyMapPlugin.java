package com.akf.tradeexpansion.client.compat.journeymap;

import com.akf.tradeexpansion.TradeExpansion;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.common.JourneyMapPlugin;

/**
 * JourneyMap client plugin (I, v0.34). Listed under the {@code "journeymap"} entrypoint of {@code fabric.mod.json}, so
 * JourneyMap finds and creates it itself; without JourneyMap nothing ever loads this class.
 */
@JourneyMapPlugin(apiVersion = "2.0.0")
public final class TradeExpansionJourneyMapPlugin implements IClientPlugin {
	@Override
	public String getModId() {
		return TradeExpansion.MOD_ID;
	}

	@Override
	public void initialize(IClientAPI api) {
		JourneyMapCompat.init(api);
		TradeExpansion.LOGGER.info("JourneyMap found: markets can be added as waypoints");
	}
}
