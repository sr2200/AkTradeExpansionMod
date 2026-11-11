package com.akf.tradeexpansion.staff;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.advancement.TradeAdvancements;
import com.akf.tradeexpansion.bank.BankAccounts;
import com.akf.tradeexpansion.bank.BankService;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.development.FacilityKind;
import com.akf.tradeexpansion.core.GoodParams;
import com.akf.tradeexpansion.core.GoodState;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.core.StaffMath;
import com.akf.tradeexpansion.currency.WalletService;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.GoodsDefinition;
import com.akf.tradeexpansion.item.CoinItem;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketClock;
import com.akf.tradeexpansion.market.MarketNames;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.market.MarketUpkeep;
import com.akf.tradeexpansion.market.Propagation;
import com.akf.tradeexpansion.market.Reputation;
import com.akf.tradeexpansion.menu.MarketMenu;
import com.akf.tradeexpansion.network.StaffRequestC2S;
import com.akf.tradeexpansion.network.StaffS2C;
import com.akf.tradeexpansion.trade.InventoryUtil;
import com.akf.tradeexpansion.trade.TradeResult;
import com.akf.tradeexpansion.trade.TradeService;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Hired staff (design Q, v0.48). Carriers buy a good at their home market while its price is at most their limit,
 * travel to the destination (time from the distance), sell there while the price is at least their limit and come
 * back; a loaded trip may end in an accident (part of the cargo lost, a rest, sometimes the carrier quits). Clerks sell
 * the goods left with them at their home market while the price is at least their limit. Trades use the same price
 * model as players (and move the prices, C-2) but give no reputation or development points.
 * <p>
 * Employees are moved on every second on the server with the market clock, so they also work while their owner is
 * away or offline (the server must be running). Wages are paid from the employee's purse once a day while it works.
 */
public final class StaffService {
	/** A carrier that could not buy or sell tries again after this long (0.1 days = 2 minutes). */
	static final long RETRY_TICKS = 2400;
	/** A clerk looks at the price this often (0.05 days = 1 minute). */
	static final long CLERK_CHECK_TICKS = 1200;
	/** Destinations listed for one good. */
	private static final int MAX_DESTINATIONS = 30;
	/** Steps one employee may take in one update (catching up after the clock jumped). */
	private static final int MAX_STEPS = 16;

	private static int tickCounter;

	private StaffService() {
	}

