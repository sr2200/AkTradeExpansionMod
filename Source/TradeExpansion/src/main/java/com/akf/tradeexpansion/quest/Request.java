package com.akf.tradeexpansion.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A delivery request of a market (design J).
 *
 * @param id         unique within its market
 * @param deadline   game time (market clock) after which the request disappears
 * @param difficulty 1..3 (stars)
 * @param accepted   players who accepted it (M, v0.29): more reputation for delivering it, less when it expires
 */
public record Request(int id, Identifier goods, int quantity, long reward, long deadline, int difficulty, List<UUID> accepted) {
	public static final Codec<Request> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("id").forGetter(Request::id),
			Identifier.CODEC.fieldOf("goods").forGetter(Request::goods),
			Codec.INT.fieldOf("quantity").forGetter(Request::quantity),
			Codec.LONG.fieldOf("reward").forGetter(Request::reward),
			Codec.LONG.fieldOf("deadline").forGetter(Request::deadline),
			Codec.INT.fieldOf("difficulty").forGetter(Request::difficulty),
			UUIDUtil.CODEC.listOf().optionalFieldOf("accepted", List.of()).forGetter(Request::accepted)
	).apply(i, Request::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, Request> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Request::id,
			Identifier.STREAM_CODEC, Request::goods,
			ByteBufCodecs.VAR_INT, Request::quantity,
			ByteBufCodecs.VAR_LONG, Request::reward,
			ByteBufCodecs.VAR_LONG, Request::deadline,
			ByteBufCodecs.VAR_INT, Request::difficulty,
			UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list()), Request::accepted,
			Request::new);

	public Request {
		accepted = List.copyOf(accepted);
	}

	public Request(int id, Identifier goods, int quantity, long reward, long deadline, int difficulty) {
		this(id, goods, quantity, reward, deadline, difficulty, List.of());
	}

	/** This request with {@code player} added to the accepted players. */
	public Request accept(UUID player) {
		if (accepted.contains(player)) return this;
		List<UUID> players = new ArrayList<>(accepted);
		players.add(player);
		return new Request(id, goods, quantity, reward, deadline, difficulty, players);
	}

	/** The same request with another reward (the reward a player sees with their reputation bonus). */
	public Request withReward(long newReward) {
		return new Request(id, goods, quantity, newReward, deadline, difficulty, accepted);
	}
}
