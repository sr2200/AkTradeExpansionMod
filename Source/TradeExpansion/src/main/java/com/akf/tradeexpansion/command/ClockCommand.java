package com.akf.tradeexpansion.command;

import com.akf.tradeexpansion.api.TradeExpansionEvents;
import com.akf.tradeexpansion.config.ServerConfig;
import com.akf.tradeexpansion.core.PriceModel;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketClock;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.market.MarketUpkeep;
import com.akf.tradeexpansion.menu.MarketMenu;
import com.akf.tradeexpansion.network.MarketSnapshotS2C;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@code /aktradeex clock [advance <days>]} (design G, for testing): moves the market clock of the whole world forward.
 * Unlike {@code market advance} (recovery of one market only) everything driven by {@link MarketClock} follows: specials,
 * request deadlines, interest, reputation decay, rival guilds, trends, shortages, staff and construction. The game's own
 * time (day and night, crops) does not change. The clock never goes back.
 */
final class ClockCommand {
	private ClockCommand() {
	}

	static LiteralArgumentBuilder<CommandSourceStack> build(LiteralArgumentBuilder<CommandSourceStack> node) {
		return node.executes(ClockCommand::info)
				.then(Commands.literal("advance")
						.then(Commands.argument("days", DoubleArgumentType.doubleArg(0, 3650))
								.executes(ClockCommand::advance)));
	}

	private static int info(CommandContext<CommandSourceStack> context) {
		MinecraftServer server = context.getSource().getServer();
		long offset = MarketRegistry.get(server).clockOffset();
		double days = offset / (double) PriceModel.TICKS_PER_DAY;
		double today = MarketClock.now(server.overworld()) / (double) PriceModel.TICKS_PER_DAY;
		context.getSource().sendSuccess(() -> Component.translatable("command.ak_trade_expansion.clock.info",
				String.format("%.2f", today), String.format("%.2f", days)), false);
		return 1;
	}

	private static int advance(CommandContext<CommandSourceStack> context) {
		MinecraftServer server = context.getSource().getServer();
		double days = DoubleArgumentType.getDouble(context, "days");
		long ticks = Math.round(days * PriceModel.TICKS_PER_DAY);
		MarketRegistry registry = MarketRegistry.get(server);
		registry.addClockOffset(ticks);
		ServerConfig.sync(server); // clients show time left with the same clock
		refreshViewers(server, registry);
		Set<Long> all = registry.all().stream().map(Market::id).collect(Collectors.toSet());
		if (!all.isEmpty()) TradeExpansionEvents.MARKETS_CHANGED.invoker().onMarketsChanged(server, Set.copyOf(all)); // S
		double total = registry.clockOffset() / (double) PriceModel.TICKS_PER_DAY;
		context.getSource().sendSuccess(() -> Component.translatable("command.ak_trade_expansion.clock.advanced",
				String.format("%.2f", days), String.format("%.2f", total)), true);
		return 1;
	}

	/** Players with a trade screen open see the market brought up to the new time at once. */
	private static void refreshViewers(MinecraftServer server, MarketRegistry registry) {
		for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
			if (!(viewer.containerMenu instanceof MarketMenu menu)) continue;
			Market market = registry.get(menu.marketId());
			ServerLevel level = market == null ? null : server.getLevel(market.dimension());
			if (level == null) continue;
			long now = MarketUpkeep.touch(level, market);
			ServerPlayNetworking.send(viewer, MarketSnapshotS2C.of(market, viewer, now));
		}
	}
}
