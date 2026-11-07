package com.akf.tradeexpansion.registry;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.quest.Cargo;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/** Item data of the wallet, the bank card (design E-6 / E-8) and the cargo crate (J-2). */
public final class ModDataComponents {
	/** Copper held by a wallet. */
	public static final DataComponentType<Long> WALLET_BALANCE = register("wallet_balance",
			DataComponentType.<Long>builder().persistent(Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG).build());

	/** Owner of a bank card; only the card with the account's current number works (re-issue invalidates old cards). */
	public record CardOwner(UUID uuid, String name, int number) {
		public static final Codec<CardOwner> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("uuid").forGetter(CardOwner::uuid),
				Codec.STRING.fieldOf("name").forGetter(CardOwner::name),
				Codec.INT.fieldOf("number").forGetter(CardOwner::number)
		).apply(i, CardOwner::new));
		public static final StreamCodec<ByteBuf, CardOwner> STREAM_CODEC = StreamCodec.composite(
				UUIDUtil.STREAM_CODEC, CardOwner::uuid,
				ByteBufCodecs.STRING_UTF8, CardOwner::name,
				ByteBufCodecs.VAR_INT, CardOwner::number,
				CardOwner::new);
	}

	public static final DataComponentType<CardOwner> CARD_OWNER = register("card_owner",
			DataComponentType.<CardOwner>builder().persistent(CardOwner.CODEC).networkSynchronized(CardOwner.STREAM_CODEC).build());

	/** J-2 (v0.35): what a cargo crate carries, and where to. */
	public static final DataComponentType<Cargo> CARGO = register("cargo",
			DataComponentType.<Cargo>builder().persistent(Cargo.CODEC).networkSynchronized(Cargo.STREAM_CODEC).build());

	private ModDataComponents() {
	}

	private static <T> DataComponentType<T> register(String name, DataComponentType<T> type) {
		return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, TradeExpansion.id(name), type);
	}

	public static void init() {
	}
}
