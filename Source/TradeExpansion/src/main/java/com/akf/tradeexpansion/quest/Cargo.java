package com.akf.tradeexpansion.quest;

import com.akf.tradeexpansion.registry.ModDataComponents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Contents of a cargo crate (design J-2, v0.35): a transport request taken at one market, to be delivered at another.
 * The crate carries everything needed to pay it out, so it still works if the issuing market changed its requests.
 * Crates cannot be opened; the goods only exist as the crate.
 *
 * @param source      market that issued the request (reputation, removing the request on delivery)
 * @param destination market where the crate is delivered
 * @param reward      fixed when the crate was taken (with the taker's reputation bonus at the source)
 * @param deposit     paid when taking the crate, paid back on delivery
 * @param deadline    market clock time; later the crate cannot be delivered (the deposit is lost)
 */
public record Cargo(long source, Component sourceName, long destination, Component destinationName, BlockPos destinationCenter,
		ResourceKey<Level> destinationDimension, Identifier goods, Identifier item, int quantity, long reward, long deposit, long deadline,
		int difficulty, int requestId) {
	public static final Codec<Cargo> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("source").forGetter(Cargo::source),
			ComponentSerialization.CODEC.fieldOf("source_name").forGetter(Cargo::sourceName),
			Codec.LONG.fieldOf("destination").forGetter(Cargo::destination),
			ComponentSerialization.CODEC.fieldOf("destination_name").forGetter(Cargo::destinationName),
			BlockPos.CODEC.fieldOf("destination_center").forGetter(Cargo::destinationCenter),
			ResourceKey.codec(Registries.DIMENSION).fieldOf("destination_dimension").forGetter(Cargo::destinationDimension),
			Identifier.CODEC.fieldOf("goods").forGetter(Cargo::goods),
			Identifier.CODEC.fieldOf("item").forGetter(Cargo::item),
			Codec.INT.fieldOf("quantity").forGetter(Cargo::quantity),
			Codec.LONG.fieldOf("reward").forGetter(Cargo::reward),
			Codec.LONG.fieldOf("deposit").forGetter(Cargo::deposit),
			Codec.LONG.fieldOf("deadline").forGetter(Cargo::deadline),
			Codec.INT.fieldOf("difficulty").forGetter(Cargo::difficulty),
			Codec.INT.fieldOf("request_id").forGetter(Cargo::requestId)
	).apply(i, Cargo::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, Cargo> STREAM_CODEC = StreamCodec.ofMember(Cargo::write, Cargo::read);

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarLong(source);
		ComponentSerialization.STREAM_CODEC.encode(buf, sourceName);
		buf.writeVarLong(destination);
		ComponentSerialization.STREAM_CODEC.encode(buf, destinationName);
		BlockPos.STREAM_CODEC.encode(buf, destinationCenter);
		ResourceKey.streamCodec(Registries.DIMENSION).encode(buf, destinationDimension);
		Identifier.STREAM_CODEC.encode(buf, goods);
		Identifier.STREAM_CODEC.encode(buf, item);
		buf.writeVarInt(quantity);
		buf.writeVarLong(reward);
		buf.writeVarLong(deposit);
		buf.writeVarLong(deadline);
		buf.writeVarInt(difficulty);
		buf.writeVarInt(requestId);
	}

	private static Cargo read(RegistryFriendlyByteBuf buf) {
		return new Cargo(buf.readVarLong(), ComponentSerialization.STREAM_CODEC.decode(buf), buf.readVarLong(),
				ComponentSerialization.STREAM_CODEC.decode(buf), BlockPos.STREAM_CODEC.decode(buf),
				ResourceKey.streamCodec(Registries.DIMENSION).decode(buf), Identifier.STREAM_CODEC.decode(buf),
				Identifier.STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readVarLong(), buf.readVarLong(), buf.readVarLong(),
				buf.readVarInt(), buf.readVarInt());
	}

	public boolean expired(long now) {
		return now > deadline;
	}

	/** Slots of the main inventory (hotbar included) holding a crate for {@code marketId}. */
	public static List<Integer> slotsFor(Player player, long marketId) {
		List<Integer> slots = new ArrayList<>();
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
			Cargo cargo = of(inventory.getItem(slot));
			if (cargo != null && cargo.destination() == marketId) slots.add(slot);
		}
		return slots;
	}

	public static Cargo of(ItemStack stack) {
		return stack.isEmpty() ? null : stack.get(ModDataComponents.CARGO);
	}
}
