package com.akf.tradeexpansion.test.client;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.client.MarketScreen;
import com.akf.tradeexpansion.client.compat.journeymap.JourneyMapCompat;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketOrigin;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.market.MarketUpkeep;
import com.akf.tradeexpansion.network.BoardS2C;
import com.akf.tradeexpansion.network.MarketSnapshotS2C;
import com.akf.tradeexpansion.test.CaseResult;
import com.akf.tradeexpansion.test.client.ClientChecklistTests.Session;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.common.waypoint.Waypoint;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

import static com.akf.tradeexpansion.test.client.ClientUtil.*;

/**
 * Client case with JourneyMap installed (checklist 20-3 and 20-5; runClientGameTestJourneyMap): the board's map
 * buttons add JourneyMap waypoints instead of writing the coordinates to the chat.
 */
final class ClientJourneyMapCases {
	private static final String NS = "ak_trade_expansion";
	/** COLOR of JourneyMapCompat (gold). */
	private static final int GOLD = 0xFFD700;

	private ClientJourneyMapCases() {
	}

	private static String text(String key, Object... args) {
		return Component.translatable(key, args).getString();
	}

	private static IClientAPI api() {
		return (IClientAPI) get(null, JourneyMapCompat.class, "api");
	}

	/** Our waypoints in JourneyMap (name, position, color). */
	private static List<Waypoint> waypoints(ClientGameTestContext context) {
		return context.computeOnClient(c -> {
			List<? extends Waypoint> list = api().getWaypoints(TradeExpansion.MOD_ID);
			return list == null ? List.<Waypoint>of() : List.<Waypoint>copyOf(list);
		});
	}

	private static String describe(List<Waypoint> list) {
		return list.stream().map(w -> w.getName() + "@" + w.getBlockPos().toShortString() + String.format("#%06X", w.getColor() & 0xFFFFFF)).toList().toString();
	}

	private static boolean waitWaypoints(ClientGameTestContext context, int count) {
		for (int i = 0; i < 60; i++) {
			if (waypoints(context).size() == count) return true;
			context.waitTick();
		}
		return false;
	}

	private static MarketScreen openBoard(Session s) {
		ClientGameTestContext context = s.context;
		context.setScreen(() -> null);
		MarketScreen screen = ClientChecklistTests.openStall(s);
		context.clickScreenButton("screen." + NS + ".tab.board");
		check(waitClient(context, c -> get(screen, "board") != null, 60), "the board did not arrive");
		mouseAway(context);
		return screen;
	}

	private static void checkWaypoint(List<Waypoint> list, String name, BlockPos pos, String what) {
		Waypoint w = list.stream().filter(x -> x.getName().equals(name)).findFirst().orElse(null);
		check(w != null, "JourneyMap has no waypoint \"%s\" for %s: %s", name, what, describe(list));
		check(w.getBlockPos().equals(pos.above()), "the waypoint of %s is at %s, not above the market's center %s", what, w.getBlockPos().toShortString(), pos.toShortString());
		check((w.getColor() & 0xFFFFFF) == GOLD, "the waypoint of %s is #%06X, not gold", what, w.getColor() & 0xFFFFFF);
	}

