package com.akf.tradeexpansion.test.server;

import com.akf.tradeexpansion.bank.BankService;
import com.akf.tradeexpansion.board.BoardService;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.currency.ExchangeService;
import com.akf.tradeexpansion.currency.WalletService;
import com.akf.tradeexpansion.development.DevelopmentService;
import com.akf.tradeexpansion.item.WalletItem;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.network.BankRequestC2S;
import com.akf.tradeexpansion.network.BoardRequestC2S;
import com.akf.tradeexpansion.network.BoardS2C;
import com.akf.tradeexpansion.network.CargoDeliverC2S;
import com.akf.tradeexpansion.network.DevelopmentRequestC2S;
import com.akf.tradeexpansion.network.ExchangeRequestC2S;
import com.akf.tradeexpansion.network.QuestAcceptC2S;
import com.akf.tradeexpansion.network.QuestDeliverC2S;
import com.akf.tradeexpansion.network.QuestResultS2C;
import com.akf.tradeexpansion.network.SellAllC2S;
import com.akf.tradeexpansion.network.StaffRequestC2S;
import com.akf.tradeexpansion.network.StaffS2C;
import com.akf.tradeexpansion.network.TradeRequestC2S;
import com.akf.tradeexpansion.quest.QuestService;
import com.akf.tradeexpansion.quest.Request;
import com.akf.tradeexpansion.registry.ModItems;
import com.akf.tradeexpansion.staff.StaffService;
import com.akf.tradeexpansion.trade.SellAllService;
import com.akf.tradeexpansion.trade.TradeResult;
import com.akf.tradeexpansion.trade.TradeService;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.LongConsumer;

import static com.akf.tradeexpansion.test.server.AutotestCheck.equal;
import static com.akf.tradeexpansion.test.server.AutotestCheck.that;
import static com.akf.tradeexpansion.test.server.ServerCase.full;

/**
 * Requests a modified or out-of-date client could send (CLAUDE.md: every handler checks the market of the open screen,
 * the distance to the stall and the quantities). Each case sends one kind of request
 * <ul>
 *   <li>naming another market than the open screen's, a market that does not exist, with no screen open and from too
 *       far away: nothing may change (or the answer is the refusal), and</li>
 *   <li>the same request done right, which must change something — so the refusals above are not just requests that
 *       would fail anyway.</li>
 * </ul>
 * None of these is a checklist item; they appear in the report only.
 */
final class ServerRequestCases {
	private ServerRequestCases() {
	}

	/** Two markets 300 blocks apart; the player stands at A with A's trade screen open. */
	@FunctionalInterface
	interface TwoMarkets {
		void run(ServerCtx ctx, ServerPlayer player, Market a, Market b, BlockPos stallA) throws Exception;
	}

	static void twoMarkets(ServerCtx ctx, TwoMarkets body) {
		BlockPos a = ctx.center();
		BlockPos b = a.east(300);
		ctx.village(a, 3, 3);
		ctx.floor(b);
		ctx.village(b, 3, 3);
		ctx.later(20, c -> {
			ServerPlayer other = c.player(b.south(2));
			c.placeStall(other, b);
			ServerPlayer player = c.player(a.south(2));
			c.placeStall(player, a);
			Market ma = c.marketAt(a), mb = c.marketAt(b);
			that(ma != null && mb != null && ma != mb, "the two markets were not founded (A %s, B %s): %s / %s", ma, mb, c.messages(player), c.messages(other));
			c.openStall(player, a);
			body.run(c, player, ma, mb, a);
		});
	}

	/** How to tell whether a request did something: by default the fingerprint changes. */
	@FunctionalInterface
	interface Effect {
		boolean happened(ServerCtx c, ServerPlayer player, String before);
	}

	private static final Effect CHANGES = (c, p, before) -> !before.equals(c.fingerprint(p));

	/**
	 * Sends {@code request} (given a market id) the four wrong ways, then the right way.
	 * {@code effect} says whether a request did something.
	 */
	static void refusesWrongRequests(ServerCtx c, ServerPlayer player, Market a, Market b, BlockPos stall, LongConsumer request, Effect effect) {
		List<String> problems = new ArrayList<>();
		String before = c.fingerprint(player);
		c.forget(player);
		// 1. another market than the open screen's
		request.accept(b.id());
		if (effect.happened(c, player, before)) problems.add("a request for another market than the open screen's was done");
		// 2. a market that does not exist
		before = c.fingerprint(player);
		c.forget(player);
		request.accept(987_654_321L);
		if (effect.happened(c, player, before)) problems.add("a request for a market that does not exist was done");
		// 3. too far from the stall (the screen still open)
		double max = TradeExpansionConfig.get().trade.maxDistance;
		player.teleportTo(stall.getX() + 0.5 + max + 10, stall.getY(), stall.getZ() + 0.5);
		before = c.fingerprint(player);
		c.forget(player);
		request.accept(a.id());
		if (effect.happened(c, player, before)) problems.add("a request from " + (int) (max + 10) + " blocks away was done");
		player.teleportTo(stall.getX() + 0.5, stall.getY(), stall.getZ() + 2.5);
		// 4. no screen open
		player.closeContainer();
		before = c.fingerprint(player);
		c.forget(player);
		request.accept(a.id());
		if (effect.happened(c, player, before)) problems.add("a request with no screen open was done");
		// 5. the right way
		c.openStall(player, stall);
		before = c.fingerprint(player);
		c.forget(player);
		request.accept(a.id());
		if (!effect.happened(c, player, before)) problems.add("the right request did nothing either, so the refusals prove nothing (answers: "
				+ c.sent(player).stream().filter(m -> m instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket)
				.map(m -> ((net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket) m).payload().toString())
				.map(t -> t.length() > 160 ? t.substring(0, 160) : t).toList() + ", messages: " + c.messages(player) + ")");
		that(problems.isEmpty(), "%s", String.join("; ", problems));
	}

