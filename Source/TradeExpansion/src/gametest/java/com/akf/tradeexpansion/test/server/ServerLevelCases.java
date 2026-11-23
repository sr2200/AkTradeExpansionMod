package com.akf.tradeexpansion.test.server;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.core.FacilityLevelMath;
import com.akf.tradeexpansion.data.FacilityCatalog;
import com.akf.tradeexpansion.development.Development;
import com.akf.tradeexpansion.development.Facility;
import com.akf.tradeexpansion.development.FacilityBuilder;
import com.akf.tradeexpansion.development.FacilityEffects;
import com.akf.tradeexpansion.development.FacilityKind;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.network.DevelopmentRequestC2S;
import com.akf.tradeexpansion.network.MarketSnapshotS2C;
import com.akf.tradeexpansion.trade.TradeResult;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

import static com.akf.tradeexpansion.test.server.AutotestCheck.equal;
import static com.akf.tradeexpansion.test.server.AutotestCheck.that;
import static com.akf.tradeexpansion.test.server.ServerCase.partial;
import static com.akf.tradeexpansion.test.server.ServerDevelopmentCases.breakBlock;
import static com.akf.tradeexpansion.test.server.ServerDevelopmentCases.build;
import static com.akf.tradeexpansion.test.server.ServerDevelopmentCases.buildAndComplete;
import static com.akf.tradeexpansion.test.server.ServerDevelopmentCases.builder;
import static com.akf.tradeexpansion.test.server.ServerDevelopmentCases.dev;
import static com.akf.tradeexpansion.test.server.ServerDevelopmentCases.facility;
import static com.akf.tradeexpansion.test.server.ServerDevelopmentCases.points;
import static com.akf.tradeexpansion.test.server.ServerDevelopmentCases.town;
import static com.akf.tradeexpansion.test.server.ServerDevelopmentCases.view;

/** Checklist 44 (v0.86, design N-11): raising the buildings to level 2 and 3. */
final class ServerLevelCases {
	private static final Identifier WAREHOUSE = TradeExpansion.id("warehouse");
	private static final Identifier INN = TradeExpansion.id("inn");
	private static final Identifier FOUNTAIN = TradeExpansion.id("fountain");

	private ServerLevelCases() {
	}

