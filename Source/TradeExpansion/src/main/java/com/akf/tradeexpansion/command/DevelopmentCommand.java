package com.akf.tradeexpansion.command;

import com.akf.tradeexpansion.development.Development;
import com.akf.tradeexpansion.development.Facility;
import com.akf.tradeexpansion.development.FacilityBuilder;
import com.akf.tradeexpansion.market.MarketFactory;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketClock;
import com.akf.tradeexpansion.market.MarketNames;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** {@code /aktradeex development}: development of the nearest market (design N-7). */
final class DevelopmentCommand {
	private DevelopmentCommand() {
	}

	static int info(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		Market market = TradeExpansionCommand.nearestMarket(player);
		if (market == null) {
			context.getSource().sendFailure(Component.translatable("command.ak_trade_expansion.no_market"));
			return 0;
		}
		if (!Development.enabled(market)) {
			context.getSource().sendFailure(Component.translatable("command.ak_trade_expansion.development.disabled", MarketNames.title(market)));
			return 0;
		}
		long now = MarketClock.now(player.level());
		long points = (long) Math.floor(Development.points(market, now));
		context.getSource().sendSuccess(() -> Component.translatable("command.ak_trade_expansion.development.info", MarketNames.title(market), points), false);
		for (Market.Contributor c : Development.top(market, 5)) {
			context.getSource().sendSuccess(() -> Component.literal(String.format("  %s: %d", c.name(), (long) Math.floor(c.points()))), false);
		}
		// N-3 / N-4: level breakdown and the facilities
		context.getSource().sendSuccess(() -> Component.literal(String.format(" level %d = measured %d + bonus %d (%d completed)",
				market.level(), market.measuredLevel(), MarketFactory.levelBonus(market), market.completedFacilities())), false);
		for (Facility f : market.facilities().values()) {
			long left = Math.max(0, f.due() - now);
			context.getSource().sendSuccess(() -> Component.literal(String.format("  %s: %s at %s%s", f.id(), f.state().getSerializedName(),
					f.box().getCenter().toShortString(), f.state() == Facility.State.CONSTRUCTING ? String.format(" (%.2f days left)", left / 24000.0) : "")), false);
		}
		return (int) Math.min(Integer.MAX_VALUE, points);
	}

	/** Completes the constructions of the nearest market now (tests; the site must be loaded). */
	static int complete(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		Market market = TradeExpansionCommand.nearestMarket(player);
		if (market == null) {
			context.getSource().sendFailure(Component.translatable("command.ak_trade_expansion.no_market"));
			return 0;
		}
		int count = FacilityBuilder.completeNow(player.level(), market);
		context.getSource().sendSuccess(() -> Component.translatable("command.ak_trade_expansion.development.completed", count), false);
		return count;
	}

	/** Adds (or removes) points without a contributor, for tests. */
	static int add(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		Market market = TradeExpansionCommand.nearestMarket(player);
		if (market == null) {
			context.getSource().sendFailure(Component.translatable("command.ak_trade_expansion.no_market"));
			return 0;
		}
		long now = MarketClock.now(player.level());
		market.setDevelopment(Development.points(market, now) + DoubleArgumentType.getDouble(context, "points"), now);
		MarketRegistry.get(player.level().getServer()).setDirty();
		return info(context);
	}
}
