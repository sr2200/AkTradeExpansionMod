package com.akf.tradeexpansion.test.client;

import com.akf.tradeexpansion.block.FacilitySignBlock;
import com.akf.tradeexpansion.client.MarketScreen;
import com.akf.tradeexpansion.config.ConfigManager;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.currency.WalletService;
import com.akf.tradeexpansion.development.Facility;
import com.akf.tradeexpansion.development.FacilityKind;
import com.akf.tradeexpansion.item.CoinItem;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketClock;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.network.MarketSnapshotS2C;
import com.akf.tradeexpansion.network.MarketSnapshotS2C.FacilityView;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.registry.ModBlocks;
import com.akf.tradeexpansion.staff.Employee;
import com.akf.tradeexpansion.test.CaseResult;
import com.akf.tradeexpansion.test.client.ClientChecklistTests.Session;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

import static com.akf.tradeexpansion.test.client.ClientUtil.*;

/**
 * Client cases of the development tab (checklist chapters 22, 28 and 29): the coin donations, why "建てる" cannot be
 * pressed, the materials and the stake, the rows' states, a building's sign opening its tab, and the tabs that go away
 * when their feature is turned off. Buildings are made on the server without their blocks (a facility sign at the
 * core keeps a completed one from counting as destroyed) and removed at the end of each case.
 */
final class ClientTownCases {
	private ClientTownCases() {
	}

	private static final String NS = "ak_trade_expansion";
	private static final String T = "screen." + NS + ".town.";
	private static final int TOWN_HEADER_H = 10;

	private static String text(String key, Object... args) {
		return Component.translatable(key, args).getString();
	}

	private static String prefix(String key) {
		return Language.getInstance().getOrDefault(key).split("%s", -1)[0];
	}

	private static String squeeze(String s) {
		return s.replaceAll("\\s", "").replace("…", "");
	}

	private static String coins(long amount) {
		return CoinItem.formatCopper(amount);
	}

	/** Development points as the screen writes them. */
	private static String points(double value) {
		return String.format(Locale.ROOT, "%,d", (long) Math.floor(value + 1e-9));
	}

	static Identifier id(String name) {
		return Identifier.fromNamespaceAndPath(NS, name);
	}

	// ---- set-up on the server

	private static void config(Session s, Consumer<TradeExpansionConfig> change) {
		s.world.getServer().runOnServer(server -> {
			TradeExpansionConfig config = ConfigManager.copy(TradeExpansionConfig.get());
			change.accept(config);
			ConfigManager.apply(config);
		});
	}

	private static TradeExpansionConfig saveConfig(Session s) {
		return s.world.getServer().computeOnServer(server -> ConfigManager.copy(TradeExpansionConfig.get()));
	}

	private static void restoreConfig(Session s, TradeExpansionConfig old) {
		s.world.getServer().runOnServer(server -> ConfigManager.apply(old));
	}

	private static void setPoints(Session s, double reputation, double development) {
		BlockPos stall = s.stall;
		s.world.getServer().runOnServer(server -> {
			Market market = market(server, stall);
			long now = MarketClock.now(server.overworld());
			UUID id = player(server).getUUID();
			if (reputation >= 0) market.setStanding(new Market.Standing(id, reputation, now));
			if (development >= 0) market.setDevelopment(development, now);
			MarketRegistry.get(server).setDirty();
		});
	}

	record Saved(Market.Standing standing, double development) {
	}

	static Saved save(Session s) {
		BlockPos stall = s.stall;
		return s.world.getServer().computeOnServer(server -> {
			Market market = market(server, stall);
			return new Saved(market.standing(player(server).getUUID()), market.development());
		});
	}

	static void restore(Session s, Saved saved, List<String> facilities, List<BlockPos> signs) {
		BlockPos stall = s.stall;
		s.world.getServer().runOnServer(server -> {
			Market market = market(server, stall);
			for (String f : facilities) market.facilities().remove(id(f));
			for (BlockPos pos : signs) server.overworld().setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
			UUID uuid = player(server).getUUID();
			long now = MarketClock.now(server.overworld());
			market.setStanding(saved.standing() != null ? saved.standing() : new Market.Standing(uuid, 0, now));
			market.setDevelopment(saved.development(), now);
			MarketRegistry.get(server).setDirty();
		});
	}

	enum State { CONSTRUCTING, COMPLETE, DESTROYED }

	/**
	 * A facility of the stall's market without its blocks. A completed one gets its sign at {@code core} (without it the
	 * next touch of the market counts it as destroyed).
	 */
	static void facility(MinecraftServer server, BlockPos stall, String name, FacilityKind kind, State state, BlockPos core) {
		ServerLevel level = server.overworld();
		Market market = market(server, stall);
		long now = MarketClock.now(level);
		Facility f = new Facility(id(name), kind);
		f.start(core, Rotation.NONE, core, new BoundingBox(core), "plains", now, now + 2 * PriceModel.TICKS_PER_DAY);
		if (state != State.CONSTRUCTING) {
			if (state == State.COMPLETE) {
				level.setBlock(core, ModBlocks.FACILITY_SIGN.defaultBlockState().setValue(FacilitySignBlock.KIND, kind).setValue(FacilitySignBlock.BUILT, true), 2);
			}
			f.complete(core);
			if (state == State.DESTROYED) f.destroy();
		}
		market.facilities().put(id(name), f);
		MarketRegistry.get(server).setDirty();
	}

	// ---- the screen

	static MarketScreen openTown(Session s) {
		ClientGameTestContext context = s.context;
		context.setScreen(() -> null);
		context.waitTicks(2);
		MarketScreen screen = ClientChecklistTests.openStall(s);
		context.clickScreenButton("screen." + NS + ".tab.town");
		context.waitTicks(3);
		mouseAway(context);
		return screen;
	}

	static MarketSnapshotS2C.DevelopmentView development(ClientGameTestContext context, MarketScreen screen) {
		return snapshot(context, screen).development();
	}

	static FacilityView view(ClientGameTestContext context, MarketScreen screen, String name) {
		return development(context, screen).facilities().stream().filter(f -> f.kind().equals(name)).findFirst()
				.orElseThrow(() -> new AssertionError("the development tab has no " + name));
	}

	private static void selectFacility(ClientGameTestContext context, MarketScreen screen, String name) {
		String id = view(context, screen, name).id();
		context.runOnClient(c -> {
			set(screen, "selectedFacility", id);
			call(screen, "updateButtons");
		});
		context.waitTicks(2);
	}

