package com.akf.tradeexpansion.command;

import com.akf.tradeexpansion.data.MarketEvents;
import com.akf.tradeexpansion.event.EventService;
import com.akf.tradeexpansion.event.MarketEvent;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketClock;
import com.akf.tradeexpansion.market.MarketNames;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.market.MarketUpkeep;
import com.akf.tradeexpansion.trade.TradeService;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** {@code /aktradeex events [start [event]|clear]} (design V-6): lists, starts or clears market events. */
final class EventCommand {
	private EventCommand() {
	}

	static LiteralArgumentBuilder<CommandSourceStack> build(LiteralArgumentBuilder<CommandSourceStack> base) {
		return base.executes(EventCommand::list)
				.then(Commands.literal("start")
						.executes(c -> start(c, null))
						.then(Commands.argument("event", IdentifierArgument.id())
								.suggests((c, b) -> SharedSuggestionProvider.suggestResource(MarketEvents.all().keySet(), b))
								.executes(c -> start(c, IdentifierArgument.getId(c, "event")))))
				.then(Commands.literal("clear").executes(EventCommand::clear));
	}

	private static int list(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		ServerLevel level = source.getLevel();
		MarketRegistry registry = MarketRegistry.get(source.getServer());
		long now = MarketClock.now(level);
		EventService.advance(level, registry, now);
		long today = EventService.day(now);
		List<MarketEvent> listed = registry.events().stream().filter(e -> e.endDay() > today).toList();
		source.sendSuccess(() -> Component.translatable("command.ak_trade_expansion.events.header", listed.size(), today), false);
		for (MarketEvent e : listed) source.sendSuccess(() -> describe(registry, e, today), false);
		return listed.size();
	}

	private static Component describe(MarketRegistry registry, MarketEvent e, long today) {
		Market center = registry.get(e.market());
		return Component.translatable(e.startDay() > today ? "command.ak_trade_expansion.events.line.forecast" : "command.ak_trade_expansion.events.line",
				e.id(), MarketEvents.name(e.event()), EventService.goodsNames(e), center == null ? Component.literal("#" + e.market()) : MarketNames.name(center),
				com.akf.tradeexpansion.weather.WeatherService.multiplier(e.multiplier()), e.startDay(), e.endDay() - 1, e.daysLeft(today));
	}

	private static int start(CommandContext<CommandSourceStack> context, Identifier eventId) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		if (eventId != null && MarketEvents.get(eventId) == null) {
			context.getSource().sendFailure(Component.translatable("command.ak_trade_expansion.events.unknown", eventId.toString()));
			return 0;
		}
		Market market = TradeExpansionCommand.nearestMarket(player);
		if (market == null) {
			context.getSource().sendFailure(Component.translatable("command.ak_trade_expansion.no_market"));
			return 0;
		}
		ServerLevel level = player.level();
		MarketRegistry registry = MarketRegistry.get(level.getServer());
		long now = MarketUpkeep.touch(level, market);
		MarketEvent e = EventService.startNow(level, registry, eventId, market, now);
		if (e == null) {
			context.getSource().sendFailure(Component.translatable("command.ak_trade_expansion.events.none", market.id()));
			return 0;
		}
		// The markets in range get today's multiplier at once; the others when they are touched.
		Set<Long> touched = new HashSet<>();
		for (Market m : registry.all()) {
			if (!EventService.felt(registry, m, e)) continue;
			m.refresh(now);
			if (EventService.apply(registry, m, now)) touched.add(m.id());
		}
		registry.setDirty();
		TradeService.broadcast(level.getServer(), registry, touched, now);
		EventService.announce(level.getServer(), registry, e, false);
		context.getSource().sendSuccess(() -> Component.translatable("command.ak_trade_expansion.events.started")
				.append(" ").append(describe(registry, e, EventService.day(now))), true);
		return 1;
	}

	private static int clear(CommandContext<CommandSourceStack> context) {
		MarketRegistry registry = MarketRegistry.get(context.getSource().getServer());
		int count = registry.events().size();
		registry.events().clear();
		Set<Long> touched = new HashSet<>();
		for (Market m : registry.all()) {
			if (m.eventMultipliers().isEmpty()) continue;
			m.refresh(MarketClock.now(context.getSource().getLevel()));
			m.eventMultipliers().clear();
			touched.add(m.id());
		}
		registry.setDirty();
		TradeService.broadcast(context.getSource().getServer(), registry, touched, MarketClock.now(context.getSource().getLevel()));
		context.getSource().sendSuccess(() -> Component.translatable("command.ak_trade_expansion.events.cleared", count), true);
		return count;
	}
}
