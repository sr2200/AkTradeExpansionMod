package com.akf.tradeexpansion.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A delivery request of a market (design J), or a transport request (J-2, v0.35).
 *
 * @param id         unique within its market
 * @param deadline   game time (market clock) after which the request disappears
 * @param difficulty 1..3 (stars)
 * @param accepted   players who accepted it (M, v0.29): more reputation for delivering it, less when it expires. For a
 *                   transport request: the one player who took the cargo crate (then nobody else can take it)
 * @param transport  present for a transport request: carry the goods (in a cargo crate) to another market
 */
public record Request(int id, Identifier goods, int quantity, long reward, long deadline, int difficulty, List<UUID> accepted,
		Optional<Transport> transport) {
	/**
	 * J-2: where the goods go and what the crate costs.
	 *
	 * @param destination id of the market the crate must be delivered to
	 * @param deposit     paid when taking the crate, paid back on delivery (the goods' value at the issuing market)
	 */
	public record Transport(long destination, long deposit) {
		public static final Codec<Transport> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.fieldOf("destination").forGetter(Transport::destination),
				Codec.LONG.fieldOf("deposit").forGetter(Transport::deposit)
		).apply(i, Transport::new));
		public static final StreamCodec<ByteBuf, Transport> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_LONG, Transport::destination,
				ByteBufCodecs.VAR_LONG, Transport::deposit,
				Transport::new);
	}

	public static final Codec<Request> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("id").forGetter(Request::id),
			Identifier.CODEC.fieldOf("goods").forGetter(Request::goods),
			Codec.INT.fieldOf("quantity").forGetter(Request::quantity),
			Codec.LONG.fieldOf("reward").forGetter(Request::reward),
			Codec.LONG.fieldOf("deadline").forGetter(Request::deadline),
			Codec.INT.fieldOf("difficulty").forGetter(Request::difficulty),
			UUIDUtil.CODEC.listOf().optionalFieldOf("accepted", List.of()).forGetter(Request::accepted),
			Transport.CODEC.optionalFieldOf("transport").forGetter(Request::transport)
	).apply(i, Request::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, Request> STREAM_CODEC = StreamCodec.ofMember(Request::write, Request::read);

	public Request {
		accepted = List.copyOf(accepted);
	}

	public Request(int id, Identifier goods, int quantity, long reward, long deadline, int difficulty) {
		this(id, goods, quantity, reward, deadline, difficulty, List.of(), Optional.empty());
	}

	public boolean isTransport() {
		return transport.isPresent();
	}

	/** J-2: a transport request whose crate somebody already took. */
	public boolean taken() {
		return isTransport() && !accepted.isEmpty();
	}

	/** This request with {@code player} added to the accepted players. */
	public Request accept(UUID player) {
		if (accepted.contains(player)) return this;
		List<UUID> players = new ArrayList<>(accepted);
		players.add(player);
		return new Request(id, goods, quantity, reward, deadline, difficulty, players, transport);
	}

	/** The same request with another reward (the reward a player sees with their reputation bonus). */
	public Request withReward(long newReward) {
		return new Request(id, goods, quantity, newReward, deadline, difficulty, accepted, transport);
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(id);
		Identifier.STREAM_CODEC.encode(buf, goods);
		buf.writeVarInt(quantity);
		buf.writeVarLong(reward);
		buf.writeVarLong(deadline);
		buf.writeVarInt(difficulty);
		UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, accepted);
		ByteBufCodecs.optional(Transport.STREAM_CODEC).encode(buf, transport);
	}

	private static Request read(RegistryFriendlyByteBuf buf) {
		return new Request(buf.readVarInt(), Identifier.STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readVarLong(), buf.readVarLong(),
				buf.readVarInt(), UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf),
				ByteBufCodecs.optional(Transport.STREAM_CODEC).decode(buf));
	}
}
