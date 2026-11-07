package com.akf.tradeexpansion.development;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.bank.BankAccounts;
import com.akf.tradeexpansion.bank.BankService;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.DevelopmentMath;
import com.akf.tradeexpansion.data.FacilityCatalog;
import com.akf.tradeexpansion.data.FacilityDefinition;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.GoodsDefinition;
import com.akf.tradeexpansion.registry.ModBlocks;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Prediction;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import java.util.Map;
import com.akf.tradeexpansion.currency.WalletService;
import com.akf.tradeexpansion.item.CoinItem;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketClock;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.market.Reputation;
import com.akf.tradeexpansion.menu.MarketMenu;
import com.akf.tradeexpansion.network.DevelopmentRequestC2S;
import com.akf.tradeexpansion.network.DevelopmentResultS2C;
import com.akf.tradeexpansion.trade.TradeResult;
import com.akf.tradeexpansion.trade.TradeService;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;

/**
 * Development tab actions (design N-6). Runs on the server thread; checks the open screen, the distance to the stall,
 * the dimension and the amounts before changing anything.
 */
public final class DevelopmentService {
	/** Largest single coin donation (1e9 copper). */
	public static final long MAX_DONATION = 1_000_000_000L;

	private DevelopmentService() {
	}

	public static void handle(ServerPlayer player, DevelopmentRequestC2S request) {
		MarketRegistry registry = MarketRegistry.get(player.level().getServer());
		Market market = registry.get(request.marketId());
		long now = MarketClock.now(player.level());
		DevelopmentResultS2C result;
		if (market == null || !(player.containerMenu instanceof MarketMenu menu) || menu.marketId() != market.id()) {
			result = fail(TradeResult.INVALID_REQUEST, request);
		} else if (!menu.stillValid(player)) {
			result = fail(TradeResult.TOO_FAR, request);
		} else if (!Development.enabled(market)) {
			result = fail(TradeResult.NOT_AVAILABLE, request);
		} else if (player.isCreative() && !TradeExpansionConfig.get().trade.allowCreative) {
			result = fail(TradeResult.CREATIVE_NOT_ALLOWED, request);
		} else {
			result = switch (request.action()) {
				case DONATE_COINS -> donateCoins(player, market, request, now);
				case DONATE_ITEMS -> donateItems(player, menu, market, request, now);
				case BUILD -> build(player, market, request, now);
				case MARKER -> marker(player, request);
			};
		}
		if (result.result() == TradeResult.SUCCESS) {
			registry.setDirty();
			TradeService.broadcast(player.level().getServer(), registry, Set.of(market.id()), now);
		}
		ServerPlayNetworking.send(player, result);
		BankService.sendAccount(player);
	}

	private static DevelopmentResultS2C fail(TradeResult result, DevelopmentRequestC2S request) {
		return new DevelopmentResultS2C(result, request.action(), 0, 0);
	}

	/**
	 * N-2: the wanted materials of a facility not yet built (or destroyed) that the player carries, up to what it still
	 * wants, become development points (base price B of the market, or the entry's value, × count × materialMultiplier).
	 */
	private static DevelopmentResultS2C donateItems(ServerPlayer player, MarketMenu menu, Market market, DevelopmentRequestC2S request, long now) {
		Identifier id = Identifier.tryParse(request.facility());
		FacilityDefinition def = id == null ? null : FacilityCatalog.get(id);
		if (def == null) return fail(TradeResult.NOT_AVAILABLE, request);
		Facility existing = market.facilities().get(id);
		if (existing != null && existing.state() != Facility.State.DESTROYED) return fail(TradeResult.ALREADY_BUILT, request);
		Map<String, Integer> donated = market.donations(id);
		int items = 0;
		double points = 0;
		for (FacilityDefinition.Wanted wanted : def.wanted()) {
			int want = DevelopmentMath.acceptable(FacilityBuilder.required(market, id, wanted), donated.getOrDefault(wanted.key(), 0), Integer.MAX_VALUE);
			if (want <= 0) continue;
			int taken = take(player, menu.sellSlot(), wanted, want);
			if (taken <= 0) continue;
			donated.merge(wanted.key(), taken, Integer::sum);
			items += taken;
			points += DevelopmentMath.materialPoints(value(market, wanted), taken, Development.config().materialMultiplier);
		}
		if (items == 0) return fail(TradeResult.NOTHING_TO_DONATE, request);
		Development.add(market, player, points, now);
		Reputation.add(market, player.getUUID(), points * Development.config().reputationPerPoint, now);
		if (TradeExpansionConfig.get().log.trades) {
			TradeExpansion.LOGGER.info("{} DONATE {} items for {} @market#{} (+{} development)", player.getPlainTextName(), items, id, market.id(),
					(long) Math.floor(points));
		}
		return new DevelopmentResultS2C(TradeResult.SUCCESS, request.action(), items, (long) Math.floor(points));
	}