	private static TradeExpansionConfig.Staff config() {
		return TradeExpansionConfig.get().staff;
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (++tickCounter % 20 == 0) tick(server);
		});
	}

	/** What one update changed: markets traded with (for the snapshots) and whether anything is to be saved. */
	private static final class Changes {
		final Set<Long> markets = new HashSet<>();
		boolean dirty;
	}

	private static void tick(MinecraftServer server) {
		if (!config().enabled) return;
		MarketRegistry registry = MarketRegistry.get(server);
		if (registry.employees().isEmpty()) return;
		long now = MarketClock.now(server.overworld());
		Changes changes = new Changes();
		for (Employee e : List.copyOf(registry.employees())) update(server, registry, e, now, changes);
		if (changes.dirty || !changes.markets.isEmpty()) registry.setDirty();
		if (!changes.markets.isEmpty()) TradeService.broadcast(server, registry, changes.markets, now);
	}

	// ---- the work ----

	private static long wage(Employee e) {
		return e.role == Employee.Role.CARRIER ? config().carrierWagePerDay : config().clerkWagePerDay;
	}

	/** Working (and paid): not quit, a carrier not resting at home on purpose, a clerk with goods and not paused. */
	private static boolean active(Employee e) {
		if (e.phase == Employee.Phase.QUIT) return false;
		if (e.role == Employee.Role.CARRIER) return !(e.paused && e.phase == Employee.Phase.HOME);
		return !e.paused && e.stock > 0;
	}

	private static void update(MinecraftServer server, MarketRegistry registry, Employee e, long now, Changes changes) {
		if (e.phase == Employee.Phase.QUIT) return;
		Market home = registry.get(e.home);
		if (home == null) {
			// The home market was removed: the employee stops; its purse and goods can be taken at any market.
			e.phase = Employee.Phase.QUIT;
			setStatus(server, e, Employee.Status.HOME_GONE, now, changes);
			log(e, now, "home_gone", null, -1);
			notify(server, e, describe(registry, e, e.log.getLast()));
			changes.dirty = true;
			return;
		}
		if (!active(e)) {
			if (e.wageDue < now) e.wageDue = now; // no wage while idle; the next working day is paid when it starts
			setStatus(server, e, e.role == Employee.Role.CLERK && e.stock <= 0 && !e.paused ? Employee.Status.SOLD_OUT : Employee.Status.PAUSED, now, changes);
			return;
		}
		if (!payWages(e, now, changes)) {
			if (e.status != Employee.Status.UNPAID) {
				log(e, now, "unpaid", null, -1, wage(e));
				notify(server, e, describe(registry, e, e.log.getLast()));
			}
			setStatus(server, e, Employee.Status.UNPAID, now, changes);
			return;
		}
		if (e.status == Employee.Status.UNPAID || e.status == Employee.Status.PAUSED || e.status == Employee.Status.SOLD_OUT) {
			setStatus(server, e, Employee.Status.WORKING, now, changes);
		}
		for (int steps = 0; steps < MAX_STEPS && e.nextTime <= now && active(e); steps++) {
			if (e.role == Employee.Role.CLERK) clerkStep(server, registry, e, home, now, changes);
			else carrierStep(server, registry, e, home, now, changes);
			changes.dirty = true;
		}
	}

	/**
	 * Pays every wage due by {@code now} from the purse. When the purse is short the employee stops and the wage stays due
	 * "now" (days without work are not paid). @return all wages paid
	 */
	private static boolean payWages(Employee e, long now, Changes changes) {
		long wage = wage(e);
		if (wage <= 0) {
			if (e.wageDue <= now) e.wageDue = now + PriceModel.TICKS_PER_DAY;
			return true;
		}
		for (int days = 0; e.wageDue <= now && days < 60; days++) {
			if (e.purse < wage) {
				e.wageDue = now;
				return false;
			}
			e.purse -= wage;
			e.wages += wage;
			e.wageDue += PriceModel.TICKS_PER_DAY;
			changes.dirty = true;
		}
		if (e.wageDue <= now) e.wageDue = now + PriceModel.TICKS_PER_DAY; // more than 60 days behind: start over
		return true;
	}

	private static void clerkStep(MinecraftServer server, MarketRegistry registry, Employee e, Market home, long now, Changes changes) {
		Deal deal = sell(server, registry, home, e, e.stock, now, changes);
		if (deal.units() > 0) {
			e.stock -= deal.units();
			e.purse += deal.total();
			e.earned += deal.total();
			e.sold += deal.units();
			logSale(e, now, deal);
		}
		if (e.stock <= 0) {
			log(e, now, "clerk_sold_out", e.goods, -1);
			notify(server, e, describe(registry, e, e.log.getLast()));
			setStatus(server, e, Employee.Status.SOLD_OUT, now, changes);
		} else {
			setStatus(server, e, deal.units() > 0 ? Employee.Status.WORKING : deal.reason(), now, changes);
		}
		e.nextTime = now + CLERK_CHECK_TICKS;
	}

	/** A clerk sells a little at a time: sales of one day are kept on one log line. */
	private static void logSale(Employee e, long now, Deal deal) {
		long day = Math.floorDiv(now, PriceModel.TICKS_PER_DAY);
		Employee.LogEntry last = e.log.isEmpty() ? null : e.log.getLast();
		if (last != null && last.key().equals("clerk_sold") && Math.floorDiv(last.time(), PriceModel.TICKS_PER_DAY) == day) {
			e.log.removeLast();
			log(e, now, "clerk_sold", e.goods, -1, last.values().get(0) + deal.items(), last.values().get(1) + deal.total());
		} else {
			log(e, now, "clerk_sold", e.goods, -1, deal.items(), deal.total());
		}
	}

	private static void carrierStep(MinecraftServer server, MarketRegistry registry, Employee e, Market home, long now, Changes changes) {
		Market destination = registry.get(e.destination);
		switch (e.phase) {
			case HOME -> {
				sellReturnCargo(server, registry, home, e, now, changes); // v0.54: what is left of the return cargo
				if (destination == null || !linked(home, destination)) {
					setStatus(server, e, Employee.Status.NOT_DEALT, now, changes);
					e.nextTime = now + RETRY_TICKS;
					return;
				}
				// v0.48.1: no buying while goods left unsold last time are carried; they go first.
				int room = e.quantity - e.returnUnits(); // v0.54: unsold return cargo takes room too
				Deal deal = e.stock > 0 ? Deal.none(Employee.Status.WORKING) : room <= 0 ? Deal.none(Employee.Status.WAIT_SELL)
						: e.auto() ? autoBuy(server, registry, home, destination, e, room, now, changes)
						: carrierBuy(server, registry, home, destination, e, room, now, changes);
				if (deal.units() > 0) {
					e.stock += deal.units();
					e.purse -= deal.total();
					e.spent += deal.total();
					e.cargoCost += deal.total();
					if (e.auto()) log(e, now, "bought_mix", null, destination.id(), deal.kinds(), deal.items(), deal.total());
					else log(e, now, "bought", e.goods, destination.id(), deal.items(), deal.total());
				}
				// v0.54: with nothing to take there, it still goes when the return cargo should pay
				if (e.stock > 0 || returnTripPays(server, home, destination, e)) {
					depart(e, Employee.Phase.OUTBOUND, home, destination, now);
					setStatus(server, e, Employee.Status.WORKING, now, changes);
				} else {
					setStatus(server, e, deal.reason(), now, changes);
					e.nextTime = now + RETRY_TICKS;
				}
			}
			case OUTBOUND -> {
				double distance = destination == null ? 0 : MarketRegistry.linkedDistance(home, destination);
				if (accident(server, registry, e, distance, now, changes)) return;
				e.phase = Employee.Phase.AWAY;
				if (e.status == Employee.Status.RESTING) return; // rests first; selling starts at nextTime
				e.phaseStart = now;
				e.nextTime = now;
				setStatus(server, e, Employee.Status.WORKING, now, changes);
			}
			case AWAY -> {
				if (e.status == Employee.Status.RESTING) {
					e.phaseStart = now; // the waiting time at the destination starts after the rest
					setStatus(server, e, Employee.Status.WORKING, now, changes);
				}
				if (destination == null) {
					log(e, now, "destination_gone", e.goods, -1);
					depart(e, Employee.Phase.RETURNING, home, null, now);
					return;
				}
				Deal deal = e.auto() ? autoSell(server, registry, destination, e, now, changes) : carrierSell(server, registry, destination, e, now, changes);
				if (deal.units() > 0) {
					long cost = e.auto() ? deal.cost() : StaffMath.shareOf(e.cargoCost, deal.units(), e.stock);
					e.cargoCost -= cost;
					e.stock -= deal.units();
					e.purse += deal.total();
					e.earned += deal.total();
					e.tripRevenue += deal.total();
					e.tripCost += cost; // what the goods sold on this trip cost (the commission is on the difference)
					e.sold += deal.units();
					if (e.auto()) log(e, now, "sold_mix", null, destination.id(), deal.kinds(), deal.items(), deal.total(), cost);
					else log(e, now, "sold", e.goods, destination.id(), deal.items(), deal.total(), cost);
				}
				double distance = MarketRegistry.linkedDistance(home, destination);
				if (e.stock <= 0) {
					buyReturnCargo(server, registry, destination, home, e, now, changes);
					depart(e, Employee.Phase.RETURNING, home, destination, now);
					setStatus(server, e, Employee.Status.WORKING, now, changes);
				} else if (now - e.phaseStart >= Math.round(config().carrierMaxWaitDays * PriceModel.TICKS_PER_DAY)) {
					if (e.auto()) log(e, now, "leftover_mix", null, destination.id(), cargoItems(e));
					else log(e, now, "leftover", e.goods, destination.id(), (long) e.stock * unit(e));
					buyReturnCargo(server, registry, destination, home, e, now, changes);
					depart(e, Employee.Phase.RETURNING, home, destination, now);
					setStatus(server, e, Employee.Status.WORKING, now, changes);
				} else {
					setStatus(server, e, deal.units() > 0 ? Employee.Status.WAIT_SELL : deal.reason(), now, changes);
					e.nextTime = now + RETRY_TICKS;
				}
			}
			case RETURNING -> {
				double distance = destination == null ? 0 : MarketRegistry.linkedDistance(home, destination);
				if (e.loaded() && accident(server, registry, e, distance, now, changes)) return;
				sellReturnCargo(server, registry, home, e, now, changes); // v0.54: part of this round trip
				e.trips++;
				// v0.48.1: the carrier's pay is a share of the round trip's profit (nothing without profit).
				long profit = e.tripRevenue - e.tripCost;
				long commission = Math.min(e.purse, StaffMath.commission(profit, config().carrierCommission));
				e.purse -= commission;
				e.wages += commission;
				log(e, now, "returned", null, -1, e.trips, profit - commission, commission);
				ServerPlayer owner = profit - commission > 0 ? server.getPlayerList().getPlayer(e.owner) : null;
				if (owner != null) TradeAdvancements.event(owner, TradeAdvancements.STAFF_PROFIT, profit - commission); // R
				e.tripRevenue = 0;
				e.tripCost = 0;
				e.phase = Employee.Phase.HOME;
				e.phaseStart = now;
				if (e.status != Employee.Status.RESTING) {
					e.nextTime = now;
					setStatus(server, e, Employee.Status.WORKING, now, changes);
				}
			}
			default -> {
			}
		}
	}

	private static void depart(Employee e, Employee.Phase phase, Market home, Market destination, long now) {
		double distance = home == null || destination == null ? 0 : MarketRegistry.linkedDistance(home, destination);
		e.phase = phase;
		e.phaseStart = now;
		long ticks = StaffMath.travelTicks(distance, config().carrierBlocksPerDay);
		if (has(home, FacilityKind.HARBOR) || has(destination, FacilityKind.HARBOR)) { // v0.51: by water
			ticks = Math.max(StaffMath.MIN_TRAVEL_TICKS, Math.round(ticks * Math.clamp(TradeExpansionConfig.get().development.harborTravelMultiplier, 0.1, 1.0)));
		}
		e.nextTime = now + ticks;
	}

	private static boolean has(Market market, FacilityKind kind) {
		return market != null && market.hasFacility(kind);
	}

	/**
	 * Rolls the accident of a loaded trip that just ended. On an accident part of the cargo is lost and the carrier rests
	 * ({@code staff.accidentRestDays}) or quits ({@code staff.quitChance}, the rest of the cargo is lost too).
	 *
	 * @return the carrier quit
	 */
	private static boolean accident(MinecraftServer server, MarketRegistry registry, Employee e, double distance, long now, Changes changes) {
		if (!e.loaded()) return false;
		TradeExpansionConfig.Staff c = config();
		Random random = new Random(e.id * 0x9E3779B97F4A7C15L ^ e.nextTime ^ e.trips);
		Market home = registry.get(e.home);
		Market destination = e.destination < 0 ? null : registry.get(e.destination);
		double chance = StaffMath.accidentChance(distance, c.accidentBase, c.accidentPerThousandBlocks, c.accidentMax) * watchtower(home, destination);
		if (random.nextDouble() >= chance) return false;
		double fraction = c.accidentLossMin + (c.accidentLossMax - c.accidentLossMin) * random.nextDouble();
		// every good carried (and the return cargo, v0.54) loses the same share
		long lostItems = 0;
		long cost = 0;
		if (e.auto()) {
			Loss loss = lose(e.cargo, fraction);
			e.stock -= loss.units();
			e.cargoCost -= loss.cost();
			lostItems += loss.items();
			cost += loss.cost();
		} else if (e.stock > 0) {
			int lost = StaffMath.lostUnits(e.stock, fraction);
			long lineCost = StaffMath.shareOf(e.cargoCost, lost, e.stock);
			e.stock -= lost;
			e.cargoCost -= lineCost;
			lostItems += (long) lost * unit(e);
			cost += lineCost;
		}
		boolean returnLoss = !e.returnCargo.isEmpty();
		Loss back = lose(e.returnCargo, fraction);
		lostItems += back.items();
		cost += back.cost();
		e.lost += cost;
		if (e.auto() || returnLoss) log(e, now, "accident_mix", null, -1, lostItems, cost);
		else log(e, now, "accident", e.goods, -1, lostItems, cost);
		Component line = describe(registry, e, e.log.getLast());
		boolean spring = has(home, FacilityKind.HOT_SPRING); // v0.51: a hot spring at home
		TradeExpansionConfig.Development dev = TradeExpansionConfig.get().development;
		if (random.nextDouble() < c.quitChance * (spring ? Math.clamp(dev.hotSpringQuitMultiplier, 0.0, 1.0) : 1.0)) {
			e.lost += e.cargoCost + lineCosts(e.returnCargo); // what is left of the cargo goes with the carrier
			e.stock = 0;
			e.cargoCost = 0;
			e.cargo.clear();
			e.returnCargo.clear();
			e.phase = Employee.Phase.QUIT;
			log(e, now, "quit", null, -1);
			notify(server, e, line.copy().append(" ").append(describe(registry, e, e.log.getLast())));
			setStatus(server, e, Employee.Status.QUIT, now, changes);
			changes.dirty = true;
			return true;
		}
		notify(server, e, line);
		e.nextTime = now + Math.round(c.accidentRestDays * (spring ? Math.clamp(dev.hotSpringRestMultiplier, 0.0, 1.0) : 1.0) * PriceModel.TICKS_PER_DAY);
		setStatus(server, e, Employee.Status.RESTING, now, changes);
		return false;
	}

	/** What an accident took from some cargo lines. */
	private record Loss(int units, long items, long cost) {
	}

	/** Takes {@code fraction} of every line (rounded up, at least one unit, StaffMath.lostUnits) and drops empty lines. */
	private static Loss lose(List<Employee.CargoLine> lines, double fraction) {
		int units = 0;
		long items = 0;
		long cost = 0;
		for (Employee.CargoLine line : lines) {
			if (line.units <= 0) continue;
			int lost = StaffMath.lostUnits(line.units, fraction);
			long lineCost = StaffMath.shareOf(line.cost, lost, line.units);
			line.units -= lost;
			line.cost -= lineCost;
			units += lost;
			items += (long) lost * unitOf(line.goods);
			cost += lineCost;
		}
		lines.removeIf(l -> l.units <= 0);
		return new Loss(units, items, cost);
	}

	private static long lineCosts(List<Employee.CargoLine> lines) {
		long cost = 0;
		for (Employee.CargoLine line : lines) cost += line.cost;
		return cost;
	}

	/** One buy or sale of an employee. {@code reason}: why nothing (more) was traded. */
	private record Deal(int units, int items, long total, long cost, int kinds, Employee.Status reason) {
		static Deal of(int units, long total, int unit) {
			return new Deal(units, units * unit, total, 0, 1, Employee.Status.WORKING);
		}

		static Deal none(Employee.Status reason) {
			return new Deal(0, 0, 0, 0, 0, reason);
		}
	}

	private static int unitOf(Identifier goodsId) {
		GoodsDefinition def = GoodsCatalog.get(goodsId);
		return def == null ? 1 : Math.max(1, def.unit());
	}

	/** Items an automatic carrier carries (all goods). */
	static long cargoItems(Employee e) {
		long items = 0;
		for (Employee.CargoLine line : e.cargo) items += (long) line.units * unitOf(line.goods);
		return items;
	}

	private static int unit(Employee e) {
		GoodsDefinition def = GoodsCatalog.get(e.goods);
		return def == null ? 1 : Math.max(1, def.unit());
	}

	/** A good the market deals in openly: not a token (auto_add) and not today's special (D-5), whose shelf changes daily. */
	private static boolean regular(Market market, Identifier goodsId) {
		GoodsDefinition def = GoodsCatalog.get(goodsId);
		return def != null && def.autoAdd().isEmpty() && market.good(goodsId) != null && market.params(goodsId) != null && !market.isSpecial(goodsId);
	}

	/** v0.48.1: a carrier's prices at a market: the spread × {@code staff.carrierSpreadMultiplier} (wholesale). */
	private static GoodParams carrierParams(Market market, Identifier goodsId) {
		GoodParams p = market.params(goodsId);
		return p == null ? null : StaffMath.withSpread(p, config().carrierSpreadMultiplier);
	}

	private static double accidentChance(Market home, Market destination) {
		TradeExpansionConfig.Staff c = config();
		double distance = MarketRegistry.linkedDistance(home, destination);
		return StaffMath.accidentChance(distance, c.accidentBase, c.accidentPerThousandBlocks, c.accidentMax) * watchtower(home, destination);
	}

	/** v0.51: a watchtower at either end makes accidents rarer. */
	private static double watchtower(Market home, Market destination) {
		return has(home, FacilityKind.WATCHTOWER) || has(destination, FacilityKind.WATCHTOWER)
				? Math.clamp(TradeExpansionConfig.get().development.watchtowerAccidentMultiplier, 0.0, 1.0) : 1.0;
	}

	/** Mean share of the cargo lost in an accident. */
	private static double lostShare() {
		return (config().accidentLossMin + config().accidentLossMax) / 2;
	}

	/** Brings both markets up to date; {@code false} when one cannot be reached (its dimension is not loaded). */
	private static boolean touch(MinecraftServer server, Market... markets) {
		for (Market m : markets) {
			ServerLevel level = server.getLevel(m.dimension());
			if (level == null) return false;
			MarketUpkeep.touch(level, m);
		}
		return true;
	}

	/**
	 * What a carrier would buy at {@code home} for {@code destination} now: one unit after another while the price is at
	 * most {@code maxPrice}, within {@code funds}, and the unit should sell for more at the destination (its state now,
	 * the units before it sold, the accident risk counted). {@code null} when the good cannot go this way.
	 */
	private static StaffMath.BuyPlan plan(Market home, Market destination, Identifier goodsId, int maxUnits, long maxPrice, long funds) {
		GoodsDefinition def = GoodsCatalog.get(goodsId);
		if (def == null || !def.buy() || !def.sell() || !regular(home, goodsId) || !regular(destination, goodsId)) return null;
		GoodState a = home.good(goodsId);
		GoodState b = destination.good(goodsId);
		int max = Math.min(maxUnits, (int) Math.floor(a.stock()));
		if (max <= 0) return StaffMath.BuyPlan.NONE;
		double keep = 1 - accidentChance(home, destination) * lostShare();
		return StaffMath.planBuy(carrierParams(home, goodsId), a.pressure(), carrierParams(destination, goodsId), b.pressure(), max, maxPrice, funds,
				config().carrierPriceImpact, keep);
	}

	/** v0.48.1: the carrier buys only what should sell for more at its destination (and within its limit and purse). */
	private static Deal carrierBuy(MinecraftServer server, MarketRegistry registry, Market home, Market destination, Employee e, int capacity, long now,
			Changes changes) {
		GoodsDefinition def = GoodsCatalog.get(e.goods);
		if (def == null || !touch(server, home, destination)) return Deal.none(Employee.Status.NOT_DEALT);
		StaffMath.BuyPlan plan = plan(home, destination, e.goods, capacity, e.buyLimit, e.purse);
		if (plan == null) return Deal.none(Employee.Status.NOT_DEALT);
		GoodState state = home.good(e.goods);
		if (plan.units() <= 0) {
			if (Math.floor(state.stock()) < 1) return Deal.none(Employee.Status.WAIT_STOCK);
			long price = PriceModel.buyPrice(carrierParams(home, e.goods), state.pressure());
			return Deal.none(price > e.buyLimit ? Employee.Status.WAIT_PRICE : price > e.purse ? Employee.Status.WAIT_FUNDS : Employee.Status.NO_MARGIN);
		}
		GoodParams p = home.params(e.goods);
		double step = config().carrierPriceImpact;
		state.setPressure(p.clampPressure(state.pressure() - plan.units() * step));
		state.setStock(state.stock() - plan.units());
		state.setLastTradeGameTime(now);
		traded(registry, home, e.goods, -plan.units() * step, now, changes);
		logTrade(e, "STAFF BUY", def, plan.units(), home, plan.total());
		return Deal.of(plan.units(), plan.total(), def.unit());
	}

	/** v0.48.1: the carrier sells at wholesale prices while the price is at least its limit and above what the goods cost. */
	private static Deal carrierSell(MinecraftServer server, MarketRegistry registry, Market market, Employee e, long now, Changes changes) {
		GoodsDefinition def = GoodsCatalog.get(e.goods);
		if (def == null || e.stock <= 0 || !touch(server, market)) return Deal.none(Employee.Status.NOT_DEALT);
		if (!def.sell() || !regular(market, e.goods)) return Deal.none(Employee.Status.NOT_DEALT);
		GoodState state = market.good(e.goods);
		GoodParams p = market.params(e.goods);
		int max = Math.min(e.stock, PriceModel.sellCapacity(p, state));
		if (max <= 0) return Deal.none(Employee.Status.WAIT_SELL);
		long minPrice = Math.max(e.sellLimit, Math.floorDiv(e.cargoCost, e.stock) + 1); // never below the cost
		double step = config().carrierPriceImpact;
		StaffMath.SellPlan plan = StaffMath.planSell(carrierParams(market, e.goods), state.pressure(), max, minPrice, step);
		if (plan.units() <= 0) return Deal.none(Employee.Status.WAIT_SELL);
		state.setPressure(p.clampPressure(state.pressure() + plan.units() * step));
		state.setStock(state.stock() + plan.units());
		state.setLastTradeGameTime(now);
		traded(registry, market, e.goods, plan.units() * step, now, changes);
		logTrade(e, "STAFF SELL", def, plan.units(), market, plan.total());
		return Deal.of(plan.units(), plan.total(), def.unit());
	}

	/** Sells up to {@code maxUnits} at the market while the unit price is at least the sell limit and it takes them. */
	private static Deal sell(MinecraftServer server, MarketRegistry registry, Market market, Employee e, int maxUnits, long now, Changes changes) {
		ServerLevel level = server.getLevel(market.dimension());
		GoodsDefinition def = GoodsCatalog.get(e.goods);
		if (level == null || def == null) return Deal.none(Employee.Status.NOT_DEALT);
		MarketUpkeep.touch(level, market);
		if (!def.sell() || !regular(market, e.goods)) return Deal.none(Employee.Status.NOT_DEALT);
		GoodState state = market.good(e.goods);
		GoodParams p = market.params(e.goods);
		int max = Math.min(maxUnits, PriceModel.sellCapacity(p, state));
		if (max <= 0) return Deal.none(Employee.Status.WAIT_SELL);
		int n = StaffMath.sellableWithin(p, state.pressure(), max, e.sellLimit);
		if (n <= 0) return Deal.none(Employee.Status.WAIT_SELL);
		long total = PriceModel.sumSell(p, state.pressure(), n);
		PriceModel.applySell(p, state, n);
		state.setLastTradeGameTime(now);
		traded(registry, market, e.goods, n, now, changes);
		logTrade(e, "STAFF SELL", def, n, market, total);
		return Deal.of(n, total, def.unit());
	}

	/** v0.48.1: what an automatic carrier would take on one trip: several goods, the most profitable first. */
	private record AutoPlan(List<Identifier> goods, List<StaffMath.BuyPlan> plans, int units, long items, long total, long profit) {
		int kinds() {
			return goods.size();
		}

		static final AutoPlan NONE = new AutoPlan(List.of(), List.of(), 0, 0, 0, 0);
	}

	/**
	 * Plans an automatic carrier's purchase at {@code home} for {@code destination}: every good that should sell for more
	 * there (expected profit &gt; 0), the best first, sharing {@code capacity} units and {@code funds}. Goods are
	 * independent, so planning one after another on the states now is the same as buying them one after another.
	 */
	private static AutoPlan autoPlan(Market home, Market destination, int capacity, long funds) {
		record Option(Identifier goods, long profit) {
		}
		List<Option> options = new ArrayList<>();
		for (Identifier goodsId : home.goods().keySet()) {
			if (home.bonusSpecials().contains(goodsId)) continue; // shown only to some players
			StaffMath.BuyPlan plan = plan(home, destination, goodsId, capacity, Long.MAX_VALUE, Long.MAX_VALUE);
			if (plan == null || plan.units() <= 0) continue;
			long profit = expectedProfit(home, destination, plan);
			if (profit > 0) options.add(new Option(goodsId, profit));
		}
		options.sort(Comparator.comparingLong((Option o) -> -o.profit()).thenComparing(Option::goods));
		List<Identifier> goods = new ArrayList<>();
		List<StaffMath.BuyPlan> plans = new ArrayList<>();
		int room = capacity;
		long left = funds;
		int units = 0;
		long items = 0;
		long total = 0;
		long profit = 0;
		for (Option o : options) {
			if (room <= 0) break;
			StaffMath.BuyPlan plan = plan(home, destination, o.goods(), room, Long.MAX_VALUE, left);
			if (plan == null || plan.units() <= 0) continue;
			goods.add(o.goods());
			plans.add(plan);
			room -= plan.units();
			left -= plan.total();
			units += plan.units();
			items += (long) plan.units() * unitOf(o.goods());
			total += plan.total();
			profit += expectedProfit(home, destination, plan);
		}
		return new AutoPlan(goods, plans, units, items, total, profit);
	}

	/** v0.48.1: an automatic carrier buys everything that should sell for more at its destination. */
	private static Deal autoBuy(MinecraftServer server, MarketRegistry registry, Market home, Market destination, Employee e, int capacity, long now,
			Changes changes) {
		if (!touch(server, home, destination)) return Deal.none(Employee.Status.NOT_DEALT);
		AutoPlan plan = autoPlan(home, destination, capacity, e.purse);
		if (plan.units() <= 0) {
			boolean someProfit = autoPlan(home, destination, capacity, Long.MAX_VALUE).units() > 0;
			return Deal.none(someProfit ? Employee.Status.WAIT_FUNDS : Employee.Status.NO_MARGIN);
		}
		take(registry, home, plan, e.cargo, e, now, changes);
		return new Deal(plan.units(), (int) plan.items(), plan.total(), 0, plan.kinds(), Employee.Status.WORKING);
	}

	/** Buys the goods of {@code plan} at {@code market} (wholesale, moving its prices) into {@code lines}. */
	private static void take(MarketRegistry registry, Market market, AutoPlan plan, List<Employee.CargoLine> lines, Employee e, long now, Changes changes) {
		double step = config().carrierPriceImpact;
		for (int i = 0; i < plan.kinds(); i++) {
			Identifier goodsId = plan.goods().get(i);
			StaffMath.BuyPlan p = plan.plans().get(i);
			GoodState state = market.good(goodsId);
			state.setPressure(market.params(goodsId).clampPressure(state.pressure() - p.units() * step));
			state.setStock(state.stock() - p.units());
			state.setLastTradeGameTime(now);
			changes.markets.add(market.id());
			for (Market m : Propagation.apply(registry, market, goodsId, -p.units() * step, now)) changes.markets.add(m.id());
			lines.add(new Employee.CargoLine(goodsId, p.units(), p.total()));
			GoodsDefinition def = GoodsCatalog.get(goodsId);
			if (def != null) logTrade(e, "STAFF BUY", def, p.units(), market, p.total());
		}
		changes.dirty = true;
	}

	/** v0.54: what a carrier at {@code market} would bring back to {@code home} (chosen like an automatic carrier's goods). */
	private static AutoPlan returnPlan(Market market, Market home, int capacity, long funds) {
		return config().carrierBackhaul && capacity > 0 ? autoPlan(market, home, capacity, funds) : AutoPlan.NONE;
	}

	/**
	 * v0.54: before leaving the destination, a carrier buys return cargo: goods that should sell for more at its home
	 * market, in the room its leftovers leave. None while return cargo from before is still carried.
	 */
	private static void buyReturnCargo(MinecraftServer server, MarketRegistry registry, Market market, Market home, Employee e, long now,
			Changes changes) {
		if (!config().carrierBackhaul || !e.returnCargo.isEmpty() || !touch(server, market, home)) return;
		AutoPlan plan = returnPlan(market, home, e.quantity - e.stock, e.purse);
		if (plan.units() <= 0) return;
		take(registry, market, plan, e.returnCargo, e, now, changes);
		e.purse -= plan.total();
		e.spent += plan.total();
		log(e, now, "bought_return", null, market.id(), plan.kinds(), plan.items(), plan.total());
	}

	/** v0.54: the return cargo is sold at the home market while the price is above what it cost; the rest waits there. */
	private static void sellReturnCargo(MinecraftServer server, MarketRegistry registry, Market home, Employee e, long now, Changes changes) {
		if (e.returnCargo.isEmpty()) return;
		Deal deal = sellLines(server, registry, home, e, e.returnCargo, now, changes);
		if (deal.units() <= 0) return;
		e.purse += deal.total();
		e.earned += deal.total();
		e.tripRevenue += deal.total();
		e.tripCost += deal.cost();
		e.sold += deal.units();
		log(e, now, "sold_mix", null, home.id(), deal.kinds(), deal.items(), deal.total(), deal.cost());
	}

	/** v0.54: an empty trip is worth it when the return cargo should pay (and none is carried yet). */
	private static boolean returnTripPays(MinecraftServer server, Market home, Market destination, Employee e) {
		if (!config().carrierBackhaul || !e.returnCargo.isEmpty() || !touch(server, home, destination)) return false;
		return returnPlan(destination, home, e.quantity, e.purse).profit() > 0;
	}

	/** v0.48.1: an automatic carrier sells each good while the price is above what it cost (and the market takes it). */
	private static Deal autoSell(MinecraftServer server, MarketRegistry registry, Market market, Employee e, long now, Changes changes) {
		return sellLines(server, registry, market, e, e.cargo, now, changes);
	}

	/** Sells every good of {@code lines} at {@code market} while the price is above what it cost; drops sold-out lines. */
	private static Deal sellLines(MinecraftServer server, MarketRegistry registry, Market market, Employee e, List<Employee.CargoLine> lines, long now,
			Changes changes) {
		if (!touch(server, market)) return Deal.none(Employee.Status.NOT_DEALT);
		double step = config().carrierPriceImpact;
		int units = 0;
		long items = 0;
		long total = 0;
		long cost = 0;
		int kinds = 0;
		for (Employee.CargoLine line : lines) {
			GoodsDefinition def = GoodsCatalog.get(line.goods);
			if (line.units <= 0 || def == null || !def.sell() || !regular(market, line.goods)) continue;
			GoodState state = market.good(line.goods);
			GoodParams p = market.params(line.goods);
			int max = Math.min(line.units, PriceModel.sellCapacity(p, state));
			if (max <= 0) continue;
			long minPrice = Math.floorDiv(line.cost, line.units) + 1; // never below the cost
			StaffMath.SellPlan plan = StaffMath.planSell(carrierParams(market, line.goods), state.pressure(), max, minPrice, step);
			if (plan.units() <= 0) continue;
			state.setPressure(p.clampPressure(state.pressure() + plan.units() * step));
			state.setStock(state.stock() + plan.units());
			state.setLastTradeGameTime(now);
			traded(registry, market, line.goods, plan.units() * step, now, changes);
			long lineCost = StaffMath.shareOf(line.cost, plan.units(), line.units);
			line.units -= plan.units();
			line.cost -= lineCost;
			units += plan.units();
			items += (long) plan.units() * Math.max(1, def.unit());
			total += plan.total();
			cost += lineCost;
			kinds++;
			logTrade(e, "STAFF SELL", def, plan.units(), market, plan.total());
		}
		lines.removeIf(l -> l.units <= 0);
		if (units <= 0) return Deal.none(Employee.Status.WAIT_SELL);
		return new Deal(units, (int) items, total, cost, kinds, Employee.Status.WORKING);
	}

	/** After a trade: the X change spreads to nearby markets (C-2) and everyone looking at them gets new prices. */
	private static void traded(MarketRegistry registry, Market market, Identifier goodsId, double deltaX, long now, Changes changes) {
		changes.markets.add(market.id());
		for (Market m : Propagation.apply(registry, market, goodsId, deltaX, now)) changes.markets.add(m.id());
		changes.dirty = true;
	}

	private static void logTrade(Employee e, String what, GoodsDefinition def, int units, Market market, long total) {
		if (!TradeExpansionConfig.get().log.trades) return;
		TradeExpansion.LOGGER.info("{} ({} #{}) {} {} x{} @market#{} total={}", e.ownerName, e.role.name().toLowerCase(java.util.Locale.ROOT), e.id,
				what, def.item(), units * def.unit(), market.id(), CoinItem.formatCopper(total));
	}

	private static boolean linked(Market a, Market b) {
		if (a.dimension().equals(b.dimension())) return true;
		return b.dimension().equals(MarketRegistry.linkedDimension(a.dimension()));
	}

	// ---- status, log, chat ----

	private static void setStatus(MinecraftServer server, Employee e, Employee.Status status, long now, Changes changes) {
		if (e.status == status) return;
		e.status = status;
		changes.dirty = true;
	}

	private static void log(Employee e, long now, String key, Identifier item, long market, long... values) {
		List<Long> list = new ArrayList<>(values.length);
		for (long v : values) list.add(v);
		e.addLog(new Employee.LogEntry(now, key, Optional.ofNullable(item), market, list));
	}

	/** Tells the owner in the chat when online ({@code staff.notifyChat}). */
	private static void notify(MinecraftServer server, Employee e, Component line) {
		if (!config().notifyChat) return;
		ServerPlayer owner = server.getPlayerList().getPlayer(e.owner);
		if (owner == null) return;
		owner.sendSystemMessage(Component.translatable("staff.ak_trade_expansion.chat", roleName(e.role), line).withStyle(ChatFormatting.GOLD));
	}

	static Component roleName(Employee.Role role) {
		return Component.translatable("staff.ak_trade_expansion.role." + role.name().toLowerCase(java.util.Locale.ROOT));
	}

	private static Component itemName(Identifier goodsId) {
		if (Employee.AUTO.equals(goodsId)) return Component.translatable("staff.ak_trade_expansion.auto");
		GoodsDefinition def = goodsId == null ? null : GoodsCatalog.get(goodsId);
		if (def == null) return Component.literal(goodsId == null ? "?" : goodsId.toString());
		return new ItemStack(BuiltInRegistries.ITEM.getValue(def.item())).getHoverName();
	}

	private static Component marketName(MarketRegistry registry, long id) {
		Market m = registry.get(id);
		return m == null ? Component.translatable("staff.ak_trade_expansion.market_gone") : MarketNames.name(m);
	}

	/** The text of a log line: {@code staff.ak_trade_expansion.log.<key>} with the item, the market and the values. */
	static Component describe(MarketRegistry registry, Employee e, Employee.LogEntry entry) {
		List<Long> v = entry.values();
		String key = "staff.ak_trade_expansion.log." + entry.key();
		Component item = itemName(entry.item().orElse(e.goods));
		Component market = marketName(registry, entry.market());
		return switch (entry.key()) {
			case "bought" -> Component.translatable(key, item, v.get(0), coins(v.get(1)), market);
			case "sold" -> Component.translatable(key, market, item, v.get(0), coins(v.get(1)), coins(v.get(1) - v.get(2)));
			case "clerk_sold" -> Component.translatable(key, item, v.get(0), coins(v.get(1)));
			case "accident" -> Component.translatable(key, item, v.get(0), coins(v.get(1)));
			case "leftover" -> Component.translatable(key, market, item, v.get(0));
			case "returned" -> Component.translatable(key, v.get(0), coins(v.get(1)), coins(v.get(2)));
			case "unpaid" -> Component.translatable(key, coins(v.get(0)));
			case "hired" -> Component.translatable(key, coins(v.get(0)));
			case "changed" -> entry.market() >= 0 ? Component.translatable(key, item, market) : Component.translatable(key + ".clerk", item);
			case "bought_mix" -> Component.translatable(key, v.get(0), v.get(1), coins(v.get(2)), market);
			case "bought_return" -> Component.translatable(key, market, v.get(0), v.get(1), coins(v.get(2)));
			case "sold_mix" -> Component.translatable(key, market, v.get(0), v.get(1), coins(v.get(2)), coins(v.get(2) - v.get(3)));
			case "accident_mix" -> Component.translatable(key, v.get(0), coins(v.get(1)));
			case "leftover_mix" -> Component.translatable(key, market, v.get(0));
			case "clerk_sold_out", "destination_gone" -> Component.translatable(key, item);
			default -> Component.translatable(key);
		};
	}

	private static String coins(long amount) {
		return amount < 0 ? "-" + CoinItem.formatCopper(-amount) : CoinItem.formatCopper(amount);
	}

	// ---- the staff tab ----

	public static void handle(ServerPlayer player, StaffRequestC2S request) {
		MarketRegistry registry = MarketRegistry.get(player.level().getServer());
		Market market = registry.get(request.marketId());
		if (market == null || !(player.containerMenu instanceof MarketMenu menu) || menu.marketId() != market.id()) return;
		long now = MarketUpkeep.touch(player.level(), market);
		StaffRequestC2S.Action action = request.action();
		boolean plain = action == StaffRequestC2S.Action.LIST || action == StaffRequestC2S.Action.DESTINATIONS || action == StaffRequestC2S.Action.GOODS;
		Outcome outcome = new Outcome(null, 0, -1);
		if (!plain) {
			outcome = act(player, registry, market, menu, request, now);
			if (outcome.result() == TradeResult.SUCCESS) registry.setDirty();
		}
		MinecraftServer server = player.level().getServer();
		List<StaffS2C.Destination> destinations = List.of();
		Optional<Identifier> destinationGoods = Optional.empty();
		if (action == StaffRequestC2S.Action.DESTINATIONS) {
			destinations = destinations(market, candidates(server, registry, market), request.goods());
			destinationGoods = Optional.of(request.goods());
		}
		List<Identifier> carrierGoods = hireable(player, market, true, now);
		List<Identifier> clerkGoods = hireable(player, market, false, now);
		List<StaffS2C.CarrierHint> carrierHints = List.of();
		List<StaffS2C.ClerkHint> clerkHints = List.of();
		boolean hints = action == StaffRequestC2S.Action.GOODS;
		if (hints) {
			List<Market> candidates = candidates(server, registry, market);
			Map<Long, AutoPlan> returns = returnPlans(market, candidates);
			List<StaffS2C.CarrierHint> list = new ArrayList<>();
			list.add(autoHint(market, candidates, returns)); // the automatic carrier first
			carrierGoods.forEach(g -> list.add(carrierHint(market, candidates, returns, g)));
			carrierHints = list;
			clerkHints = clerkGoods.stream().map(g -> clerkHint(market, g)).toList();
		}
		ServerPlayNetworking.send(player, new StaffS2C(market.id(), plain ? -1 : action.ordinal(), Optional.ofNullable(outcome.result()),
				outcome.amount(), outcome.subject(), info(player, market, now), views(registry, market, player.getUUID()), carrierGoods, clerkGoods,
				destinationGoods, destinations, hints, carrierHints, clerkHints));
		if (!plain) BankService.sendAccount(player);
	}

	/** Recommended lowest price of a new clerk: this share of the sell price now (v0.48.1). */
	public static final double CLERK_LIMIT_SHARE = 0.85;

	/**
	 * Markets a carrier hired at {@code here} could go to: linked with it (C-2), within {@code staff.carrierMaxDistance},
	 * nearest first, brought up to date (latest prices).
	 */
	private static List<Market> candidates(MinecraftServer server, MarketRegistry registry, Market here) {
		List<Market> list = new ArrayList<>();
		for (Market m : registry.linkedTo(here.dimension())) {
			if (m.id() == here.id() || MarketRegistry.linkedDistance(here, m) > config().carrierMaxDistance) continue;
			if (touch(server, m)) list.add(m);
		}
		list.sort(Comparator.comparingDouble(m -> MarketRegistry.linkedDistance(here, m)));
		return list;
	}

	/** Expected profit of the trip {@code plan} to {@code destination} (commission and accident risk counted). */
	private static long expectedProfit(Market here, Market destination, StaffMath.BuyPlan plan) {
		return StaffMath.expectedProfit(plan, accidentChance(here, destination), lostShare(), config().carrierCommission);
	}

	/** What a carrier would buy now for a trip to {@code destination}, with enough money and no price limit. */
	private static StaffMath.BuyPlan freePlan(Market here, Market destination, Identifier goodsId) {
		return plan(here, destination, goodsId, config().carrierCapacity, Long.MAX_VALUE, Long.MAX_VALUE);
	}

	/**
	 * Markets a carrier hired here could take {@code goodsId} to (dealing in it and buying it from players), with the
	 * wholesale prices now and what a round trip there would bring now. Nearest first; the client sorts by profit.
	 */
	private static List<StaffS2C.Destination> destinations(Market here, List<Market> candidates, Identifier goodsId) {
		if (Employee.AUTO.equals(goodsId)) return autoDestinations(here, candidates);
		GoodsDefinition def = GoodsCatalog.get(goodsId);
		if (def == null || !def.sell()) return List.of();
		List<StaffS2C.Destination> list = new ArrayList<>();
		Map<Long, AutoPlan> returns = returnPlans(here, candidates);
		GoodParams home = regular(here, goodsId) ? carrierParams(here, goodsId) : null;
		long buyPrice = home == null ? -1 : PriceModel.buyPrice(home, here.good(goodsId).pressure());
		for (Market m : candidates) {
			if (list.size() >= MAX_DESTINATIONS) break;
			if (!regular(m, goodsId)) continue;
			GoodState state = m.good(goodsId);
			GoodParams p = carrierParams(m, goodsId);
			long price = PriceModel.sellCapacity(p, state) >= 1 ? PriceModel.sellPrice(p, state.pressure()) : -1;
			StaffMath.BuyPlan plan = freePlan(here, m, goodsId);
			int units = plan == null ? 0 : plan.units();
			long profit = units > 0 ? expectedProfit(here, m, plan) : 0;
			long sellLimit = units > 0 ? (long) Math.ceil(plan.total() / (double) units) : Math.max(1, buyPrice);
			double sh = here.dimension().equals(Level.NETHER) && !m.dimension().equals(here.dimension()) ? 8 : 1;
			double sm = m.dimension().equals(Level.NETHER) && !m.dimension().equals(here.dimension()) ? 8 : 1;
			int dx = (int) Math.round(m.center().getX() * sm - here.center().getX() * sh);
			int dz = (int) Math.round(m.center().getZ() * sm - here.center().getZ() * sh);
			AutoPlan back = returns.getOrDefault(m.id(), AutoPlan.NONE);
			list.add(new StaffS2C.Destination(m.id(), MarketNames.name(m), dx, dz, (int) Math.round(MarketRegistry.linkedDistance(here, m)), buyPrice,
					price, units, profit, units > 0 ? plan.highestPrice() : Math.max(1, buyPrice), sellLimit, 1, (int) back.items(), back.kinds(),
					back.profit()));
		}
		return list;
	}

	/** v0.48.1: what an automatic carrier would take to each destination now (all goods together). */
	private static List<StaffS2C.Destination> autoDestinations(Market here, List<Market> candidates) {
		List<StaffS2C.Destination> list = new ArrayList<>();
		Map<Long, AutoPlan> returns = returnPlans(here, candidates);
		for (Market m : candidates) {
			if (list.size() >= MAX_DESTINATIONS) break;
			AutoPlan plan = autoPlan(here, m, config().carrierCapacity, Long.MAX_VALUE);
			AutoPlan back = returns.getOrDefault(m.id(), AutoPlan.NONE);
			double sh = here.dimension().equals(Level.NETHER) && !m.dimension().equals(here.dimension()) ? 8 : 1;
			double sm = m.dimension().equals(Level.NETHER) && !m.dimension().equals(here.dimension()) ? 8 : 1;
			int dx = (int) Math.round(m.center().getX() * sm - here.center().getX() * sh);
			int dz = (int) Math.round(m.center().getZ() * sm - here.center().getZ() * sh);
			list.add(new StaffS2C.Destination(m.id(), MarketNames.name(m), dx, dz, (int) Math.round(MarketRegistry.linkedDistance(here, m)), -1, 0,
					(int) plan.items(), plan.profit(), 0, 0, plan.kinds(), (int) back.items(), back.kinds(), back.profit()));
		}
		return list;
	}

	/**
	 * v0.54: the return cargo a carrier hired here would bring back from each candidate now (a full load: what it took
	 * there is sold). Empty with {@code staff.carrierBackhaul} off.
	 */
	private static Map<Long, AutoPlan> returnPlans(Market here, List<Market> candidates) {
		Map<Long, AutoPlan> plans = new HashMap<>();
		if (!config().carrierBackhaul) return plans;
		for (Market m : candidates) plans.put(m.id(), returnPlan(m, here, config().carrierCapacity, Long.MAX_VALUE));
		return plans;
	}

	/** v0.48.1: the destination where an automatic carrier would make the most now (v0.54: return cargo included). */
	private static StaffS2C.CarrierHint autoHint(Market here, List<Market> candidates, Map<Long, AutoPlan> returns) {
		StaffS2C.CarrierHint best = new StaffS2C.CarrierHint(Employee.AUTO, -1, Component.empty(), 0, 0);
		for (Market m : candidates) {
			AutoPlan plan = autoPlan(here, m, config().carrierCapacity, Long.MAX_VALUE);
			AutoPlan back = returns.getOrDefault(m.id(), AutoPlan.NONE);
			long profit = plan.profit() + back.profit();
			if (profit > best.profit()) {
				best = new StaffS2C.CarrierHint(Employee.AUTO, m.id(), MarketNames.name(m), (int) (plan.items() + back.items()), profit);
			}
		}
		return best;
	}

	/** v0.48.1: the destination where a carrier of {@code goodsId} would make the most now (v0.54: return cargo included). */
	private static StaffS2C.CarrierHint carrierHint(Market here, List<Market> candidates, Map<Long, AutoPlan> returns, Identifier goodsId) {
		StaffS2C.CarrierHint best = new StaffS2C.CarrierHint(goodsId, -1, Component.empty(), 0, 0);
		for (Market m : candidates) {
			StaffMath.BuyPlan plan = freePlan(here, m, goodsId);
			if (plan == null || plan.units() <= 0) continue;
			long profit = expectedProfit(here, m, plan) + returns.getOrDefault(m.id(), AutoPlan.NONE).profit();
			if (profit > best.profit()) best = new StaffS2C.CarrierHint(goodsId, m.id(), MarketNames.name(m), plan.units(), profit);
		}
		return best;
	}

	/**
	 * v0.48.1: a clerk of {@code goodsId} at {@code here} with the recommended limit ({@link #CLERK_LIMIT_SHARE} of the sell
	 * price now): what it would sell in the next day with plenty of goods (checks every {@link #CLERK_CHECK_TICKS}, the
	 * market recovering in between, B-3).
	 */
	private static StaffS2C.ClerkHint clerkHint(Market here, Identifier goodsId) {
		GoodParams p = here.params(goodsId);
		GoodState now = here.good(goodsId);
		GoodsDefinition def = GoodsCatalog.get(goodsId);
		if (p == null || now == null || def == null) return new StaffS2C.ClerkHint(goodsId, 1, 0, 0);
		long limit = Math.max(1, (long) Math.floor(PriceModel.sellPrice(p, now.pressure()) * CLERK_LIMIT_SHARE));
		GoodState s = new GoodState(now.priceFactor(), now.pressure(), now.stock(), 0);
		int held = config().clerkCapacity;
		int units = 0;
		long revenue = 0;
		double step = CLERK_CHECK_TICKS / (double) PriceModel.TICKS_PER_DAY;
		for (double t = 0; t < 1 - 1e-9 && held > 0; t += step) {
			StaffMath.SellPlan plan = StaffMath.planSell(p, s.pressure(), Math.min(held, PriceModel.sellCapacity(p, s)), limit, 1.0);
			if (plan.units() > 0) {
				PriceModel.applySell(p, s, plan.units());
				held -= plan.units();
				units += plan.units();
				revenue += plan.total();
			}
			PriceModel.recover(p, s, step, TradeExpansionConfig.get().recovery.restockMultiplier);
		}
		return new StaffS2C.ClerkHint(goodsId, limit, units * Math.max(1, def.unit()), revenue);
	}

	/** Goods a carrier (it buys them here) or a clerk (it sells them here) may be hired for at this market, in display order. */
	private static List<Identifier> hireable(ServerPlayer player, Market market, boolean carrier, long now) {
		List<Identifier> list = new ArrayList<>();
		for (Identifier goodsId : market.goods().keySet()) {
			GoodsDefinition def = GoodsCatalog.get(goodsId);
			if (def == null || !regular(market, goodsId) || !Reputation.canSee(market, player.getUUID(), goodsId, now)) continue;
			if (carrier ? def.buy() && def.sell() : def.sell()) list.add(goodsId);
		}
		return list;
	}

	private record Outcome(TradeResult result, long amount, int subject) {
		Outcome(TradeResult result) {
			this(result, 0, -1);
		}
	}

	private static StaffS2C.Info info(ServerPlayer player, Market market, long now) {
		TradeExpansionConfig.Staff c = config();
		boolean reputation = TradeExpansionConfig.get().reputation.enabled;
		return new StaffS2C.Info(c.enabled, c.maxPerPlayer, c.minReputationLevel, Reputation.level(market, player.getUUID(), now), reputation,
				c.carrierHireFee, c.clerkHireFee, c.carrierWagePerDay, c.clerkWagePerDay, c.carrierCapacity, c.clerkCapacity, c.carrierBlocksPerDay,
				c.accidentBase, c.accidentPerThousandBlocks, c.accidentMax, c.quitChance, c.carrierMaxWaitDays, c.carrierMaxDistance,
				c.carrierCommission, c.carrierBackhaul);
	}

	/** Every employee of {@code owner} (home market here first, then by id). */
	private static List<StaffS2C.View> views(MarketRegistry registry, Market here, UUID owner) {
		List<Employee> mine = new ArrayList<>(registry.employees().stream().filter(e -> e.owner.equals(owner)).toList());
		mine.sort(Comparator.comparing((Employee e) -> e.home != here.id()).thenComparingInt(e -> e.id));
		List<StaffS2C.View> views = new ArrayList<>();
		for (Employee e : mine) {
			Market home = registry.get(e.home);
			Market destination = e.destination < 0 ? null : registry.get(e.destination);
			GoodsDefinition def = GoodsCatalog.get(e.goods);
			Identifier item = e.auto() ? BuiltInRegistries.ITEM.getKey(Items.CHEST) : def == null ? BuiltInRegistries.ITEM.getKey(Items.BARRIER) : def.item();
			int distance = home != null && destination != null ? (int) Math.round(MarketRegistry.linkedDistance(home, destination)) : 0;
			List<Component> log = new ArrayList<>();
			for (int i = e.log.size() - 1; i >= 0; i--) log.add(describe(registry, e, e.log.get(i)));
			views.add(new StaffS2C.View(e.id, e.role.ordinal(), e.home, home == null ? marketName(registry, e.home) : MarketNames.name(home),
					atHome(e, here, home), e.goods, item, def == null ? 1 : def.unit(), e.destination,
					destination == null ? (e.destination < 0 ? Component.empty() : marketName(registry, e.destination)) : MarketNames.name(destination),
					distance, e.quantity, e.buyLimit, e.sellLimit, e.purse, e.auto() ? (int) cargoItems(e) : e.stock, e.phase.ordinal(), e.paused,
					e.status.ordinal(), e.phaseStart, e.nextTime, wage(e), e.earned, e.spent, e.wages, e.lost, e.trips, e.sold, log, e.auto(), e.cargo.size(),
					(int) lineItems(e.returnCargo)));
		}
		return views;
	}

	/** Money, goods and settings are handled at the home market; anywhere once the home market is gone. */
	private static boolean atHome(Employee e, Market here, Market home) {
		return home == null || e.home == here.id();
	}

	private static Employee find(MarketRegistry registry, UUID owner, int id) {
		for (Employee e : registry.employees()) {
			if (e.id == id && e.owner.equals(owner)) return e;
		}
		return null;
	}

	private static Outcome act(ServerPlayer player, MarketRegistry registry, Market market, MarketMenu menu, StaffRequestC2S request, long now) {
		if (!menu.stillValid(player)) return new Outcome(TradeResult.TOO_FAR);
		// With staff off, employees stand still; their purse and goods can still be taken (and they can be let go).
		boolean takingBack = request.action() == StaffRequestC2S.Action.WITHDRAW_MONEY || request.action() == StaffRequestC2S.Action.WITHDRAW_ITEMS
				|| request.action() == StaffRequestC2S.Action.DISMISS;
		if (!config().enabled && !takingBack) return new Outcome(TradeResult.NOT_AVAILABLE);
		if (player.isCreative() && !TradeExpansionConfig.get().trade.allowCreative) return new Outcome(TradeResult.CREATIVE_NOT_ALLOWED);
		if (request.useCard() && !BankService.hasValidCard(player)) return new Outcome(TradeResult.CARD_INVALID);
		return switch (request.action()) {
			case HIRE_CARRIER, HIRE_CLERK -> hire(player, registry, market, menu, request, now);
			default -> {
				Employee e = find(registry, player.getUUID(), request.employee());
				if (e == null) yield new Outcome(TradeResult.NOT_AVAILABLE);
				yield manage(player, registry, market, menu, e, request, now);
			}
		};
	}

	private static Outcome hire(ServerPlayer player, MarketRegistry registry, Market market, MarketMenu menu, StaffRequestC2S request, long now) {
		TradeExpansionConfig.Staff c = config();
		boolean carrier = request.action() == StaffRequestC2S.Action.HIRE_CARRIER;
		if (TradeExpansionConfig.get().reputation.enabled && Reputation.level(market, player.getUUID(), now) < c.minReputationLevel) {
			return new Outcome(TradeResult.REPUTATION_TOO_LOW);
		}
		long hired = registry.employees().stream().filter(e -> e.owner.equals(player.getUUID()) && !e.quit()).count();
		if (hired >= c.maxPerPlayer) return new Outcome(TradeResult.STAFF_LIMIT);
		Identifier goodsId = request.goods();
		boolean auto = carrier && Employee.AUTO.equals(goodsId); // v0.48.1: the carrier chooses its goods
		GoodsDefinition def = GoodsCatalog.get(goodsId);
		if (!auto && (def == null || !regular(market, goodsId) || !Reputation.canSee(market, player.getUUID(), goodsId, now)
				|| (carrier ? !def.buy() : !def.sell()))) {
			return new Outcome(TradeResult.NOT_AVAILABLE);
		}
		long sellLimit = Math.max(1, request.sellLimit());
		InventoryUtil.Match match = auto ? null : new InventoryUtil.Match(BuiltInRegistries.ITEM.getValue(def.item()), GoodsCatalog.patch(goodsId), false);
		int units = 0;
		long destinationId = -1;
		if (carrier) {
			Market destination = registry.get(request.destination());
			if (destination == null || destination.id() == market.id() || !linked(market, destination)
					|| MarketRegistry.linkedDistance(market, destination) > c.carrierMaxDistance
					|| !auto && (!def.sell() || !regular(destination, goodsId))) {
				return new Outcome(TradeResult.NOT_AVAILABLE);
			}
			destinationId = destination.id();
		} else {
			units = Math.clamp(request.quantity(), 1, c.clerkCapacity);
			if (InventoryUtil.count(player, match, menu.sellSlot()) < units * def.unit()) return new Outcome(TradeResult.INSUFFICIENT_ITEMS);
		}
		long fee = carrier ? c.carrierHireFee : c.clerkHireFee;
		if (!charge(player, market, fee, request.useCard())) return new Outcome(TradeResult.INSUFFICIENT_FUNDS);
		int quantity = carrier ? Math.clamp(request.quantity(), 1, c.carrierCapacity) : 0;
		Employee e = Employee.hire(registry.takeEmployeeId(), player.getUUID(), player.getPlainTextName(),
				carrier ? Employee.Role.CARRIER : Employee.Role.CLERK, market.id(), goodsId, destinationId, quantity,
				Math.max(1, request.buyLimit()), sellLimit, now, PriceModel.TICKS_PER_DAY);
		if (!carrier) {
			InventoryUtil.remove(player, match, menu.sellSlot(), units * def.unit());
			e.stock = units;
		}
		log(e, now, "hired", null, -1, fee);
		registry.employees().add(e);
		if (TradeExpansionConfig.get().log.trades) {
			TradeExpansion.LOGGER.info("{} HIRE {} #{} {} @market#{}{} fee={}", player.getPlainTextName(), e.role.name().toLowerCase(java.util.Locale.ROOT),
					e.id, auto ? goodsId : def.item(), market.id(), carrier ? " -> market#" + destinationId : " x" + units * def.unit(), CoinItem.formatCopper(fee));
		}
		TradeAdvancements.event(player, TradeAdvancements.STAFF_HIRED); // R
		return new Outcome(TradeResult.SUCCESS, fee, e.id);
	}

	private static Outcome manage(ServerPlayer player, MarketRegistry registry, Market market, MarketMenu menu, Employee e, StaffRequestC2S request,
			long now) {
		Market home = registry.get(e.home);
		boolean atHome = atHome(e, market, home);
		StaffRequestC2S.Action action = request.action();
		if (action == StaffRequestC2S.Action.PAUSE) {
			if (e.quit()) return new Outcome(TradeResult.NOT_AVAILABLE);
			e.paused = !e.paused;
			if (!e.paused && e.nextTime > now + RETRY_TICKS && e.phase == Employee.Phase.HOME) e.nextTime = now;
			return new Outcome(TradeResult.SUCCESS, 0, e.id);
		}
		if (!atHome) return new Outcome(TradeResult.STAFF_NOT_HOME);
		GoodsDefinition def = GoodsCatalog.get(e.goods);
		Item item = def == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(def.item());
		InventoryUtil.Match match = new InventoryUtil.Match(item, GoodsCatalog.patch(e.goods), false);
		int unit = def == null ? 1 : Math.max(1, def.unit());
		switch (action) {
			case CHANGE -> {
				return change(player, registry, market, menu, e, request, match, unit, now);
			}
			case CONFIGURE -> {
				if (e.quit()) return new Outcome(TradeResult.NOT_AVAILABLE);
				if (e.role == Employee.Role.CARRIER) {
					e.quantity = Math.clamp(request.quantity(), 1, config().carrierCapacity);
					e.buyLimit = Math.max(1, request.buyLimit());
				}
				e.sellLimit = Math.max(1, request.sellLimit());
				if (e.phase != Employee.Phase.OUTBOUND && e.phase != Employee.Phase.RETURNING && e.status != Employee.Status.RESTING) e.nextTime = now;
				return new Outcome(TradeResult.SUCCESS, 0, e.id);
			}
			case DEPOSIT_MONEY -> {
				if (e.quit() || request.amount() <= 0) return new Outcome(TradeResult.INVALID_REQUEST);
				if (!charge(player, market, request.amount(), request.useCard())) return new Outcome(TradeResult.INSUFFICIENT_FUNDS);
				e.purse += request.amount();
				return new Outcome(TradeResult.SUCCESS, request.amount(), e.id);
			}
			case WITHDRAW_MONEY -> {
				long amount = request.amount() <= 0 ? e.purse : Math.min(e.purse, request.amount());
				if (amount <= 0) return new Outcome(TradeResult.INSUFFICIENT_FUNDS);
				e.purse -= amount;
				pay(player, amount, request.useCard());
				return new Outcome(TradeResult.SUCCESS, amount, e.id);
			}
			case DEPOSIT_ITEMS -> {
				if (e.role != Employee.Role.CLERK || e.quit() || def == null) return new Outcome(TradeResult.NOT_AVAILABLE);
				int room = config().clerkCapacity - e.stock;
				int units = Math.min(room, PriceModel.units(InventoryUtil.count(player, match, menu.sellSlot()), unit));
				if (units <= 0) return new Outcome(room <= 0 ? TradeResult.STOCK_FULL : TradeResult.INSUFFICIENT_ITEMS);
				InventoryUtil.remove(player, match, menu.sellSlot(), units * unit);
				e.stock += units;
				if (!e.paused) e.nextTime = now;
				return new Outcome(TradeResult.SUCCESS, (long) units * unit, e.id);
			}
			case WITHDRAW_ITEMS -> {
				if (e.role == Employee.Role.CARRIER && e.phase != Employee.Phase.HOME && !e.quit() && config().enabled) return new Outcome(TradeResult.STAFF_AWAY);
				if (e.returnCargo.isEmpty() && (e.stock <= 0 || def == null && !e.auto())) return new Outcome(TradeResult.INSUFFICIENT_ITEMS);
				long items = giveCargo(player, e, match, unit); // what does not fit is dropped
				return new Outcome(TradeResult.SUCCESS, items, e.id);
			}
			case DISMISS -> {
				if (e.role == Employee.Role.CARRIER && e.phase != Employee.Phase.HOME && !e.quit() && config().enabled) return new Outcome(TradeResult.STAFF_AWAY);
				long amount = e.purse;
				if (amount > 0) pay(player, amount, request.useCard());
				if (e.loaded()) giveCargo(player, e, match, unit);
				registry.employees().remove(e);
				if (TradeExpansionConfig.get().log.trades) {
					TradeExpansion.LOGGER.info("{} DISMISS {} #{} paid back={}", player.getPlainTextName(), e.role.name().toLowerCase(java.util.Locale.ROOT),
							e.id, CoinItem.formatCopper(amount));
				}
				return new Outcome(TradeResult.SUCCESS, amount, e.id);
			}
			default -> {
				return new Outcome(TradeResult.INVALID_REQUEST);
			}
		}
	}

	/**
	 * v0.48.3: another good (a carrier: also another destination, or おまかせ) for an employee, at its home market. A carrier
	 * must be at home. The goods it holds come back to the player first; a clerk takes {@code request.quantity()} units of
	 * the new good (0: none yet). No fee.
	 *
	 * @param match the employee's present good (to give it back)
	 */
	private static Outcome change(ServerPlayer player, MarketRegistry registry, Market market, MarketMenu menu, Employee e, StaffRequestC2S request,
			InventoryUtil.Match match, int unit, long now) {
		if (e.quit()) return new Outcome(TradeResult.NOT_AVAILABLE);
		boolean carrier = e.role == Employee.Role.CARRIER;
		if (carrier && e.phase != Employee.Phase.HOME) return new Outcome(TradeResult.STAFF_AWAY);
		TradeExpansionConfig.Staff c = config();
		Identifier goodsId = request.goods();
		boolean auto = carrier && Employee.AUTO.equals(goodsId);
		GoodsDefinition def = GoodsCatalog.get(goodsId);
		if (!auto && (def == null || !regular(market, goodsId) || !Reputation.canSee(market, player.getUUID(), goodsId, now)
				|| (carrier ? !def.buy() || !def.sell() : !def.sell()))) {
			return new Outcome(TradeResult.NOT_AVAILABLE);
		}
		long destinationId = -1;
		if (carrier) {
			Market destination = registry.get(request.destination());
			if (destination == null || destination.id() == market.id() || !linked(market, destination)
					|| MarketRegistry.linkedDistance(market, destination) > c.carrierMaxDistance || !auto && !regular(destination, goodsId)) {
				return new Outcome(TradeResult.NOT_AVAILABLE);
			}
			destinationId = destination.id();
		}
		InventoryUtil.Match newMatch = auto ? null : new InventoryUtil.Match(BuiltInRegistries.ITEM.getValue(def.item()), GoodsCatalog.patch(goodsId), false);
		int newUnit = auto ? 1 : Math.max(1, def.unit());
		int units = 0;
		if (!carrier) {
			units = Math.clamp(request.quantity(), 0, c.clerkCapacity);
			int held = InventoryUtil.count(player, newMatch, menu.sellSlot());
			if (goodsId.equals(e.goods)) held += e.stock * unit; // the old goods come back first
			if (held < units * newUnit) return new Outcome(TradeResult.INSUFFICIENT_ITEMS);
		}
		long returned = e.loaded() ? giveCargo(player, e, match, unit) : 0; // what does not fit is dropped
		e.stock = 0; // goods of a good no longer defined cannot be given back; they do not become the new good
		e.cargoCost = 0;
		e.goods = goodsId;
		e.destination = destinationId;
		if (carrier) {
			e.quantity = Math.clamp(request.quantity(), 1, c.carrierCapacity);
			e.buyLimit = Math.max(1, request.buyLimit());
		}
		e.sellLimit = Math.max(1, request.sellLimit());
		if (units > 0) {
			InventoryUtil.remove(player, newMatch, menu.sellSlot(), units * newUnit);
			e.stock = units;
		}
		e.tripCost = 0;
		e.tripRevenue = 0;
		e.phaseStart = now;
		e.nextTime = now;
		e.status = Employee.Status.WORKING;
		log(e, now, "changed", null, destinationId);
		if (TradeExpansionConfig.get().log.trades) {
			TradeExpansion.LOGGER.info("{} CHANGE {} #{} -> {}{}", player.getPlainTextName(), e.role.name().toLowerCase(java.util.Locale.ROOT), e.id, goodsId,
					carrier ? " -> market#" + destinationId : " x" + units * newUnit);
		}
		return new Outcome(TradeResult.SUCCESS, returned, e.id);
	}

	/**
	 * Gives the employee's goods to the player (an automatic carrier's goods by kind, then the return cargo, v0.54) and
	 * empties them. Goods of a good no longer defined stay with the employee. @return items given
	 */
	private static long giveCargo(ServerPlayer player, Employee e, InventoryUtil.Match match, int unit) {
		long items = 0;
		if (e.auto()) {
			items += giveLines(player, e.cargo);
			e.stock = 0;
			e.cargoCost = 0;
		} else if (e.stock > 0 && match != null && match.item() != Items.AIR) {
			items = (long) e.stock * unit;
			InventoryUtil.give(player, match, (int) items);
			e.stock = 0;
			e.cargoCost = 0;
		}
		items += giveLines(player, e.returnCargo);
		return items;
	}

	/** Gives the goods of {@code lines} to the player and empties them. @return items given */
	private static long giveLines(ServerPlayer player, List<Employee.CargoLine> lines) {
		long items = 0;
		for (Employee.CargoLine line : lines) {
			GoodsDefinition d = GoodsCatalog.get(line.goods);
			if (d == null || line.units <= 0) continue;
			int n = line.units * Math.max(1, d.unit());
			InventoryUtil.give(player, new InventoryUtil.Match(BuiltInRegistries.ITEM.getValue(d.item()), GoodsCatalog.patch(line.goods), false), n);
			items += n;
		}
		lines.clear();
		return items;
	}

	/** Items in {@code lines}. */
	private static long lineItems(List<Employee.CargoLine> lines) {
		long items = 0;
		for (Employee.CargoLine line : lines) items += (long) line.units * unitOf(line.goods);
		return items;
	}

	/** Takes {@code amount} from the cash (wallets, then coins) or the account (card, with the fee, E-8). @return paid */
	private static boolean charge(ServerPlayer player, Market market, long amount, boolean card) {
		if (amount <= 0) return true;
		if (card) {
			BankAccounts bank = BankService.accounts(player);
			long charge = BankService.cardCharge(market, amount);
			if (bank.balance(player.getUUID()) < charge) return false;
			bank.add(player.getUUID(), -charge);
			return true;
		}
		if (!WalletService.canPay(player, amount)) return false;
		long change = WalletService.take(player, amount);
		WalletService.give(player, change);
		return true;
	}

	/** Pays {@code amount} into the wallet / coins, or the account with the card (E-8). */
	private static void pay(ServerPlayer player, long amount, boolean card) {
		if (card) BankService.accounts(player).add(player.getUUID(), amount);
		else WalletService.give(player, amount);
	}

	// ---- commands ----

	/** {@code /aktradeex staff}: one line per employee of the world. */
	public static List<Component> list(MinecraftServer server) {
		MarketRegistry registry = MarketRegistry.get(server);
		List<Component> lines = new ArrayList<>();
		for (Employee e : registry.employees()) {
			lines.add(Component.translatable("command.ak_trade_expansion.staff.line", e.id, e.ownerName, roleName(e.role), itemName(e.goods),
					marketName(registry, e.home), e.destination < 0 ? Component.literal("-") : marketName(registry, e.destination),
					Component.translatable("staff.ak_trade_expansion.status." + e.status.name().toLowerCase(java.util.Locale.ROOT)),
					CoinItem.formatCopper(e.purse), e.stock * unit(e)));
		}
		return lines;
	}

	/** {@code /aktradeex staff hurry}: every employee takes its next step now (test command). @return employees moved on */
	public static int hurry(MinecraftServer server) {
		MarketRegistry registry = MarketRegistry.get(server);
		long now = MarketClock.now(server.overworld());
		int n = 0;
		for (Employee e : registry.employees()) {
			if (e.quit()) continue;
			e.hurry(now);
			n++;
		}
		tick(server);
		registry.setDirty();
		return n;
	}
}
