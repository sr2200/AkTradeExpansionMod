package com.akf.tradeexpansion.test.client;

import com.akf.tradeexpansion.client.MarketScreen;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.data.FacilityCatalog;
import com.akf.tradeexpansion.data.FacilityDefinition;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.GoodsDefinition;
import com.akf.tradeexpansion.development.Development;
import com.akf.tradeexpansion.development.Facility;
import com.akf.tradeexpansion.development.FacilityKind;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketClock;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.market.MarketUpkeep;
import com.akf.tradeexpansion.network.MarketSnapshotS2C;
import com.akf.tradeexpansion.network.MarketSnapshotS2C.FacilityView;
import com.akf.tradeexpansion.network.StaffRequestC2S;
import com.akf.tradeexpansion.network.StaffS2C;
import com.akf.tradeexpansion.rival.Campaign;
import com.akf.tradeexpansion.rival.RivalService;
import com.akf.tradeexpansion.staff.Employee;
import com.akf.tradeexpansion.test.CaseResult;
import com.akf.tradeexpansion.test.client.ClientChecklistTests.AlsoFailed;
import com.akf.tradeexpansion.test.client.ClientChecklistTests.Session;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static com.akf.tradeexpansion.test.client.ClientStaffCases.*;
import static com.akf.tradeexpansion.test.client.ClientUtil.*;

/**
 * Closing the world and opening it again for real (checklist 22-11, 22-40, 23-8, 24-8, 26-18; the server cases only
 * write the saved data out and read it back): the development points and the contributors, a building under
 * construction / completed / destroyed and the donated materials, a rival campaign with its price shift and the trends,
 * and two carriers (one on the way, one おまかせ at home with cargo of two goods and return cargo) are the same on the
 * server and on the screen. Then the construction completes when its due date comes and the carrier goes on with its
 * trip. Everything is made on the server (as {@link ClientTownCases} and {@link ClientStaffMoreCases}).
 */
final class ClientReopenCases {
	private ClientReopenCases() {
	}

	private static final String NS = "ak_trade_expansion";
	private static final String T = "screen." + NS + ".town.";
	private static final String OTHER_NAME = "Reopen Tester";

	private static String text(String key, Object... args) {
		return Component.translatable(key, args).getString();
	}

	private static String points(double value) {
		return String.format(Locale.ROOT, "%,d", (long) Math.floor(value + 1e-9));
	}

	/** What the saved data must keep, read on the server. */
	private record Kept(double development, Map<UUID, Market.Contributor> contributors, Map<String, String> facilities, Map<String, Integer> donations,
			Campaign campaign, double pressure, Map<Identifier, Double> trends, long trendPeriod, long rivalDay, Map<Integer, Tag> staff) {
	}

