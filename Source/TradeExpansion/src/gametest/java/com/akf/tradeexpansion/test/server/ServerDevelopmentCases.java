package com.akf.tradeexpansion.test.server;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.bank.BankService;
import com.akf.tradeexpansion.block.FacilitySignBlock;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.DevelopmentMath;
import com.akf.tradeexpansion.core.GoodParams;
import com.akf.tradeexpansion.data.FacilityCatalog;
import com.akf.tradeexpansion.data.FacilityDefinition;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.GoodsDefinition;
import com.akf.tradeexpansion.development.Development;
import com.akf.tradeexpansion.development.DevelopmentService;
import com.akf.tradeexpansion.development.Facility;
import com.akf.tradeexpansion.development.FacilityBuilder;
import com.akf.tradeexpansion.development.FacilityKind;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketFactory;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.market.MarketUpkeep;
import com.akf.tradeexpansion.market.Reputation;
import com.akf.tradeexpansion.menu.MarketMenu;
import com.akf.tradeexpansion.network.BankRequestC2S;
import com.akf.tradeexpansion.network.CargoDeliverC2S;
import com.akf.tradeexpansion.network.DevelopmentRequestC2S;
import com.akf.tradeexpansion.network.DevelopmentResultS2C;
import com.akf.tradeexpansion.network.MarketSnapshotS2C;
import com.akf.tradeexpansion.network.QuestAcceptC2S;
import com.akf.tradeexpansion.network.QuestDeliverC2S;
import com.akf.tradeexpansion.network.QuestResultS2C;
import com.akf.tradeexpansion.quest.Cargo;
import com.akf.tradeexpansion.quest.QuestService;
import com.akf.tradeexpansion.quest.Request;
import com.akf.tradeexpansion.registry.ModBlocks;
import com.akf.tradeexpansion.registry.ModItems;
import com.akf.tradeexpansion.trade.TradeResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.akf.tradeexpansion.test.server.AutotestCheck.equal;
import static com.akf.tradeexpansion.test.server.AutotestCheck.that;
import static com.akf.tradeexpansion.test.server.ServerCase.full;
import static com.akf.tradeexpansion.test.server.ServerCase.partial;

/**
 * Server cases of the village development (22): donations, points from trades and requests, materials, building,
 * completing and destroying facilities, their effects, building site stakes, protection and saving. Cases that build lay
 * a large floor first ({@link #town}), so that the site search finds flat ground around the stall.
 */
final class ServerDevelopmentCases {
	private ServerDevelopmentCases() {
	}

	private static final Identifier QUEST_HALL = TradeExpansion.id("quest_hall");
	private static final Identifier WAREHOUSE = TradeExpansion.id("warehouse");
	private static final Identifier INN = TradeExpansion.id("inn");
	private static final Identifier BANK = TradeExpansion.id("bank");

	static List<ServerCase> all() {
		List<ServerCase> cases = new ArrayList<>();
		points(cases);
		materials(cases);
		building(cases);
		stakes(cases);
		return cases;
	}

	// ---- 22-2 .. 22-11: development points

