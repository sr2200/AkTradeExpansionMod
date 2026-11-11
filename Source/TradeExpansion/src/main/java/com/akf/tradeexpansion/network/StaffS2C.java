package com.akf.tradeexpansion.network;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.trade.TradeResult;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The staff tab (design Q, v0.48): what hiring costs here, every employee of the player, and (after DESTINATIONS) the
 * markets a carrier could take a good to.
 *
 * @param action  the request answered (its ordinal), or -1 for a plain list
 * @param result  outcome of {@code action}, or empty for a plain list
 * @param subject the employee the action was about (a new hire's id), or -1
 * @param carrierGoods goods a carrier hired here may take (this market sells them)
 * @param clerkGoods   goods a clerk hired here may sell (this market buys them)
 * @param hints        v0.48.1 (GOODS): the recommendations below were worked out
 * @param carrierHints for each carrier good, the best destination and the expected profit of a round trip now
 * @param clerkHints   for each clerk good, the recommended lowest price and what a clerk would sell in a day
 */
public record StaffS2C(long marketId, int action, Optional<TradeResult> result, long amount, int subject, Info info, List<View> staff,
		List<Identifier> carrierGoods, List<Identifier> clerkGoods, Optional<Identifier> destinationGoods, List<Destination> destinations,
		boolean hints, List<CarrierHint> carrierHints, List<ClerkHint> clerkHints) implements CustomPacketPayload {
	public static final Type<StaffS2C> TYPE = new Type<>(TradeExpansion.id("staff"));
	public static final StreamCodec<RegistryFriendlyByteBuf, StaffS2C> CODEC = StreamCodec.ofMember(StaffS2C::write, StaffS2C::read);

	/**
	 * @param level the player's reputation level here (M)
	 */
	public record Info(boolean enabled, int maxPerPlayer, int minLevel, int level, boolean reputation, long carrierFee, long clerkFee,
			long carrierWage, long clerkWage, int carrierCapacity, int clerkCapacity, double blocksPerDay, double accidentBase,
			double accidentPerThousand, double accidentMax, double quitChance, double maxWaitDays, double maxDistance, double commission,
			boolean backhaul) {
		void write(RegistryFriendlyByteBuf buf) {
			buf.writeBoolean(enabled);
			buf.writeVarInt(maxPerPlayer);
			buf.writeVarInt(minLevel);
			buf.writeVarInt(level);
			buf.writeBoolean(reputation);
			buf.writeVarLong(carrierFee);
			buf.writeVarLong(clerkFee);
			buf.writeVarLong(carrierWage);
			buf.writeVarLong(clerkWage);
			buf.writeVarInt(carrierCapacity);
			buf.writeVarInt(clerkCapacity);
			buf.writeDouble(blocksPerDay);
			buf.writeDouble(accidentBase);
			buf.writeDouble(accidentPerThousand);
			buf.writeDouble(accidentMax);
			buf.writeDouble(quitChance);
			buf.writeDouble(maxWaitDays);
			buf.writeDouble(maxDistance);
			buf.writeDouble(commission);
			buf.writeBoolean(backhaul);
		}

		static Info read(RegistryFriendlyByteBuf buf) {
			return new Info(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readVarLong(),
					buf.readVarLong(), buf.readVarLong(), buf.readVarLong(), buf.readVarInt(), buf.readVarInt(), buf.readDouble(), buf.readDouble(),
					buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readBoolean());
		}
	}

	/**
	 * One employee as its owner sees it.
	 *
	 * @param role     {@code Employee.Role} ordinal
	 * @param atHome   the screen is the employee's home market (or the home is gone): money, goods and settings can be changed
	 * @param distance carrier: blocks to the destination
	 * @param phase    {@code Employee.Phase} ordinal
	 * @param status   {@code Employee.Status} ordinal
	 * @param log      newest first
	 * @param auto     v0.48.1: a carrier choosing its goods (stock is then in items, unit 1)
	 * @param kinds    an automatic carrier: kinds of goods carried
	 * @param returnItems v0.54: items of the return cargo carried
	 */
	public record View(int id, int role, long home, Component homeName, boolean atHome, Identifier goods, Identifier item, int unit,
			long destination, Component destinationName, int distance, int quantity, long buyLimit, long sellLimit, long purse, int stock,
			int phase, boolean paused, int status, long phaseStart, long nextTime, long wage, long earned, long spent, long wages, long lost,
			int trips, int sold, List<Component> log, boolean auto, int kinds, int returnItems) {
		void write(RegistryFriendlyByteBuf buf) {
			buf.writeVarInt(id);
			buf.writeVarInt(role);
			buf.writeVarLong(home);
			ComponentSerialization.STREAM_CODEC.encode(buf, homeName);
			buf.writeBoolean(atHome);
			Identifier.STREAM_CODEC.encode(buf, goods);
			Identifier.STREAM_CODEC.encode(buf, item);
			buf.writeVarInt(unit);
			buf.writeLong(destination);
			ComponentSerialization.STREAM_CODEC.encode(buf, destinationName);
			buf.writeVarInt(distance);
			buf.writeVarInt(quantity);
			buf.writeVarLong(buyLimit);
			buf.writeVarLong(sellLimit);
			buf.writeVarLong(purse);
			buf.writeVarInt(stock);
			buf.writeVarInt(phase);
			buf.writeBoolean(paused);
			buf.writeVarInt(status);
			buf.writeLong(phaseStart);
			buf.writeLong(nextTime);
			buf.writeVarLong(wage);
			buf.writeVarLong(earned);
			buf.writeVarLong(spent);
			buf.writeVarLong(wages);
			buf.writeVarLong(lost);
			buf.writeVarInt(trips);
			buf.writeVarInt(sold);
			buf.writeVarInt(log.size());
			for (Component line : log) ComponentSerialization.STREAM_CODEC.encode(buf, line);
			buf.writeBoolean(auto);
			buf.writeVarInt(kinds);
			buf.writeVarInt(returnItems);
		}

		static View read(RegistryFriendlyByteBuf buf) {
			int id = buf.readVarInt();
			int role = buf.readVarInt();
			long home = buf.readVarLong();
			Component homeName = ComponentSerialization.STREAM_CODEC.decode(buf);
			boolean atHome = buf.readBoolean();
			Identifier goods = Identifier.STREAM_CODEC.decode(buf);
			Identifier item = Identifier.STREAM_CODEC.decode(buf);
			int unit = buf.readVarInt();
			long destination = buf.readLong();
			Component destinationName = ComponentSerialization.STREAM_CODEC.decode(buf);
			int distance = buf.readVarInt();
			int quantity = buf.readVarInt();
			long buyLimit = buf.readVarLong();
			long sellLimit = buf.readVarLong();
			long purse = buf.readVarLong();
			int stock = buf.readVarInt();
			int phase = buf.readVarInt();
			boolean paused = buf.readBoolean();
			int status = buf.readVarInt();
			long phaseStart = buf.readLong();
			long nextTime = buf.readLong();
			long wage = buf.readVarLong();
			long earned = buf.readVarLong();
			long spent = buf.readVarLong();
			long wages = buf.readVarLong();
			long lost = buf.readVarLong();
			int trips = buf.readVarInt();
			int sold = buf.readVarInt();
			int n = buf.readVarInt();
			List<Component> log = new ArrayList<>(n);
			for (int i = 0; i < n; i++) log.add(ComponentSerialization.STREAM_CODEC.decode(buf));
			return new View(id, role, home, homeName, atHome, goods, item, unit, destination, destinationName, distance, quantity, buyLimit,
					sellLimit, purse, stock, phase, paused, status, phaseStart, nextTime, wage, earned, spent, wages, lost, trips, sold, log, buf.readBoolean(),
					buf.readVarInt(), buf.readVarInt());
		}
	}

	/**
	 * A market a carrier could sell the good at.
	 *
	 * @param dx        offset from this market (Overworld blocks; Nether coordinates ×8), for the direction
	 * @param buyPrice  v0.48.1: a carrier's unit price here now (wholesale)
	 * @param sellPrice the carrier's unit price paid there now, or -1 when it takes none now (stock full)
	 * @param units     v0.48.1: units a carrier would take there now (0 = no profit now)
	 * @param profit    expected profit of the goods taken there, after the commission and the accident risk
	 * @param buyLimit  recommended highest buy price (the dearest unit of that trip)
	 * @param sellLimit recommended lowest sell price (what a unit of that trip costs)
	 * @param kinds     kinds of goods of that trip (an automatic carrier, {@code Employee.AUTO}; otherwise 1)
	 * @param returnItems  v0.54: items of the return cargo it would bring back now (0 = none, or {@code staff.carrierBackhaul} off)
	 * @param returnKinds  kinds of goods of that return cargo
	 * @param returnProfit expected profit of that return cargo (after the commission and the accident risk)
	 */
	public record Destination(long id, Component name, int dx, int dz, int distance, long buyPrice, long sellPrice, int units, long profit,
			long buyLimit, long sellLimit, int kinds, int returnItems, int returnKinds, long returnProfit) {
		void write(RegistryFriendlyByteBuf buf) {
			buf.writeVarLong(id);
			ComponentSerialization.STREAM_CODEC.encode(buf, name);
			buf.writeVarInt(dx);
			buf.writeVarInt(dz);
			buf.writeVarInt(distance);
			buf.writeLong(buyPrice);
			buf.writeLong(sellPrice);
			buf.writeVarInt(units);
			buf.writeLong(profit);
			buf.writeVarLong(buyLimit);
			buf.writeVarLong(sellLimit);
			buf.writeVarInt(kinds);
			buf.writeVarInt(returnItems);
			buf.writeVarInt(returnKinds);
			buf.writeLong(returnProfit);
		}

		static Destination read(RegistryFriendlyByteBuf buf) {
			return new Destination(buf.readVarLong(), ComponentSerialization.STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt(),
					buf.readVarInt(), buf.readLong(), buf.readLong(), buf.readVarInt(), buf.readLong(), buf.readVarLong(), buf.readVarLong(), buf.readVarInt(),
					buf.readVarInt(), buf.readVarInt(), buf.readLong());
		}
	}

	/** v0.48.1: the best destination of a carrier good now (units 0 = no profit anywhere now). */
	public record CarrierHint(Identifier goods, long destination, Component destinationName, int units, long profit) {
		void write(RegistryFriendlyByteBuf buf) {
			Identifier.STREAM_CODEC.encode(buf, goods);
			buf.writeLong(destination);
			ComponentSerialization.STREAM_CODEC.encode(buf, destinationName);
			buf.writeVarInt(units);
			buf.writeLong(profit);
		}

		static CarrierHint read(RegistryFriendlyByteBuf buf) {
			return new CarrierHint(Identifier.STREAM_CODEC.decode(buf), buf.readLong(), ComponentSerialization.STREAM_CODEC.decode(buf),
					buf.readVarInt(), buf.readLong());
		}
	}

	/**
	 * v0.48.1: a clerk good: the recommended lowest price and what a clerk would sell at it in one day from now.
	 *
	 * @param items   items sold in a day (with enough goods left with it)
	 * @param revenue coins of those sales
	 */
	public record ClerkHint(Identifier goods, long limit, int items, long revenue) {
		void write(RegistryFriendlyByteBuf buf) {
			Identifier.STREAM_CODEC.encode(buf, goods);
			buf.writeVarLong(limit);
			buf.writeVarInt(items);
			buf.writeVarLong(revenue);
		}

		static ClerkHint read(RegistryFriendlyByteBuf buf) {
			return new ClerkHint(Identifier.STREAM_CODEC.decode(buf), buf.readVarLong(), buf.readVarInt(), buf.readVarLong());
		}
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarLong(marketId);
		buf.writeVarInt(action);
		buf.writeVarInt(result.map(r -> r.ordinal() + 1).orElse(0));
		buf.writeLong(amount);
		buf.writeVarInt(subject);
		info.write(buf);
		buf.writeVarInt(staff.size());
		for (View v : staff) v.write(buf);
		writeIds(buf, carrierGoods);
		writeIds(buf, clerkGoods);
		buf.writeBoolean(destinationGoods.isPresent());
		destinationGoods.ifPresent(id -> Identifier.STREAM_CODEC.encode(buf, id));
		buf.writeVarInt(destinations.size());
		for (Destination d : destinations) d.write(buf);
		buf.writeBoolean(hints);
		buf.writeVarInt(carrierHints.size());
		for (CarrierHint h : carrierHints) h.write(buf);
		buf.writeVarInt(clerkHints.size());
		for (ClerkHint h : clerkHints) h.write(buf);
	}

	private static StaffS2C read(RegistryFriendlyByteBuf buf) {
		long marketId = buf.readVarLong();
		int action = buf.readVarInt();
		int result = buf.readVarInt();
		long amount = buf.readLong();
		int subject = buf.readVarInt();
		Info info = Info.read(buf);
		int n = buf.readVarInt();
		List<View> staff = new ArrayList<>(n);
		for (int i = 0; i < n; i++) staff.add(View.read(buf));
		List<Identifier> carrierGoods = readIds(buf);
		List<Identifier> clerkGoods = readIds(buf);
		Optional<Identifier> goods = buf.readBoolean() ? Optional.of(Identifier.STREAM_CODEC.decode(buf)) : Optional.empty();
		int m = buf.readVarInt();
		List<Destination> destinations = new ArrayList<>(m);
		for (int i = 0; i < m; i++) destinations.add(Destination.read(buf));
		boolean hints = buf.readBoolean();
		int hc = buf.readVarInt();
		List<CarrierHint> carrierHints = new ArrayList<>(hc);
		for (int i = 0; i < hc; i++) carrierHints.add(CarrierHint.read(buf));
		int hk = buf.readVarInt();
		List<ClerkHint> clerkHints = new ArrayList<>(hk);
		for (int i = 0; i < hk; i++) clerkHints.add(ClerkHint.read(buf));
		TradeResult[] results = TradeResult.values();
		Optional<TradeResult> r = result <= 0 || result > results.length ? Optional.empty() : Optional.of(results[result - 1]);
		return new StaffS2C(marketId, action, r, amount, subject, info, staff, carrierGoods, clerkGoods, goods, destinations, hints, carrierHints, clerkHints);
	}

	private static void writeIds(RegistryFriendlyByteBuf buf, List<Identifier> ids) {
		buf.writeVarInt(ids.size());
		for (Identifier id : ids) Identifier.STREAM_CODEC.encode(buf, id);
	}

	private static List<Identifier> readIds(RegistryFriendlyByteBuf buf) {
		int n = buf.readVarInt();
		List<Identifier> ids = new ArrayList<>(n);
		for (int i = 0; i < n; i++) ids.add(Identifier.STREAM_CODEC.decode(buf));
		return ids;
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
