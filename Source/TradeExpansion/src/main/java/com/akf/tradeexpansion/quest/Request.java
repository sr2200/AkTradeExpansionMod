package com.akf.tradeexpansion.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * A delivery request of a market (design J).
 *
 * @param id         unique within its market
 * @param deadline   game time (market clock) after which the request disappears
 * @param difficulty 1..3 (stars)
 */
public record Request(int id, Identifier goods, int quantity, long reward, long deadline, int difficulty) {
	public static final Codec<Request> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("id").forGetter(Request::id),
			Identifier.CODEC.fieldOf("goods").forGetter(Request::goods),
			Codec.INT.fieldOf("quantity").forGetter(Request::quantity),
			Codec.LONG.fieldOf("reward").forGetter(Request::reward),
			Codec.LONG.fieldOf("deadline").forGetter(Request::deadline),
			Codec.INT.fieldOf("difficulty").forGetter(Request::difficulty)
	).apply(i, Request::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, Request> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Request::id,
			Identifier.STREAM_CODEC, Request::goods,
			ByteBufCodecs.VAR_INT, Request::quantity,
			ByteBufCodecs.VAR_LONG, Request::reward,
			ByteBufCodecs.VAR_LONG, Request::deadline,
			ByteBufCodecs.VAR_INT, Request::difficulty,
			Request::new);
}
