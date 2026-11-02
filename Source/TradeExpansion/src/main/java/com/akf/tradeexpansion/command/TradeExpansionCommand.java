package com.akf.tradeexpansion.command;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.bank.BankAccounts;
import com.akf.tradeexpansion.bank.BankService;
import com.akf.tradeexpansion.config.ServerConfig;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.core.GoodParams;
import com.akf.tradeexpansion.core.GoodState;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.currency.WalletService;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.item.CoinItem;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketClock;
import com.akf.tradeexpansion.market.MarketFactory;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.trade.TradeService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionCheck;

import java.util.Comparator;
import java.util.Set;
import java.util.function.Function;

/**
 * {@code /tradeex} (alias {@code /trade_expansion}), design 4-2 / 5-1 stage 8.
 * Everything except {@code balance} needs {@code command.permissionLevel}.
 */
public final class TradeExpansionCommand {
	private TradeExpansionCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralCommandNode<CommandSourceStack> root = dispatcher.register(Commands.literal("tradeex")
				.then(Commands.literal("balance")
						.executes(TradeExpansionCommand::balance))
				.then(admin(Commands.literal("pay"))
						.then(Commands.argument("amount", LongArgumentType.longArg(0))
								.executes(TradeExpansionCommand::pay)))
				.then(admin(Commands.literal("give"))
						.then(Commands.argument("amount", LongArgumentType.longArg(0, 1_000_000_000L))
								.executes(TradeExpansionCommand::give)))
				.then(Commands.literal("bank")
						.then(Commands.literal("balance")
								.executes(TradeExpansionCommand::bankBalance))
						.then(admin(Commands.literal("set"))
								.then(Commands.argument("player", EntityArgument.player())
										.then(Commands.argument("amount", LongArgumentType.longArg(0, 1_000_000_000_000L))
												.executes(c -> bankChange(c, true)))))
						.then(admin(Commands.literal("add"))
								.then(Commands.argument("player", EntityArgument.player())
										.then(Commands.argument("amount", LongArgumentType.longArg(-1_000_000_000_000L, 1_000_000_000_000L))
												.executes(c -> bankChange(c, false))))))
				.then(admin(Commands.literal("reload"))
						.executes(TradeExpansionCommand::reload))
				.then(admin(Commands.literal("simulate"))
						.then(Commands.argument("goods", IdentifierArgument.id())
								.suggests((c, b) -> SharedSuggestionProvider.suggestResource(GoodsCatalog.all().keySet(), b))
								.then(Commands.literal("buy")
										.then(Commands.argument("count", IntegerArgumentType.integer(1, 2304))
												.executes(c -> simulate(c, true))))
								.then(Commands.literal("sell")
										.then(Commands.argument("count", IntegerArgumentType.integer(1, 2304))
												.executes(c -> simulate(c, false))))))
				.then(admin(Commands.literal("market"))
						.then(Commands.literal("info")
								.executes(TradeExpansionCommand::marketInfo))
						.then(Commands.literal("advance")
								.then(Commands.argument("days", DoubleArgumentType.doubleArg(0, 100))
										.executes(TradeExpansionCommand::marketAdvance)))
						.then(withOptionalId(Commands.literal("reset"), TradeExpansionCommand::marketReset))
						.then(withOptionalId(Commands.literal("reroll"), TradeExpansionCommand::marketReroll))
						.then(Commands.literal("delete")
								.then(Commands.argument("id", LongArgumentType.longArg(1))
										.executes(TradeExpansionCommand::marketDelete)))));
		dispatcher.register(Commands.literal("trade_expansion").redirect(root));
	}

	/** {@code command.permissionLevel} 0..4 → the 26.3 permission constants (design G). Read on every check, so reload applies. */
	private static <T extends ArgumentBuilder<CommandSourceStack, T>> T admin(T builder) {
		return builder.requires(source -> Commands.<CommandSourceStack>hasPermission(permission()).test(source));
	}

	private static PermissionCheck permission() {
		return switch (TradeExpansionConfig.get().command.permissionLevel) {
			case 0 -> Commands.LEVEL_ALL;
			case 1 -> Commands.LEVEL_MODERATORS;
			case 3 -> Commands.LEVEL_ADMINS;
			case 4 -> Commands.LEVEL_OWNERS;
			default -> Commands.LEVEL_GAMEMASTERS;
		};
	}

	/** {@code <literal>} acts on the nearest market, {@code <literal> <id>} on that market. */
	private static ArgumentBuilder<CommandSourceStack, ?> withOptionalId(ArgumentBuilder<CommandSourceStack, ?> literal,
			Function<Market, Function<CommandContext<CommandSourceStack>, Integer>> action) {
		return literal
				.executes(c -> run(c, nearestMarket(c.getSource().getPlayerOrException()), action))
				.then(Commands.argument("id", LongArgumentType.longArg(1))
						.executes(c -> run(c, MarketRegistry.get(c.getSource().getServer()).get(LongArgumentType.getLong(c, "id")), action)));
	}

	private static int run(CommandContext<CommandSourceStack> context, Market market,
			Function<Market, Function<CommandContext<CommandSourceStack>, Integer>> action) {
		if (market == null) {
			context.getSource().sendFailure(Component.translatable("command.trade_expansion.no_market"));
			return 0;
		}
		return action.apply(market).apply(context);
	}

	private static int balance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		long balance = WalletService.balance(player);
		context.getSource().sendSuccess(() -> Component.translatable("command.trade_expansion.balance", CoinItem.formatCopper(balance)), false);
		return (int) Math.min(Integer.MAX_VALUE, balance);
	}

	private static int pay(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		long amount = LongArgumentType.getLong(context, "amount");
		if (!WalletService.pay(player, amount)) {
			context.getSource().sendFailure(Component.translatable("command.trade_expansion.insufficient_funds",
					CoinItem.formatCopper(WalletService.balance(player)), CoinItem.formatCopper(amount)));
			return 0;
		}
		long balance = WalletService.balance(player);
		TradeExpansion.LOGGER.info("{} PAY {} (test command)", player.getPlainTextName(), CoinItem.formatCopper(amount));
		context.getSource().sendSuccess(() -> Component.translatable("command.trade_expansion.paid",
				CoinItem.formatCopper(amount), CoinItem.formatCopper(balance)), false);
		return 1;
	}

	private static int give(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		long amount = LongArgumentType.getLong(context, "amount");
		WalletService.give(player, amount);
		long balance = WalletService.balance(player);
		context.getSource().sendSuccess(() -> Component.translatable("command.trade_expansion.given",
				CoinItem.formatCopper(amount), CoinItem.formatCopper(balance)), false);
		return 1;
	}

	/** E-7: own bank balance. */
	private static int bankBalance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		long balance = BankAccounts.get(context.getSource().getServer()).balance(player.getUUID());
		context.getSource().sendSuccess(() -> Component.translatable("command.trade_expansion.bank_balance", CoinItem.formatCopper(balance)), false);
		return (int) Math.min(Integer.MAX_VALUE, balance);
	}

	/** E-7: admin set / add of a player's bank balance. */
	private static int bankChange(CommandContext<CommandSourceStack> context, boolean set) throws CommandSyntaxException {
		ServerPlayer target = EntityArgument.getPlayer(context, "player");
		long amount = LongArgumentType.getLong(context, "amount");
		BankAccounts bank = BankAccounts.get(context.getSource().getServer());
		if (set) bank.set(target.getUUID(), amount);
		else bank.add(target.getUUID(), amount);
		long balance = bank.balance(target.getUUID());
		BankService.sendAccount(target);
		context.getSource().sendSuccess(() -> Component.translatable("command.trade_expansion.bank_changed",
				target.getPlainTextName(), CoinItem.formatCopper(balance)), true);
		return 1;
	}

	private static int reload(CommandContext<CommandSourceStack> context) {
		ServerConfig.reload(context.getSource().getServer());
		context.getSource().sendSuccess(() -> Component.translatable("command.trade_expansion.reloaded"), true);
		return 1;
	}

	/** The market whose center is nearest to the player in the same dimension. */
	private static Market nearestMarket(ServerPlayer player) {
		return MarketRegistry.get(player.level().getServer()).inDimension(player.level().dimension()).stream()
				.min(Comparator.comparingDouble(m -> m.horizontalDistanceTo(player.blockPosition())))
				.orElse(null);
	}

	private static int marketInfo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		Market market = nearestMarket(player);
		if (market == null) {
			context.getSource().sendFailure(Component.translatable("command.trade_expansion.no_market"));
			return 0;
		}
		long now = MarketClock.now(player.level());
		market.refresh(now);
		CommandSourceStack source = context.getSource();
		source.sendSuccess(() -> Component.literal(String.format("Market #%d (%s) center=%s stalls=%d distance=%.0f",
				market.id(), market.origin().getSerializedName(), market.center().toShortString(), market.stalls().size(),
				market.horizontalDistanceTo(player.blockPosition()))), false);
		ServerLevel level = player.level();
		boolean villagers = MarketFactory.usesVillagers(level);
		long count = villagers ? MarketFactory.countVillagers(level, market.center()) : MarketFactory.countBeds(level, market.center());
		source.sendSuccess(() -> Component.literal(String.format(" profile=%s level=%d (%s now: %d) goods=%d",
				market.profileId(), market.level(), villagers ? "villagers" : "beds", count, market.goods().size())), false);
		market.goods().forEach((goodsId, state) -> {
			GoodParams p = market.params(goodsId);
			if (p == null) return;
			int unit = GoodsCatalog.get(goodsId).unit();
			source.sendSuccess(() -> Component.literal(String.format(" %s%s buy=%dc sell=%dc X=%.2f V=%.0f%% S=%.1f/%.0f (max %.0f)",
					goodsId, unit > 1 ? " /" + unit : "", PriceModel.buyPrice(p, state.pressure()), PriceModel.sellPrice(p, state.pressure()),
					state.pressure(), PriceModel.multiplier(p, state.pressure()) * 100, state.stock(), p.targetStock(), p.maxStock())), false);
		});
		return 1;
	}

	private static int marketAdvance(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		Market market = nearestMarket(player);
		if (market == null) {
			context.getSource().sendFailure(Component.translatable("command.trade_expansion.no_market"));
			return 0;
		}
		double days = DoubleArgumentType.getDouble(context, "days");
		MarketRegistry registry = MarketRegistry.get(player.level().getServer());
		long now = MarketClock.now(player.level());
		market.refresh(now);
		market.elapse(days);
		registry.setDirty();
		TradeService.broadcast(player.level().getServer(), registry, Set.of(market.id()), now);
		context.getSource().sendSuccess(() -> Component.translatable("command.trade_expansion.advanced", market.id(), days), true);
		return 1;
	}

	/** C-1: back to X = 0 and S = T; goods, price factors and level stay. */
	private static Function<CommandContext<CommandSourceStack>, Integer> marketReset(Market market) {
		return context -> {
			ServerLevel level = context.getSource().getServer().getLevel(market.dimension());
			long now = level == null ? market.lastUpdateGameTime() : MarketClock.now(level);
			market.refresh(now);
			market.goods().forEach((goodsId, state) -> {
				GoodParams p = market.params(goodsId);
				state.setPressure(0);
				if (p != null) state.setStock(p.targetStock());
			});
			MarketRegistry registry = MarketRegistry.get(context.getSource().getServer());
			registry.setDirty();
			TradeService.broadcast(context.getSource().getServer(), registry, Set.of(market.id()), now);
			context.getSource().sendSuccess(() -> Component.translatable("command.trade_expansion.market_reset", market.id()), true);
			return 1;
		};
	}

	/** D-1: choose the profile and goods again with the current data packs and level. */
	private static Function<CommandContext<CommandSourceStack>, Integer> marketReroll(Market market) {
		return context -> {
			ServerLevel level = context.getSource().getServer().getLevel(market.dimension());
			if (level == null) return 0;
			MarketRegistry registry = MarketRegistry.get(context.getSource().getServer());
			MarketFactory.reroll(level, market, registry.inDimension(market.dimension()));
			registry.setDirty();
			TradeService.broadcast(context.getSource().getServer(), registry, Set.of(market.id()), MarketClock.now(level));
			context.getSource().sendSuccess(() -> Component.translatable("command.trade_expansion.market_rerolled",
					market.id(), String.valueOf(market.profileId()), market.goods().size()), true);
			return 1;
		};
	}

	/** Deletes a market; its stalls get a market again on next use (A-3, using their stored origin). */
	private static int marketDelete(CommandContext<CommandSourceStack> context) {
		long id = LongArgumentType.getLong(context, "id");
		if (!MarketRegistry.get(context.getSource().getServer()).remove(id)) {
			context.getSource().sendFailure(Component.translatable("command.trade_expansion.no_market"));
			return 0;
		}
		context.getSource().sendSuccess(() -> Component.translatable("command.trade_expansion.market_deleted", id), true);
		return 1;
	}

	/** Price progression of n units at the nearest market without trading (5-2). */
	private static int simulate(CommandContext<CommandSourceStack> context, boolean buy) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		Market market = nearestMarket(player);
		Identifier goodsId = IdentifierArgument.getId(context, "goods");
		int count = IntegerArgumentType.getInteger(context, "count");
		GoodState state = market == null ? null : market.good(goodsId);
		GoodParams p = market == null ? null : market.params(goodsId);
		if (state == null || p == null) {
			context.getSource().sendFailure(Component.translatable("command.trade_expansion.not_traded", goodsId));
			return 0;
		}
		market.refresh(MarketClock.now(player.level()));
		double x = state.pressure();
		long first = buy ? PriceModel.buyPrice(p, x) : PriceModel.sellPrice(p, x);
		long total = buy ? PriceModel.sumBuy(p, x, count) : PriceModel.sumSell(p, x, count);
		double after = p.clampPressure(x + (buy ? -count : count));
		long last = buy ? PriceModel.buyPrice(p, p.clampPressure(x - (count - 1))) : PriceModel.sellPrice(p, p.clampPressure(x + (count - 1)));
		long next = buy ? PriceModel.buyPrice(p, after) : PriceModel.sellPrice(p, after);
		context.getSource().sendSuccess(() -> Component.literal(String.format(
				"%s %s x%d @market#%d: first=%dc last=%dc total=%s, X %.2f -> %.2f, next=%dc (stock %.1f, %s)",
				buy ? "BUY" : "SELL", goodsId, count, market.id(), first, last, CoinItem.formatCopper(total), x, after, next, state.stock(),
				buy ? (count <= Math.floor(state.stock()) ? "in stock" : "exceeds stock")
						: (count <= PriceModel.sellCapacity(p, state) ? "within capacity" : "exceeds capacity"))), false);
		return 1;
	}
}
