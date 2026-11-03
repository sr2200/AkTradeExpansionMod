package com.akf.tradeexpansion.command;

import com.akf.tradeexpansion.config.ConfigManager;
import com.akf.tradeexpansion.config.ServerConfig;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.data.GoodsCatalog;
import com.akf.tradeexpansion.data.GoodsDefinition;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.TreeSet;
import java.util.function.BiConsumer;

/**
 * {@code /tradeex consumable list|add|remove|reset <item>} (design B-3): changes which goods count as consumables
 * without a datapack. Writes {@code consumables.add / remove} to the config file and reloads it; markets use the new
 * recovery speed at once.
 */
final class ConsumableCommand {
	private ConsumableCommand() {
	}

	static LiteralArgumentBuilder<CommandSourceStack> build(LiteralArgumentBuilder<CommandSourceStack> base) {
		return base
				.then(Commands.literal("list").executes(ConsumableCommand::list))
				.then(Commands.literal("add").then(item((c, id) -> {
					c.remove.remove(id);
					if (!c.add.contains(id)) c.add.add(id);
				}, "command.trade_expansion.consumable.added")))
				.then(Commands.literal("remove").then(item((c, id) -> {
					c.add.remove(id);
					if (!c.remove.contains(id)) c.remove.add(id);
				}, "command.trade_expansion.consumable.removed")))
				.then(Commands.literal("reset").then(item((c, id) -> {
					c.add.remove(id);
					c.remove.remove(id);
				}, "command.trade_expansion.consumable.reset")));
	}

	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, Identifier> item(
			BiConsumer<TradeExpansionConfig.Consumables, String> change, String message) {
		return Commands.argument("item", IdentifierArgument.id())
				.suggests((c, b) -> SharedSuggestionProvider.suggestResource(BuiltInRegistries.ITEM.keySet(), b))
				.executes(context -> {
					Identifier id = IdentifierArgument.getId(context, "item");
					if (!BuiltInRegistries.ITEM.containsKey(id)) {
						context.getSource().sendFailure(Component.translatable("command.trade_expansion.consumable.unknown_item", id.toString()));
						return 0;
					}
					// Edit the file (not the live copy) so hand edits are kept, then reload like /tradeex reload.
					TradeExpansionConfig file = ConfigManager.read(ConfigManager.path());
					change.accept(file.consumables, id.toString());
					ConfigManager.write(ConfigManager.path(), file);
					ServerConfig.reload(context.getSource().getServer());
					boolean now = GoodsDefinition.isConsumable(id, TradeExpansionConfig.get());
					context.getSource().sendSuccess(() -> Component.translatable(message, id.toString(),
							Component.translatable(now ? "command.trade_expansion.consumable.yes" : "command.trade_expansion.consumable.no")), true);
					return 1;
				});
	}

	private static int list(CommandContext<CommandSourceStack> context) {
		TradeExpansionConfig config = TradeExpansionConfig.get();
		TreeSet<String> goods = new TreeSet<>();
		GoodsCatalog.all().forEach((goodsId, def) -> {
			if (def.consumable(config)) goods.add(def.item().getPath());
		});
		CommandSourceStack source = context.getSource();
		source.sendSuccess(() -> Component.translatable("command.trade_expansion.consumable.list", goods.size(), String.join(", ", goods)), false);
		source.sendSuccess(() -> Component.translatable("command.trade_expansion.consumable.overrides",
				text(config.consumables.add), text(config.consumables.remove)), false);
		return goods.size();
	}

	private static String text(List<String> ids) {
		return ids.isEmpty() ? "-" : String.join(", ", ids);
	}
}
