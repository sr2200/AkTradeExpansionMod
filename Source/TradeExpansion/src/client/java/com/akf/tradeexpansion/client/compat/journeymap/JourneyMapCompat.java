package com.akf.tradeexpansion.client.compat.journeymap;

import com.akf.tradeexpansion.TradeExpansion;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * JourneyMap waypoints for markets (I, v0.34). The only place besides the plugin that uses JourneyMap's types: it is
 * reached only after {@code FabricLoader.isModLoaded("journeymap")} (see {@code MapWaypoints}), so nothing here is
 * loaded when JourneyMap is not installed.
 */
public final class JourneyMapCompat {
	private static final String MARKET_KEY = "market_id";
	/** Gold, like prices on the trade screen. */
	private static final int COLOR = 0xFFD700;

	private static volatile IClientAPI api;

	private JourneyMapCompat() {
	}

	static void init(IClientAPI clientApi) {
		api = clientApi;
	}

	public static boolean ready() {
		return api != null;
	}

	/** Adds (or replaces) the waypoint of a market. @return {@code false} if JourneyMap refused it */
	public static boolean addMarket(long marketId, String name, BlockPos pos, ResourceKey<Level> dimension) {
		IClientAPI clientApi = api;
		if (clientApi == null) return false;
		try {
			String id = Long.toString(marketId);
			List<? extends Waypoint> old = clientApi.getWaypoints(TradeExpansion.MOD_ID);
			if (old != null) {
				for (Waypoint w : List.copyOf(old)) {
					if (id.equals(w.getCustomData(MARKET_KEY))) clientApi.removeWaypoint(TradeExpansion.MOD_ID, w);
				}
			}
			Waypoint waypoint = WaypointFactory.createWaypoint(TradeExpansion.MOD_ID, pos.above(), name, dimension, true);
			waypoint.setColor(COLOR);
			waypoint.setCustomData(MARKET_KEY, id);
			clientApi.addWaypoint(TradeExpansion.MOD_ID, waypoint);
			return true;
		} catch (RuntimeException | LinkageError e) {
			// A JourneyMap version with a different API: fall back to the chat message.
			TradeExpansion.LOGGER.warn("Could not add a JourneyMap waypoint for market #{}", marketId, e);
			return false;
		}
	}
}
