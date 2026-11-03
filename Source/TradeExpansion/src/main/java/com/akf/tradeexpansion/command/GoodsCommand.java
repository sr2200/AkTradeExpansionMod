package com.akf.tradeexpansion.command;

import com.akf.tradeexpansion.config.ConfigManager;
import com.akf.tradeexpansion.config.GoodsOverride;
import com.akf.tradeexpansion.config.ServerConfig;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.GoodsDefinition;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * {@code /tradeex goods info|set|reset <goods> ...} (design D-2 / G): per-goods values in the config file
 * ({@code goods}), so prices and stock can be changed without a data pack. Changes are saved to the file and applied
 * to every market at once.
 */
final class GoodsCommand {
	private GoodsCommand() {
	}

	static LiteralArgumentBuilder<CommandSourceStack> build(LiteralArgumentBuilder<CommandSourceStack> base) {
		RequiredArgumentBuilder<CommandSourceStack, Identifier> set = goods();
		RequiredArgumentBuilder<CommandSourceStack, Identifier> reset = goods();
		for (GoodsOverride.NumberKey key : GoodsOverride.NumberKey.values()) {
			set.then(Commands.literal(key.json).then(Commands.argument("value", DoubleArgumentType.doubleArg(key.min, key.max))
					.executes(c -> change(c, o -> o.with(key, Optional.of(DoubleArgumentType.getDouble(c, "value"))), true))));
			reset.then(Commands.literal(key.json).executes(c -> change(c, o -> o.with(key, Optional.empty()), false)));
		}
		set.then(Commands.literal("buy").then(Commands.argument("value", BoolArgumentType.bool())
				.executes(c -> change(c, o -> o.withBuy(Optional.of(BoolArgumentType.getBool(c, "value"))), true))));
		set.then(Commands.literal("sell").then(Commands.argument("value", BoolArgumentType.bool())
				.executes(c -> change(c, o -> o.withSell(Optional.of(BoolArgumentType.getBool(c, "value"))), true))));
		reset.then(Commands.literal("buy").executes(c -> change(c, o -> o.withBuy(Optional.empty()), false)));
		reset.then(Commands.literal("sell").executes(c -> change(c, o -> o.withSell(Optional.empty()), false)));
		reset.executes(c -> change(c, o -> GoodsOverride.EMPTY, false));
		return base
				.then(Commands.literal("info").then(goods().executes(GoodsCommand::info)))
				.then(Commands.literal("set").then(set))
				.then(Commands.literal("reset").then(reset));
	}

	private static RequiredArgumentBuilder<CommandSourceStack, Identifier> goods() {
		return Commands.argument("goods", IdentifierArgument.id())
				.suggests((c, b) -> SharedSuggestionProvider.suggestResource(GoodsCatalog.all().keySet(), b));
	}

	/** "dandelion" means trade_expansion:dandelion (the command parser would make it minecraft:dandelion), like the config file. */
	private static Identifier goodsId(CommandContext<CommandSourceStack> context) {
		Identifier raw = IdentifierArgument.getId(context, "goods");
		if (GoodsCatalog.loaded(raw) == null && raw.getNamespace().equals(Identifier.DEFAULT_NAMESPACE)) {
			return Identifier.fromNamespaceAndPath("trade_expansion", raw.getPath());
		}
		return raw;
	}

	private static int change(CommandContext<CommandSourceStack> context, UnaryOperator<GoodsOverride> edit, boolean set) {
		Identifier id = goodsId(context);
		if (GoodsCatalog.loaded(id) == null) {
			context.getSource().sendFailure(Component.translatable("command.trade_expansion.not_goods", id.toString()));
			return 0;
		}
		// Edit the file (not the live copy) so hand edits are kept, then reload like /tradeex reload.
		TradeExpansionConfig file = ConfigManager.read(ConfigManager.path());
		GoodsOverride updated = edit.apply(file.goods.getOrDefault(id.toString(), GoodsOverride.EMPTY));
		if (updated.isEmpty()) file.goods.remove(id.toString());
		else file.goods.put(id.toString(), updated);
		ConfigManager.write(ConfigManager.path(), file);
		ServerConfig.reload(context.getSource().getServer());
		context.getSource().sendSuccess(() -> Component.translatable(set ? "command.trade_expansion.goods_set" : "command.trade_expansion.goods_reset",
				id.toString()), true);
		return info(context);
	}

	/** Data pack value → config value → value in use, per key. */
	private static int info(CommandContext<CommandSourceStack> context) {
		Identifier id = goodsId(context);
		GoodsDefinition loaded = GoodsCatalog.loaded(id);
		GoodsDefinition effective = GoodsCatalog.get(id);
		if (loaded == null || effective == null) {
			context.getSource().sendFailure(Component.translatable("command.trade_expansion.not_goods", id.toString()));
			return 0;
		}
		GoodsOverride o = TradeExpansionConfig.get().goods.getOrDefault(id.toString(), GoodsOverride.EMPTY);
		CommandSourceStack source = context.getSource();
		source.sendSuccess(() -> Component.translatable("command.trade_expansion.goods_info", id.toString(),
				GoodsDefinition.isConsumable(effective.item(), TradeExpansionConfig.get())
						? Component.translatable("command.trade_expansion.consumable.yes") : Component.translatable("command.trade_expansion.consumable.no")), false);
		for (GoodsOverride.NumberKey key : GoodsOverride.NumberKey.values()) {
			String pack = value(loaded, key).map(GoodsCommand::number).orElse("-");
			String config = o.get(key).map(GoodsCommand::number).orElse("-");
			source.sendSuccess(() -> Component.literal(String.format(" %s: %s / %s", key.json, pack, config)), false);
		}
		source.sendSuccess(() -> Component.literal(String.format(" buy: %s / %s  sell: %s / %s", loaded.buy(), o.buy().map(String::valueOf).orElse("-"),
				loaded.sell(), o.sell().map(String::valueOf).orElse("-"))), false);
		return 1;
	}

	private static Optional<Double> value(GoodsDefinition def, GoodsOverride.NumberKey key) {
		return switch (key) {
			case BASE_PRICE -> Optional.of(def.basePrice());
			case ELASTICITY -> def.elasticity();
			case TARGET_STOCK -> Optional.of(def.targetStock());
			case MAX_STOCK -> def.maxStock();
			case RESTOCK_PER_DAY -> def.restockPerDay();
			case CONSUME_PER_DAY -> def.consumePerDay();
			case HALF_LIFE_DAYS -> def.halfLifeDays();
			case SPREAD -> def.spread();
		};
	}

	private static String number(double v) {
		return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
	}
}
