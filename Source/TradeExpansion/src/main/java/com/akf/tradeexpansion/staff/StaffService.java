package com.akf.tradeexpansion.staff;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.bank.BankAccounts;
import com.akf.tradeexpansion.bank.BankService;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.BulkDiscount;
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
import java.util.HashSet;
import java.util.List;
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
				if (destination == null || !linked(home, destination)) {
					setStatus(server, e, Employee.Status.NOT_DEALT, now, changes);
					e.nextTime = now + RETRY_TICKS;
					return;
				}
				int room = Math.max(0, e.quantity - e.stock);
				Deal deal = room > 0 ? buy(server, registry, home, e, room, now, changes) : Deal.none(Employee.Status.WORKING);
				if (deal.units() > 0) {
					e.stock += deal.units();
					e.purse -= deal.total();
					e.spent += deal.total();
					e.cargoCost += deal.total();
					e.tripCost += deal.total();
					log(e, now, "bought", e.goods, destination.id(), deal.items(), deal.total());
				}
				if (e.stock > 0) {
					depart(e, Employee.Phase.OUTBOUND, MarketRegistry.linkedDistance(home, destination), now);
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
					depart(e, Employee.Phase.RETURNING, 0, now);
					return;
				}
				Deal deal = sell(server, registry, destination, e, e.stock, now, changes);
				if (deal.units() > 0) {
					long cost = StaffMath.shareOf(e.cargoCost, deal.units(), e.stock);
					e.cargoCost -= cost;
					e.stock -= deal.units();
					e.purse += deal.total();
					e.earned += deal.total();
					e.tripRevenue += deal.total();
					e.sold += deal.units();
					log(e, now, "sold", e.goods, destination.id(), deal.items(), deal.total(), cost);
				}
				double distance = MarketRegistry.linkedDistance(home, destination);
				if (e.stock <= 0) {
					depart(e, Employee.Phase.RETURNING, distance, now);
					setStatus(server, e, Employee.Status.WORKING, now, changes);
				} else if (now - e.phaseStart >= Math.round(config().carrierMaxWaitDays * PriceModel.TICKS_PER_DAY)) {
					log(e, now, "leftover", e.goods, destination.id(), (long) e.stock * unit(e));
					depart(e, Employee.Phase.RETURNING, distance, now);
					setStatus(server, e, Employee.Status.WORKING, now, changes);
				} else {
					setStatus(server, e, deal.units() > 0 ? Employee.Status.WAIT_SELL : deal.reason(), now, changes);
					e.nextTime = now + RETRY_TICKS;
				}
			}
			case RETURNING -> {
				double distance = destination == null ? 0 : MarketRegistry.linkedDistance(home, destination);
				if (e.stock > 0 && accident(server, registry, e, distance, now, changes)) return;
				e.trips++;
				log(e, now, "returned", null, -1, e.trips, e.tripRevenue, e.tripCost);
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

	private static void depart(Employee e, Employee.Phase phase, double distance, long now) {
		e.phase = phase;
		e.phaseStart = now;
		e.nextTime = now + StaffMath.travelTicks(distance, config().carrierBlocksPerDay);
	}

	/**
	 * Rolls the accident of a loaded trip that just ended. On an accident part of the cargo is lost and the carrier rests
	 * ({@code staff.accidentRestDays}) or quits ({@code staff.quitChance}, the rest of the cargo is lost too).
	 *
	 * @return the carrier quit
	 */
	private static boolean accident(MinecraftServer server, MarketRegistry registry, Employee e, double distance, long now, Changes changes) {
		if (e.stock <= 0) return false;
		TradeExpansionConfig.Staff c = config();
		Random random = new Random(e.id * 0x9E3779B97F4A7C15L ^ e.nextTime ^ e.trips);
		double chance = StaffMath.accidentChance(distance, c.accidentBase, c.accidentPerThousandBlocks, c.accidentMax);
		if (random.nextDouble() >= chance) return false;
		double fraction = c.accidentLossMin + (c.accidentLossMax - c.accidentLossMin) * random.nextDouble();
		int lost = StaffMath.lostUnits(e.stock, fraction);
		long cost = StaffMath.shareOf(e.cargoCost, lost, e.stock);
		e.stock -= lost;
		e.cargoCost -= cost;
		e.lost += cost;
		log(e, now, "accident", e.goods, -1, (long) lost * unit(e), cost);
		Component line = describe(registry, e, e.log.getLast());
		if (random.nextDouble() < c.quitChance) {
			e.lost += e.cargoCost; // what is left of the cargo goes with the carrier
			e.stock = 0;
			e.cargoCost = 0;
			e.phase = Employee.Phase.QUIT;
			log(e, now, "quit", null, -1);
			notify(server, e, line.copy().append(" ").append(describe(registry, e, e.log.getLast())));
			setStatus(server, e, Employee.Status.QUIT, now, changes);
			changes.dirty = true;
			return true;
		}
		notify(server, e, line);
		e.nextTime = now + Math.round(c.accidentRestDays * PriceModel.TICKS_PER_DAY);
		setStatus(server, e, Employee.Status.RESTING, now, changes);
		return false;
	}

	/** One buy or sale of an employee. {@code reason}: why nothing (more) was traded. */
	private record Deal(int units, int items, long total, Employee.Status reason) {
		static Deal of(int units, long total, int unit) {
			return new Deal(units, units * unit, total, Employee.Status.WORKING);
		}

		static Deal none(Employee.Status reason) {
			return new Deal(0, 0, 0, reason);
		}
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

	/** Buys up to {@code maxUnits} at the market while the unit price is at most the buy limit and the purse lasts. */
	private static Deal buy(MinecraftServer server, MarketRegistry registry, Market market, Employee e, int maxUnits, long now, Changes changes) {
		ServerLevel level = server.getLevel(market.dimension());
		GoodsDefinition def = GoodsCatalog.get(e.goods);
		if (level == null || def == null) return Deal.none(Employee.Status.NOT_DEALT);
		MarketUpkeep.touch(level, market);
		if (!def.buy() || !regular(market, e.goods)) return Deal.none(Employee.Status.NOT_DEALT);
		GoodState state = market.good(e.goods);
		GoodParams p = market.params(e.goods);
		int max = Math.min(maxUnits, (int) Math.floor(state.stock()));
		if (max <= 0) return Deal.none(Employee.Status.WAIT_STOCK);
		int n = StaffMath.buyableWithin(p, state.pressure(), max, e.buyLimit);
		if (n <= 0) return Deal.none(Employee.Status.WAIT_PRICE);
		BulkDiscount bulk = TradeExpansionConfig.get().trade.bulkDiscount();
		n = PriceModel.maxAffordable(p, state.pressure(), e.purse, n, def.unit(), bulk);
		if (n <= 0) return Deal.none(Employee.Status.WAIT_FUNDS);
		long total = PriceModel.sumBuy(p, state.pressure(), n, def.unit(), bulk);
		PriceModel.applyBuy(p, state, n);
		state.setLastTradeGameTime(now);
		traded(registry, market, e, -n, now, changes);
		logTrade(e, "STAFF BUY", def, n, market, total);
		return Deal.of(n, total, def.unit());
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
		traded(registry, market, e, n, now, changes);
		logTrade(e, "STAFF SELL", def, n, market, total);
		return Deal.of(n, total, def.unit());
	}

	/** After a trade: the X change spreads to nearby markets (C-2) and everyone looking at them gets new prices. */
	private static void traded(MarketRegistry registry, Market market, Employee e, double deltaX, long now, Changes changes) {
		changes.markets.add(market.id());
		for (Market m : Propagation.apply(registry, market, e.goods, deltaX, now)) changes.markets.add(m.id());
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
			case "returned" -> Component.translatable(key, v.get(0), coins(v.get(1) - v.get(2)));
			case "unpaid" -> Component.translatable(key, coins(v.get(0)));
			case "hired" -> Component.translatable(key, coins(v.get(0)));
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
		Outcome outcome = switch (request.action()) {
			case LIST, DESTINATIONS -> new Outcome(null, 0, -1);
			default -> {
				Outcome o = act(player, registry, market, menu, request, now);
				if (o.result() == TradeResult.SUCCESS) registry.setDirty();
				yield o;
			}
		};
		List<StaffS2C.Destination> destinations = request.action() == StaffRequestC2S.Action.DESTINATIONS
				? destinations(player, registry, market, request.goods()) : List.of();
		Optional<Identifier> destinationGoods = request.action() == StaffRequestC2S.Action.DESTINATIONS ? Optional.of(request.goods()) : Optional.empty();
		boolean plain = request.action() == StaffRequestC2S.Action.LIST || request.action() == StaffRequestC2S.Action.DESTINATIONS;
		ServerPlayNetworking.send(player, new StaffS2C(market.id(), plain ? -1 : request.action().ordinal(), Optional.ofNullable(outcome.result()),
				outcome.amount(), outcome.subject(), info(player, market, now), views(registry, market, player.getUUID()), hireable(player, market, true, now),
				hireable(player, market, false, now), destinationGoods, destinations));
		if (!plain) BankService.sendAccount(player);
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
				c.accidentBase, c.accidentPerThousandBlocks, c.accidentMax, c.quitChance, c.carrierMaxWaitDays, c.carrierMaxDistance);
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
			Identifier item = def == null ? BuiltInRegistries.ITEM.getKey(Items.BARRIER) : def.item();
			int distance = home != null && destination != null ? (int) Math.round(MarketRegistry.linkedDistance(home, destination)) : 0;
			List<Component> log = new ArrayList<>();
			for (int i = e.log.size() - 1; i >= 0; i--) log.add(describe(registry, e, e.log.get(i)));
			views.add(new StaffS2C.View(e.id, e.role.ordinal(), e.home, home == null ? marketName(registry, e.home) : MarketNames.name(home),
					atHome(e, here, home), e.goods, item, def == null ? 1 : def.unit(), e.destination,
					destination == null ? (e.destination < 0 ? Component.empty() : marketName(registry, e.destination)) : MarketNames.name(destination),
					distance, e.quantity, e.buyLimit, e.sellLimit, e.purse, e.stock, e.phase.ordinal(), e.paused, e.status.ordinal(), e.phaseStart,
					e.nextTime, wage(e), e.earned, e.spent, e.wages, e.lost, e.trips, e.sold, log));
		}
		return views;
	}

	/** Money, goods and settings are handled at the home market; anywhere once the home market is gone. */
	private static boolean atHome(Employee e, Market here, Market home) {
		return home == null || e.home == here.id();
	}

	/**
	 * Markets a carrier hired here could take {@code goodsId} to: linked with this one (C-2), within
	 * {@code staff.carrierMaxDistance}, dealing in the good and buying it from players. Latest prices, nearest first.
	 */
	private static List<StaffS2C.Destination> destinations(ServerPlayer player, MarketRegistry registry, Market here, Identifier goodsId) {
		GoodsDefinition def = GoodsCatalog.get(goodsId);
		if (def == null || !def.sell()) return List.of();
		List<Market> candidates = new ArrayList<>();
		for (Market m : registry.linkedTo(here.dimension())) {
			if (m.id() == here.id() || m.good(goodsId) == null) continue;
			if (MarketRegistry.linkedDistance(here, m) <= config().carrierMaxDistance) candidates.add(m);
		}
		candidates.sort(Comparator.comparingDouble(m -> MarketRegistry.linkedDistance(here, m)));
		List<StaffS2C.Destination> list = new ArrayList<>();
		MinecraftServer server = player.level().getServer();
		for (Market m : candidates) {
			if (list.size() >= MAX_DESTINATIONS) break;
			ServerLevel level = server.getLevel(m.dimension());
			if (level == null) continue;
			MarketUpkeep.touch(level, m); // latest prices
			if (!regular(m, goodsId)) continue;
			GoodState state = m.good(goodsId);
			GoodParams p = m.params(goodsId);
			long price = PriceModel.sellCapacity(p, state) >= 1 ? PriceModel.sellPrice(p, state.pressure()) : -1;
			double sh = here.dimension().equals(Level.NETHER) && !m.dimension().equals(here.dimension()) ? 8 : 1;
			double sm = m.dimension().equals(Level.NETHER) && !m.dimension().equals(here.dimension()) ? 8 : 1;
			int dx = (int) Math.round(m.center().getX() * sm - here.center().getX() * sh);
			int dz = (int) Math.round(m.center().getZ() * sm - here.center().getZ() * sh);
			list.add(new StaffS2C.Destination(m.id(), MarketNames.name(m), dx, dz, (int) Math.round(MarketRegistry.linkedDistance(here, m)), price));
		}
		return list;
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
		GoodsDefinition def = GoodsCatalog.get(goodsId);
		if (def == null || !regular(market, goodsId) || !Reputation.canSee(market, player.getUUID(), goodsId, now)
				|| (carrier ? !def.buy() : !def.sell())) {
			return new Outcome(TradeResult.NOT_AVAILABLE);
		}
		long sellLimit = Math.max(1, request.sellLimit());
		Item item = BuiltInRegistries.ITEM.getValue(def.item());
		InventoryUtil.Match match = new InventoryUtil.Match(item, GoodsCatalog.patch(goodsId), false); // plain items only
		int units = 0;
		long destinationId = -1;
		if (carrier) {
			Market destination = registry.get(request.destination());
			if (destination == null || destination.id() == market.id() || !linked(market, destination)
					|| MarketRegistry.linkedDistance(market, destination) > c.carrierMaxDistance || !def.sell() || !regular(destination, goodsId)) {
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
					e.id, def.item(), market.id(), carrier ? " -> market#" + destinationId : " x" + units * def.unit(), CoinItem.formatCopper(fee));
		}
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
				if (e.stock <= 0 || def == null) return new Outcome(TradeResult.INSUFFICIENT_ITEMS);
				int items = e.stock * unit;
				InventoryUtil.give(player, match, items); // what does not fit is dropped
				e.stock = 0;
				e.cargoCost = 0;
				return new Outcome(TradeResult.SUCCESS, items, e.id);
			}
			case DISMISS -> {
				if (e.role == Employee.Role.CARRIER && e.phase != Employee.Phase.HOME && !e.quit() && config().enabled) return new Outcome(TradeResult.STAFF_AWAY);
				long amount = e.purse;
				if (amount > 0) pay(player, amount, request.useCard());
				if (e.stock > 0 && def != null) InventoryUtil.give(player, match, e.stock * unit);
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
