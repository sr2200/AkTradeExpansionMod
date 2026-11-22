package com.akf.tradeexpansion.test.server;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.currency.MintService;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.development.FacilityKind;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.market.MarketUpkeep;
import com.akf.tradeexpansion.market.TradeRoutes;
import com.akf.tradeexpansion.network.MarketSnapshotS2C;
import com.akf.tradeexpansion.network.MintRequestC2S;
import com.akf.tradeexpansion.network.MintResultS2C;
import com.akf.tradeexpansion.registry.ModBlocks;
import com.akf.tradeexpansion.trade.TradeResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.akf.tradeexpansion.test.server.AutotestCheck.equal;
import static com.akf.tradeexpansion.test.server.AutotestCheck.that;
import static com.akf.tradeexpansion.test.server.ServerCase.partial;
import static com.akf.tradeexpansion.test.server.ServerDevelopmentCases.buildAndComplete;
import static com.akf.tradeexpansion.test.server.ServerDevelopmentCases.builder;
import static com.akf.tradeexpansion.test.server.ServerDevelopmentCases.town;

/** Server cases of chapter 37 (the mint) and 38 (trade routes of signposts). */
final class ServerRouteCases {
	private ServerRouteCases() {
	}

	private static final Identifier MINT = TradeExpansion.id("mint");
	private static final net.minecraft.world.item.Item COPPER_BLOCK = MintService.METALS.get(0).block();

	static List<ServerCase> all() {
		List<ServerCase> cases = new ArrayList<>();
		mint(cases);
		routes(cases);
		return cases;
	}

	// ---- 37. the mint

	private static void mint(List<ServerCase> cases) {
		cases.add(partial("mint_coins", List.of("37-2", "37-3", "37-4", "37-5"),
				"鋳造所の無い村では鋳造できず（NO_MINT）、鋳造所が建つと銅ブロック9個ずつが 96c 分のコインになり（2回で 192c、ブロックが18個減る）、"
						+ "持っている数より多い・65回以上の鋳造は断られて何も減らず、鉄ブロックは既定では「設定で無効」・鉄の鋳造を ON にすると9個で 960c、"
						+ "倍率を 2 にすると銅ブロック9個で 128c になること。看板が両替タブを開く種類であること（画面のボタンと表示は見ていない）",
				ctx -> town(ctx, (c, player, market, stall) -> {
					builder(c, player, market);
					ServerDevelopmentCases.ensureOpen(c, player, market);
					c.emptyInventory(player);
					ServerCases.giveItems(player, COPPER_BLOCK, 20);
					is(TradeResult.NO_MINT, mint(c, player, market, 0, 1).result(), "minting without a mint");
					equal(20, c.count(player, COPPER_BLOCK), "copper blocks after the refused minting");
					that(!c.lastSnapshot(player).mint().built(), "the snapshot shows a mint before it was built");

					buildAndComplete(c, player, market, MINT);
					is("exchange", FacilityKind.MINT.tab(), "tab the mint's sign opens");
					c.forget(player);
					player.closeContainer();
					ServerDevelopmentCases.ensureOpen(c, player, market);
					MarketSnapshotS2C.MintView view = c.lastSnapshot(player).mint();
					that(view.built() && view.options().size() == 2, "mint in the snapshot: %s", view);
					equal(96L, view.options().get(0).value(), "copper: value of 9 blocks");
					that(view.options().get(0).enabled() && !view.options().get(1).enabled(), "copper on and iron off by default: %s", view.options());

					long before = c.balance(player);
					MintResultS2C r = mint(c, player, market, 0, 2);
					is(TradeResult.SUCCESS, r.result(), "minting 2 × 9 copper blocks");
					equal(192L, r.value(), "value minted");
					equal(2, c.count(player, COPPER_BLOCK), "copper blocks left");
					equal(before + 192, c.balance(player), "money after minting");

					is(TradeResult.INSUFFICIENT_ITEMS, mint(c, player, market, 0, 1).result(), "minting with 2 blocks");
					is(TradeResult.INVALID_REQUEST, mint(c, player, market, 0, MintService.MAX_BATCHES + 1).result(), "65 mintings");
					is(TradeResult.INVALID_REQUEST, mint(c, player, market, 2, 1).result(), "an unknown metal");
					equal(2, c.count(player, COPPER_BLOCK), "copper blocks after the refused mintings");

					ServerCases.giveItems(player, Items.IRON_BLOCK, 9);
					is(TradeResult.MINTING_DISABLED, mint(c, player, market, 1, 1).result(), "iron minting by default");
					equal(9, c.count(player, Items.IRON_BLOCK), "iron blocks after the refused minting");
					c.config(cfg -> cfg.currency.allowIronMinting = true);
					before = c.balance(player);
					r = mint(c, player, market, 1, 1);
					is(TradeResult.SUCCESS, r.result(), "iron minting turned on");
					equal(960L, r.value(), "iron: value of 9 blocks");
					equal(0, c.count(player, Items.IRON_BLOCK), "iron blocks left");
					equal(before + 960, c.balance(player), "money after minting iron");

					c.config(cfg -> cfg.development.mintValueMultiplier = 2.0);
					ServerCases.giveItems(player, COPPER_BLOCK, 7);
					r = mint(c, player, market, 0, 1);
					equal(128L, r.value(), "copper with the multiplier 2");

					// The sign gone, the mint does not work any more.
					player.closeContainer();
					that(ServerDevelopmentCases.breakBlock(player, market.facilities().get(MINT).core()), "the mint's sign could not be broken");
					ServerDevelopmentCases.ensureOpen(c, player, market);
					ServerCases.giveItems(player, COPPER_BLOCK, 9);
					is(TradeResult.NO_MINT, mint(c, player, market, 0, 1).result(), "minting after the sign was broken");
				})));
	}