	static List<ServerCase> all() {
		List<ServerCase> cases = new ArrayList<>();
		cases.add(partial("facility_upgrade_level2", List.of("44-2", "44-3", "44-5"),
				"完成した倉庫を「大きな蔵」で Lv2 に上げると、建てる時と同じ発展度が減って工事中になり、工事中も効果が続き、終わると「Lv2 になりました」が届いて Lv2 になること。"
						+ "建物の飾りが増え、チェストに入れた物は残り、在庫の上限の倍率が 1.5 → 1.875、補充の速さが 1.5 → 1.625 になること（画面の表示と見た目そのものは見ていない）",
				ctx -> town(ctx, (c, player, market, stall) -> {
					builder(c, player, market);
					buildAndComplete(c, player, market, WAREHOUSE);
					Facility f = market.facilities().get(WAREHOUSE);
					equal(1, f.level(), "level of a new warehouse");
					BlockPos chest = chestIn(c, f.box());
					((ChestBlockEntity) c.level().getBlockEntity(chest)).setItem(0, new ItemStack(Items.DIAMOND, 3));
					int blocks = solidBlocks(c, f.box());
					long cost = FacilityCatalog.get(WAREHOUSE).cost();
					equal(cost, FacilityBuilder.cost(market, WAREHOUSE), "cost of raising the warehouse to level 2");
					double before = points(c, market);
					TradeResult r = upgrade(c, player, market, WAREHOUSE, 0);
					that(r == TradeResult.SUCCESS, "raising the warehouse gave %s", r);
					that(Math.abs(points(c, market) - (before - cost)) < 1e-6, "raising spent %s points, expected %d", before - points(c, market), cost);
					that(f.upgrading() && f.complete(), "the warehouse is not being raised (or lost its effect): %s", f.state());
					that(market.hasFacility(FacilityKind.WAREHOUSE), "the warehouse lost its effect while being raised");
					that(f.branch().equals("a"), "direction of the warehouse: %s", f.branch());
					that(upgrade(c, player, market, WAREHOUSE, 0) == TradeResult.ALREADY_BUILT, "raising it again while it is being raised did not fail");
					c.forget(player);
					c.command(player, "aktradeex development complete");
					equal(2, f.level(), "level after the raise");
					that(!f.upgrading(), "the raise did not end");
					that(c.gotMessage(player, "message.ak_trade_expansion.facility.upgraded"), "no \"is now Lv2\" message: %s", c.messages(player));
					that(solidBlocks(c, f.box()) > blocks, "no decorations were added (%d blocks, before %d)", solidBlocks(c, f.box()), blocks);
					ItemStack kept = ((ChestBlockEntity) c.level().getBlockEntity(chest)).getItem(0);
					that(kept.is(Items.DIAMOND) && kept.getCount() == 3, "the chest lost what was in it: %s", kept);
					double stock = Development.config().warehouseStockMultiplier;
					double restock = Development.config().warehouseRestockMultiplier;
					that(Math.abs(FacilityEffects.raise(market, FacilityKind.WAREHOUSE, 0, stock) - FacilityLevelMath.raise(stock, 1.75)) < 1e-9,
							"stock multiplier at level 2 (a): %s", FacilityEffects.raise(market, FacilityKind.WAREHOUSE, 0, stock));
					that(Math.abs(FacilityEffects.raise(market, FacilityKind.WAREHOUSE, 1, restock) - FacilityLevelMath.raise(restock, 1.25)) < 1e-9,
							"restock multiplier at level 2 (a): %s", FacilityEffects.raise(market, FacilityKind.WAREHOUSE, 1, restock));
					MarketSnapshotS2C.FacilityView view = facility(view(c, player, stall), WAREHOUSE);
					equal(2, view.level(), "level on the development tab");
					that(view.branch().equals("a"), "direction on the development tab: %s", view.branch());
					equal(FacilityLevelMath.scaled(cost, 1.5), view.cost(), "cost of level 3 on the development tab");
				})));
		cases.add(partial("facility_upgrade_level3_tall", List.of("44-4", "44-5"),
				"宿屋を「大部屋」で Lv2・Lv3 に上げると、そのたびに村人が増え（2人 → 4人 → 5人の分）、上が空いている Lv3 は2階建てになって建物の高さが 4 増えること。"
						+ "上をふさいだ倉庫は2階なしで Lv3 になり、そのことがチャットに出ること。Lv3 はそれ以上上げられないこと（2階の見た目そのものは見ていない）",
				ctx -> town(ctx, (c, player, market, stall) -> {
					builder(c, player, market);
					buildAndComplete(c, player, market, INN);
					Facility inn = market.facilities().get(INN);
					int villagers = villagers(c, market);
					that(upgrade(c, player, market, INN, 0) == TradeResult.SUCCESS, "raising the inn to level 2 failed");
					c.command(player, "aktradeex development complete");
					int base = Development.config().innVillagers;
					int two = FacilityLevelMath.count(base, 1.75) - base;
					equal(villagers + two, villagers(c, market), "villagers after the inn's level 2");
					BoundingBox low = inn.box();
					that(upgrade(c, player, market, INN, 0) == TradeResult.SUCCESS, "raising the inn to level 3 failed");
					that(inn.upgradeTall(), "the inn with free space above does not get a second storey");
					c.command(player, "aktradeex development complete");
					equal(3, inn.level(), "level of the inn");
					that(inn.tall(), "the inn has no second storey");
					equal(low.getYSpan() + 4, inn.box().getYSpan(), "height of the inn with its second storey");
					c.clearAfter(inn.box());
					equal(villagers + FacilityLevelMath.count(base, 2.5) - base, villagers(c, market), "villagers after the inn's level 3");
					that(inn.core() != null && c.level().getBlockState(inn.core()).is(com.akf.tradeexpansion.registry.ModBlocks.FACILITY_SIGN),
							"the inn's sign is gone after the second storey");
					that(upgrade(c, player, market, INN, 0) == TradeResult.MAX_LEVEL, "raising a level 3 building did not say \"top level\"");

					// The warehouse: a block over it, no second storey.
					c.forget(player);
					buildAndComplete(c, player, market, WAREHOUSE);
					Facility wh = market.facilities().get(WAREHOUSE);
					that(upgrade(c, player, market, WAREHOUSE, 1) == TradeResult.SUCCESS, "raising the warehouse to level 2 failed");
					c.command(player, "aktradeex development complete");
					BoundingBox box = wh.box();
					BlockPos above = new BlockPos((box.minX() + box.maxX()) / 2, box.maxY() + 2, (box.minZ() + box.maxZ()) / 2);
					c.level().setBlockAndUpdate(above, Blocks.STONE.defaultBlockState());
					c.atCleanUp(() -> c.level().setBlockAndUpdate(above, Blocks.AIR.defaultBlockState()));
					c.forget(player);
					that(upgrade(c, player, market, WAREHOUSE, 1) == TradeResult.SUCCESS, "raising the covered warehouse to level 3 failed");
					that(!wh.upgradeTall(), "the covered warehouse gets a second storey");
					that(c.gotMessage(player, "message.ak_trade_expansion.facility.upgrade.no_room"), "no \"no room above\" message: %s", c.messages(player));
					c.command(player, "aktradeex development complete");
					equal(3, wh.level(), "level of the covered warehouse");
					equal(box.getYSpan(), wh.box().getYSpan(), "height of the warehouse without a second storey");
					that(c.level().getBlockState(above).is(Blocks.STONE), "the block over the warehouse was removed");
				})));
		cases.add(partial("facility_upgrade_checks", List.of("44-2", "44-7"),
				"建っていない建物は「完成した建物だけ」で上げられず、評判・同時の工事の数・発展度が足りないと建てる時と同じ理由で上げられず、Lv1 の方向は 0 か 1 だけなこと。"
						+ "噴水を「にぎわう広場」にすると、噴水の評判の倍率は少し、発展度は +15% になること。upgradeEnabled を OFF にすると上げられず、効果は Lv1 と同じになること（ボタンの表示は見ていない）",
				ctx -> town(ctx, (c, player, market, stall) -> {
					builder(c, player, market);
					that(upgrade(c, player, market, WAREHOUSE, 0) == TradeResult.NOT_BUILT, "raising a building that is not built did not say so");
					buildAndComplete(c, player, market, FOUNTAIN);
					TradeResult third = dev(c, player, market, DevelopmentRequestC2S.Action.UPGRADE, 2, FOUNTAIN.toString()).result();
					that(third == TradeResult.INVALID_REQUEST, "a third direction gave %s", third);
					c.command(player, "aktradeex reputation set 0");
					that(upgrade(c, player, market, FOUNTAIN, 1) == TradeResult.REPUTATION_TOO_LOW, "raising with no reputation did not fail");
					builder(c, player, market);
					double points = points(c, market);
					market.setDevelopment(0, c.now());
					that(upgrade(c, player, market, FOUNTAIN, 1) == TradeResult.NOT_ENOUGH_POINTS, "raising with no points did not fail");
					market.setDevelopment(points, c.now());
					that(upgrade(c, player, market, FOUNTAIN, 1) == TradeResult.SUCCESS, "raising the fountain failed");
					c.command(player, "aktradeex development complete");
					double rep = Development.config().fountainReputationMultiplier;
					that(Math.abs(FacilityEffects.raise(market, FacilityKind.FOUNTAIN, 0, rep) - FacilityLevelMath.raise(rep, 1.25)) < 1e-9,
							"the fountain's reputation multiplier at level 2 (b): %s", FacilityEffects.raise(market, FacilityKind.FOUNTAIN, 0, rep));
					that(Math.abs(FacilityEffects.perk(market, FacilityKind.Perk.DEVELOPMENT) - (1 + Development.config().upgradePerkBonus)) < 1e-9,
							"more points with the busy square: %s", FacilityEffects.perk(market, FacilityKind.Perk.DEVELOPMENT));
					that(FacilityEffects.perk(market, FacilityKind.Perk.REPUTATION) == 1.0, "the busy square gives more reputation");
					// Points gained: the park's bonus is not built, the square adds 15 %.
					double now = points(c, market);
					double added = Development.add(market, player, 100, c.now());
					that(Math.abs(added - 100 * (1 + Development.config().upgradePerkBonus) * Development.vigorMultiplier(market)) < 1e-6,
							"points added with the busy square: %s", added);
					that(points(c, market) > now, "no points were added");
					// Off: no raise, the effects of level 1.
					c.config(cfg -> cfg.development.upgradeEnabled = false);
					that(upgrade(c, player, market, FOUNTAIN, 0) == TradeResult.NOT_AVAILABLE, "raising with the levels off did not fail");
					that(FacilityEffects.raise(market, FacilityKind.FOUNTAIN, 0, rep) == rep, "the fountain is stronger with the levels off");
					that(FacilityEffects.perk(market, FacilityKind.Perk.DEVELOPMENT) == 1.0, "the busy square adds points with the levels off");
					that(!view(c, player, stall).levels().enabled(), "the development tab shows the levels while they are off");
				})));
		cases.add(partial("facility_rebuilt_at_level", List.of("44-6"),
				"Lv2 の倉庫の看板を壊すと壊れ、建て直す費用は建てた分と Lv2 に上げた分の合計の半分で、建て直すと Lv2 のまま、Lv2 の見た目で建つこと（見た目そのものは見ていない）",
				ctx -> town(ctx, (c, player, market, stall) -> {
					builder(c, player, market);
					buildAndComplete(c, player, market, WAREHOUSE);
					Facility f = market.facilities().get(WAREHOUSE);
					that(upgrade(c, player, market, WAREHOUSE, 1) == TradeResult.SUCCESS, "raising the warehouse failed");
					c.command(player, "aktradeex development complete");
					int blocks = solidBlocks(c, f.box());
					BoundingBox old = f.box();
					that(breakBlock(player, f.core()), "the sign could not be broken");
					that(f.state() == Facility.State.DESTROYED, "the warehouse is not destroyed");
					equal(2, f.level(), "level of the destroyed warehouse");
					long cost = FacilityCatalog.get(WAREHOUSE).cost();
					long expected = FacilityLevelMath.scaled(cost, FacilityLevelMath.totalFactor(2, Development.config().upgradeCostMultipliers)
							* Development.config().rebuildCostRatio);
					equal(expected, FacilityBuilder.cost(market, WAREHOUSE), "cost of rebuilding the level 2 warehouse");
					that(build(c, player, market, WAREHOUSE) == TradeResult.SUCCESS, "rebuilding the warehouse failed");
					c.clearAfter(old);
					c.command(player, "aktradeex development complete");
					that(f.complete(), "the rebuilt warehouse did not complete");
					equal(2, f.level(), "level of the rebuilt warehouse");
					that(f.branch().equals("b"), "direction of the rebuilt warehouse: %s", f.branch());
					equal(blocks, solidBlocks(c, f.box()), "blocks of the rebuilt warehouse (the level 2 look)");
				})));
		return cases;
	}

	static TradeResult upgrade(ServerCtx c, ServerPlayer player, Market market, Identifier id, int branch) {
		return dev(c, player, market, DevelopmentRequestC2S.Action.UPGRADE, branch, id.toString()).result();
	}

	private static BlockPos chestIn(ServerCtx c, BoundingBox box) {
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (c.level().getBlockEntity(p) instanceof ChestBlockEntity) return p.immutable();
		}
		throw new AutotestCheck.Failed("the building has no chest");
	}

	private static int solidBlocks(ServerCtx c, BoundingBox box) {
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (!c.level().getBlockState(p).isAir()) n++;
		}
		return n;
	}

	private static int villagers(ServerCtx c, Market market) {
		return c.level().getEntitiesOfClass(Villager.class, new AABB(market.center()).inflate(48, 12, 48)).size();
	}
}
