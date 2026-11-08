package com.akf.tradeexpansion.network;

import com.akf.tradeexpansion.TradeExpansion;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * A staff tab action (design Q, v0.48). Answered with {@link StaffS2C}.
 *
 * @param employee  the employee acted on (CONFIGURE … DISMISS)
 * @param goods     HIRE_*: the good; DESTINATIONS: the good to find destinations for
 * @param quantity  carrier: units per trip; HIRE_CLERK: units left with the clerk
 * @param amount    DEPOSIT_MONEY / WITHDRAW_MONEY: coins (WITHDRAW: 0 or less = everything)
 * @param useCard   payments from / to the bank account (E-8)
 */
public record StaffRequestC2S(long marketId, Action action, int employee, Identifier goods, long destination, int quantity, long buyLimit,
		long sellLimit, long amount, boolean useCard) implements CustomPacketPayload {
	public enum Action {
		/** Sends the list (opening the tab, and every few seconds while it is open). */
		LIST,
		/** Markets a carrier could take {@code goods} to. */
		DESTINATIONS,
		HIRE_CARRIER, HIRE_CLERK, CONFIGURE, PAUSE, DEPOSIT_MONEY, WITHDRAW_MONEY, DEPOSIT_ITEMS, WITHDRAW_ITEMS, DISMISS
	}

	public static final Type<StaffRequestC2S> TYPE = new Type<>(TradeExpansion.id("staff_request"));
	public static final StreamCodec<RegistryFriendlyByteBuf, StaffRequestC2S> CODEC = StreamCodec.ofMember(StaffRequestC2S::write, StaffRequestC2S::read);

	public static StaffRequestC2S simple(long marketId, Action action, int employee) {
		return new StaffRequestC2S(marketId, action, employee, TradeExpansion.id("none"), -1, 0, 0, 0, 0, false);
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarLong(marketId);
		buf.writeVarInt(action.ordinal());
		buf.writeVarInt(employee);
		Identifier.STREAM_CODEC.encode(buf, goods);
		buf.writeLong(destination);
		buf.writeVarInt(quantity);
		buf.writeVarLong(buyLimit);
		buf.writeVarLong(sellLimit);
		buf.writeLong(amount);
		buf.writeBoolean(useCard);
	}

	private static StaffRequestC2S read(RegistryFriendlyByteBuf buf) {
		long marketId = buf.readVarLong();
		int action = buf.readVarInt();
		Action[] actions = Action.values();
		return new StaffRequestC2S(marketId, actions[Math.clamp(action, 0, actions.length - 1)], buf.readVarInt(), Identifier.STREAM_CODEC.decode(buf),
				buf.readLong(), buf.readVarInt(), buf.readVarLong(), buf.readVarLong(), buf.readLong(), buf.readBoolean());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