	/** The texts of a building's row (scrolled into view). */
	static List<String> row(ClientGameTestContext context, MarketScreen screen, String name) {
		List<FacilityView> facilities = development(context, screen).facilities();
		int index = facilities.stream().map(FacilityView::kind).toList().indexOf(name);
		check(index >= 0, "the development tab has no %s", name);
		int n = facilities.size();
		int rowH = context.computeOnClient(c -> (Integer) call(screen, "townRowH", n));
		int visible = context.computeOnClient(c -> (Integer) call(screen, "townVisible", n));
		int scroll = Math.clamp(index, 0, Math.max(0, n - visible));
		context.runOnClient(c -> set(screen, "townScroll", scroll));
		mouseAway(context);
		int top = context.computeOnClient(c -> topPos(screen)) + constant(MarketScreen.class, "LIST_Y") + TOWN_HEADER_H + (index - scroll) * rowH;
		return strings(TextRecorder.capture(context).stream().filter(t -> t.y0() >= top && t.y0() < top + rowH).toList());
	}

	private static String all(ClientGameTestContext context) {
		mouseAway(context);
		return squeeze(String.join("", strings(TextRecorder.capture(context))));
	}

	private static Button button(ClientGameTestContext context, MarketScreen screen, String field) {
		return context.computeOnClient(c -> (Button) get(screen, field));
	}

	/** The tooltip shown with the mouse over a button, joined without spaces. */
	private static String buttonTooltip(ClientGameTestContext context, Button b) {
		moveTo(context, b.getX() + b.getWidth() / 2.0, b.getY() + b.getHeight() / 2.0);
		context.waitTicks(3);
		String tip = squeeze(String.join("", strings(tooltip(TextRecorder.capture(context)))));
		mouseAway(context);
		return tip;
	}

	@SuppressWarnings("unchecked")
	private static Button donation(ClientGameTestContext context, MarketScreen screen, int index) {
		return context.computeOnClient(c -> ((List<Button>) get(screen, "townWidgets")).get(index));
	}

	private static double serverDevelopment(Session s) {
		BlockPos stall = s.stall;
		return s.world.getServer().computeOnServer(server -> market(server, stall).development());
	}

	private static double serverReputation(Session s) {
		BlockPos stall = s.stall;
		return s.world.getServer().computeOnServer(server -> {
			Market.Standing st = market(server, stall).standing(player(server).getUUID());
			return st == null ? 0 : st.points();
		});
	}

	/** Clicks a donation button and waits for the result; returns the money it took. */
	private static long donate(Session s, MarketScreen screen, int index) {
		long before = serverMoney(s);
		clickWidget(s.context, donation(s.context, screen, index));
		mouseAway(s.context);
		for (int i = 0; i < 40 && serverMoney(s) == before; i++) s.context.waitTick();
		s.context.waitTicks(4); // the result and the new snapshot
		return before - serverMoney(s);
	}

	// ---- 22-2 / 22-3 / 22-4 / 28-8 donations