	/**
	 * 20-5 with JourneyMap: the board's market list has "この市場を登録"; it adds a gold waypoint for this market and the
	 * status line says so; pressed again, the waypoint is replaced (no second one). 20-3: with a market selected the
	 * button is "地図に登録" and adds that market. Reopening the world keeps both waypoints.
	 */
	static void waypointButtons(Session s, CaseResult.Builder result, CaseResult.Builder also) {
		ClientGameTestContext context = s.context;
		check(FabricLoader.getInstance().isModLoaded("journeymap"), "JourneyMap is not loaded");
		ClientChecklistTests.ensureStall(s);
		check(waitClient(context, c -> JourneyMapCompat.ready(), 600), "our JourneyMap plugin was not initialised");
		BlockPos stall = s.stall;
		// A second market 200 blocks east for the board to list.
		s.world.getServer().runOnServer(server -> {
			ServerLevel level = server.overworld();
			Market home = market(server, stall);
			BlockPos pos = ground(server, home.center().getX() + 200, home.center().getZ());
			Market m = MarketRegistry.get(server).assignStall(level, pos, MarketOrigin.NATURAL).market();
			check(m != null && m != home, "no second market at %s", pos);
			MarketUpkeep.touch(level, m);
			MarketRegistry.get(server).setDirty();
		});
		int before = waypoints(context).size();

		MarketScreen screen = openBoard(s);
		String here = "screen." + NS + ".board.map.here.journeymap";
		check(ClientScreenCases.visible(context, here), "the market list of the board has no \"%s\"", text(here));
		check(!ClientScreenCases.visible(context, "screen." + NS + ".board.map.here"), "the board shows the chat button \"%s\" with JourneyMap", text("screen." + NS + ".board.map.here"));
		MarketSnapshotS2C snapshot = snapshot(context, screen);
		Component title = context.computeOnClient(c -> screen.getTitle());
		context.runOnClient(c -> c.gui.hud.getChat().clearMessages(false));
		context.clickScreenButton(here);
		mouseAway(context);
		check(waitWaypoints(context, before + 1), "\"%s\" did not add one waypoint: %s", text(here), describe(waypoints(context)));
		checkWaypoint(waypoints(context), title.getString(), snapshot.center(), "this market");
		String status = text("screen." + NS + ".board.map.added", title);
		check(TextRecorder.capture(context).stream().anyMatch(t -> status.startsWith(t.text().replace("…", "")) && t.text().length() >= 4),
				"the status line does not say \"%s\"", status);
		boolean chat = context.computeOnClient(c -> ClientScreenCases.chat(c).stream().anyMatch(m -> m.content().getString().contains(String.valueOf(snapshot.center().getX()))));
		check(!chat, "with JourneyMap the coordinates also went to the chat");
		screenshot(context, also, "20-5_journeymap_here");
		context.clickScreenButton(here);
		mouseAway(context);
		context.waitTicks(10);
		check(waypoints(context).size() == before + 1, "pressed again, \"%s\" added another waypoint: %s", text(here), describe(waypoints(context)));

		// A market of the list selected: "地図に登録".
		BoardS2C board = context.computeOnClient(c -> (BoardS2C) get(screen, "board"));
		check(!board.markets().isEmpty(), "the board lists no other market");
		BoardS2C.Entry entry = board.markets().getFirst();
		context.runOnClient(c -> {
			set(screen, "boardMarket", entry.id());
			call(screen, "updateButtons");
		});
		context.waitTicks(2);
		String map = "screen." + NS + ".board.map.journeymap";
		check(ClientScreenCases.visible(context, map), "no \"%s\" with a market selected", text(map));
		check(!ClientScreenCases.visible(context, here), "\"%s\" is still there with a market selected", text(here));
		context.clickScreenButton(map);
		mouseAway(context);
		check(waitWaypoints(context, before + 2), "\"%s\" did not add a waypoint: %s", text(map), describe(waypoints(context)));
		checkWaypoint(waypoints(context), entry.title().getString(), entry.center(), "the selected market");
		screenshot(context, result, "20-3_journeymap_board");
		context.clickScreenButton(map);
		mouseAway(context);
		context.waitTicks(10);
		check(waypoints(context).size() == before + 2, "pressed again, \"%s\" added another waypoint: %s", text(map), describe(waypoints(context)));
		String selectedName = entry.title().getString();
		context.setScreen(() -> null);

		// The world opened again: JourneyMap still has both.
		TestWorldSave save = s.world.getWorldSave();
		s.world.close();
		s.world = save.open();
		s.world.getConnection().waitForChunksRender();
		applyTestConfig(s.world.getServer());
		check(waitClient(context, c -> JourneyMapCompat.ready(), 600), "our JourneyMap plugin was not initialised after reopening the world");
		boolean kept = false;
		for (int i = 0; i < 200 && !kept; i++) {
			List<Waypoint> list = waypoints(context);
			kept = list.stream().anyMatch(w -> w.getName().equals(title.getString())) && list.stream().anyMatch(w -> w.getName().equals(selectedName));
			if (!kept) context.waitTick();
		}
		check(kept, "after reopening the world JourneyMap has %s", describe(waypoints(context)));
		check(waypoints(context).size() == before + 2, "after reopening the world JourneyMap has %d waypoints of the mod, not %d: %s", waypoints(context).size(),
				before + 2, describe(waypoints(context)));
	}
}
