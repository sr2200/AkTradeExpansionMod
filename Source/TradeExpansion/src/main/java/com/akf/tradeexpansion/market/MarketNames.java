package com.akf.tradeexpansion.market;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.block.MarketStallBlockEntity;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.data.MarketProfile;
import com.akf.tradeexpansion.data.MarketProfiles;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Market names (design C-4): set with a name tag on a stall, by placing a renamed stall that founds a market, or by
 * {@code /aktradeex market name}. Unnamed markets show the profile name ("Plains Village #12").
 */
public final class MarketNames {
	public static final int MAX_LENGTH = 32;

	private MarketNames() {
	}

	/** Plain text of a name tag / item name: formatting removed, trimmed, at most {@link #MAX_LENGTH} characters; {@code null} if empty. */
	public static @Nullable String sanitize(@Nullable String raw) {
		if (raw == null) return null;
		String text = ChatFormatting.stripFormatting(raw);
		text = text == null ? "" : text.strip();
		if (text.codePointCount(0, text.length()) > MAX_LENGTH) text = text.substring(0, text.offsetByCodePoints(0, MAX_LENGTH)).strip();
		return text.isEmpty() ? null : text;
	}

	/** The market's name, or "<profile name> #id" when it has none. */
	public static Component name(Market market) {
		if (market.name() != null) return Component.literal(market.name());
		MarketProfile profile = market.profileId() == null ? null : MarketProfiles.get(market.profileId());
		if (profile != null && profile.displayName().isPresent()) {
			return Component.translatable("market.ak_trade_expansion.auto_name", profile.displayName().get(), market.id());
		}
		return Component.translatable("container.ak_trade_expansion.market", market.id());
	}

	/** Screen title / board line: "name (level)". */
	public static Component title(Market market) {
		return Component.translatable("container.ak_trade_expansion.market_title", name(market), MarketStallBlockEntity.levelName(market.level()));
	}

	/**
	 * Who may rename ({@code market.renamePermission}): the singleplayer owner always; otherwise ANYONE, OP_ONLY, or
	 * NAMER_OR_OP (default) = OPs, the namer (founder of a crafted market or whoever named it first), and anyone while
	 * the market has no namer yet.
	 */
	public static boolean canRename(ServerPlayer player, Market market) {
		if (player.level().getServer().isSingleplayerOwner(player.nameAndId())) return true;
		boolean op = Commands.<net.minecraft.commands.CommandSourceStack>hasPermission(Commands.LEVEL_GAMEMASTERS).test(player.createCommandSourceStack());
		return switch (TradeExpansionConfig.get().market.renamePermission) {
			case ANYONE -> true;
			case OP_ONLY -> op;
			case NAMER_OR_OP -> op || market.namer() == null || market.namer().equals(player.getUUID());
		};
	}

	/** Sets (or with {@code null} clears) the name; the first player to name a market becomes its namer. */
	public static void rename(Market market, @Nullable String name, @Nullable ServerPlayer by) {
		market.setName(name);
		if (by != null && market.namer() == null) market.setNamer(by.getUUID());
		TradeExpansion.LOGGER.info("Market #{} renamed to {} by {}", market.id(), name == null ? "(auto)" : "\"" + name + "\"",
				by == null ? "command" : by.getGameProfile().name());
	}
}
