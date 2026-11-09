package com.akf.tradeexpansion.quest;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.advancement.TradeAdvancements;
import com.akf.tradeexpansion.bank.BankAccounts;
import com.akf.tradeexpansion.bank.BankService;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.core.QuestMath;
import com.akf.tradeexpansion.currency.WalletService;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.development.Development;
import com.akf.tradeexpansion.data.GoodsDefinition;
import com.akf.tradeexpansion.item.CoinItem;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketClock;
import com.akf.tradeexpansion.market.MarketFactory;
import com.akf.tradeexpansion.market.MarketNames;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.market.Reputation;
import com.akf.tradeexpansion.menu.MarketMenu;
import com.akf.tradeexpansion.network.CargoDeliverC2S;
import com.akf.tradeexpansion.network.QuestAcceptC2S;
import com.akf.tradeexpansion.network.QuestDeliverC2S;
import com.akf.tradeexpansion.network.QuestResultS2C;
import com.akf.tradeexpansion.registry.ModDataComponents;
import com.akf.tradeexpansion.registry.ModItems;
import com.akf.tradeexpansion.trade.InventoryUtil;
import com.akf.tradeexpansion.trade.TradeResult;
import com.akf.tradeexpansion.trade.TradeService;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * Delivery requests (design J): markets post up to {@code quest.maxPerMarket} requests, refilled every
 * {@code quest.refreshDays}; anyone may deliver (first come). Rewards are new money, tuned by the multipliers.
 * <p>
 * Transport requests (J-2, v0.35): up to {@code quest.transportPerMarket} more per market. Taking one pays a deposit
 * (the goods' value) and gives a cargo crate; delivering the crate at the destination market pays the reward
 * (growing with the distance) plus the deposit back, and raises the reputation with both markets.
 */
public final class QuestService {
	private QuestService() {
	}

	private static TradeExpansionConfig.Quest config() {
		return TradeExpansionConfig.get().quest;
	}

	/** Drops expired requests and refills the free slots when the refresh interval has passed. @return changed */
	public static boolean refresh(ServerLevel level, Market market, long now) {
		TradeExpansionConfig.Quest config = config();
		// M: players who accepted a request that expires undelivered lose reputation (J-2: the taker of a crate too).
		market.requests().stream().filter(r -> r.deadline() < now).forEach(r -> Reputation.onExpired(market, r, now));
		boolean changed = market.requests().removeIf(r -> r.deadline() < now);
		long interval = Math.round(config.refreshDays * PriceModel.TICKS_PER_DAY);
		if (market.lastRequestRefresh() != Long.MIN_VALUE && now - market.lastRequestRefresh() < interval) return changed;
		market.setLastRequestRefresh(now);
		Random random = new Random(MarketFactory.seed(level, market.center()) ^ now);
		// N-4: a request hall adds slots.
		boolean hall = market.hasFacility(com.akf.tradeexpansion.development.FacilityKind.QUEST_HALL);
		TradeExpansionConfig.Development dev = TradeExpansionConfig.get().development;
		int maxRequests = config.maxPerMarket + (hall ? dev.questHallExtraQuests : 0);
		int maxTransport = config.transportPerMarket + (hall ? dev.questHallExtraTransport : 0);
		while (market.requests().stream().filter(r -> !r.isTransport()).count() < maxRequests) {
			Request request = create(market, random, now);
			if (request == null) break;
			market.requests().add(request);
			changed = true;
		}
		if (maxTransport > 0) {
			MarketRegistry registry = MarketRegistry.get(level.getServer());
			while (market.requests().stream().filter(Request::isTransport).count() < maxTransport) {
				Request request = createTransport(registry, market, random, now);
				if (request == null) break;
				market.requests().add(request);
				changed = true;
			}
		}
		return changed;
	}

	/** Replaces every request (used by {@code market reroll}, design K). Crates already taken can still be delivered. */
	public static void reroll(ServerLevel level, Market market, long now) {
		market.requests().clear();
		market.setLastRequestRefresh(Long.MIN_VALUE);
		refresh(level, market, now);
	}

	/** Goods that can be a request: one item per unit, no tokens (auto_add), plain items only (no components). */
	private static boolean requestable(GoodsDefinition def) {
		return def.unit() == 1 && def.autoAdd().isEmpty() && def.components().isEmpty();
	}

	private static Set<Identifier> requestedGoods(Market market) {
		Set<Identifier> taken = new HashSet<>();
		market.requests().forEach(r -> taken.add(r.goods()));
		return taken;
	}

	private static Request create(Market market, Random random, long now) {
		TradeExpansionConfig.Quest config = config();
		Set<Identifier> taken = requestedGoods(market);
		// Prefer goods this market does not sell to players, so buying here and delivering here cannot profit.
		List<Identifier> preferred = new ArrayList<>();
		List<Identifier> others = new ArrayList<>();
		for (Map.Entry<Identifier, GoodsDefinition> e : GoodsCatalog.all().entrySet()) {
			GoodsDefinition def = e.getValue();
			if (taken.contains(e.getKey()) || !requestable(def)) continue;
			boolean soldHere = market.good(e.getKey()) != null && def.buy();
			(soldHere ? others : preferred).add(e.getKey());
		}
		List<Identifier> pool = preferred.isEmpty() ? others : preferred;
		if (pool.isEmpty()) return null;
		Identifier goodsId = pool.get(random.nextInt(pool.size()));
		GoodsDefinition def = GoodsCatalog.get(goodsId);
		int quantity = 1 + random.nextInt(Math.max(1, (int) Math.round(def.targetStock())));
		double value = market.basePriceFor(goodsId) * quantity;
		int difficulty = QuestMath.difficulty(value, thresholds());
		long reward = Math.max(1, QuestMath.reward(value, difficulty, multipliers()));
		long deadline = now + Math.round(config.durationDays * PriceModel.TICKS_PER_DAY);
		return new Request(market.takeRequestId(), goodsId, quantity, reward, deadline, difficulty);
	}

	/**
	 * J-2: a transport request. Destination: another market linked with this one (same dimension, or Overworld ↔ Nether
	 * when linked, C-2) between {@code quest.transportMinDistance} and {@code transportMaxDistance}. Goods: what this
	 * market sells, preferring goods the destination does not sell (the trip makes sense). Deposit = the goods' value here.
	 */
	private static Request createTransport(MarketRegistry registry, Market market, Random random, long now) {
		TradeExpansionConfig.Quest config = config();
		List<Market> destinations = new ArrayList<>();
		for (Market other : registry.linkedTo(market.dimension())) {
			if (other.id() == market.id()) continue;
			double distance = MarketRegistry.linkedDistance(market, other);
			if (distance >= config.transportMinDistance && distance <= config.transportMaxDistance) destinations.add(other);
		}
		if (destinations.isEmpty()) return null;
		destinations.sort((a, b) -> Long.compare(a.id(), b.id())); // the same seed gives the same choice
		Market destination = destinations.get(random.nextInt(destinations.size()));

		Set<Identifier> taken = requestedGoods(market);
		List<Identifier> preferred = new ArrayList<>();
		List<Identifier> others = new ArrayList<>();
		for (Identifier goodsId : market.regularGoods()) {
			GoodsDefinition def = GoodsCatalog.get(goodsId);
			if (def == null || taken.contains(goodsId) || !def.buy() || !requestable(def)) continue;
			boolean soldThere = destination.good(goodsId) != null;
			(soldThere ? others : preferred).add(goodsId);
		}
		List<Identifier> pool = preferred.isEmpty() ? others : preferred;
		if (pool.isEmpty()) return null;
		pool.sort(Identifier::compareTo);
		Identifier goodsId = pool.get(random.nextInt(pool.size()));
		GoodsDefinition def = GoodsCatalog.get(goodsId);
		int quantity = 1 + random.nextInt(Math.max(1, (int) Math.round(def.targetStock())));
		double value = market.basePriceFor(goodsId) * quantity;
		long deposit = Math.max(1, (long) Math.ceil(value - 1e-9));
		double multiplier = QuestMath.transportMultiplier(MarketRegistry.linkedDistance(market, destination), config.transportBaseMultiplier,
				config.transportPerThousandBlocks, config.transportMaxMultiplier);
		if (harbor(market) || harbor(destination)) multiplier *= Math.max(1.0, TradeExpansionConfig.get().development.harborRewardMultiplier); // v0.51
		long reward = Math.max(1, (long) Math.ceil(value * multiplier - 1e-9));
		int difficulty = QuestMath.difficulty(value, thresholds());
		long deadline = now + Math.round(config.durationDays * PriceModel.TICKS_PER_DAY);
		return new Request(market.takeRequestId(), goodsId, quantity, reward, deadline, difficulty, List.of(),
				Optional.of(new Request.Transport(destination.id(), deposit)));
	}

	private static int[] thresholds() {
		return config().difficultyThresholds.stream().mapToInt(Integer::intValue).toArray();
	}

	private static boolean harbor(Market market) {
		return market != null && market.hasFacility(com.akf.tradeexpansion.development.FacilityKind.HARBOR);
	}

	private static double[] multipliers() {
		return config().rewardMultipliers.stream().mapToDouble(Double::doubleValue).toArray();
	}

	/**
	 * M (v0.29): marks a request as accepted by the player (more reputation when delivered, less if it expires).
	 * J-2: for a transport request this takes the cargo crate (deposit paid).
	 */
	public static void accept(ServerPlayer player, QuestAcceptC2S request) {
		MarketRegistry registry = MarketRegistry.get(player.level().getServer());
		Market market = registry.get(request.marketId());
		if (market == null || !(player.containerMenu instanceof MarketMenu menu) || menu.marketId() != market.id() || !menu.stillValid(player)) return;
		long now = MarketClock.now(player.level());
		refresh(player.level(), market, now);
		List<Request> requests = market.requests();
		for (int i = 0; i < requests.size(); i++) {
			Request r = requests.get(i);
			if (r.id() != request.requestId()) continue;
			if (r.isTransport()) {
				takeCargo(player, registry, market, i, request.useCard(), now);
				return;
			}
			if (r.accepted().contains(player.getUUID())) return;
			requests.set(i, r.accept(player.getUUID()));
			registry.setDirty();
			TradeService.broadcast(player.level().getServer(), registry, Set.of(market.id()), now);
			return;
		}
		// The request is gone (expired, or delivered / taken by another player first).
		ServerPlayNetworking.send(player, new QuestResultS2C(TradeResult.NOT_AVAILABLE, QuestResultS2C.Kind.TAKE_CARGO, 0, 0));
	}

	/** J-2: pays the deposit and gives the cargo crate of {@code market.requests().get(index)}. */
	private static void takeCargo(ServerPlayer player, MarketRegistry registry, Market market, int index, boolean card, long now) {
		Request r = market.requests().get(index);
		Request.Transport transport = r.transport().orElseThrow();
		Market destination = registry.get(transport.destination());
		GoodsDefinition def = GoodsCatalog.get(r.goods());
		long deposit = transport.deposit();
		TradeResult result;
		if (r.taken()) {
			result = TradeResult.CARGO_TAKEN;
		} else if (destination == null || def == null) {
			result = TradeResult.NOT_AVAILABLE;
		} else if (player.isCreative() && !TradeExpansionConfig.get().trade.allowCreative) {
			result = TradeResult.CREATIVE_NOT_ALLOWED;
		} else if (card && !BankService.hasValidCard(player)) {
			result = TradeResult.CARD_INVALID;
		} else if (!Reputation.canDeliver(market, player.getUUID(), r, now)) {
			result = TradeResult.REPUTATION_TOO_LOW; // M: 3-star requests need reputation.hardQuestLevel
		} else {
			BankAccounts bank = BankService.accounts(player);
			long charge = card ? BankService.cardCharge(market, deposit) : deposit; // N-4: no fee with a bank building
			boolean affordable = card ? bank.balance(player.getUUID()) >= charge : WalletService.canPay(player, deposit);
			if (!affordable) {
				result = TradeResult.INSUFFICIENT_FUNDS;
			} else if (InventoryUtil.freeSpaceFor(player, ModItems.CARGO_CRATE, !card) < 1) {
				result = TradeResult.INVENTORY_FULL;
			} else {
				long reward = Reputation.reward(market, player.getUUID(), r, now); // fixed now, with the reputation bonus
				ItemStack crate = new ItemStack(ModItems.CARGO_CRATE);
				crate.set(ModDataComponents.CARGO, new Cargo(market.id(), MarketNames.name(market), destination.id(), MarketNames.name(destination),
						destination.center(), destination.dimension(), r.goods(), def.item(), r.quantity(), reward, deposit, r.deadline(),
						r.difficulty(), r.id()));
				if (card) {
					bank.add(player.getUUID(), -charge);
					give(player, crate);
				} else {
					long change = WalletService.take(player, deposit);
					give(player, crate);
					WalletService.give(player, change);
				}
				market.requests().set(index, r.accept(player.getUUID()));
				registry.setDirty();
				if (TradeExpansionConfig.get().log.trades) {
					TradeExpansion.LOGGER.info("{} TAKE CARGO {} x{} @market#{} -> market#{} deposit={} reward={}{}", player.getPlainTextName(),
							def.item(), r.quantity(), market.id(), destination.id(), CoinItem.formatCopper(deposit), CoinItem.formatCopper(reward),
							card ? " (card)" : "");
				}
				TradeService.broadcast(player.level().getServer(), registry, Set.of(market.id()), now);
				ServerPlayNetworking.send(player, new QuestResultS2C(TradeResult.SUCCESS, QuestResultS2C.Kind.TAKE_CARGO, deposit, 0));
				BankService.sendAccount(player);
				return;
			}
		}
		ServerPlayNetworking.send(player, new QuestResultS2C(result, QuestResultS2C.Kind.TAKE_CARGO, 0, 0));
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY); // dropped if the inventory is full
	}

	/**
	 * J-2: delivers every cargo crate for this market in the player's inventory. Pays reward + deposit (to the account
	 * with the card), raises the reputation with the source (as a delivered, accepted request) and with this market.
	 * Expired crates stay in the inventory (their deposit is lost).
	 */
	public static void deliverCargo(ServerPlayer player, CargoDeliverC2S request) {
		MarketRegistry registry = MarketRegistry.get(player.level().getServer());
		Market market = registry.get(request.marketId());
		TradeResult result;
		long paid = 0;
		int delivered = 0;
		if (market == null || !(player.containerMenu instanceof MarketMenu menu) || menu.marketId() != market.id()) {
			result = TradeResult.INVALID_REQUEST;
		} else if (!menu.stillValid(player)) {
			result = TradeResult.TOO_FAR;
		} else if (player.isCreative() && !TradeExpansionConfig.get().trade.allowCreative) {
			result = TradeResult.CREATIVE_NOT_ALLOWED;
		} else if (request.useCard() && !BankService.hasValidCard(player)) {
			result = TradeResult.CARD_INVALID;
		} else {
			long now = MarketClock.now(player.level());
			Inventory inventory = player.getInventory();
			List<Integer> slots = Cargo.slotsFor(player, market.id());
			Set<Long> changed = new HashSet<>();
			changed.add(market.id());
			boolean expired = false;
			for (int slot : slots) {
				Cargo cargo = Cargo.of(inventory.getItem(slot));
				if (cargo == null) continue;
				if (cargo.expired(now)) {
					expired = true;
					continue;
				}
				inventory.setItem(slot, ItemStack.EMPTY);
				long amount = cargo.reward() + cargo.deposit();
				if (request.useCard()) {
					BankService.accounts(player).add(player.getUUID(), amount);
				} else {
					WalletService.give(player, amount);
				}
				paid += amount;
				delivered++;
				Market source = registry.get(cargo.source());
				Reputation.onCargoDelivered(source, market, player.getUUID(), cargo, now);
				// N-2: the reward counts for both the source and this market.
				if (source != null) Development.onRequest(source, player, cargo.reward(), now);
				Development.onRequest(market, player, cargo.reward(), now);
				TradeAdvancements.event(player, TradeAdvancements.CARGO_DELIVERED); // R
				if (source != null) {
					source.requests().removeIf(r -> r.id() == cargo.requestId() && r.isTransport());
					changed.add(source.id());
				}
				if (TradeExpansionConfig.get().log.trades) {
					TradeExpansion.LOGGER.info("{} DELIVER CARGO {} x{} market#{} -> market#{} paid={}{}", player.getPlainTextName(), cargo.item(),
							cargo.quantity(), cargo.source(), market.id(), CoinItem.formatCopper(amount), request.useCard() ? " (card)" : "");
				}
			}
			inventory.setChanged();
			if (delivered > 0) {
				TradeAdvancements.checkMarket(player, market, now); // R
				TradeAdvancements.checkWealth(player);
				registry.setDirty();
				result = TradeResult.SUCCESS;
				TradeService.broadcast(player.level().getServer(), registry, changed, now);
			} else {
				result = expired ? TradeResult.CARGO_EXPIRED : TradeResult.NO_CARGO;
			}
		}
		ServerPlayNetworking.send(player, new QuestResultS2C(result, QuestResultS2C.Kind.DELIVER_CARGO, paid, delivered));
		BankService.sendAccount(player);
	}

	public static void deliver(ServerPlayer player, QuestDeliverC2S request) {
		MarketRegistry registry = MarketRegistry.get(player.level().getServer());
		Market market = registry.get(request.marketId());
		long reward = 0;
		TradeResult result;
		if (market == null || !(player.containerMenu instanceof MarketMenu menu) || menu.marketId() != market.id()) {
			result = TradeResult.INVALID_REQUEST;
		} else if (!menu.stillValid(player)) {
			result = TradeResult.TOO_FAR;
		} else if (player.isCreative() && !TradeExpansionConfig.get().trade.allowCreative) {
			result = TradeResult.CREATIVE_NOT_ALLOWED;
		} else if (request.useCard() && !BankService.hasValidCard(player)) {
			result = TradeResult.CARD_INVALID;
		} else {
			long now = MarketClock.now(player.level());
			refresh(player.level(), market, now);
			Request target = market.requests().stream().filter(r -> r.id() == request.requestId() && !r.isTransport()).findFirst().orElse(null);
			GoodsDefinition def = target == null ? null : GoodsCatalog.get(target.goods());
			Item item = def == null ? Items.AIR : BuiltInRegistries.ITEM.getValue(def.item());
			// Requests take exact items only (with the good's components), never damaged ones.
			InventoryUtil.Match match = def == null ? null : new InventoryUtil.Match(item, GoodsCatalog.patch(target.goods()), false);
			if (target == null || item == Items.AIR) {
				result = TradeResult.NOT_AVAILABLE; // expired or delivered by someone else
			} else if (!Reputation.canDeliver(market, player.getUUID(), target, now)) {
				result = TradeResult.REPUTATION_TOO_LOW; // M: 3-star requests need reputation.hardQuestLevel
			} else if (InventoryUtil.count(player, match, menu.sellSlot()) < target.quantity()) {
				result = TradeResult.INSUFFICIENT_ITEMS;
			} else {
				InventoryUtil.remove(player, match, menu.sellSlot(), target.quantity());
				reward = Reputation.reward(market, player.getUUID(), target, now); // M: reward bonus of the reputation level
				if (request.useCard()) {
					BankService.accounts(player).add(player.getUUID(), reward);
				} else {
					WalletService.give(player, reward);
				}
				Reputation.onDeliver(market, player.getUUID(), target, now);
				Development.onRequest(market, player, reward, now); // N-2
				TradeAdvancements.event(player, TradeAdvancements.QUEST_DELIVERED, target.difficulty()); // R
				TradeAdvancements.checkMarket(player, market, now);
				TradeAdvancements.checkWealth(player);
				market.requests().remove(target);
				registry.setDirty();
				result = TradeResult.SUCCESS;
				if (TradeExpansionConfig.get().log.trades) {
					TradeExpansion.LOGGER.info("{} DELIVER {} x{} @market#{} reward={}{}", player.getPlainTextName(), def.item(),
							target.quantity(), market.id(), CoinItem.formatCopper(reward), request.useCard() ? " (card)" : "");
				}
				TradeService.broadcast(player.level().getServer(), registry, Set.of(market.id()), now);
			}
		}
		ServerPlayNetworking.send(player, new QuestResultS2C(result, QuestResultS2C.Kind.DELIVER, reward, 0));
		BankService.sendAccount(player);
	}
}