	private static MintResultS2C mint(ServerCtx c, ServerPlayer player, Market market, int metal, int batches) {
		int before = c.payloads(player, MintResultS2C.class).size();
		MintService.handle(player, new MintRequestC2S(market.id(), metal, batches));
		List<MintResultS2C> results = c.payloads(player, MintResultS2C.class);
		that(results.size() > before, "no answer to the minting");
		return results.getLast();
	}

	// ---- 38. trade routes

	private static void routes(List<ServerCase> cases) {
		cases.add(partial("signpost_route", List.of("38-2", "38-3", "38-4", "38-5", "38-6", "38-7"),
				"2つの市場（600ブロック、遠い方の端を 400 にした設定で連動 0%）の間に60ブロックおきに道標を並べると、最後の1本で「交易路ができました」と出て、"
						+ "Aで売った相場の変化の 50% がBに伝わり、道標の右クリックで道標の数・市場・交易路が出て、market info に相手が出て、保存データを書き出して読み直しても道標が残り、"
						+ "上限を 0 にすると「上限を超える」と出てつながらず、途中の道標を壊すとBに伝わらなくなること（道標は1本だけ実際に置き、ほかは保存データに登録した。道の見た目・音は見ていない）",
				ctx -> {
					ctx.config(cfg -> cfg.distance.farRadius = 400);
					ServerChapterCases.markets(ctx, new int[] {0, 600}, (c, player, m) -> {
						Market a = m.get(0), b = m.get(1);
						MarketRegistry registry = c.registry();
						Identifier good = ServerChapterCases.shared(c, a, 0, 64, b);
						that(TradeRoutes.partners(registry, a).isEmpty(), "a route before any signpost");
						// One real signpost by A (placed by the player), the rest recorded straight away (their chunks need not be loaded).
						BlockPos first = a.center().east(40).south(3);
						c.floor(first);
						player.closeContainer();
						c.emptyHand(player);
						player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModBlocks.SIGNPOST.asItem()));
						c.click(player, first.below(), Direction.UP);
						that(c.level().getBlockState(first).is(ModBlocks.SIGNPOST), "the signpost was not placed");
						that(registry.signposts(c.level().dimension()).contains(first), "the placed signpost was not recorded");
						that(c.gotMessage(player, "message.ak_trade_expansion.route.hint"), "placing a lone signpost did not explain the road");
						for (int x = 100; x <= 580; x += 60) {
							c.forget(player);
							TradeRoutes.onPlaced(c.level(), a.center().east(x), player);
						}
						that(c.gotMessage(player, "message.ak_trade_expansion.route.opened"), "the last signpost did not open a route");
						is(List.of(b), TradeRoutes.partners(registry, a), "partners of A");

						// Right-click: the road and its route.
						c.forget(player);
						c.emptyHand(player);
						c.click(player, first, Direction.SOUTH);
						that(c.gotMessage(player, "message.ak_trade_expansion.route.header"), "right-click: no header");
						that(c.gotMessage(player, "message.ak_trade_expansion.route.route"), "right-click: no route");
						c.forget(player);
						c.commandShown(player, "aktradeex market info");
						that(c.messages(player).stream().anyMatch(s -> s.contains("route ↔")), "market info lists no route: %s", c.messages(player));

						// Half of the change at A reaches B, 600 blocks away (beyond the far radius).
						ServerChapterCases.openAt(c, player, m, 0);
						double moved = sellAndMeasure(c, player, a, b, good, 32);
						double link = GoodsCatalog.get(good).link();
						that(Math.abs(moved - 32 * 0.5 * link) < 0.5, "B's X moved %.2f, expected %.2f", moved, 32 * 0.5 * link);

						// Saved and read back.
						RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, c.server().registryAccess());
						Tag saved = MarketRegistry.CODEC.encodeStart(ops, registry).getOrThrow();
						MarketRegistry back = MarketRegistry.CODEC.parse(ops, saved).getOrThrow();
						is(Set.copyOf(registry.signposts(c.level().dimension())), Set.copyOf(back.signposts(c.level().dimension())), "signposts read back");

						// The limit.
						c.config(cfg -> cfg.route.maxRoutesPerMarket = 0);
						that(TradeRoutes.partners(registry, a).isEmpty(), "a route over the limit");
						List<String> keys = new ArrayList<>();
						TradeRoutes.lines(registry, c.level().dimension(), first).forEach(l -> keys.add(l.getContents() instanceof
								net.minecraft.network.chat.contents.TranslatableContents t ? t.getKey() : ""));
						that(keys.contains("message.ak_trade_expansion.route.blocked"), "no line about the limit: %s", keys);
						c.config(cfg -> cfg.route.maxRoutesPerMarket = 4);
						is(List.of(b), TradeRoutes.partners(registry, a), "partners of A after the limit came back");

						// Breaking the real signpost cuts the road.
						that(ServerDevelopmentCases.breakBlock(player, first), "the signpost could not be broken");
						that(!registry.signposts(c.level().dimension()).contains(first), "the broken signpost is still recorded");
						that(TradeRoutes.partners(registry, a).isEmpty(), "the route is left after the first signpost was broken");
						ServerChapterCases.openAt(c, player, m, 0);
						double after = sellAndMeasure(c, player, a, b, good, 16);
						that(Math.abs(after) < 1e-6, "B's X moved %.2f without the route", after);
						for (int x = 100; x <= 580; x += 60) TradeRoutes.onRemoved(c.level(), a.center().east(x));
					});
				}));
	}

	/** Sells {@code qty} of {@code good} at A; how far B's X moved. */
	static double sellAndMeasure(ServerCtx c, ServerPlayer player, Market a, Market b, Identifier good, int qty) {
		MarketUpkeep.touch(c.level(), b);
		double before = b.good(good).pressure();
		ServerCases.giveItems(player, c.item(good), qty);
		c.trade(player, a, good, false, qty);
		that(c.lastTradeResult(player) == TradeResult.SUCCESS, "selling at A gave %s", c.lastTradeResult(player));
		MarketUpkeep.touch(c.level(), b);
		return b.good(good).pressure() - before;
	}

	private static void is(Object expected, Object actual, String what) {
		that(java.util.Objects.equals(expected, actual), "%s: expected %s but was %s", what, expected, actual);
	}
}