	private static Kept kept(MinecraftServer server, BlockPos stall, int campaignId, List<Integer> staff) {
		ServerLevel level = server.overworld();
		Market market = market(server, stall);
		MarketRegistry registry = MarketRegistry.get(server);
		long now = MarketClock.now(level);
		Map<String, String> facilities = new LinkedHashMap<>();
		market.facilities().forEach((id, f) -> facilities.put(id.toString(), f.state() + " due " + f.due() + " box " + f.box() + " core " + f.core()));
		Campaign campaign = registry.campaigns().stream().filter(c -> c.id() == campaignId).findFirst().orElse(null);
		double pressure = campaign == null || market.good(campaign.goods()) == null ? Double.NaN : market.good(campaign.goods()).pressure();
		RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, level.registryAccess());
		Map<Integer, Tag> employees = new LinkedHashMap<>();
		for (int id : staff) {
			registry.employees().stream().filter(e -> e.id() == id).findFirst()
					.ifPresent(e -> employees.put(id, Employee.CODEC.encodeStart(ops, e).getOrThrow()));
		}
		return new Kept(Development.points(market, now), new LinkedHashMap<>(market.contributors()), facilities,
				new LinkedHashMap<>(market.donations(ClientTownCases.id("quest_hall"))), campaign, pressure, new LinkedHashMap<>(market.trends()),
				market.trendPeriod(), market.rivalDay(), employees);
	}

	/** What the trade screen shows of the employees (minutes left are compared apart). */
	private static String shown(StaffS2C.View v) {
		return "purse " + v.purse() + " stock " + v.stock() + " phase " + v.phase() + " paused " + v.paused() + " status " + v.status() + " kinds " + v.kinds()
				+ " return " + v.returnItems() + " trips " + v.trips() + " log " + v.log().stream().map(Component::getString).toList();
	}

	private static void reopen(Session s) {
		ClientGameTestContext context = s.context;
		context.setScreen(() -> null);
		TestWorldSave save = s.world.getWorldSave();
		s.world.close();
		s.world = save.open();
		s.world.getConnection().waitForChunksRender();
		applyTestConfig(s.world.getServer());
	}

	/**
	 * 22-11 / 22-40 / 23-8 / 24-8 (the result) and 26-18 (also): see the class. The second check of the construction is
	 * {@code clock advance 0.1} past its due date (it is due in 0.05 days): the building is placed 40 blocks from the
	 * stall, away from the other market, and taken away at the end.
	 */
	static void townAndStaff(Session s, CaseResult.Builder result, CaseResult.Builder also) {
		ClientGameTestContext context = s.context;
		ClientChecklistTests.ensureStall(s);
		ClientTownCases.Saved saved = ClientTownCases.save(s);
		BlockPos stall = s.stall;
		BlockPos innSign = stall.above(5);
		BlockPos bankCore = stall.above(8);
		BoundingBox[] site = new BoundingBox[1];
		int campaignId = -1;
		Throwable staffFailed = null;
		try {
			ClientStaffMoreCases.fresh(s, c -> {
			});
			// The town: points of the player and of another contributor, three buildings and materials for a fourth.
			int[] made = s.world.getServer().computeOnServer(server -> {
				ServerLevel level = server.overworld();
				Market home = market(server, stall);
				MarketRegistry registry = MarketRegistry.get(server);
				long now = MarketUpkeep.touch(level, home);
				Development.add(home, player(server), 500, now);
				UUID other = UUID.nameUUIDFromBytes(OTHER_NAME.getBytes(java.nio.charset.StandardCharsets.UTF_8));
				home.contributors().put(other, new Market.Contributor(other, OTHER_NAME, 120));
				int dx = other(server, home).center().getX() >= stall.getX() ? -1 : 1;
				BlockPos origin = ground(server, stall.getX() + 40 * dx, stall.getZ());
				FacilityDefinition def = FacilityCatalog.get(ClientTownCases.id("warehouse"));
				BlockPos size = ClientTownCases.templateSize(server, def, "plains");
				check(size.getX() > 0, "the warehouse has no template");
				site[0] = new BoundingBox(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + size.getX() - 1, origin.getY() + size.getY() - 1,
						origin.getZ() + size.getZ() - 1);
				Facility warehouse = new Facility(ClientTownCases.id("warehouse"), FacilityKind.WAREHOUSE);
				warehouse.start(origin, Rotation.NONE, origin, site[0], "plains", now, now + PriceModel.TICKS_PER_DAY / 20);
				home.facilities().put(warehouse.id(), warehouse);
				ClientTownCases.facility(server, stall, "inn", FacilityKind.INN, ClientTownCases.State.COMPLETE, innSign);
				ClientTownCases.facility(server, stall, "bank", FacilityKind.BANK, ClientTownCases.State.DESTROYED, bankCore);
				FacilityDefinition questHall = FacilityCatalog.get(ClientTownCases.id("quest_hall"));
				home.donations(ClientTownCases.id("quest_hall")).put(questHall.wanted().getFirst().key(), 3);
				// A rival campaign and a trend.
				ClientDisplayCases.ensureTrend(home);
				Campaign c = RivalService.startNow(level, registry, home, now);
				check(c != null, "no campaign could start at the stall's market");
				registry.setDirty();
				return new int[] {c.id()};
			});
			campaignId = made[0];

			// The carriers: one on the way, one おまかせ at home (paused) with cargo of two goods and return cargo.
			Identifier good = carrierGood;
			long otherId = ClientStaffMoreCases.otherId(s);
			ClientStaffMoreCases.prices(s, good, true, true);
			Object staff = openTab(s);
			MarketScreen screen = screen(context);
			ClientStaffMoreCases.request(s, StaffRequestC2S.Action.HIRE_CARRIER, -1, good, otherId, 8, 1_000_000, 1, 0);
			int carrier = ClientStaffMoreCases.hired(context, staff, 0);
			ClientStaffMoreCases.request(s, StaffRequestC2S.Action.DEPOSIT_MONEY, carrier, good, otherId, 0, 0, 0, 20_000);
			ClientStaffMoreCases.hurryUntil(s, staff, carrier, x -> x.phase() == 1, 3, "on the way");
			ClientStaffMoreCases.request(s, StaffRequestC2S.Action.HIRE_CARRIER, -1, Employee.AUTO, otherId, 16, 1, 1, 0);
			check(waitClient(context, c -> data(context, staff).staff().stream().anyMatch(StaffS2C.View::auto), 60), "the おまかせ carrier was not hired");
			int auto = data(context, staff).staff().stream().filter(StaffS2C.View::auto).findFirst().orElseThrow().id();
			Map<Identifier, Integer> cargo = loadAuto(s, auto, good);
			refresh(context, staff);
			context.waitTicks(30); // the employees' own update (every second) settles first
			refresh(context, staff);
			context.waitTicks(4);
			StaffS2C.View carrierBefore = view(context, staff, carrier), autoBefore = view(context, staff, auto);
			check(carrierBefore.phase() == 1, "the carrier is not on the way (%s)", describe(carrierBefore));
			check(autoBefore.kinds() == 2 && autoBefore.returnItems() > 0, "the おまかせ carrier shows %d kinds and return %d", autoBefore.kinds(),
					autoBefore.returnItems());
			String outbound = K + "phase.outbound";
			int minutesBefore = ClientStaffMoreCases.number(squeeze(ClientStaffMoreCases.status(context, screen, staff, carrier)), outbound);
			check(minutesBefore >= 3, "the carrier's row says %d minutes on the way", minutesBefore);

			int cid = campaignId;
			List<Integer> ids = List.of(carrier, auto);
			Kept before = s.world.getServer().computeOnServer(server -> kept(server, stall, cid, ids));
			check(before.campaign() != null && !before.trends().isEmpty(), "no campaign or trend before closing the world");
			reopen(s);
			Kept after = s.world.getServer().computeOnServer(server -> kept(server, stall, cid, ids));
			context.waitTicks(5);

			// 22-11 / 22-40 / 23-8 / 24-8 on the server.
			List<String> problems = new ArrayList<>();
			if (Math.abs(after.development() - before.development()) > Math.max(0.5, before.development() * 0.01)) {
				problems.add(String.format(Locale.ROOT, "development %.2f -> %.2f", before.development(), after.development()));
			}
			if (!after.contributors().equals(before.contributors())) problems.add("contributors " + before.contributors() + " -> " + after.contributors());
			if (!after.facilities().equals(before.facilities())) problems.add("buildings " + before.facilities() + " -> " + after.facilities());
			if (!after.donations().equals(before.donations()) || before.donations().isEmpty()) {
				problems.add("donated materials " + before.donations() + " -> " + after.donations());
			}
			if (!Objects.equals(after.campaign(), before.campaign())) problems.add("campaign " + before.campaign() + " -> " + after.campaign());
			if (!(Math.abs(after.pressure() - before.pressure()) <= Math.max(0.01, Math.abs(before.pressure()) * 0.01))) {
				problems.add(String.format(Locale.ROOT, "pressure of the campaign's good %.4f -> %.4f", before.pressure(), after.pressure()));
			}
			if (!after.trends().keySet().equals(before.trends().keySet())
					|| before.trends().entrySet().stream().anyMatch(e -> Math.abs(after.trends().get(e.getKey()) - e.getValue()) > 0.05)) {
				problems.add("trends " + before.trends() + " -> " + after.trends());
			}
			if (after.trendPeriod() != before.trendPeriod()) problems.add("trend period " + before.trendPeriod() + " -> " + after.trendPeriod());
			if (after.rivalDay() != before.rivalDay()) problems.add("rival day " + before.rivalDay() + " -> " + after.rivalDay());
			check(problems.isEmpty(), "after opening the world again: %s", problems);

			// The development tab.
			MarketScreen town = ClientTownCases.openTown(s);
			MarketSnapshotS2C.DevelopmentView dev = ClientTownCases.development(context, town);
			check(Math.abs(dev.points() - after.development()) <= Math.max(0.5, after.development() * 0.01), "the tab shows %.1f points, the server has %.1f",
					dev.points(), after.development());
			List<String> names = dev.top().stream().map(MarketSnapshotS2C.ContributorView::name).toList();
			check(names.contains(OTHER_NAME) && names.contains(s.world.getServer().computeOnServer(server -> player(server).getPlainTextName())),
					"the contributors on the tab: %s", names);
			FacilityView warehouseView = ClientTownCases.view(context, town, "warehouse");
			int minutes = context.computeOnClient(c -> (Integer) call(town, "minutesUntil", warehouseView.due()));
			expect(context, town, "warehouse", text(T + "state.constructing", minutes), problems);
			expect(context, town, "inn", text(T + "state.complete"), problems);
			expect(context, town, "bank", text(T + "state.destroyed", points(ClientTownCases.view(context, town, "bank").cost())), problems);
			FacilityView questHall = ClientTownCases.view(context, town, "quest_hall");
			if (questHall.wanted().isEmpty() || questHall.wanted().getFirst().donated() != 3) {
				problems.add("the quest hall's donated materials on the tab: " + questHall.wanted());
			}
			check(problems.isEmpty(), "%s", problems);
			screenshot(context, result, "22-40_reopened_town");

			// 23-8 / 24-8 on the screen: the campaign's good and the trends.
			MarketSnapshotS2C snapshot = snapshot(context, town);
			Campaign campaign = after.campaign();
			MarketSnapshotS2C.GoodView rivalGood = snapshot.goods().stream().filter(v -> v.goods().equals(campaign.goods())).findFirst()
					.orElseThrow(() -> new AssertionError("the screen has no " + campaign.goods()));
			check(rivalGood.rival().isPresent() && rivalGood.rival().get().buying() == campaign.buying(), "the screen shows the campaign on %s as %s",
					campaign.goods(), rivalGood.rival());
			List<Identifier> groups = snapshot.trends().stream().map(MarketSnapshotS2C.TrendView::group).toList();
			check(groups.containsAll(after.trends().keySet()), "the screen shows the trends %s, the market has %s", groups, after.trends().keySet());

			// 26-18: the employees.
			try {
				List<String> staffProblems = new ArrayList<>();
				for (int id : ids) {
					Tag b = before.staff().get(id), a = after.staff().get(id);
					if (a == null) staffProblems.add("the employee #" + id + " is gone");
					else if (!a.equals(b)) staffProblems.add("the employee #" + id + " " + b + " -> " + a);
				}
				check(staffProblems.isEmpty(), "after opening the world again: %s", staffProblems);
				Object staffAfter = openTab(s);
				MarketScreen staffScreen = screen(context);
				for (StaffS2C.View v : List.of(carrierBefore, autoBefore)) {
					StaffS2C.View now = waitView(context, staffAfter, v.id(), x -> true, 60, "listed");
					check(shown(now).equals(shown(v)), "the staff tab showed %s, after opening the world again %s", shown(v), shown(now));
				}
				int minutesAfter = ClientStaffMoreCases.number(squeeze(ClientStaffMoreCases.status(context, staffScreen, staffAfter, carrier)), outbound);
				check(minutesAfter >= minutesBefore - 1 && minutesAfter <= minutesBefore, "the carrier's row said %d minutes on the way, after opening the world again %d",
						minutesBefore, minutesAfter);
				Map<Identifier, Integer> held = new LinkedHashMap<>(lines(s, auto, "cargo"));
				lines(s, auto, "returnCargo").forEach((g, n) -> held.merge(g, n, Integer::sum));
				check(held.equals(cargo), "the おまかせ carrier holds %s after opening the world again (%s before)", held, cargo);
				screenshot(context, result, "26-18_reopened_staff");
			} catch (AssertionError e) {
				staffFailed = e;
			}

			// 22-40: the construction completes when its due date comes.
			ClientStaffMoreCases.advance(s, "0.1");
			boolean done = false;
			for (int i = 0; i < 100 && !done; i++) {
				context.waitTick();
				done = s.world.getServer().computeOnServer(server -> {
					Facility f = market(server, stall).facilities().get(ClientTownCases.id("warehouse"));
					return f != null && f.complete();
				});
			}
			check(done, "the warehouse did not complete after its due date");
			MarketScreen completed = ClientTownCases.openTown(s);
			List<String> row = ClientTownCases.row(context, completed, "warehouse");
			check(row.contains(text(T + "state.complete")), "the completed warehouse's row: %s", row);
			screenshot(context, result, "22-40_completed_after_reopen");

			// 26-18: the carrier goes on with its trip.
			if (staffFailed == null) {
				try {
					Object staffAfter = openTab(s);
					StaffS2C.View v = ClientStaffMoreCases.hurryUntil(s, staffAfter, carrier, x -> x.phase() == 2 || x.phase() == 3 || x.phase() == 0 && x.trips() > 0,
							3, "at the other market");
					check(v.trips() >= carrierBefore.trips(), "the carrier's trips went %d -> %d", carrierBefore.trips(), v.trips());
				} catch (AssertionError e) {
					staffFailed = e;
				}
			}
		} finally {
			context.setScreen(() -> null);
			int cid = campaignId;
			s.world.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				Market home = market(server, stall);
				MarketRegistry registry = MarketRegistry.get(server);
				registry.campaigns().removeIf(c -> c.id() == cid);
				home.contributors().remove(UUID.nameUUIDFromBytes(OTHER_NAME.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
				home.clearDonations(ClientTownCases.id("quest_hall"));
				if (site[0] != null) {
					BoundingBox b = site[0];
					for (BlockPos p : BlockPos.betweenClosed(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ())) {
						level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
					}
				}
				registry.setDirty();
			});
			ClientTownCases.restore(s, saved, List.of("warehouse", "inn", "bank"), List.of(innSign, bankCore));
			cleanUp(s);
		}
		if (staffFailed != null) throw new AlsoFailed(staffFailed);
	}

	private static void expect(ClientGameTestContext context, MarketScreen screen, String name, String status, List<String> problems) {
		List<String> row = ClientTownCases.row(context, screen, name);
		if (!row.contains(status)) problems.add("the row of " + name + " does not say \"" + status + "\": " + row);
	}

	/**
	 * Cargo of two goods (other than {@code not}) and return cargo of a third on the おまかせ carrier at home, paused, on
	 * the server. @return the items it holds by good
	 */
	private static Map<Identifier, Integer> loadAuto(Session s, int id, Identifier not) {
		BlockPos stall = s.stall;
		return s.world.getServer().computeOnServer(server -> {
			Market home = market(server, stall);
			List<Identifier> goods = home.regularGoods().stream().filter(g -> {
				GoodsDefinition def = GoodsCatalog.get(g);
				return def != null && def.unit() == 1 && def.components().isEmpty() && !g.equals(not);
			}).limit(3).toList();
			check(goods.size() == 3, "the market has only %d goods for the cargo", goods.size());
			Employee e = MarketRegistry.get(server).employees().stream().filter(x -> x.id() == id).findFirst().orElseThrow();
			check(e.phase() == Employee.Phase.HOME, "the おまかせ carrier is not at home (%s)", e.phase());
			set(e, "paused", true);
			@SuppressWarnings("unchecked")
			List<Object> cargo = (List<Object>) get(e, Employee.class, "cargo");
			@SuppressWarnings("unchecked")
			List<Object> back = (List<Object>) get(e, Employee.class, "returnCargo");
			cargo.clear();
			back.clear();
			Map<Identifier, Integer> held = new LinkedHashMap<>();
			try {
				var make = Employee.CargoLine.class.getDeclaredConstructor(Identifier.class, int.class, long.class);
				make.setAccessible(true);
				cargo.add(make.newInstance(goods.get(0), 4, 40L));
				cargo.add(make.newInstance(goods.get(1), 3, 30L));
				back.add(make.newInstance(goods.get(2), 2, 20L));
			} catch (ReflectiveOperationException ex) {
				throw new AssertionError("cannot make a cargo line", ex);
			}
			held.put(goods.get(0), 4);
			held.put(goods.get(1), 3);
			held.merge(goods.get(2), 2, Integer::sum);
			set(e, "stock", 7);
			set(e, "cargoCost", 70L);
			MarketRegistry.get(server).setDirty();
			return held;
		});
	}

	/** Items of the employee's cargo ({@code "cargo"}) or return cargo ({@code "returnCargo"}) by good, read on the server. */
	private static Map<Identifier, Integer> lines(Session s, int id, String field) {
		return s.world.getServer().computeOnServer(server -> {
			Employee e = MarketRegistry.get(server).employees().stream().filter(x -> x.id() == id).findFirst().orElseThrow();
			Map<Identifier, Integer> items = new LinkedHashMap<>();
			for (Object line : (List<?>) get(e, Employee.class, field)) {
				Identifier g = (Identifier) get(line, "goods");
				items.merge(g, (Integer) get(line, "units") * Math.max(1, GoodsCatalog.get(g).unit()), Integer::sum);
			}
			return items;
		});
	}
}
