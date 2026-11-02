package com.akf.tradeexpansion.command;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.core.GoodParams;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.currency.WalletService;
import com.akf.tradeexpansion.item.CoinItem;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.trade.TradeService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;
import java.util.Set;

/**
 * {@code /tradeex} (alias {@code /trade_expansion}). Stage 2 test commands: balance / pay / give.
 */
public final class TradeExpansionCommand {
	private TradeExpansionCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralCommandNode<CommandSourceStack> root = dispatcher.register(Commands.literal("tradeex")
				.then(Commands.literal("balance")
						.executes(TradeExpansionCommand::balance))
				.then(Commands.literal("pay")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.argument("amount", LongArgumentType.longArg(0))
								.executes(TradeExpansionCommand::pay)))
				.then(Commands.literal("give")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.argument("amount", LongArgumentType.longArg(0, 1_000_000_000L))
								.executes(TradeExpansionCommand::give)))
				.then(Commands.literal("market")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.literal("info")
								.executes(TradeExpansionCommand::marketInfo))
						.then(Commands.literal("advance")
								.then(Commands.argument("days", DoubleArgumentType.doubleArg(0, 100))
										.executes(TradeExpansionCommand::marketAdvance)))));
		dispatcher.register(Commands.literal("trade_expansion").redirect(root));
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
		long now = player.level().getGameTime();
		market.refresh(now);
		CommandSourceStack source = context.getSource();
		source.sendSuccess(() -> Component.literal(String.format("Market #%d (%s) center=%s stalls=%d distance=%.0f",
				market.id(), market.origin().getSerializedName(), market.center().toShortString(), market.stalls().size(),
				market.horizontalDistanceTo(player.blockPosition()))), false);
		market.goods().forEach((item, state) -> {
			GoodParams p = market.params(item);
			if (p == null) return;
			source.sendSuccess(() -> Component.literal(String.format(" %s buy=%dc sell=%dc X=%.2f V=%.0f%% S=%.1f/%.0f (max %.0f)",
					item, PriceModel.buyPrice(p, state.pressure()), PriceModel.sellPrice(p, state.pressure()), state.pressure(),
					PriceModel.multiplier(p, state.pressure()) * 100, state.stock(), p.targetStock(), p.maxStock())), false);
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
		long now = player.level().getGameTime();
		market.refresh(now);
		market.elapse(days);
		registry.setDirty();
		TradeService.broadcast(player.level().getServer(), registry, Set.of(market.id()), now);
		context.getSource().sendSuccess(() -> Component.translatable("command.trade_expansion.advanced", market.id(), days), true);
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
}