	/** Base price of a wanted item: the market's B when it is a plain good, otherwise the entry's value (tags too). */
	private static double value(Market market, FacilityDefinition.Wanted wanted) {
		if (wanted.item().isPresent()) {
			for (Map.Entry<Identifier, GoodsDefinition> e : GoodsCatalog.all().entrySet()) {
				GoodsDefinition g = e.getValue();
				if (g.item().equals(wanted.item().get()) && g.unit() == 1 && g.components().isEmpty() && g.autoAdd().isEmpty()) {
					return market.basePriceFor(e.getKey());
				}
			}
		}
		return wanted.value();
	}

	/** Removes up to {@code max} matching items: the sell slot first, then the inventory. @return how many */
	private static int take(ServerPlayer player, Container sellSlot, FacilityDefinition.Wanted wanted, int max) {
		int left = max;
		left -= takeFrom(sellSlot, 0, wanted, left);
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE && left > 0; slot++) left -= takeFrom(inventory, slot, wanted, left);
		inventory.setChanged();
		sellSlot.setChanged();
		return max - left;
	}

	private static int takeFrom(Container container, int slot, FacilityDefinition.Wanted wanted, int max) {
		ItemStack stack = container.getItem(slot);
		if (max <= 0 || !wanted.matches(stack)) return 0;
		int n = Math.min(max, stack.getCount());
		stack.shrink(n);
		if (stack.isEmpty()) container.setItem(slot, ItemStack.EMPTY);
		return n;
	}

	/** N-5: starts building a facility. */
	private static DevelopmentResultS2C build(ServerPlayer player, Market market, DevelopmentRequestC2S request, long now) {
		Identifier id = Identifier.tryParse(request.facility());
		if (id == null) return fail(TradeResult.INVALID_REQUEST, request);
		FacilityBuilder.Outcome outcome = FacilityBuilder.build(player, player.level(), market, id, now);
		if (outcome.reason() != null) player.sendSystemMessage(outcome.reason().copy().withStyle(net.minecraft.ChatFormatting.YELLOW));
		return new DevelopmentResultS2C(outcome.result(), request.action(), 0, outcome.cost());
	}

	/** N-5: one building site stake, if the player does not carry one already. */
	private static DevelopmentResultS2C marker(ServerPlayer player, DevelopmentRequestC2S request) {
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (inventory.getItem(slot).is(ModBlocks.CONSTRUCTION_MARKER.asItem())) return fail(TradeResult.HAS_MARKER, request);
		}
		inventory.placeItemBackInInventory(new ItemStack(ModBlocks.CONSTRUCTION_MARKER), Prediction.SERVER_ONLY);
		return new DevelopmentResultS2C(TradeResult.SUCCESS, request.action(), 1, 0);
	}

	/** N-2: coins (from the wallets and loose coins, or from the account with a card) become development points. */
	private static DevelopmentResultS2C donateCoins(ServerPlayer player, Market market, DevelopmentRequestC2S request, long now) {
		long amount = request.amount();
		if (amount <= 0 || amount > MAX_DONATION) return fail(TradeResult.INVALID_REQUEST, request);
		boolean card = request.useCard();
		if (card && !BankService.hasValidCard(player)) return fail(TradeResult.CARD_INVALID, request);
		if (card) {
			BankAccounts bank = BankService.accounts(player);
			if (bank.balance(player.getUUID()) < amount) return fail(TradeResult.INSUFFICIENT_FUNDS, request);
			bank.add(player.getUUID(), -amount); // no card fee: the money goes to the village (N-2)
		} else if (!WalletService.pay(player, amount)) {
			return fail(TradeResult.INSUFFICIENT_FUNDS, request);
		}
		double points = amount * Development.config().pointsPerCoin;
		Development.add(market, player, points, now);
		Reputation.add(market, player.getUUID(), points * Development.config().reputationPerPoint, now);
		if (TradeExpansionConfig.get().log.trades) {
			TradeExpansion.LOGGER.info("{} DONATE {} @market#{}{}", player.getPlainTextName(), CoinItem.formatCopper(amount), market.id(),
					card ? " (card)" : "");
		}
		return new DevelopmentResultS2C(TradeResult.SUCCESS, request.action(), amount, (long) Math.floor(points));
	}
}