	private static void points(List<ServerCase> cases) {
		cases.add(partial("development_tab_view", List.of("22-2"), "交易画面を開くと、発展度 0・寄付した人なし・自分の値 0 と建物の一覧が送られること（画面の表示は見ていない）",
				ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
					MarketSnapshotS2C.DevelopmentView view = view(c, player, stall);
					that(view.enabled(), "the development tab is not shown at an overworld market");
					that(view.points() == 0, "development points of a new market: %s", view.points());
					that(view.top().isEmpty(), "contributors of a new market: %s", view.top());
					that(view.mine() == 0, "the player's points at a new market: %s", view.mine());
					for (Identifier id : List.of(QUEST_HALL, WAREHOUSE, INN, BANK)) facility(view, id);
				})));
		cases.add(partial("donate_coins", List.of("22-3"), "100c を寄付すると所持金が 100c 減り、発展度が 100 増え、寄付した人に自分の名前が出て、評判のポイントが 3 増えること（画面の表示は見ていない）",
				ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
					c.openStall(player, stall);
					c.giveMoney(player, 1_000);
					long money = c.balance(player);
					double rep = Reputation.points(market, player.getUUID(), c.now());
					DevelopmentResultS2C r = donate(c, player, market, 100, false);
					that(r.result() == TradeResult.SUCCESS, "donating 100c gave %s", r.result());
					equal(money - 100, c.balance(player), "money after donating 100c");
					that(Math.abs(points(c, market) - 100) < 1e-6, "development after donating 100c: %s", points(c, market));
					double gained = Reputation.points(market, player.getUUID(), c.now()) - rep;
					that(Math.abs(gained - 3) < 1e-6, "reputation points gained by donating 100c: %.4f", gained);
					MarketSnapshotS2C.DevelopmentView view = view(c, player, stall);
					that(view.top().size() == 1 && view.top().getFirst().name().equals(player.getPlainTextName()), "contributors: %s", view.top());
					that(Math.abs(view.mine() - 100) < 1e-6, "the player's points: %s", view.mine());
				})));
		cases.add(partial("donate_without_money", List.of("22-4"), "所持金より多い額の寄付は「所持金が足りない」で断られ、何も変わらないこと（画面の文は見ていない）",
				ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
					c.openStall(player, stall);
					c.emptyInventory(player);
					c.giveMoney(player, 50);
					String before = c.fingerprint(player);
					DevelopmentResultS2C r = donate(c, player, market, 100, false);
					that(r.result() == TradeResult.INSUFFICIENT_FUNDS, "donating 100c with 50c gave %s", r.result());
					that(before.equals(c.fingerprint(player)), "a refused donation changed something");
					that(market.contributors().isEmpty(), "a refused donation was counted: %s", market.contributors());
				})));
		cases.add(full("donate_by_card", List.of("22-5"), ctx -> {
			ctx.config(cfg -> cfg.bank.minMarketLevel = 1);
			ServerCases.withMarket(ctx, (c, player, market, stall) -> {
				c.openStall(player, stall);
				c.giveMoney(player, 2_000);
				BankService.handle(player, new BankRequestC2S(market.id(), BankRequestC2S.Action.DEPOSIT, 2_000));
				BankService.handle(player, new BankRequestC2S(market.id(), BankRequestC2S.Action.ISSUE_CARD, 0));
				that(BankService.hasValidCard(player), "no valid card");
				long money = c.balance(player), bank = bank(player);
				DevelopmentResultS2C r = donate(c, player, market, 100, true);
				that(r.result() == TradeResult.SUCCESS, "donating 100c by card gave %s", r.result());
				equal(bank - 100, bank(player), "bank after donating 100c by card (no card fee)");
				equal(money, c.balance(player), "money after donating by card");
				that(Math.abs(points(c, market) - 100) < 1e-6, "development after donating by card: %s", points(c, market));
			});
		}));
		cases.add(full("development_from_requests", List.of("22-6"), ctx -> ServerChapterCases.markets(ctx, new int[] {0, 600}, (c, player, m) -> {
			Market a = m.get(0), b = m.get(1);
			double ratio = Development.config().questRewardRatio;
			c.command(player, "aktradeex reputation set 1000"); // 3-star requests need a reputation
			// An ordinary request at A.
			Request request = a.requests().stream().filter(r -> !r.isTransport()).min(Comparator.comparingInt(Request::difficulty))
					.orElseThrow(() -> new AutotestCheck.Failed("market A has no request"));
			ServerCases.giveItemsOutsideHotbar(player, c.item(request.goods()), request.quantity());
			long money = c.balance(player);
			double before = points(c, a);
			c.forget(player);
			QuestService.deliver(player, new QuestDeliverC2S(a.id(), request.id(), false));
			that(questResult(c, player) == TradeResult.SUCCESS, "delivering the request gave %s", questResult(c, player));
			long reward = c.balance(player) - money;
			that(reward > 0, "no reward was paid");
			that(Math.abs(points(c, a) - before - reward * ratio) < 1e-6, "development after a %dc reward: +%.3f, expected +%.3f", reward,
					points(c, a) - before, reward * ratio);
			// A transport request from A to B: both markets.
			Request transport = a.requests().stream().filter(r -> r.isTransport() && r.transport().get().destination() == b.id()).findFirst()
					.orElseThrow(() -> new AutotestCheck.Failed("market A has no transport request to B: " + a.requests()));
			c.giveMoney(player, transport.transport().orElseThrow().deposit() + 1_000);
			c.forget(player);
			QuestService.accept(player, new QuestAcceptC2S(a.id(), transport.id(), false));
			that(questResult(c, player) == TradeResult.SUCCESS, "taking the crate gave %s", questResult(c, player));
			Cargo cargo = null;
			for (ItemStack s : player.getInventory()) if (s.is(ModItems.CARGO_CRATE)) cargo = Cargo.of(s);
			that(cargo != null, "no crate");
			double beforeA = points(c, a), beforeB = points(c, b);
			ServerChapterCases.openAt(c, player, m, 1);
			c.forget(player);
			QuestService.deliverCargo(player, new CargoDeliverC2S(b.id(), false));
			that(questResult(c, player) == TradeResult.SUCCESS, "delivering the crate gave %s", questResult(c, player));
			double expected = cargo.reward() * ratio;
			that(Math.abs(points(c, a) - beforeA - expected) < 1e-6, "development of the source after a transport: +%.3f, expected +%.3f",
					points(c, a) - beforeA, expected);
			that(Math.abs(points(c, b) - beforeB - expected) < 1e-6, "development of the destination after a transport: +%.3f, expected +%.3f",
					points(c, b) - beforeB, expected);
		})));
		cases.add(full("development_from_trades", List.of("22-7"), ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
			c.openStall(player, stall);
			c.giveMoney(player, 100_000);
			Identifier good = c.tradableGood(market, 8, 0);
			double before = points(c, market);
			long total = c.trade(player, market, good, true, 8);
			that(c.lastTradeResult(player) == TradeResult.SUCCESS, "buying gave %s", c.lastTradeResult(player));
			double expected = total * Development.config().tradePointsPerCoin;
			that(Math.abs(points(c, market) - before - expected) < 1e-6, "development after trading %dc: +%.4f, expected +%.4f (1,000c = 20)", total,
					points(c, market) - before, expected);
		})));
		cases.add(full("development_command", List.of("22-8"), ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
			c.openStall(player, stall);
			c.giveMoney(player, 1_000);
			donate(c, player, market, 100, false);
			c.forget(player);
			c.commandShown(player, "aktradeex development");
			that(c.gotMessage(player, "command.ak_trade_expansion.development.info"), "\"development\" said %s", c.messages(player));
			String line = "  " + player.getPlainTextName() + ": 100";
			that(c.messages(player).contains(line), "the top contributors were not listed (%s): %s", line, c.messages(player));
			// Not an operator: "add" is refused.
			c.server().getCommands().performPrefixedCommand(player.createCommandSourceStack(), "aktradeex development add 500");
			that(Math.abs(points(c, market) - 100) < 1e-6, "a player who is not an operator added points: %s", points(c, market));
			c.command(player, "aktradeex development add 500");
			that(Math.abs(points(c, market) - 600) < 1e-6, "development after \"add 500\": %s", points(c, market));
		})));
		cases.add(partial("development_off", List.of("22-9"), "設定 development.enabled を OFF にすると、発展のタブが送られず、取引しても発展度が増えず、寄付も断られること（/aktradeex reload での読み直しは見ていない）",
				ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
					c.config(cfg -> cfg.development.enabled = false);
					MarketSnapshotS2C.DevelopmentView view = view(c, player, stall);
					that(!view.enabled(), "the development tab is shown with development off");
					c.giveMoney(player, 100_000);
					Identifier good = c.tradableGood(market, 8, 0);
					c.trade(player, market, good, true, 8);
					that(c.lastTradeResult(player) == TradeResult.SUCCESS, "buying gave %s", c.lastTradeResult(player));
					that(points(c, market) == 0, "trading with development off added %s points", points(c, market));
					DevelopmentResultS2C r = donate(c, player, market, 100, false);
					that(r.result() == TradeResult.NOT_AVAILABLE, "donating with development off gave %s", r.result());
				})));
		cases.add(partial("development_two_players", List.of("22-10"), "仮のプレイヤー2人で寄付すると、多い順に2人の名前が並び、画面を開いているもう1人にも新しい発展度が送られること（画面の表示は見ていない）",
				ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
					ServerPlayer other = c.player(stall.south(3));
					c.openStall(player, stall);
					c.openStall(other, stall);
					c.giveMoney(player, 1_000);
					c.giveMoney(other, 1_000);
					donate(c, player, market, 100, false);
					c.forget(player);
					DevelopmentResultS2C r = donate(c, other, market, 300, false);
					that(r.result() == TradeResult.SUCCESS, "the second player's donation gave %s", r.result());
					MarketSnapshotS2C snapshot = c.lastSnapshot(player);
					that(snapshot != null, "the first player's open screen was not updated");
					that(Math.abs(snapshot.development().points() - 400) < 1e-6, "development on the first player's screen: %s", snapshot.development().points());
					List<String> names = snapshot.development().top().stream().map(MarketSnapshotS2C.ContributorView::name).toList();
					that(names.equals(List.of(other.getPlainTextName(), player.getPlainTextName())), "contributors (most first): %s", names);
				})));
		cases.add(partial("development_saved", List.of("22-11", "22-40"),
				"保存データ（markets）を書き出して読み直すと、発展度・寄付した人・建設中／完成／壊れた建物と寄付した資材が同じで、読み直した後も建設中の建物は期日が来ると完成すること（実際にワールドを開き直してはいない）",
				ctx -> town(ctx, (c, player, market, stall) -> {
					builder(c, player, market);
					c.giveMoney(player, 1_000);
					donate(c, player, market, 100, false);
					// A destroyed warehouse, materials for the bank and a quest hall under construction.
					buildAndComplete(c, player, market, WAREHOUSE);
					Facility warehouse = market.facilities().get(WAREHOUSE);
					breakBlock(player, warehouse.core());
					that(warehouse.state() == Facility.State.DESTROYED, "the warehouse was not destroyed: %s", warehouse.state());
					ServerCases.giveItemsOutsideHotbar(player, Items.STONE_BRICKS, 10);
					DevelopmentResultS2C given = dev(c, player, market, DevelopmentRequestC2S.Action.DONATE_ITEMS, 0, BANK.toString());
					that(given.result() == TradeResult.SUCCESS, "donating stone bricks gave %s", given.result());
					that(build(c, player, market, QUEST_HALL) == TradeResult.SUCCESS, "building the quest hall failed");
					RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, c.level().registryAccess());
					Tag saved = MarketRegistry.CODEC.encodeStart(ops, c.registry()).getOrThrow();
					Market back = MarketRegistry.CODEC.parse(ops, saved).getOrThrow().get(market.id());
					that(back != null, "the market was not loaded");
					that(back.development() == market.development(), "development after loading: %s / %s", back.development(), market.development());
					that(back.contributors().equals(market.contributors()), "contributors after loading: %s / %s", back.contributors(), market.contributors());
					for (Identifier id : List.of(WAREHOUSE, QUEST_HALL)) {
						Facility f = market.facilities().get(id), g = back.facilities().get(id);
						that(g != null, "%s was not loaded", id);
						that(g.state() == f.state() && g.due() == f.due() && g.box().equals(f.box()) && java.util.Objects.equals(g.core(), f.core())
								&& g.rotation() == f.rotation(), "%s after loading: %s %s %s / %s %s %s", id, g.state(), g.box(), g.core(), f.state(), f.box(), f.core());
					}
					that(back.donations(BANK).equals(market.donations(BANK)), "donated materials after loading: %s / %s", back.donations(BANK), market.donations(BANK));
					that(!back.donations(BANK).isEmpty(), "the bank's materials were not saved");
					// The due date passes: the quest hall completes by itself.
					c.command(player, "aktradeex clock advance " + (Development.config().buildDays + 0.1));
					c.later(100, cc -> that(market.facilities().get(QUEST_HALL).complete(), "the quest hall did not complete after its due date: %s",
							market.facilities().get(QUEST_HALL).state()));
				})));
	}

	// ---- 22-22 .. 22-23b: reputation and materials

	private static void materials(List<ServerCase> cases) {
		cases.add(partial("build_needs_reputation", List.of("22-22"), "評判が足りないと「選べない」として送られ、建てる要求が「評判が足りない」で断られること（ボタンとツールチップは見ていない）",
				ctx -> {
					ctx.config(cfg -> cfg.development.requireMaterials = false);
					ServerCases.withMarket(ctx, (c, player, market, stall) -> {
						c.command(player, "aktradeex development add 100000");
						that(!view(c, player, stall).canChoose(), "a player with no reputation can choose a facility");
						double before = points(c, market);
						that(build(c, player, market, WAREHOUSE) == TradeResult.REPUTATION_TOO_LOW, "building with no reputation was not refused");
						that(points(c, market) == before, "a refused build spent points");
						that(market.facilities().isEmpty(), "a refused build started a facility");
						c.command(player, "aktradeex reputation set " + threshold(Development.config().buildReputationLevel));
						that(view(c, player, stall).canChoose(), "a player at level %d cannot choose", Development.config().buildReputationLevel);
					});
				}));
		cases.add(partial("donate_materials", List.of("22-23"), "丸石を寄付すると丸石が減り（512個まで）、発展度と評判が増え、寄付した数が送られ、512個の後や資材を持っていない時は「寄付できる物がない」で断られること（ボタンの状態は見ていない）",
				ctx -> ServerCases.withMarket(ctx, (c, player, market, stall) -> {
					c.openStall(player, stall);
					ServerCases.giveItemsOutsideHotbar(player, Items.COBBLESTONE, 600);
					FacilityDefinition def = FacilityCatalog.get(WAREHOUSE);
					FacilityDefinition.Wanted cobble = def.wanted().stream().filter(w -> w.item().map(Identifier.parse("minecraft:cobblestone")::equals).orElse(false))
							.findFirst().orElseThrow(() -> new AutotestCheck.Failed("the warehouse does not want cobblestone"));
					int required = FacilityBuilder.required(market, WAREHOUSE, cobble);
					equal(512, required, "cobblestone the warehouse wants");
					double rep = Reputation.points(market, player.getUUID(), c.now());
					DevelopmentResultS2C r = dev(c, player, market, DevelopmentRequestC2S.Action.DONATE_ITEMS, 0, WAREHOUSE.toString());
					that(r.result() == TradeResult.SUCCESS, "donating cobblestone gave %s", r.result());
					equal(512, r.amount(), "items taken");
					equal(600 - 512, c.count(player, Items.COBBLESTONE), "cobblestone left");
					double expected = DevelopmentMath.materialPoints(value(market, cobble), 512, Development.config().materialMultiplier);
					that(Math.abs(points(c, market) - expected) < 1e-6, "development after 512 cobblestone: %s, expected %s", points(c, market), expected);
					double repGained = Reputation.points(market, player.getUUID(), c.now()) - rep;
					that(Math.abs(repGained - expected * Development.config().reputationPerPoint) < 1e-6, "reputation gained: %s", repGained);
					MarketSnapshotS2C.WantedView shown = facility(view(c, player, stall), WAREHOUSE).wanted().stream()
							.filter(w -> w.item().equals(Identifier.parse("minecraft:cobblestone"))).findFirst().orElseThrow();
					that(shown.donated() == 512 && shown.required() == 512, "the screen gets %d / %d cobblestone", shown.donated(), shown.required());
					// The rest is not taken.
					r = dev(c, player, market, DevelopmentRequestC2S.Action.DONATE_ITEMS, 0, WAREHOUSE.toString());
					that(r.result() == TradeResult.NOTHING_TO_DONATE, "donating more cobblestone gave %s", r.result());
					equal(600 - 512, c.count(player, Items.COBBLESTONE), "cobblestone after the refused donation");
					c.emptyInventory(player);
					r = dev(c, player, market, DevelopmentRequestC2S.Action.DONATE_ITEMS, 0, BANK.toString());
					that(r.result() == TradeResult.NOTHING_TO_DONATE, "donating with no materials gave %s", r.result());
				})));
		cases.add(partial("materials_required", List.of("22-23b"),
				"資材が足りないと「資材が足りない」で建てられず、資材の数は4倍で、全部寄付すると建てられること。requireMaterials を OFF にすると資材なしで建てられ、materialCountMultiplier を 2 にすると必要数が半分になること（ボタンとツールチップは見ていない）",
				ctx -> town(ctx, (c, player, market, stall) -> {
					c.config(cfg -> cfg.development.requireMaterials = true);
					builder(c, player, market);
					MarketSnapshotS2C.FacilityView shown = facility(view(c, player, stall), WAREHOUSE);
					that(!shown.materialsReady(), "the warehouse is ready to build without materials");
					List<Integer> required = shown.wanted().stream().map(MarketSnapshotS2C.WantedView::required).toList();
					List<Integer> expected = FacilityCatalog.get(WAREHOUSE).wanted().stream().map(w -> w.count() * 4).toList();
					that(required.equals(expected), "materials the warehouse wants: %s, expected %s", required, expected);
					that(build(c, player, market, WAREHOUSE) == TradeResult.MATERIALS_MISSING, "building without materials was not refused");
					ServerCases.giveItemsOutsideHotbar(player, Items.OAK_LOG, required.get(0));
					ServerCases.giveItemsOutsideHotbar(player, Items.COBBLESTONE, required.get(1));
					ServerCases.giveItemsOutsideHotbar(player, Items.CHEST, required.get(2));
					DevelopmentResultS2C r = dev(c, player, market, DevelopmentRequestC2S.Action.DONATE_ITEMS, 0, WAREHOUSE.toString());
					that(r.result() == TradeResult.SUCCESS, "donating every material gave %s", r.result());
					that(facility(view(c, player, stall), WAREHOUSE).materialsReady(), "the warehouse is not ready after every material");
					that(build(c, player, market, WAREHOUSE) == TradeResult.SUCCESS, "building with every material failed");
					// Settings.
					c.config(cfg -> cfg.development.materialCountMultiplier = 2);
					int bricks = facility(view(c, player, stall), BANK).wanted().getFirst().required();
					equal(FacilityCatalog.get(BANK).wanted().getFirst().count() * 2L, bricks, "stone bricks the bank wants with materialCountMultiplier 2");
					c.config(cfg -> cfg.development.requireMaterials = false);
					that(facility(view(c, player, stall), BANK).materialsReady(), "the bank needs materials with requireMaterials off");
				})));
	}

	// ---- 22-24 .. 22-34, 22-37, 22-39: building

	private static void building(List<ServerCase> cases) {
		cases.add(partial("build_warehouse", List.of("22-24", "22-25", "22-26", "22-27"),
				"建てると発展度が減り、四隅に足場・正面に看板が立ち、看板で「建設中」が出る。建設中はほかの建物を建てられない。development complete で足場が消えて建物が建ち、完成の知らせが届き、在庫の上限と回復の速さが上がること（画面の一覧と土台は見ていない）",
				ctx -> town(ctx, (c, player, market, stall) -> {
					builder(c, player, market);
					Identifier good = market.regularGoods().stream().filter(g -> market.params(g).maxStock() < 1e9).findFirst()
							.orElseThrow(() -> new AutotestCheck.Failed("no good with a stock cap"));
					GoodParams before = market.params(good);
					double points = points(c, market);
					that(build(c, player, market, WAREHOUSE) == TradeResult.SUCCESS, "building the warehouse failed");
					Facility f = market.facilities().get(WAREHOUSE);
					that(f != null && f.state() == Facility.State.CONSTRUCTING, "the warehouse is not under construction");
					long cost = FacilityCatalog.get(WAREHOUSE).cost();
					that(Math.abs(points(c, market) - (points - cost)) < 1e-6, "development after building: %s, expected %s", points(c, market), points - cost);
					BoundingBox box = f.box();
					for (int x : new int[] {box.minX(), box.maxX()}) {
						for (int z : new int[] {box.minZ(), box.maxZ()}) {
							BlockPos p = new BlockPos(x, f.front().getY(), z);
							that(c.level().getBlockState(p).is(Blocks.SCAFFOLDING), "no scaffolding at the corner %s", p.toShortString());
						}
					}
					BlockState sign = c.level().getBlockState(f.front());
					that(sign.is(ModBlocks.FACILITY_SIGN) && !sign.getValue(FacilitySignBlock.BUILT), "no construction sign at the front: %s", sign);
					long days = Math.round(Development.config().buildDays * com.akf.tradeexpansion.core.PriceModel.TICKS_PER_DAY);
					that(Math.abs(f.due() - c.now() - days) <= 1, "due in %d ticks, expected %d", f.due() - c.now(), days);
					player.closeContainer();
					c.forget(player);
					c.emptyHand(player);
					c.click(player, f.front(), Direction.UP);
					that(c.gotMessage(player, "message.ak_trade_expansion.facility.under_construction"), "the construction sign said %s", c.messages(player));
					// 22-25
					that(build(c, player, market, QUEST_HALL) == TradeResult.BUILD_LIMIT, "a second building started while one is under construction");
					// 22-26
					c.forget(player);
					c.command(player, "aktradeex development complete");
					that(f.complete(), "the warehouse did not complete: %s", f.state());
					that(c.gotMessage(player, "message.ak_trade_expansion.facility.completed"), "no \"completed\" message: %s", c.messages(player));
					for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
						that(!c.level().getBlockState(p).is(Blocks.SCAFFOLDING), "scaffolding is left at %s", p.toShortString());
					}
					that(f.core() != null && c.level().getBlockState(f.core()).is(ModBlocks.FACILITY_SIGN)
							&& c.level().getBlockState(f.core()).getValue(FacilitySignBlock.BUILT), "the building has no sign");
					that(solidBlocks(c, box) > 50, "the building has only %d blocks", solidBlocks(c, box));
					// 22-27
					GoodParams after = market.params(good);
					TradeExpansionConfig.Development d = Development.config();
					double cap = Math.max(before.targetStock(), Math.floor(before.maxStock() * d.warehouseStockMultiplier));
					that(after.maxStock() == cap, "stock cap of %s: %s -> %s, expected %s", good, before.maxStock(), after.maxStock(), cap);
					that(Math.abs(after.restockPerDay() - before.restockPerDay() * d.warehouseRestockMultiplier) < 1e-9, "restock of %s: %s -> %s", good,
							before.restockPerDay(), after.restockPerDay());
				})));
		cases.add(partial("quest_hall_more_requests", List.of("22-28"), "依頼所が完成すると依頼が5件まで出ること（輸送依頼の2件は見ていない）",
				ctx -> town(ctx, (c, player, market, stall) -> {
					builder(c, player, market);
					int max = TradeExpansionConfig.get().quest.maxPerMarket;
					MarketUpkeep.touch(c.level(), market);
					equal(max, market.requests().stream().filter(r -> !r.isTransport()).count(), "requests before the quest hall");
					buildAndComplete(c, player, market, QUEST_HALL);
					MarketUpkeep.touch(c.level(), market);
					equal(max + Development.config().questHallExtraQuests, market.requests().stream().filter(r -> !r.isTransport()).count(),
							"requests with a quest hall");
				})));
		cases.add(partial("inn_brings_villagers", List.of("22-29"), "宿屋が完成すると村人が2人来て、市場の周りのベッドが4つ増えること（market info の表示は見ていない）",
				ctx -> town(ctx, (c, player, market, stall) -> {
					builder(c, player, market);
					int villagers = villagers(c, market);
					long beds = MarketFactory.countBeds(c.level(), market.center());
					buildAndComplete(c, player, market, INN);
					equal(villagers + Development.config().innVillagers, villagers(c, market), "villagers after the inn");
					equal(beds + 4, MarketFactory.countBeds(c.level(), market.center()), "beds after the inn");
				})));
		cases.add(partial("bank_building", List.of("22-30"), "銀行が完成すると、レベル1の市場でも銀行が使え、カード払いの手数料がなく、預け入れでその銀行に登録されて利息の上限が2倍になること（銀行タブの表示は見ていない）",
				ctx -> town(ctx, (c, player, market, stall) -> {
					builder(c, player, market);
					that(market.level() < TradeExpansionConfig.get().bank.minMarketLevel, "the test market is already level %d", market.level());
					that(!BankService.hasBank(market), "the market has a bank before the bank building");
					buildAndComplete(c, player, market, BANK);
					that(BankService.hasBank(market), "no bank with the bank building");
					that(view(c, player, stall).bank(), "the screen is not told about the bank building");
					c.giveMoney(player, 10_000);
					BankService.handle(player, new BankRequestC2S(market.id(), BankRequestC2S.Action.DEPOSIT, 5_000));
					equal(5_000, bank(player), "bank after depositing");
					var accounts = BankService.accounts(player);
					equal(market.id(), accounts.bankFacility(player.getUUID()), "the account's bank building");
					long cap = TradeExpansionConfig.get().bank.interestMaxBalance;
					equal((long) (cap * Development.config().bankInterestMaxMultiplier), BankService.interestCap(player, accounts), "interest cap");
					BankService.handle(player, new BankRequestC2S(market.id(), BankRequestC2S.Action.ISSUE_CARD, 0));
					that(BankService.hasValidCard(player), "no valid card");
					Identifier good = c.tradableGood(market, 1, 0);
					long price = c.previewTotal(player, market, good, true, 1);
					long before = bank(player);
					var r = c.tradeRaw(player, market.id(), good, true, 1, price, true, false);
					that(r == TradeResult.SUCCESS, "buying by card gave %s", r);
					equal(before - price, bank(player), "bank after buying by card (no card fee)");
				})));
		cases.add(partial("facilities_raise_level", List.of("22-31", "22-32"),
				"建物2つで市場の規模が1つ上がって「市場が成長しました」が届き、4つで2つ上がること。完成した建物の看板を右クリックすると、その建物のタブで交易画面が開くこと（画面の表示は見ていない）",
				ctx -> town(ctx, (c, player, market, stall) -> {
					builder(c, player, market);
					buildAndComplete(c, player, market, QUEST_HALL);
					c.forget(player);
					buildAndComplete(c, player, market, WAREHOUSE);
					equal(1, MarketFactory.levelBonus(market), "level bonus with 2 buildings");
					equal(DevelopmentMath.effectiveLevel(market.measuredLevel(), 1), market.level(), "level with 2 buildings");
					that(c.gotMessage(player, "message.ak_trade_expansion.market_grew"), "no \"market grew\" message: %s", c.messages(player));
					buildAndComplete(c, player, market, INN);
					buildAndComplete(c, player, market, BANK);
					equal(2, MarketFactory.levelBonus(market), "level bonus with 4 buildings");
					equal(DevelopmentMath.effectiveLevel(market.measuredLevel(), 2), market.level(), "level with 4 buildings");
					for (Identifier id : List.of(QUEST_HALL, WAREHOUSE, INN, BANK)) {
						Facility f = market.facilities().get(id);
						player.closeContainer();
						c.emptyHand(player);
						c.click(player, f.core(), Direction.UP);
						that(player.containerMenu instanceof MarketMenu menu && menu.marketId() == market.id(), "the sign of %s opened no trade screen", id);
						equal(0, ((MarketMenu) player.containerMenu).initialTab().compareTo(f.kind().tab()), "tab opened by the sign of " + id + " ("
								+ ((MarketMenu) player.containerMenu).initialTab() + ")");
					}
				})));
		cases.add(partial("facility_destroyed_and_rebuilt", List.of("22-33", "22-34"),
				"看板以外を壊しても効果は消えず、看板を壊すと「壊れました」が届いて効果が消え、半分の発展度で別の場所に建て直せて、宿屋は建て直しても村人が来ないこと（一覧の表示は見ていない）",
				ctx -> town(ctx, (c, player, market, stall) -> {
					builder(c, player, market);
					buildAndComplete(c, player, market, INN);
					Facility inn = market.facilities().get(INN);
					BoundingBox old = inn.box();
					int villagers = villagers(c, market);
					// 22-34: a wall block.
					BlockPos wall = wallBlock(c, inn);
					that(breakBlock(player, wall), "the wall block could not be broken");
					that(inn.complete(), "breaking a wall block ended the building: %s", inn.state());
					// 22-33: the sign.
					c.forget(player);
					that(breakBlock(player, inn.core()), "the sign could not be broken");
					that(inn.state() == Facility.State.DESTROYED, "the inn is not destroyed: %s", inn.state());
					that(!market.hasFacility(FacilityKind.INN), "the inn still works without its sign");
					that(c.gotMessage(player, "message.ak_trade_expansion.facility.destroyed"), "no \"destroyed\" message: %s", c.messages(player));
					long half = DevelopmentMath.cost(FacilityCatalog.get(INN).cost(), true, Development.config().rebuildCostRatio);
					equal(half, FacilityBuilder.cost(market, INN), "cost of rebuilding");
					double points = points(c, market);
					that(build(c, player, market, INN) == TradeResult.SUCCESS, "rebuilding the inn failed");
					c.clearAfter(old); // the old building stays where it stood
					that(Math.abs(points(c, market) - (points - half)) < 1e-6, "rebuilding spent %s points, expected %d", points - points(c, market), half);
					that(!inn.box().intersects(old), "the inn was rebuilt over the old one: %s / %s", inn.box(), old);
					c.command(player, "aktradeex development complete");
					that(inn.complete(), "the rebuilt inn did not complete");
					equal(villagers, villagers(c, market), "villagers after rebuilding the inn");
				})));
		cases.add(full("no_site", List.of("22-37"), ctx -> {
			ctx.config(cfg -> cfg.development.siteRadius = 12);
			town(ctx, (c, player, market, stall) -> {
				builder(c, player, market);
				// Posts every 4 blocks: no free 5×5 place anywhere a building could reach.
				BlockPos center = c.center();
				for (int dx = -28; dx <= 28; dx += 4) {
					for (int dz = -28; dz <= 28; dz += 4) {
						BlockPos p = center.offset(dx, 0, dz);
						if (c.level().getBlockState(p).isAir()) c.level().setBlock(p, Blocks.OAK_LOG.defaultBlockState(), 3);
					}
				}
				double points = points(c, market);
				that(build(c, player, market, QUEST_HALL) == TradeResult.NO_SITE, "building with no free place did not say \"no site\"");
				that(points(c, market) == points, "a build with no site spent points");
				that(market.facilities().isEmpty(), "a build with no site started a facility");
			});
		}));
		cases.add(full("protect_facilities", List.of("22-39"), ctx -> town(ctx, (c, player, market, stall) -> {
			builder(c, player, market);
			buildAndComplete(c, player, market, WAREHOUSE);
			Facility f = market.facilities().get(WAREHOUSE);
			c.config(cfg -> cfg.development.protectFacilities = true);
			BlockPos wall = wallBlock(c, f);
			BlockState state = c.level().getBlockState(wall);
			c.forget(player);
			that(!breakBlock(player, wall), "a protected wall block was broken in survival");
			that(c.level().getBlockState(wall).equals(state), "the protected wall block changed");
			that(c.gotMessage(player, "message.ak_trade_expansion.facility.protected"), "no \"cannot be broken\" message: %s", c.messages(player));
			int blocks = solidBlocks(c, f.box());
			var mid = f.box().getCenter();
			c.level().explode(null, mid.getX() + 0.5, f.box().minY() + 1.5, mid.getZ() + 0.5, 4.0f, Level.ExplosionInteraction.TNT);
			equal(blocks, solidBlocks(c, f.box()), "blocks of the building after an explosion");
			that(f.complete(), "the explosion ended the building");
			player.setGameMode(GameType.CREATIVE);
			that(breakBlock(player, wall), "a protected block could not be broken in creative");
			that(c.level().getBlockState(wall).isAir(), "the block broken in creative is still there");
			player.setGameMode(GameType.SURVIVAL);
		})));
	}

	// ---- 22-35, 22-36, 22-38: building site stakes

	private static void stakes(List<ServerCase> cases) {
		cases.add(partial("site_stakes", List.of("22-35"), "「杭をもらう」で杭が1本手に入り、持っている時はもらえない。市場の近くに置くと建設予定地になり、遠くに置くと「市場がありません」が届くこと（パーティクルの枠は見ていない）",
				ctx -> town(ctx, (c, player, market, stall) -> {
					DevelopmentResultS2C r = dev(c, player, market, DevelopmentRequestC2S.Action.MARKER, 0, "");
					that(r.result() == TradeResult.SUCCESS, "taking a stake gave %s", r.result());
					equal(1, c.count(player, ModBlocks.CONSTRUCTION_MARKER.asItem()), "stakes");
					r = dev(c, player, market, DevelopmentRequestC2S.Action.MARKER, 0, "");
					that(r.result() == TradeResult.HAS_MARKER, "a second stake gave %s", r.result());
					equal(1, c.count(player, ModBlocks.CONSTRUCTION_MARKER.asItem()), "stakes after asking again");
					BlockPos spot = stall.south(14);
					c.forget(player);
					placeStake(c, player, spot);
					that(c.level().getBlockState(spot).is(ModBlocks.CONSTRUCTION_MARKER), "the stake was not placed");
					that(market.markers().contains(spot), "the stake did not become a building site: %s", market.markers());
					that(c.gotMessage(player, "message.ak_trade_expansion.facility.marker_placed"), "no \"building site\" message: %s", c.messages(player));
					// Far from any market.
					BlockPos far = c.center().east(90);
					c.floor(far);
					player.closeContainer();
					player.teleportTo(far.getX() + 0.5, far.getY(), far.getZ() - 1.5);
					c.forget(player);
					placeStake(c, player, far);
					that(c.gotMessage(player, "message.ak_trade_expansion.facility.marker_no_market"), "no \"no market\" message far away: %s", c.messages(player));
					that(c.registry().all().stream().noneMatch(m -> m.markers().contains(far)), "the far stake became a building site");
				})));
		cases.add(full("build_at_stake", List.of("22-36"), ctx -> town(ctx, (c, player, market, stall) -> {
			builder(c, player, market);
			BlockPos spot = stall.south(14);
			placeStake(c, player, spot);
			Rotation rotation = com.akf.tradeexpansion.development.SiteFinder.rotation(
					c.level().getBlockState(spot).getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING));
			// Something in the way.
			BlockPos tree = spot.south(3);
			c.level().setBlock(tree, Blocks.OAK_LOG.defaultBlockState(), 3);
			double points = points(c, market);
			c.forget(player);
			that(build(c, player, market, QUEST_HALL) == TradeResult.MARKER_BLOCKED, "building over a log at the stake was not refused");
			that(c.gotMessage(player, "message.ak_trade_expansion.facility.marker_blocked"), "no reason for the blocked stake: %s", c.messages(player));
			that(points(c, market) == points && market.facilities().isEmpty(), "the blocked build changed something");
			that(c.level().getBlockState(spot).is(ModBlocks.CONSTRUCTION_MARKER), "the stake is gone after the blocked build");
			c.level().setBlock(tree, Blocks.AIR.defaultBlockState(), 3);
			that(build(c, player, market, QUEST_HALL) == TradeResult.SUCCESS, "building at the stake failed");
			Facility f = market.facilities().get(QUEST_HALL);
			that(f.front().equals(spot), "the front is at %s, not at the stake %s", f.front().toShortString(), spot.toShortString());
			that(f.rotation() == rotation, "the building is turned %s, the stake %s", f.rotation(), rotation);
			that(!c.level().getBlockState(spot).is(ModBlocks.CONSTRUCTION_MARKER), "the stake is still there");
			that(market.markers().isEmpty(), "the used stake is still a building site: %s", market.markers());
		})));
		cases.add(full("site_ground_kinds", List.of("22-38"), ctx -> town(ctx, (c, player, market, stall) -> {
			builder(c, player, market);
			BlockPos spot = stall.south(14);
			placeStake(c, player, spot);
			BlockPos ground = spot.offset(1, -1, 2);
			for (BlockState bad : List.of(Blocks.DIRT_PATH.defaultBlockState(), Blocks.WATER.defaultBlockState(), Blocks.ICE.defaultBlockState())) {
				c.level().setBlock(ground, bad, 3);
				TradeResult r = build(c, player, market, QUEST_HALL);
				that(r == TradeResult.MARKER_BLOCKED, "building over %s gave %s", bad.getBlock(), r);
			}
			c.level().setBlock(ground, Blocks.STONE.defaultBlockState(), 3);
			c.level().setBlock(ground.above(), Blocks.SNOW.defaultBlockState(), 3);
			TradeResult r = build(c, player, market, QUEST_HALL);
			that(r == TradeResult.SUCCESS, "building over a snow layer gave %s", r);
		})));
	}

	// ---- helpers

	/** {@link ServerCases#withMarket} on a stone floor of about 73×73 blocks, with no materials needed. */
	private static void town(ServerCtx ctx, ServerCases.MarketBody body) {
		for (int dx = -24; dx <= 24; dx += 24) {
			for (int dz = -24; dz <= 24; dz += 24) {
				if (dx != 0 || dz != 0) ctx.floor(ctx.center().offset(dx, 0, dz));
			}
		}
		ctx.config(cfg -> cfg.development.requireMaterials = false);
		ServerCases.withMarket(ctx, body);
	}

	/** Enough reputation and development points to build anything. */
	private static void builder(ServerCtx c, ServerPlayer player, Market market) {
		c.command(player, "aktradeex reputation set " + threshold(Development.config().buildReputationLevel));
		c.command(player, "aktradeex development add 100000");
		that(points(c, market) >= 100_000, "\"development add\" did not add points");
	}

	private static void buildAndComplete(ServerCtx c, ServerPlayer player, Market market, Identifier id) {
		TradeResult r = build(c, player, market, id);
		that(r == TradeResult.SUCCESS, "building %s gave %s", id, r);
		c.command(player, "aktradeex development complete");
		that(market.facilities().get(id).complete(), "%s did not complete", id);
	}

	private static TradeResult build(ServerCtx c, ServerPlayer player, Market market, Identifier id) {
		return dev(c, player, market, DevelopmentRequestC2S.Action.BUILD, 0, id.toString()).result();
	}

	private static DevelopmentResultS2C donate(ServerCtx c, ServerPlayer player, Market market, long amount, boolean card) {
		ensureOpen(c, player, market);
		c.forget(player);
		DevelopmentService.handle(player, new DevelopmentRequestC2S(market.id(), DevelopmentRequestC2S.Action.DONATE_COINS, amount, "", card));
		return result(c, player);
	}

	/** Sends a development request from the market's trade screen (opened first if needed); returns the answer. */
	private static DevelopmentResultS2C dev(ServerCtx c, ServerPlayer player, Market market, DevelopmentRequestC2S.Action action, long amount, String facility) {
		ensureOpen(c, player, market);
		int before = c.payloads(player, DevelopmentResultS2C.class).size(); // the chat is kept for the case's checks
		DevelopmentService.handle(player, new DevelopmentRequestC2S(market.id(), action, amount, facility, false));
		List<DevelopmentResultS2C> results = c.payloads(player, DevelopmentResultS2C.class);
		that(results.size() > before, "no answer to %s", action);
		return results.getLast();
	}

	private static DevelopmentResultS2C result(ServerCtx c, ServerPlayer player) {
		List<DevelopmentResultS2C> results = c.payloads(player, DevelopmentResultS2C.class);
		that(!results.isEmpty(), "no development result");
		return results.getLast();
	}

	/** The player stands at the market's stall with its trade screen open. */
	private static void ensureOpen(ServerCtx c, ServerPlayer player, Market market) {
		BlockPos stall = market.stalls().iterator().next();
		if (player.containerMenu instanceof MarketMenu menu && menu.marketId() == market.id() && menu.stillValid(player)) return;
		player.closeContainer();
		player.teleportTo(stall.getX() + 0.5, stall.getY(), stall.getZ() + 2.5);
		c.openStall(player, stall);
	}

	/** What the development tab gets when the screen is opened now. */
	private static MarketSnapshotS2C.DevelopmentView view(ServerCtx c, ServerPlayer player, BlockPos stall) {
		player.closeContainer();
		c.forget(player);
		c.openStall(player, stall);
		MarketSnapshotS2C snapshot = c.lastSnapshot(player);
		that(snapshot != null, "no snapshot when the screen opened");
		return snapshot.development();
	}

	private static MarketSnapshotS2C.FacilityView facility(MarketSnapshotS2C.DevelopmentView view, Identifier id) {
		return view.facilities().stream().filter(f -> f.id().equals(id.toString())).findFirst()
				.orElseThrow(() -> new AutotestCheck.Failed("the development tab has no " + id + ": " + view.facilities().stream().map(MarketSnapshotS2C.FacilityView::id).toList()));
	}

	private static double points(ServerCtx c, Market market) {
		return Development.points(market, c.now());
	}

	private static int threshold(int level) {
		int[] t = TradeExpansionConfig.get().reputation.levelThresholdArray();
		return level <= 0 ? 0 : t[Math.min(level, t.length) - 1];
	}

	private static long bank(ServerPlayer player) {
		return BankService.accounts(player).balance(player.getUUID());
	}

	private static TradeResult questResult(ServerCtx c, ServerPlayer player) {
		var list = c.payloads(player, QuestResultS2C.class);
		return list.isEmpty() ? null : list.getLast().result();
	}

	/** Base price of a wanted material as the donation counts it (the market's B of a plain good, else the entry's value). */
	private static double value(Market market, FacilityDefinition.Wanted wanted) {
		if (wanted.item().isPresent()) {
			for (var e : GoodsCatalog.all().entrySet()) {
				GoodsDefinition g = e.getValue();
				if (g.item().equals(wanted.item().get()) && g.unit() == 1 && g.components().isEmpty() && g.autoAdd().isEmpty()) {
					return market.basePriceFor(e.getKey());
				}
			}
		}
		return wanted.value();
	}

	/** Villagers around the market (the village's and any that came to an inn). */
	private static int villagers(ServerCtx c, Market market) {
		return c.level().getEntitiesOfClass(Villager.class, new AABB(market.center()).inflate(48, 12, 48)).size();
	}

	/** Blocks other than air in {@code box}. */
	private static int solidBlocks(ServerCtx c, BoundingBox box) {
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (!c.level().getBlockState(p).isAir()) n++;
		}
		return n;
	}

	/** A plain block of the building's walls (not its sign, no block entity). */
	private static BlockPos wallBlock(ServerCtx c, Facility f) {
		BoundingBox box = f.box();
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY() + 1, box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			BlockState s = c.level().getBlockState(p);
			if (!s.isAir() && !s.canBeReplaced() && !s.is(ModBlocks.FACILITY_SIGN) && !s.hasBlockEntity() && s.isCollisionShapeFullBlock(c.level(), p)) {
				return p.immutable();
			}
		}
		throw new AutotestCheck.Failed("the building " + f.id() + " has no wall block");
	}

	/** The player breaks the block as by hand (survival rules and the break events). @return it was broken */
	private static boolean breakBlock(ServerPlayer player, BlockPos pos) {
		return player.gameMode.destroyBlock(pos);
	}

	/** The player places a building site stake at {@code pos} (on the block below). */
	private static void placeStake(ServerCtx c, ServerPlayer player, BlockPos pos) {
		c.emptyHand(player);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModBlocks.CONSTRUCTION_MARKER.asItem()));
		c.click(player, pos.below(), Direction.UP);
	}
}
