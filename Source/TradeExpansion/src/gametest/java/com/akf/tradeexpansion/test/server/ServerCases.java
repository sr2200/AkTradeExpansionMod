package com.akf.tradeexpansion.test.server;

import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.registry.ModBlocks;
import com.akf.tradeexpansion.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.akf.tradeexpansion.test.server.AutotestCheck.equal;
import static com.akf.tradeexpansion.test.server.AutotestCheck.that;
import static com.akf.tradeexpansion.test.server.ServerCase.full;
import static com.akf.tradeexpansion.test.server.ServerCase.partial;

/**
 * The server-side checks of docs/release/ingame-checklist.md, in checklist order. Each case names the checklist items
 * it covers; {@code partial} cases say (in Japanese, shown in the checklist) what part of the item they check.
 */
public final class ServerCases {
	private ServerCases() {
	}

	/** Ticks for the POI of new beds and new entities to be seen. */
	private static final int SETTLE = 3;

	public static List<ServerCase> all() {
		List<ServerCase> cases = new ArrayList<>();

		// ---- 2. stall and basic trades
		cases.add(full("stall_recipe", List.of("2-1"), ctx -> {
			Item P = Items.OAK_PLANKS;
			ItemStack out = craft(ctx, Items.CARPET.white(), Items.CARPET.white(), Items.CARPET.white(), P, Items.EMERALD, P, P, Items.BARREL, P);
			that(out.is(ModBlocks.MARKET_STALL.asItem()), "the stall recipe gave %s", out);
		}));
		cases.add(partial("stall_in_village", List.of("2-3"), "置けて市場ができることのみ（チャットの文言は見ていない）", ctx -> {
			BlockPos stall = ctx.center();
			ctx.village(stall, 3, 3);
			ctx.later(SETTLE, c -> {
				ServerPlayer player = c.player(stall.south(2));
				c.placeStall(player, stall);
				that(c.isStall(stall), "the stall was not placed");
				that(c.marketAt(stall) != null, "no market was founded");
			});
		}));
		cases.add(partial("stall_outside_village", List.of("2-4"), "置けずに交易所が戻ることのみ（チャットの理由は見ていない）", ctx -> {
			BlockPos stall = ctx.center();
			ServerPlayer player = ctx.player(stall.south(2));
			ctx.placeStall(player, stall);
			that(!ctx.isStall(stall), "the stall stayed without beds or villagers");
			that(ctx.registry().all().isEmpty(), "a market was founded");
			ctx.later(1, c -> that(!c.level().getEntitiesOfClass(ItemEntity.class, new AABB(stall).inflate(3),
					e -> e.getItem().is(ModBlocks.MARKET_STALL.asItem())).isEmpty(), "the stall item did not drop"));
		}));
		cases.add(partial("stall_too_close", List.of("2-5"), "64〜256ブロックの間に置けないことのみ（理由の文言は見ていない）", ctx -> {
			BlockPos first = ctx.center();
			ctx.village(first, 3, 3);
			BlockPos second = first.east(100);
			ctx.floor(second);
			ctx.village(second, 3, 3);
			ctx.later(SETTLE, c -> {
				ServerPlayer player = c.player(first.south(2));
				c.placeStall(player, first);
				that(c.marketAt(first) != null, "the first market was not founded");
				ServerPlayer other = c.player(second.south(2));
				c.placeStall(other, second);
				that(!c.isStall(second), "a stall 100 blocks away was placed");
				equal(1, c.registry().all().size(), "markets");
			});
		}));
		cases.add(partial("stall_opens_menu", List.of("2-8"), "サーバー側で交易画面が開くことのみ", ctx -> withMarket(ctx, (c, player, market, stall) -> {
			var menu = c.openStall(player, stall);
			equal(market.id(), menu.marketId(), "market of the opened screen");
		})));
		cases.add(partial("buy_pays_total", List.of("2-10"), "品が手に入り、所持金が合計額どおり減ることのみ（財布へのお釣りは見ていない）", ctx -> withMarket(ctx, (c, player, market, stall) -> {
			c.openStall(player, stall);
			c.giveMoney(player, 100_000);
			Identifier good = c.tradableGood(market, 3, 0);
			Item item = c.item(good);
			int before = c.count(player, item);
			long money = c.balance(player);
			long total = c.trade(player, market, good, true, 3);
			equal(before + 3, c.count(player, item), "items after buying 3");
			equal(money - total, c.balance(player), "money after buying");
		})));
		cases.add(partial("buy_raises_price", List.of("2-11"), "買うたびに値段が上がることのみ（売り切れの表示は見ていない）", ctx -> withMarket(ctx, (c, player, market, stall) -> {
			c.openStall(player, stall);
			c.giveMoney(player, 100_000);
			Identifier good = c.tradableGood(market, 4, 0);
			long p0 = c.buyPrice(player, market, good);
			c.trade(player, market, good, true, 2);
			long p1 = c.buyPrice(player, market, good);
			c.trade(player, market, good, true, 2);
			long p2 = c.buyPrice(player, market, good);
			that(p0 < p1 && p1 < p2, "buy price did not rise: %d -> %d -> %d", p0, p1, p2);
		})));
		cases.add(partial("sell_pays_and_lowers_price", List.of("2-12"), "所持金が増えて値段が下がることのみ（買取停止は見ていない）", ctx -> withMarket(ctx, (c, player, market, stall) -> {
			c.openStall(player, stall);
			Identifier good = c.tradableGood(market, 0, 8);
			Item item = c.item(good);
			player.getInventory().add(new ItemStack(item, 8));
			long money = c.balance(player);
			long p0 = c.sellPrice(player, market, good);
			long total = c.trade(player, market, good, false, 8);
			equal(money + total, c.balance(player), "money after selling 8");
			equal(0, c.count(player, item), "items left after selling all 8");
			long p1 = c.sellPrice(player, market, good);
			that(p1 < p0, "sell price did not fall: %d -> %d", p0, p1);
		})));
		cases.add(partial("creative_cannot_trade", List.of("2-17"), "取引されないことのみ（メッセージは見ていない）", ctx -> withMarket(ctx, (c, player, market, stall) -> {
			c.openStall(player, stall);
			c.giveMoney(player, 100_000);
			player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
			Identifier good = c.tradableGood(market, 1, 0);
			Item item = c.item(good);
			int before = c.count(player, item);
			long money = c.balance(player);
			c.trade(player, market, good, true, 1);
			equal(before, c.count(player, item), "items after a creative trade");
			equal(money, c.balance(player), "money after a creative trade");
		})));

		// ---- 4. prices over time
		cases.add(full("advance_recovers_half", List.of("4-1"), ctx -> withMarket(ctx, (c, player, market, stall) -> {
			c.openStall(player, stall);
			Identifier good = c.tradableGood(market, 0, 16);
			long base = c.sellPrice(player, market, good);
			int qty = 16;
			player.getInventory().add(new ItemStack(c.item(good), qty));
			c.trade(player, market, good, false, qty);
			long low = c.sellPrice(player, market, good);
			that(low < base, "selling %d did not lower the price (%d -> %d)", qty, base, low);
			c.command(player, "aktradeex market advance 2");
			long after = c.sellPrice(player, market, good);
			double recovered = (double) (after - low) / (base - low);
			that(recovered > 0.3 && recovered < 0.75, "after 2 days %.0f%% of the drop came back (%d -> %d -> %d), expected about half",
					recovered * 100, base, low, after);
		})));

		// ---- 7. wallet
		cases.add(full("wallet_recipe", List.of("7-1"), ctx -> {
			Item L = Items.LEATHER, S = Items.STRING;
			ItemStack out = craft(ctx, L, S, L, L, S, L, Items.AIR, L, Items.AIR);
			that(out.is(ModItems.WALLET), "the wallet recipe gave %s", out);
		}));

		// ---- 8. coins
		cases.add(full("minting_copper_not_iron", List.of("8-3"), ctx -> {
			Item c = Items.COPPER_BLOCK.weathering().unaffected(), i = Items.IRON_BLOCK;
			ItemStack copper = craft(ctx, c, c, c, c, c, c, c, c, c);
			that(copper.is(ModItems.COPPER_COIN) && copper.getCount() == 64, "9 copper blocks gave %s", copper);
			ItemStack iron = craft(ctx, i, i, i, i, i, i, i, i, i);
			that(iron.isEmpty(), "9 iron blocks gave %s (iron minting is off by default)", iron);
		}));
		cases.add(full("emerald_coin_stonecutter", List.of("8-4"), ctx -> {
			var recipe = ctx.server().getRecipeManager().getRecipeFor(RecipeType.STONECUTTING, new SingleRecipeInput(new ItemStack(Items.EMERALD_BLOCK)), ctx.level());
			that(recipe.isPresent(), "no stonecutter recipe for the emerald block");
			ItemStack out = recipe.get().value().assemble(new SingleRecipeInput(new ItemStack(Items.EMERALD_BLOCK)));
			that(out.is(ModItems.EMERALD_COIN) && out.getCount() == 1, "the stonecutter gave %s", out);
		}));

		// ---- 17. starting money
		cases.add(partial("starting_money", List.of("17-1"), "初めて入った時に 240c もらえることのみ（入り直しは見ていない）", ctx -> {
			ServerPlayer player = ctx.player(ctx.center());
			equal(240, ctx.balance(player), "money of a new player");
		}));

		return cases;
	}

	// ---- helpers

	@FunctionalInterface
	interface MarketBody {
		void run(ServerCtx ctx, ServerPlayer player, Market market, BlockPos stall) throws Exception;
	}

	/** A village with a crafted stall and its market at the center, then {@code body} with a player next to it. */
	private static void withMarket(ServerCtx ctx, MarketBody body) {
		BlockPos stall = ctx.center();
		ctx.village(stall, 3, 3);
		ctx.later(SETTLE, c -> {
			ServerPlayer player = c.player(stall.south(2));
			c.placeStall(player, stall);
			Market market = c.marketAt(stall);
			that(market != null, "no market was founded for the case");
			body.run(c, player, market, stall);
		});
	}

	/** Result of a 3x3 crafting grid (row by row; AIR = empty), or EMPTY. */
	private static ItemStack craft(ServerCtx ctx, Item... grid) {
		List<ItemStack> stacks = Arrays.stream(grid).map(i -> i == Items.AIR ? ItemStack.EMPTY : new ItemStack(i)).toList();
		CraftingInput input = CraftingInput.of(3, 3, stacks);
		return ctx.server().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, ctx.level())
				.map(r -> r.value().assemble(input)).orElse(ItemStack.EMPTY);
	}
}
