package com.akf.tradeexpansion.command;

import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.GoodsDefinition;
import com.akf.tradeexpansion.data.RivalGuilds;
import com.akf.tradeexpansion.market.Market;
import com.akf.tradeexpansion.market.MarketClock;
import com.akf.tradeexpansion.market.MarketNames;
import com.akf.tradeexpansion.market.MarketRegistry;
import com.akf.tradeexpansion.market.MarketUpkeep;
import com.akf.tradeexpansion.rival.Campaign;
import com.akf.tradeexpansion.rival.RivalService;
import com.akf.tradeexpansion.trade.TradeService;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** {@code /aktradeex rivals [start|clear]} (design O-6): lists, starts or clears rival guild campaigns. */
final class RivalCommand {
	private RivalCommand() {
	}

	static LiteralArgumentBuilder<CommandSourceStack> build(LiteralArgumentBuilder<CommandSourceStack> base) {
		return base.executes(RivalCommand::list)
				.then(Commands.literal("start").executes(RivalCommand::start))
				.then(Commands.literal("clear").executes(RivalCommand::clear));
	}

	private static int list(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		ServerLevel level = source.getLevel();
		MarketRegistry registry = MarketRegistry.get(source.getServer());
		long now = MarketClock.now(level);
		RivalService.advance(level, registry, now);
		long today = RivalService.day(now);
		List<Campaign> active = RivalService.active(registry, today);
		source.sendSuccess(() -> Component.translatable("command.ak_trade_expansion.rivals.header", active.size(), today), false);
		for (Campaign c : active) source.sendSuccess(() -> describe(registry, c, today), false);
		return active.size();
	}

	private static Component describe(MarketRegistry registry, Campaign c, long today) {
		Market center = registry.get(c.market());
		GoodsDefinition def = GoodsCatalog.get(c.goods());
		Component item = def == null ? Component.literal(c.goods().toString()) : new net.minecraft.world.item.ItemStack(BuiltInRegistries.ITEM.getValue(def.item())).getHoverName();
		return Component.translatable(c.buying() ? "command.ak_trade_expansion.rivals.line.buying" : "command.ak_trade_expansion.rivals.line.selling",
				c.id(), RivalGuilds.name(c.guild()), item, center == null ? Component.literal("#" + c.market()) : MarketNames.name(center),
				Math.round(c.impact() * 100), c.startDay(), c.endDay() - 1, c.daysLeft(today));
	}

	private static int start(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		Market market = TradeExpansionCommand.nearestMarket(player);
		if (market == null) {
			context.getSource().sendFailure(Component.translatable("command.ak_trade_expansion.no_market"));
			return 0;
		}
		ServerLevel level = player.level();
		MarketRegistry registry = MarketRegistry.get(level.getServer());
		long now = MarketUpkeep.touch(level, market);
		Campaign c = RivalService.startNow(level, registry, market, now);
		if (c == null) {
			context.getSource().sendFailure(Component.translatable("command.ak_trade_expansion.rivals.none", market.id()));
			return 0;
		}
		// Apply today's push to the markets in range at once (they have received today's pushes already).
		Set<Long> touched = registry.all().stream().filter(m -> m.good(c.goods()) != null && RivalService.weight(registry, m, c) > 0)
				.map(Market::id).collect(Collectors.toSet());
		for (long id : touched) {
			Market m = registry.get(id);
			m.refresh(now);
			// Markets that already received today's pushes get this campaign's now; the others get it when touched.
			if (m.rivalDay() >= RivalService.day(now)) RivalService.push(registry, m, c, RivalService.day(now), RivalService.day(now), now);
		}
		registry.setDirty();
		TradeService.broadcast(level.getServer(), registry, touched, now);
		context.getSource().sendSuccess(() -> Component.translatable("command.ak_trade_expansion.rivals.started")
				.append(" ").append(describe(registry, c, RivalService.day(now))), true);
		return 1;
	}

	private static int clear(CommandContext<CommandSourceStack> context) {
		MarketRegistry registry = MarketRegistry.get(context.getSource().getServer());
		int count = registry.campaigns().size();
		registry.campaigns().clear();
		registry.setDirty();
		context.getSource().sendSuccess(() -> Component.translatable("command.ak_trade_expansion.rivals.cleared", count), true);
		return count;
	}
}