	static List<ServerCase> all() {
		List<ServerCase> cases = new ArrayList<>();

		cases.add(full("request_trade", List.of(), ctx -> twoMarkets(ctx, (c, player, a, b, stall) -> {
			c.giveMoney(player, 100_000);
			Identifier good = c.tradableGood(a, 10, 0);
			refusesWrongRequests(c, player, a, b, stall, id -> {
				long expected = c.previewTotal(player, a, good, true, 1);
				TradeService.handle(player, new TradeRequestC2S(id, good, true, 1, expected, false, false));
			}, CHANGES);
			// Quantities out of range, with the screen open.
			String before = c.fingerprint(player);
			for (int qty : new int[] {0, -1, -64, Integer.MIN_VALUE, TradeExpansionConfig.get().trade.maxPerTransaction + 1}) {
				var r = c.tradeRaw(player, a.id(), good, true, qty, c.previewTotal(player, a, good, true, Math.max(1, qty)), false, false);
				that(r == TradeResult.INVALID_REQUEST, "quantity %d gave %s", qty, r);
			}
			var huge = c.tradeRaw(player, a.id(), good, true, TradeService.MAX_ALL + 1, 1, false, true);
			that(huge == TradeResult.INVALID_REQUEST, "a Shift quantity above %d gave %s", TradeService.MAX_ALL, huge);
			that(before.equals(c.fingerprint(player)), "an out-of-range quantity changed something");
		})));

		cases.add(full("request_sell_all", List.of(), ctx -> twoMarkets(ctx, (c, player, a, b, stall) -> {
			Identifier good = c.tradableGood(a, 0, 40);
			ServerCases.giveItemsOutsideHotbar(player, c.item(good), 40);
			refusesWrongRequests(c, player, a, b, stall, id -> {
				long expected = c.previewTotal(player, a, good, false, 4);
				SellAllService.handle(player, new SellAllC2S(id, List.of(new SellAllC2S.Line(good, 4)), expected, false));
			}, CHANGES);
			String before = c.fingerprint(player);
			for (int units : new int[] {0, -4}) {
				SellAllService.handle(player, new SellAllC2S(a.id(), List.of(new SellAllC2S.Line(good, units)), 0, false));
			}
			that(before.equals(c.fingerprint(player)), "selling 0 or a negative number of items changed something");
		})));

		cases.add(full("request_bank", List.of(), ctx -> {
			ctx.config(cfg -> cfg.bank.minMarketLevel = 1); // every market has a bank
			twoMarkets(ctx, (c, player, a, b, stall) -> {
				refusesWrongRequests(c, player, a, b, stall,
						id -> BankService.handle(player, new BankRequestC2S(id, BankRequestC2S.Action.DEPOSIT, 100)), CHANGES);
				String before = c.fingerprint(player);
				for (long amount : new long[] {0, -100, Long.MIN_VALUE}) {
					BankService.handle(player, new BankRequestC2S(a.id(), BankRequestC2S.Action.DEPOSIT, amount));
					BankService.handle(player, new BankRequestC2S(a.id(), BankRequestC2S.Action.WITHDRAW, amount));
				}
				BankService.handle(player, new BankRequestC2S(a.id(), BankRequestC2S.Action.WITHDRAW, 1_000_000_000L));
				that(before.equals(c.fingerprint(player)), "a zero, negative or too large amount changed something");
			});
		}));

		cases.add(full("request_exchange", List.of(), ctx -> twoMarkets(ctx, (c, player, a, b, stall) -> {
			c.giveMoney(player, 1_000);
			// Break one iron coin (the starting money has some) into copper.
			refusesWrongRequests(c, player, a, b, stall, id -> ExchangeService.handle(player, new ExchangeRequestC2S(id, 1, false, 1)), CHANGES);
			String before = c.fingerprint(player);
			for (int[] bad : new int[][] {{-1, 1}, {5, 1}, {99, 1}, {1, 0}, {1, -5}, {0, Integer.MIN_VALUE}}) {
				ExchangeService.handle(player, new ExchangeRequestC2S(a.id(), bad[0], false, bad[1]));
				ExchangeService.handle(player, new ExchangeRequestC2S(a.id(), bad[0], true, bad[1]));
			}
			that(before.equals(c.fingerprint(player)), "an unknown coin or a bad count changed something");
		})));

		cases.add(full("request_quest_accept", List.of(), ctx -> twoMarkets(ctx, (c, player, a, b, stall) -> {
			Request request = a.requests().stream().filter(r -> !r.isTransport()).findFirst()
					.orElseThrow(() -> new AutotestCheck.Failed("market A has no request"));
			refusesWrongRequests(c, player, a, b, stall, id -> QuestService.accept(player, new QuestAcceptC2S(id, request.id(), false)), CHANGES);
		})));

		cases.add(full("request_quest_deliver", List.of(), ctx -> twoMarkets(ctx, (c, player, a, b, stall) -> {
			Request request = a.requests().stream().filter(r -> !r.isTransport()).min(Comparator.comparingInt(Request::difficulty))
					.orElseThrow(() -> new AutotestCheck.Failed("market A has no request"));
			ServerCases.giveItems(player, c.item(request.goods()), request.quantity());
			refusesWrongRequests(c, player, a, b, stall, id -> QuestService.deliver(player, new QuestDeliverC2S(id, request.id(), false)), CHANGES);
		})));

		cases.add(full("request_cargo_deliver", List.of(), ctx -> twoMarkets(ctx, (c, player, a, b, stall) -> {
			// No crate: done right the answer is "no crate"; done wrong it must be a refusal before that.
			Effect reachedTheCrateCheck = (cc, p, before) -> cc.payloads(p, QuestResultS2C.class).stream()
					.anyMatch(r -> r.result() == TradeResult.NO_CARGO);
			refusesWrongRequests(c, player, a, b, stall, id -> QuestService.deliverCargo(player, new CargoDeliverC2S(id, false)), reachedTheCrateCheck);
		})));

		cases.add(full("request_development", List.of(), ctx -> twoMarkets(ctx, (c, player, a, b, stall) -> {
			c.giveMoney(player, 10_000);
			refusesWrongRequests(c, player, a, b, stall,
					id -> DevelopmentService.handle(player, new DevelopmentRequestC2S(id, DevelopmentRequestC2S.Action.DONATE_COINS, 100, "", false)), CHANGES);
			String before = c.fingerprint(player);
			for (long amount : new long[] {0, -100, Long.MIN_VALUE}) {
				DevelopmentService.handle(player, new DevelopmentRequestC2S(a.id(), DevelopmentRequestC2S.Action.DONATE_COINS, amount, "", false));
			}
			that(before.equals(c.fingerprint(player)), "donating a zero or negative amount changed something");
		})));

		cases.add(full("request_staff", List.of(), ctx -> twoMarkets(ctx, (c, player, a, b, stall) -> {
			// Hiring a clerk: refused before the checks of the action itself (reputation, goods), or answered with TOO_FAR.
			// (Reading the staff list is allowed from anywhere: it changes nothing.)
			Effect reachedTheAction = (cc, p, before) -> cc.payloads(p, StaffS2C.class).stream()
					.anyMatch(r -> r.result().isPresent() && r.result().get() != TradeResult.TOO_FAR);
			Identifier good = c.tradableGood(a, 0, 1);
			ServerCases.giveItems(player, c.item(good), 1);
			refusesWrongRequests(c, player, a, b, stall, id -> StaffService.handle(player,
					new StaffRequestC2S(id, StaffRequestC2S.Action.HIRE_CLERK, 0, good, 0, 1, 0, 1, 0, false)), reachedTheAction);
		})));

		cases.add(full("request_board", List.of(), ctx -> twoMarkets(ctx, (c, player, a, b, stall) -> {
			Effect answered = (cc, p, before) -> !cc.payloads(p, BoardS2C.class).isEmpty();
			refusesWrongRequests(c, player, a, b, stall, id -> BoardService.handle(player, new BoardRequestC2S(id)), answered);
		})));

		cases.add(full("request_wallet_withdraw", List.of(), ctx -> {
			ServerPlayer player = ctx.player(ctx.center());
			ctx.emptyInventory(player);
			ItemStack wallet = new ItemStack(ModItems.WALLET);
			WalletItem.setBalance(wallet, 500);
			player.setItemInHand(InteractionHand.MAIN_HAND, wallet);
			String before = ctx.fingerprint(player);
			// Unknown coins, zero / negative / too many coins, more than the wallet holds, the wrong hand.
			int[][] bad = {{-1, 1}, {5, 1}, {0, 0}, {0, -3}, {0, Integer.MIN_VALUE}, {0, 2305}, {0, 501}, {2, 6}, {4, 1}};
			for (int[] b : bad) WalletService.withdraw(player, false, b[0], b[1]);
			WalletService.withdraw(player, true, 0, 1);
			that(before.equals(ctx.fingerprint(player)), "a bad withdrawal changed something");
			// Done right: coins come out, the total stays the same.
			WalletService.withdraw(player, false, 1, 3);
			equal(470, WalletItem.balance(player.getMainHandItem()), "wallet after taking out 3 iron coins");
			equal(500, ctx.balance(player), "money (wallet + coins) after taking coins out");
		}));

		return cases;
	}
}
