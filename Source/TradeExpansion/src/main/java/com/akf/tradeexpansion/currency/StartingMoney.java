package com.akf.tradeexpansion.currency;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.config.TradeExpansionConfig;
import com.akf.tradeexpansion.item.CoinItem;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Starting money (design E-2, {@code currency.startingMoney}, v0.31): the first time a player joins a world they get this
 * amount (into the wallet, otherwise as coins). Who has received it is saved per world ({@code ak_trade_expansion:starting_money}).
 */
public final class StartingMoney extends SavedData {
	public static final Codec<StartingMoney> CODEC = UUIDUtil.CODEC.listOf().fieldOf("received").codec()
			.xmap(StartingMoney::new, s -> List.copyOf(s.received));

	public static final SavedDataType<StartingMoney> TYPE = new SavedDataType<>(
			TradeExpansion.id("starting_money"), StartingMoney::new, CODEC, null);

	private final Set<UUID> received = new LinkedHashSet<>();

	private StartingMoney() {
	}

	private StartingMoney(List<UUID> players) {
		received.addAll(players);
	}

	public static StartingMoney get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	public static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> give(handler.getPlayer(), server));
	}

	private static void give(ServerPlayer player, MinecraftServer server) {
		long amount = TradeExpansionConfig.get().currency.startingMoney;
		StartingMoney data = get(server);
		if (amount <= 0 || !data.received.add(player.getUUID())) return;
		data.setDirty();
		WalletService.give(player, amount);
		player.sendSystemMessage(Component.translatable("message.ak_trade_expansion.starting_money", CoinItem.formatCopper(amount))
				.withStyle(ChatFormatting.GOLD));
	}
}