	/**
	 * 22-2: the tab shows the points, "寄付した人：まだいません", "あなた：0" and "コインを寄付する" with 100c / 1,000c /
	 * 10,000c. 22-3: 100c takes 100c, adds 100 points and 3 reputation points, and lists the player. 22-4: more than the
	 * player has: "所持金が足りません" and nothing changes. 28-8: with a park 1,000c gives "発展度 +1,250" and the same
	 * reputation as without.
	 */
	static void donations(Session s, CaseResult.Builder result, CaseResult.Builder also) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		Saved saved = save(s);
		BlockPos stall = s.stall;
		List<BlockPos> signs = new ArrayList<>();
		long[] taken = {0};
		try {
			s.world.getServer().runOnServer(server -> {
				Market market = market(server, stall);
				market.contributors().clear();
				market.setDevelopment(0, MarketClock.now(server.overworld()));
				WalletService.give(player(server), 20_000);
				MarketRegistry.get(server).setDirty();
			});
			MarketScreen screen = openTown(s);
			String shown = all(context);
			for (String line : List.of(text(T + "points", points(0)), text(T + "contributors") + text(T + "contributors.none"), text(T + "mine", points(0)),
					text(T + "donate"))) {
				check(shown.contains(squeeze(line)), "the development tab does not say \"%s\": %s", line, shown);
			}
			for (int i = 0; i < 3; i++) {
				Button b = donation(context, screen, i);
				String label = coins(new long[] {100, 1_000, 10_000}[i]);
				check(b.visible && b.getMessage().getString().equals(label), "the donation button %d says \"%s\" (visible %s), not \"%s\"", i,
						b.getMessage().getString(), b.visible, label);
			}
			screenshot(context, result, "22-2_town_donations");

			// 22-3: 100c.
			double rep0 = serverReputation(s);
			long paid = donate(s, screen, 0);
			check(paid == 100, "\"100c\" took %dc", paid);
			check(Math.abs(serverDevelopment(s) - 100) < 1e-6, "the points are %.1f after 100c (100 expected)", serverDevelopment(s));
			double gained = serverReputation(s) - rep0;
			check(Math.abs(gained - 3) < 0.05, "100c gave %.2f reputation points, not 3", gained);
			String name = context.computeOnClient(c -> c.player.getName().getString());
			shown = all(context);
			for (String line : List.of(text(T + "points", points(100)), "1." + name, text(T + "mine", points(100)), text(T + "donated", coins(100), points(100)))) {
				check(shown.contains(squeeze(line)), "after donating 100c the tab does not say \"%s\": %s", line, shown);
			}
			screenshot(context, result, "22-3_donated");

			// 22-4: not enough money.
			s.world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				long all = WalletService.balance(player);
				long change = WalletService.take(player, all);
				WalletService.give(player, change);
				taken[0] = all - change;
				WalletService.give(player, 5_000);
			});
			context.waitTicks(3);
			double points = serverDevelopment(s);
			long money = serverMoney(s);
			clickWidget(context, donation(context, screen, 2)); // 10,000c
			mouseAway(context);
			context.waitTicks(10);
			check(serverMoney(s) == money && Math.abs(serverDevelopment(s) - points) < 1e-6, "a refused donation changed the money (%d -> %d) or the points",
					money, serverMoney(s));
			String refused = squeeze(text("trade." + NS + ".result.insufficient_funds"));
			check(all(context).contains(refused), "the status line does not say \"%s\"", refused);
			screenshot(context, result, "22-4_not_enough");

			// 28-8: a park.
			try {
				BlockPos core = stall.above(5);
				signs.add(core);
				s.world.getServer().runOnServer(server -> facility(server, stall, "park", FacilityKind.PARK, State.COMPLETE, core));
				screen = openTown(s);
				double before = serverDevelopment(s);
				double rep1 = serverReputation(s);
				check(donate(s, screen, 1) == 1_000, "\"1,000c\" did not take 1,000c");
				double expected = 1_000 * TradeExpansionConfig.get().development.parkPointsMultiplier;
				check(Math.abs(serverDevelopment(s) - before - expected) < 1e-6, "1,000c with a park gave %.1f points, not %.1f", serverDevelopment(s) - before, expected);
				String line = squeeze(text(T + "donated", coins(1_000), points(expected)));
				check(all(context).contains(line), "the status line does not say \"%s\"", line);
				double rep = serverReputation(s) - rep1;
				check(Math.abs(rep - 30) < 0.2, "1,000c with a park gave %.2f reputation points, not 30 (as without a park)", rep);
				screenshot(context, also, "28-8_park_donation");
			} catch (Throwable t) {
				throw new ClientChecklistTests.AlsoFailed(t);
			}
		} finally {
			context.setScreen(() -> null);
			if (taken[0] > 0) s.world.getServer().runOnServer(server -> WalletService.give(player(server), taken[0]));
			restore(s, saved, List.of("park"), signs);
		}
	}

	// ---- 22-10 another player's donation

	/**
	 * 22-10: the player donates 100c on the open development tab, then a second player (added on the server, its trade
	 * screen open at the same stall) donates 1,000c: without reopening, the tab shows the new points and both names,
	 * the larger donation first.
	 */
	static void otherPlayerDonates(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		Saved saved = save(s);
		BlockPos stall = s.stall;
		ServerPlayer[] other = new ServerPlayer[1];
		try {
			s.world.getServer().runOnServer(server -> {
				Market market = market(server, stall);
				market.contributors().clear();
				market.setDevelopment(0, MarketClock.now(server.overworld()));
				if (WalletService.balance(player(server)) < 1_000) WalletService.give(player(server), 1_000);
				MarketRegistry.get(server).setDirty();
			});
			MarketScreen screen = openTown(s);
			check(donate(s, screen, 0) == 100, "\"100c\" did not take 100c");
			String name = context.computeOnClient(c -> c.player.getName().getString());
			check(all(context).contains(squeeze("1." + name)), "the tab does not list the player after donating: %s", all(context));
			MarketSnapshotS2C old = snapshot(context, screen);
			String outcome = s.world.getServer().computeOnServer(server -> {
				other[0] = ClientBuySellCases.secondPlayer(server, stall);
				ServerPlayer p = other[0];
				ServerLevel level = server.overworld();
				Market market = market(server, stall);
				com.akf.tradeexpansion.market.MarketAccess.open(p, level, market,
						(com.akf.tradeexpansion.block.MarketStallBlockEntity) level.getBlockEntity(stall));
				WalletService.give(p, 10_000);
				long money = WalletService.balance(p);
				com.akf.tradeexpansion.development.DevelopmentService.handle(p, new com.akf.tradeexpansion.network.DevelopmentRequestC2S(market.id(),
						com.akf.tradeexpansion.network.DevelopmentRequestC2S.Action.DONATE_COINS, 1_000, "", false));
				return WalletService.balance(p) == money - 1_000 ? null : "the second player's donation did not go through";
			});
			check(outcome == null, "%s", outcome);
			newSnapshot(context, screen, old);
			context.waitTicks(2);
			String shown = all(context);
			check(shown.contains(squeeze(text(T + "points", points(1_100)))), "the open tab does not show %s points: %s", points(1_100), shown);
			// The row shows the first contributor; the mouse over it lists the top five, most first.
			check(shown.contains(squeeze(text(T + "contributors") + "1. other " + points(1_000))), "the open tab does not show \"other\" first: %s", shown);
			check(shown.contains(squeeze(text(T + "mine", points(100)))), "the player's own share is not 100 any more: %s", shown);
			String contributorsLabel = text(T + "contributors");
			TextRecorder.Text line = TextRecorder.capture(context).stream().filter(t -> t.text().startsWith(contributorsLabel)).findFirst()
					.orElseThrow(() -> new AssertionError("no \"" + contributorsLabel + "\" line"));
			moveTo(context, line.x0() + 10, (line.y0() + line.y1()) / 2.0);
			context.waitTicks(2);
			List<String> tip = strings(tooltip(TextRecorder.capture(context)));
			int first = tip.indexOf("1. other  " + points(1_000)), second = tip.indexOf("2. " + name + "  " + points(100));
			check(first >= 0 && second > first, "the contributors tooltip does not list \"other\" first and %s second: %s", name, tip);
			screenshot(context, result, "22-10_other_player_donated");
			mouseAway(context);
		} finally {
			context.setScreen(() -> null);
			s.world.getServer().runOnServer(server -> {
				if (other[0] != null) {
					market(server, stall).contributors().remove(other[0].getUUID());
					other[0].closeContainer();
					server.getPlayerList().remove(other[0]);
				}
			});
			restore(s, saved, List.of(), List.of());
			context.waitTicks(2);
		}
	}

	// ---- 22-22 / 22-23b / 22-25 why "建てる" cannot be pressed

	/**
	 * 22-22: below the reputation to choose a building "建てる" cannot be pressed; its tooltip says from which reputation.
	 * 22-23b: with the reputation but without the materials it says how many are missing; with
	 * {@code development.requireMaterials} off it can be pressed. 22-25: while another building is under construction
	 * it says so.
	 */
	static void buildReasons(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		Saved saved = save(s);
		TradeExpansionConfig old = saveConfig(s);
		BlockPos stall = s.stall;
		try {
			setPoints(s, 0, 1_000_000);
			MarketScreen screen = openTown(s);
			selectFacility(context, screen, "warehouse");
			Button build = button(context, screen, "buildButton");
			check(build.visible && !build.active, "\"建てる\" can be pressed with the reputation 0 (visible %s)", build.visible);
			int level = development(context, screen).buildLevel();
			String reputation = squeeze(text(T + "reason.reputation", Component.translatable("screen." + NS + ".reputation.level." + level)));
			String tip = buttonTooltip(context, build);
			check(tip.contains(reputation), "the tooltip of \"建てる\" does not say \"%s\": %s", reputation, tip);
			screenshot(context, result, "22-22_reputation");

			int threshold = TradeExpansionConfig.get().reputation.levelThresholds.get(Math.max(0, level - 1));
			setPoints(s, threshold + 50, -1);
			screen = openTown(s);
			selectFacility(context, screen, "warehouse");
			FacilityView w = view(context, screen, "warehouse");
			int missing = w.wanted().stream().mapToInt(x -> Math.max(0, x.required() - x.donated())).sum();
			check(missing > 0, "the warehouse wants no materials");
			build = button(context, screen, "buildButton");
			check(!build.active, "\"建てる\" can be pressed without the materials");
			String materials = squeeze(text(T + "reason.materials", missing));
			tip = buttonTooltip(context, build);
			check(tip.contains(materials), "the tooltip of \"建てる\" does not say \"%s\": %s", materials, tip);
			screenshot(context, result, "22-23b_materials");

			config(s, c -> c.development.requireMaterials = false);
			screen = openTown(s);
			selectFacility(context, screen, "warehouse");
			check(button(context, screen, "buildButton").active, "\"建てる\" cannot be pressed with development.requireMaterials off");

			// 22-25: another building under construction.
			s.world.getServer().runOnServer(server -> facility(server, stall, "inn", FacilityKind.INN, State.CONSTRUCTING, stall.above(5)));
			screen = openTown(s);
			selectFacility(context, screen, "warehouse");
			build = button(context, screen, "buildButton");
			check(!build.active, "\"建てる\" can be pressed while the inn is under construction");
			String limit = squeeze(text(T + "reason.limit"));
			tip = buttonTooltip(context, build);
			check(tip.contains(limit), "the tooltip of \"建てる\" does not say \"%s\": %s", limit, tip);
			screenshot(context, result, "22-25_limit");
		} finally {
			context.setScreen(() -> null);
			restoreConfig(s, old);
			restore(s, saved, List.of("inn"), List.of());
		}
	}

	// ---- 22-23 / 22-35 materials and the stake

	/**
	 * 22-23: "資材を寄付（N）" counts the wanted materials carried; pressing it takes them, adds points and lowers the
	 * number still wanted; without them it cannot be pressed. 22-35: "杭をもらう" gives the stake once ("建設予定地の杭を
	 * もう持っています" the second time).
	 */
	static void materialsAndStake(Session s, CaseResult.Builder result, CaseResult.Builder also) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		Saved saved = save(s);
		BlockPos stall = s.stall;
		try {
			MarketScreen screen = openTown(s);
			FacilityView w = view(context, screen, "warehouse");
			MarketSnapshotS2C.WantedView wanted = w.wanted().stream().filter(x -> x.tag().isEmpty() && x.required() > x.donated()).findFirst()
					.orElseThrow(() -> new AssertionError("the warehouse wants no single item"));
			Identifier item = wanted.item();
			int give = Math.min(32, wanted.required() - wanted.donated());
			int held0 = s.world.getServer().computeOnServer(server -> count(player(server), BuiltInRegistries.ITEM.getValue(item)));
			s.world.getServer().runOnServer(server -> giveItems(player(server), BuiltInRegistries.ITEM.getValue(item), give));
			screen = openTown(s);
			selectFacility(context, screen, "warehouse");
			Button donate = button(context, screen, "donateItemsButton");
			FacilityView selected = view(context, screen, "warehouse");
			MarketScreen shown = screen;
			int donatable = context.computeOnClient(c -> (Integer) call(shown, "donatable", selected));
			check(donatable >= give, "only %d of the %d given count as donatable", donatable, give);
			check(donate.visible && donate.active && donate.getMessage().getString().equals(text(T + "donate_items", donatable)),
					"the button says \"%s\" (active %s), not \"%s\"", donate.getMessage().getString(), donate.active, text(T + "donate_items", donatable));
			int left0 = wanted.required() - wanted.donated();
			double points0 = serverDevelopment(s);
			clickWidget(context, donate);
			mouseAway(context);
			MarketScreen open = screen;
			check(waitClient(context, c -> {
				MarketSnapshotS2C now = (MarketSnapshotS2C) get(open, "snapshot");
				return now.development().facilities().stream().filter(f -> f.kind().equals("warehouse")).findFirst().orElseThrow().wanted().stream()
						.anyMatch(x -> x.item().equals(item) && x.required() - x.donated() < left0);
			}, 60), "the number still wanted did not go down");
			context.waitTicks(3);
			int held = s.world.getServer().computeOnServer(server -> count(player(server), BuiltInRegistries.ITEM.getValue(item)));
			check(held < held0 + give, "no %s was taken", item);
			check(serverDevelopment(s) > points0, "donating the materials added no points");
			check(all(context).contains(squeeze(prefix(T + "donated_items"))), "the status line does not say \"%s…\"", prefix(T + "donated_items"));
			if (held == 0) check(!button(context, screen, "donateItemsButton").active, "\"資材を寄付\" can be pressed without materials");
			screenshot(context, result, "22-23_materials_donated");

			// 22-35: the stake.
			try {
				selectFacilityNone(context, screen);
				Button marker = button(context, screen, "markerButton");
				check(marker.visible && marker.getMessage().getString().equals(text(T + "marker")), "no \"%s\" button", text(T + "marker"));
				clickWidget(context, marker);
				mouseAway(context);
				check(waitClient(context, c -> c.player.getInventory().contains(new net.minecraft.world.item.ItemStack(ModBlocks.CONSTRUCTION_MARKER)), 40),
						"\"%s\" gave no stake", text(T + "marker"));
				context.waitTicks(3);
				check(all(context).contains(squeeze(text(T + "marker_given"))), "the status line does not say \"%s\"", text(T + "marker_given"));
				clickWidget(context, marker);
				mouseAway(context);
				context.waitTicks(5);
				String has = squeeze(text("trade." + NS + ".result.has_marker"));
				check(all(context).contains(has), "a second stake was not refused with \"%s\"", text("trade." + NS + ".result.has_marker"));
				int stakes = s.world.getServer().computeOnServer(server -> count(player(server), ModBlocks.CONSTRUCTION_MARKER.asItem()));
				check(stakes == 1, "the player has %d stakes", stakes);
				screenshot(context, also, "22-35_stake");
			} catch (Throwable t) {
				throw new ClientChecklistTests.AlsoFailed(t);
			}
		} finally {
			context.setScreen(() -> null);
			s.world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
					if (player.getInventory().getItem(i).is(ModBlocks.CONSTRUCTION_MARKER.asItem())) player.getInventory().setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
				}
			});
			restore(s, saved, List.of(), List.of());
			s.world.getServer().runOnServer(server -> {
				Market market = market(server, stall);
				market.clearDonations(id("warehouse"));
				MarketRegistry.get(server).setDirty();
			});
		}
	}

	// ---- 22-23 / 22-23b the cap of the materials and their multiplier

	/** The warehouse's wanted materials of the data, by donation key. */
	private static java.util.Map<String, Integer> warehouseCounts() {
		java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
		for (var w : com.akf.tradeexpansion.data.FacilityCatalog.get(id("warehouse")).wanted()) counts.put(w.key(), w.count());
		return counts;
	}

	private static String key(MarketSnapshotS2C.WantedView w) {
		return w.tag().isEmpty() ? w.item().toString() : "#" + w.tag();
	}

	private static void takeItems(ServerPlayer player, net.minecraft.world.item.Item item, int count) {
		int left = count;
		for (int i = 0; i < player.getInventory().getContainerSize() && left > 0; i++) {
			var stack = player.getInventory().getItem(i);
			if (!stack.is(item)) continue;
			int n = Math.min(left, stack.getCount());
			stack.shrink(n);
			left -= n;
		}
	}

	/**
	 * 22-23: with only a few of a material still wanted, "資材を寄付（N）" counts those few, not all carried; after they
	 * are donated it says (0) and cannot be pressed although some are still carried, and nothing more is taken. 22-23b:
	 * every material wanted is the data's count × {@code development.materialCountMultiplier} (4 by default, rounded
	 * up); with 2 each is halved and the tooltip of "建てる" says the halved number missing; with every material donated
	 * "建てる" can be pressed and has no tooltip.
	 */
	static void materialCap(Session s, CaseResult.Builder result, CaseResult.Builder also) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		Saved saved = save(s);
		TradeExpansionConfig old = saveConfig(s);
		BlockPos stall = s.stall;
		Identifier warehouse = id("warehouse");
		int[] given = {0};
		Identifier[] given_item = {null};
		try {
			setPoints(s, 0, 1_000_000);
			MarketScreen screen = openTown(s);
			int level = development(context, screen).buildLevel();
			int threshold = TradeExpansionConfig.get().reputation.levelThresholds.get(Math.max(0, level - 1));
			setPoints(s, threshold + 50, -1);
			s.world.getServer().runOnServer(server -> {
				market(server, stall).clearDonations(warehouse);
				MarketRegistry.get(server).setDirty();
			});
			java.util.Map<String, Integer> counts = s.world.getServer().computeOnServer(server -> warehouseCounts());

			// 22-23b: the default multiplier.
			screen = openTown(s);
			double multiplier = TradeExpansionConfig.get().development.materialCountMultiplier;
			List<String> problems = new ArrayList<>();
			for (MarketSnapshotS2C.WantedView w : view(context, screen, "warehouse").wanted()) {
				long expected = com.akf.tradeexpansion.core.DevelopmentMath.materialCount(counts.getOrDefault(key(w), -1), multiplier);
				if (w.required() != expected) problems.add(key(w) + " wants " + w.required() + ", not " + expected + " (×" + multiplier + ")");
			}
			check(problems.isEmpty(), "%s", problems);

			// 22-23: the cap of one material.
			MarketSnapshotS2C.WantedView one = view(context, screen, "warehouse").wanted().stream().filter(x -> x.tag().isEmpty()).findFirst()
					.orElseThrow(() -> new AssertionError("the warehouse wants no single item"));
			Identifier item = one.item();
			String oneKey = key(one);
			int required = one.required();
			given_item[0] = item;
			s.world.getServer().runOnServer(server -> {
				market(server, stall).donations(warehouse).put(oneKey, required - 5);
				MarketRegistry.get(server).setDirty();
				giveItems(player(server), BuiltInRegistries.ITEM.getValue(item), 12);
			});
			given[0] = 12;
			int held0 = s.world.getServer().computeOnServer(server -> count(player(server), BuiltInRegistries.ITEM.getValue(item)));
			screen = openTown(s);
			selectFacility(context, screen, "warehouse");
			Button donate = button(context, screen, "donateItemsButton");
			check(donate.active && donate.getMessage().getString().equals(text(T + "donate_items", 5)),
					"with 5 still wanted and 12 carried the button says \"%s\" (active %s), not \"%s\"", donate.getMessage().getString(), donate.active,
					text(T + "donate_items", 5));
			clickWidget(context, donate);
			mouseAway(context);
			MarketScreen open = screen;
			check(waitClient(context, c -> {
				MarketSnapshotS2C now = (MarketSnapshotS2C) get(open, "snapshot");
				return now.development().facilities().stream().filter(f -> f.kind().equals("warehouse")).findFirst().orElseThrow().wanted().stream()
						.anyMatch(x -> x.item().equals(item) && x.donated() >= required);
			}, 60), "the donated count of %s did not reach %d", item, required);
			context.waitTicks(3);
			int held = s.world.getServer().computeOnServer(server -> count(player(server), BuiltInRegistries.ITEM.getValue(item)));
			given[0] = 7;
			check(held == held0 - 5, "donating the last 5 took %d of %s", held0 - held, item);
			int donated = s.world.getServer().computeOnServer(server -> market(server, stall).donations(warehouse).getOrDefault(oneKey, 0));
			check(donated == required, "%d of %s are donated, not %d (the cap)", donated, item, required);
			donate = button(context, screen, "donateItemsButton");
			check(!donate.active && donate.getMessage().getString().equals(text(T + "donate_items", 0)),
					"after the cap the button says \"%s\" (active %s) with %d still carried", donate.getMessage().getString(), donate.active, held);
			screenshot(context, result, "22-23_material_cap");

			// 22-23b: the multiplier 2 halves every count.
			try {
				config(s, c -> c.development.materialCountMultiplier = 2);
				s.world.getServer().runOnServer(server -> {
					market(server, stall).clearDonations(warehouse);
					MarketRegistry.get(server).setDirty();
				});
				screen = openTown(s);
				selectFacility(context, screen, "warehouse");
				int missing = 0;
				for (MarketSnapshotS2C.WantedView w : view(context, screen, "warehouse").wanted()) {
					long full = com.akf.tradeexpansion.core.DevelopmentMath.materialCount(counts.getOrDefault(key(w), -1), multiplier);
					long expected = com.akf.tradeexpansion.core.DevelopmentMath.materialCount(counts.getOrDefault(key(w), -1), 2);
					if (w.required() != expected) problems.add(key(w) + " wants " + w.required() + " with the multiplier 2, not " + expected + " (" + full + " with ×" + multiplier + ")");
					missing += w.required();
				}
				check(problems.isEmpty(), "%s", problems);
				Button build = button(context, screen, "buildButton");
				check(!build.active, "\"建てる\" can be pressed without the materials (multiplier 2)");
				String materials = squeeze(text(T + "reason.materials", missing));
				String tip = buttonTooltip(context, build);
				check(tip.contains(materials), "the tooltip of \"建てる\" does not say \"%s\" with the multiplier 2: %s", materials, tip);
				screenshot(context, also, "22-23b_multiplier_2");

				// Every material donated: "建てる" can be pressed.
				s.world.getServer().runOnServer(server -> {
					Market market = market(server, stall);
					for (var w : com.akf.tradeexpansion.data.FacilityCatalog.get(warehouse).wanted()) {
						market.donations(warehouse).put(w.key(), com.akf.tradeexpansion.development.FacilityBuilder.required(market, warehouse, w));
					}
					MarketRegistry.get(server).setDirty();
				});
				screen = openTown(s);
				selectFacility(context, screen, "warehouse");
				check(view(context, screen, "warehouse").materialsReady(), "the warehouse is not ready with every material donated");
				build = button(context, screen, "buildButton");
				check(build.active, "\"建てる\" cannot be pressed with every material donated");
				tip = buttonTooltip(context, build);
				// Without a tooltip the capture holds the screen's texts: no reason may be among them.
				for (String reason : List.of("materials", "points", "limit", "reputation")) {
					String head = squeeze(prefix(T + "reason." + reason));
					check(head.isEmpty() || !tip.contains(head), "\"建てる\" has the tooltip \"%s…\" with every material donated: %s", head, tip);
				}
				screenshot(context, also, "22-23b_all_donated");
			} catch (Throwable t) {
				throw new ClientChecklistTests.AlsoFailed(t);
			}
		} finally {
			context.setScreen(() -> null);
			restoreConfig(s, old);
			restore(s, saved, List.of(), List.of());
			s.world.getServer().runOnServer(server -> {
				Market market = market(server, stall);
				market.clearDonations(warehouse);
				MarketRegistry.get(server).setDirty();
				if (given_item[0] != null) {
					ServerPlayer player = player(server);
					var it = BuiltInRegistries.ITEM.getValue(given_item[0]);
					takeItems(player, it, given[0]);
				}
			});
		}
	}

	private static void selectFacilityNone(ClientGameTestContext context, MarketScreen screen) {
		context.runOnClient(c -> {
			set(screen, "selectedFacility", null);
			call(screen, "updateButtons");
		});
		context.waitTicks(2);
	}

	// ---- 22-24 / 22-26 / 22-33 / 29-13 the rows' states

	/**
	 * The rows: a building under construction "建設中 約N分", a completed one "✔ 完成", a destroyed one "壊れた <cost>"
	 * (the cost to build it again), and the festival plaza "✔ 今日は祭り" or "✔ 祭りまでN日".
	 */
	static void rowStates(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		Saved saved = save(s);
		BlockPos stall = s.stall;
		List<BlockPos> signs = List.of(stall.above(5), stall.above(6));
		try {
			s.world.getServer().runOnServer(server -> {
				facility(server, stall, "warehouse", FacilityKind.WAREHOUSE, State.CONSTRUCTING, stall.above(7));
				facility(server, stall, "inn", FacilityKind.INN, State.COMPLETE, signs.get(0));
				facility(server, stall, "bank", FacilityKind.BANK, State.DESTROYED, stall.above(8));
				facility(server, stall, "festival", FacilityKind.FESTIVAL, State.COMPLETE, signs.get(1));
			});
			MarketScreen screen = openTown(s);
			FacilityView warehouse = view(context, screen, "warehouse");
			int minutes = context.computeOnClient(c -> (Integer) call(screen, "minutesUntil", warehouse.due()));
			check(minutes > 0, "the warehouse is due in %d minutes", minutes);
			List<String> problems = new ArrayList<>();
			expectRow(context, screen, "warehouse", text(T + "state.constructing", minutes), problems);
			expectRow(context, screen, "inn", text(T + "state.complete"), problems);
			expectRow(context, screen, "bank", text(T + "state.destroyed", points(view(context, screen, "bank").cost())), problems);
			int festivalIn = development(context, screen).festivalIn();
			check(festivalIn >= 0, "the market with a festival plaza has no festival day (%d)", festivalIn);
			expectRow(context, screen, "festival", festivalIn == 0 ? text(T + "state.festival_today") : text(T + "state.festival_in", festivalIn), problems);
			check(problems.isEmpty(), "%s", problems);
			row(context, screen, "warehouse");
			screenshot(context, result, "22-24_rows");
		} finally {
			context.setScreen(() -> null);
			restore(s, saved, List.of("warehouse", "inn", "bank", "festival"), signs);
		}
	}

	private static void expectRow(ClientGameTestContext context, MarketScreen screen, String name, String status, List<String> problems) {
		List<String> row = row(context, screen, name);
		if (!row.contains(status)) problems.add("the row of " + name + " does not say \"" + status + "\": " + row);
	}

	// ---- 34-2 the size by the village style

	/** A template's size as the snapshot sends it (x, height, z), or zero without one. */
	static BlockPos templateSize(MinecraftServer server, com.akf.tradeexpansion.data.FacilityDefinition def, String style) {
		return com.akf.tradeexpansion.development.FacilityBuilder.template(server, def, style)
				.map(t -> new BlockPos(t.getSize().getX(), t.getSize().getY(), t.getSize().getZ())).orElse(BlockPos.ZERO);
	}

	private static void fillBiome(MinecraftServer server, BlockPos center, String biome) {
		ServerLevel level = server.overworld();
		// Within the world's height (the flat world's ground is 4 above its bottom).
		BlockPos a = new BlockPos(center.getX() - 8, Math.max(level.getMinY(), center.getY() - 8), center.getZ() - 8);
		BlockPos b = new BlockPos(center.getX() + 8, Math.min(level.getMaxY(), center.getY() + 8), center.getZ() + 8);
		String command = String.format(Locale.ROOT, "fillbiome %d %d %d %d %d %d %s", a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ(), biome);
		try {
			int n = server.getCommands().getDispatcher().execute(command, server.createCommandSourceStack().withSuppressedOutput());
			check(n > 0, "/%s changed nothing", command);
		} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
			throw new AssertionError("/" + command + " failed: " + e.getMessage(), e);
		}
	}

	/** The tooltip of a building's row (scrolled into view, the mouse over its name). */
	private static List<String> rowTooltip(ClientGameTestContext context, MarketScreen screen, String name) {
		List<FacilityView> facilities = development(context, screen).facilities();
		int index = facilities.stream().map(FacilityView::kind).toList().indexOf(name);
		check(index >= 0, "the development tab has no %s", name);
		int n = facilities.size();
		int rowH = context.computeOnClient(c -> (Integer) call(screen, "townRowH", n));
		int visible = context.computeOnClient(c -> (Integer) call(screen, "townVisible", n));
		int scroll = Math.clamp(index, 0, Math.max(0, n - visible));
		context.runOnClient(c -> set(screen, "townScroll", scroll));
		int top = context.computeOnClient(c -> topPos(screen)) + constant(MarketScreen.class, "LIST_Y") + TOWN_HEADER_H + (index - scroll) * rowH;
		moveTo(context, listX(context, screen) + 40, top + rowH / 2.0);
		context.waitTicks(2);
		List<String> tip = strings(ClientDisplayCases.tip(context));
		mouseAway(context);
		return tip;
	}

	/**
	 * 34-2: some buildings have another height in a desert village than in a plains one. With the market's center made a
	 * desert (/fillbiome) the rows' tooltips show the desert look's size of every building, and a building under
	 * construction in the plains look keeps the plains size (v0.58: the size of the look it has).
	 */
	static void sizeByStyle(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		Saved saved = save(s);
		BlockPos stall = s.stall;
		record Sizes(String kind, BlockPos plains, BlockPos desert) {
		}
		record Before(BlockPos center, String biome, List<Sizes> sizes) {
		}
		Before before = s.world.getServer().computeOnServer(server -> {
			BlockPos center = market(server, stall).center();
			String biome = server.overworld().getBiome(center).unwrapKey().map(k -> k.identifier().toString()).orElse("minecraft:plains");
			List<Sizes> sizes = new ArrayList<>();
			for (var def : com.akf.tradeexpansion.data.FacilityCatalog.all().values()) {
				sizes.add(new Sizes(def.kind().getSerializedName(), templateSize(server, def, "plains"), templateSize(server, def, "desert")));
			}
			return new Before(center, biome, sizes);
		});
		List<Sizes> differ = before.sizes().stream().filter(z -> !z.plains().equals(z.desert())).toList();
		check(!differ.isEmpty(), "no building has another size in a desert village: %s", before.sizes());
		check(differ.stream().anyMatch(z -> z.plains().getY() != z.desert().getY()), "no building has another height in a desert village: %s", differ);
		check(!before.biome().equals("minecraft:desert"), "the test market is already in a desert");
		try {
			// The plains place first: the snapshot's sizes are the plains ones.
			MarketScreen screen = openTown(s);
			List<String> problems = new ArrayList<>();
			for (Sizes z : before.sizes()) {
				BlockPos got = view(context, screen, z.kind()).size();
				if (!got.equals(z.plains())) problems.add(z.kind() + " in the plains is " + got.toShortString() + ", not " + z.plains().toShortString());
			}
			check(problems.isEmpty(), "%s", problems);

			// A desert: every row with its desert size; one under construction in the plains look keeps its size.
			Sizes kept = differ.getFirst();
			s.world.getServer().runOnServer(server -> {
				fillBiome(server, before.center(), "minecraft:desert");
				facility(server, stall, kept.kind(), FacilityKind.valueOf(kept.kind().toUpperCase(Locale.ROOT)), State.CONSTRUCTING, stall.above(7));
			});
			String style = s.world.getServer().computeOnServer(server -> com.akf.tradeexpansion.development.FacilityBuilder.style(server.overworld(), before.center()));
			check(style.equals("desert"), "the market's center is %s after /fillbiome desert", style);
			screen = openTown(s);
			for (Sizes z : before.sizes()) {
				BlockPos want = z.kind().equals(kept.kind()) ? z.plains() : z.desert();
				BlockPos got = view(context, screen, z.kind()).size();
				if (!got.equals(want)) problems.add(z.kind() + " in a desert is " + got.toShortString() + ", not " + want.toShortString());
			}
			for (Sizes z : differ) {
				BlockPos want = z.kind().equals(kept.kind()) ? z.plains() : z.desert();
				String line = text(T + "size", want.getX(), want.getZ(), want.getY());
				List<String> tip = rowTooltip(context, screen, z.kind());
				if (!tip.contains(line)) problems.add("the tooltip of " + z.kind() + " in a desert has no \"" + line + "\": " + tip);
			}
			check(problems.isEmpty(), "%s", problems);
			row(context, screen, differ.size() > 1 ? differ.get(1).kind() : kept.kind());
			screenshot(context, result, "34-2_desert_sizes");
		} finally {
			context.setScreen(() -> null);
			s.world.getServer().runOnServer(server -> fillBiome(server, before.center(), before.biome()));
			List<String> kinds = differ.isEmpty() ? List.of() : List.of(differ.getFirst().kind());
			restore(s, saved, kinds, List.of());
		}
	}

	// ---- 22-32 the sign of a completed building

	/** 22-32: right-clicking the sign of a completed building opens the trade screen on its tab. */
	static void signOpensTab(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		Saved saved = save(s);
		BlockPos stall = s.stall;
		// Within reach of the player (2 south of the stall), no sign in the way of another.
		List<BlockPos> signs = List.of(stall.east(2), stall.west(2), stall.south(2).east(2), stall.south(2).west(2));
		record Case(String name, FacilityKind kind, BlockPos core, String tab) {
		}
		List<Case> list = List.of(new Case("quest_hall", FacilityKind.QUEST_HALL, signs.get(0), "QUEST"),
				new Case("bank", FacilityKind.BANK, signs.get(1), "BANK"), new Case("warehouse", FacilityKind.WAREHOUSE, signs.get(2), "TOWN"),
				new Case("inn", FacilityKind.INN, signs.get(3), "TOWN"));
		try {
			s.world.getServer().runOnServer(server -> {
				for (Case c : list) facility(server, stall, c.name(), c.kind(), State.COMPLETE, c.core());
			});
			List<String> problems = new ArrayList<>();
			for (Case c : list) {
				context.setScreen(() -> null);
				context.waitTicks(2);
				context.getInput().lookAt(c.core());
				context.waitTicks(2);
				context.getInput().pressKey(options -> options.keyUse);
				if (!waitClient(context, m -> m.gui.screen() instanceof MarketScreen, 40)) {
					problems.add("the sign of the " + c.name() + " opened no trade screen");
					continue;
				}
				context.waitTicks(5);
				String tab = context.computeOnClient(m -> ((Enum<?>) get(m.gui.screen(), "tab")).name());
				if (!tab.equals(c.tab())) problems.add("the sign of the " + c.name() + " opened the tab " + tab + ", not " + c.tab());
				screenshot(context, result, "22-32_sign_" + c.name());
			}
			check(problems.isEmpty(), "%s", problems);
		} finally {
			context.setScreen(() -> null);
			restore(s, saved, list.stream().map(Case::name).toList(), signs);
		}
	}

	// ---- 37-1 / 37-2 the mint on the exchange tab

	/**
	 * 37-1 / 37-2: the sign of a mint opens the exchange tab, whose right panel shows the mint: per metal its block, the
	 * blocks held and "9個→96c"; "9個" mints 9 copper blocks into 96c of coins. The texts do not overlap.
	 */
	static void mintPanel(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		Saved saved = save(s);
		BlockPos stall = s.stall;
		BlockPos core = stall.east(2);
		net.minecraft.world.item.Item copper = com.akf.tradeexpansion.currency.MintService.METALS.get(0).block();
		try {
			s.world.getServer().runOnServer(server -> {
				facility(server, stall, "mint", FacilityKind.MINT, State.COMPLETE, core);
				player(server).getInventory().add(new net.minecraft.world.item.ItemStack(copper, 18));
			});
			context.setScreen(() -> null);
			context.waitTicks(2);
			context.getInput().lookAt(core);
			context.waitTicks(2);
			context.getInput().pressKey(options -> options.keyUse);
			check(waitClient(context, m -> m.gui.screen() instanceof MarketScreen, 40), "the mint's sign opened no trade screen");
			context.waitTicks(5);
			String tab = context.computeOnClient(m -> ((Enum<?>) get(m.gui.screen(), "tab")).name());
			check(tab.equals("EXCHANGE"), "the mint's sign opened the tab %s, not EXCHANGE", tab);
			mouseAway(context);
			context.waitTicks(3);
			List<String> texts = strings(TextRecorder.capture(context));
			String value = text("screen.ak_trade_expansion.mint.value", 9, CoinItem.formatCopper(96));
			check(texts.contains(text("screen.ak_trade_expansion.mint.title")) && texts.contains(value) && texts.contains("×18")
					&& texts.contains(text("screen.ak_trade_expansion.mint.disabled")),
					"the mint panel does not show the title, \"%s\", ×18 and the iron row turned off: %s", value, texts);
			List<String> problems = new ArrayList<>();
			ClientChecklistTests.checkLayout(context, result, "mint " + "exchange", true, problems, null);
			check(problems.isEmpty(), "%s", problems);
			long before = context.computeOnClient(c -> WalletService.balance(c.player));
			context.clickScreenButton("screen.ak_trade_expansion.mint.one");
			check(waitClient(context, c -> c.player.getInventory().countItem(copper) == 9, 40), "\"9個\" did not take 9 copper blocks");
			check(waitClient(context, c -> WalletService.balance(c.player) == before + 96, 40), "minting did not give 96c (%d -> %d)", before,
					context.computeOnClient(c -> WalletService.balance(c.player)));
			mouseAway(context);
			context.waitTicks(3);
			check(strings(TextRecorder.capture(context)).contains(text("screen.ak_trade_expansion.mint.done", CoinItem.formatCopper(96))),
					"no message after minting");
			screenshot(context, result, "37-2_mint");
		} finally {
			context.setScreen(() -> null);
			s.world.getServer().runOnServer(server -> {
				var inv = player(server).getInventory();
				for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(copper)) inv.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
			});
			restore(s, saved, List.of("mint"), List.of(core));
		}
	}

	// ---- 22-9 / 26-20 / 24-7 tabs turned off

	private static boolean tabShown(ClientGameTestContext context, String tab) {
		String key = "screen." + NS + ".tab." + tab;
		return context.computeOnClient(c -> c.gui.screen().children().stream().anyMatch(ch -> ch instanceof AbstractWidget w && w.visible
				&& w.getMessage().getContents() instanceof TranslatableContents t && t.getKey().equals(key)));
	}

	private static MarketScreen reopen(Session s) {
		s.context.setScreen(() -> null);
		s.context.waitTicks(2);
		return ClientChecklistTests.openStall(s);
	}

	/**
	 * 22-9: with {@code development.enabled} off the development tab is gone. 26-20: with {@code staff.enabled} off the
	 * staff tab is gone for a player without employees and stays for one with an employee. 24-7: with no trends to
	 * choose ({@code trend.popularCount} and {@code unpopularCount} 0) the next period has none and "流行" is not shown.
	 */
	static void tabsTurnedOff(Session s, CaseResult.Builder result) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		TradeExpansionConfig old = saveConfig(s);
		BlockPos stall = s.stall;
		try {
			reopen(s);
			check(tabShown(context, "town") && tabShown(context, "staff"), "the development or staff tab is missing before turning them off");
			config(s, c -> c.development.enabled = false);
			reopen(s);
			check(!tabShown(context, "town"), "the development tab shows with development.enabled off");
			screenshot(context, result, "22-9_no_town_tab");

			config(s, c -> c.staff.enabled = false);
			reopen(s);
			check(!tabShown(context, "staff"), "the staff tab shows with staff.enabled off (no employees)");
			s.world.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				Market market = market(server, stall);
				MarketRegistry registry = MarketRegistry.get(server);
				long now = MarketClock.now(server.overworld());
				// Employee.hire is package-private: called by reflection (a test set-up only).
				Employee e;
				try {
					var hire = java.util.Arrays.stream(Employee.class.getDeclaredMethods()).filter(m -> m.getName().equals("hire")).findFirst().orElseThrow();
					hire.setAccessible(true);
					e = (Employee) hire.invoke(null, registry.takeEmployeeId(), player.getUUID(), player.getPlainTextName(), Employee.Role.CLERK, market.id(),
							market.regularGoods().getFirst(), -1L, 0, 1L, 1L, now, (long) PriceModel.TICKS_PER_DAY);
				} catch (ReflectiveOperationException ex) {
					throw new AssertionError("cannot hire an employee", ex);
				}
				registry.employees().add(e);
				registry.setDirty();
			});
			reopen(s);
			check(tabShown(context, "staff"), "the staff tab is gone for a player with an employee (staff.enabled off)");
			screenshot(context, result, "26-20_staff_tab_kept");
			// Dismissed with the button (allowed while staff.enabled is off): the last one gone, the tab goes on reopening.
			int[] hired = new int[1];
			s.world.getServer().runOnServer(server -> {
				UUID id = player(server).getUUID();
				hired[0] = MarketRegistry.get(server).employees().stream().filter(e -> e.owner().equals(id)).mapToInt(Employee::id).findFirst().orElse(-1);
			});
			check(hired[0] >= 0, "the hired clerk is not on the server");
			Object staff = ClientStaffCases.staffTabOf(context, screen(context));
			ClientStaffCases.select(context, staff, hired[0]);
			String dismiss = ClientStaffCases.text(ClientStaffCases.K + "dismiss");
			net.minecraft.client.gui.components.Button dismissButton = context.computeOnClient(c -> ClientStaffCases.button(staff, "dismissButton"));
			check(context.computeOnClient(c -> dismissButton.visible && dismissButton.active), "\"%s\" cannot be pressed with staff.enabled off", dismiss);
			clickWidget(context, dismissButton);
			mouseAway(context);
			check(waitClient(context, c -> ((com.akf.tradeexpansion.network.StaffS2C) get(staff, "data")).staff().isEmpty(), 60),
					"the clerk is still listed after \"%s\" (staff.enabled off)", dismiss);
			s.world.getServer().runOnServer(server -> {
				UUID id = player(server).getUUID();
				MarketRegistry.get(server).employees().removeIf(e -> e.owner().equals(id)); // nothing left, whatever happened above
			});
			reopen(s);
			check(!tabShown(context, "staff"), "the staff tab stays after the last employee was dismissed (staff.enabled off)");
			screenshot(context, result, "26-20_staff_tab_gone");
			config(s, c -> c.staff.enabled = true);
			reopen(s);
			check(tabShown(context, "staff"), "the staff tab is missing after staff.enabled is on again");

			// 24-7: the next period with no trends to choose.
			config(s, c -> {
				c.trend.popularCount = 0;
				c.trend.unpopularCount = 0;
			});
			s.world.getServer().runOnServer(server -> {
				Market market = market(server, stall);
				market.setTrendPeriod(market.trendPeriod() - 1); // as if a new period began
				MarketRegistry.get(server).setDirty();
			});
			MarketScreen screen = reopen(s);
			check(snapshot(context, screen).trends().isEmpty(), "the market has trends with trend.popularCount and unpopularCount 0: %s", snapshot(context, screen).trends());
			String label = text("screen." + NS + ".trend.label");
			mouseAway(context);
			List<String> texts = strings(TextRecorder.capture(context));
			check(texts.stream().noneMatch(t -> t.contains(label)), "the title row still shows \"%s\": %s", label, texts);
			screenshot(context, result, "24-7_no_trends");
		} finally {
			context.setScreen(() -> null);
			restoreConfig(s, old);
			s.world.getServer().runOnServer(server -> {
				Market market = market(server, stall);
				market.setTrendPeriod(market.trendPeriod() - 1); // trends again from the next touch
				MarketRegistry.get(server).setDirty();
			});
		}
	}
}
