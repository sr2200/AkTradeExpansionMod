package com.akf.tradeexpansion.network;

import com.akf.tradeexpansion.TradeExpansion;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * T (v0.81): the merchant's notebook of the player: the takings of the last days and the price notes.
 *
 * @param open          open the notebook screen (false: refresh it when open)
 * @param reportOn      the player gets the morning report (their own switch)
 * @param reportEnabled the server sends morning reports ({@code notebook.dailyReport})
 * @param today         today's day number (overworld clock)
 * @param days          the days with takings, oldest first (today last when anything happened)
 * @param notes         price notes, newest first
 * @param staff         v0.83: the player's employees, as on the staff tab (nothing can be changed from the notebook)
 * @param lectern       v0.85: opened from a notebook on a lectern (the screen offers to take it)
 */
public record NotebookS2C(boolean open, boolean reportOn, boolean reportEnabled, long today, List<DayView> days, List<NoteView> notes,
		List<StaffS2C.View> staff, boolean lectern)
		implements CustomPacketPayload {
	public static final Type<NotebookS2C> TYPE = new Type<>(TradeExpansion.id("notebook"));

	/** An item of a goods (with the goods' components, e.g. an enchanted book). */
	public record GoodsItem(Identifier item, DataComponentPatch patch) {
		static final StreamCodec<RegistryFriendlyByteBuf, GoodsItem> CODEC = StreamCodec.composite(
				Identifier.STREAM_CODEC, GoodsItem::item,
				DataComponentPatch.STREAM_CODEC, GoodsItem::patch,
				GoodsItem::new);

		public ItemStack stack() {
			return new ItemStack(BuiltInRegistries.ITEM.wrapAsHolder(BuiltInRegistries.ITEM.getValue(item)), 1, patch);
		}
	}

	/** A goods on a day's list. */
	public record GoodsView(GoodsItem item, long items, long sales, long profit) {
		static final StreamCodec<RegistryFriendlyByteBuf, GoodsView> CODEC = StreamCodec.ofMember(GoodsView::write, GoodsView::read);

		private void write(RegistryFriendlyByteBuf buf) {
			GoodsItem.CODEC.encode(buf, item);
			buf.writeVarLong(items);
			buf.writeVarLong(sales);
			buf.writeLong(profit);
		}

		private static GoodsView read(RegistryFriendlyByteBuf buf) {
			return new GoodsView(GoodsItem.CODEC.decode(buf), buf.readVarLong(), buf.readVarLong(), buf.readLong());
		}
	}

	/** One day: see {@code LedgerMath.Day}. {@code top}: the most profitable goods, best first. */
	public record DayView(long day, long sales, long purchases, long profit, long quests, long staff, int trades, List<GoodsView> top) {
		static final StreamCodec<RegistryFriendlyByteBuf, DayView> CODEC = StreamCodec.ofMember(DayView::write, DayView::read);

		public long totalProfit() {
			return profit + staff;
		}

		private void write(RegistryFriendlyByteBuf buf) {
			buf.writeLong(day);
			buf.writeVarLong(sales);
			buf.writeVarLong(purchases);
			buf.writeLong(profit);
			buf.writeVarLong(quests);
			buf.writeLong(staff);
			buf.writeVarInt(trades);
			GoodsView.CODEC.apply(ByteBufCodecs.list()).encode(buf, top);
		}

		private static DayView read(RegistryFriendlyByteBuf buf) {
			return new DayView(buf.readLong(), buf.readVarLong(), buf.readVarLong(), buf.readLong(), buf.readVarLong(), buf.readLong(),
					buf.readVarInt(), GoodsView.CODEC.apply(ByteBufCodecs.list()).decode(buf));
		}
	}

	/** A price on a note: per trading unit, -1 when the market did not sell / buy it then. */
	public record PriceView(Identifier goods, GoodsItem item, int unit, long buy, long sell) {
		static final StreamCodec<RegistryFriendlyByteBuf, PriceView> CODEC = StreamCodec.ofMember(PriceView::write, PriceView::read);

		private void write(RegistryFriendlyByteBuf buf) {
			Identifier.STREAM_CODEC.encode(buf, goods);
			GoodsItem.CODEC.encode(buf, item);
			buf.writeVarInt(unit);
			buf.writeLong(buy);
			buf.writeLong(sell);
		}

		private static PriceView read(RegistryFriendlyByteBuf buf) {
			return new PriceView(Identifier.STREAM_CODEC.decode(buf), GoodsItem.CODEC.decode(buf), buf.readVarInt(), buf.readLong(), buf.readLong());
		}
	}

	/**
	 * The prices of a market as the player saw them on {@code day}.
	 *
	 * @param title     "name (level)" now (C-4)
	 * @param dimension the market's dimension
	 * @param x / z     the market's center
	 */
	public record NoteView(long market, Component title, Identifier dimension, int x, int z, long day, List<PriceView> prices) {
		static final StreamCodec<RegistryFriendlyByteBuf, NoteView> CODEC = StreamCodec.ofMember(NoteView::write, NoteView::read);

		private void write(RegistryFriendlyByteBuf buf) {
			buf.writeVarLong(market);
			ComponentSerialization.STREAM_CODEC.encode(buf, title);
			Identifier.STREAM_CODEC.encode(buf, dimension);
			buf.writeVarInt(x);
			buf.writeVarInt(z);
			buf.writeLong(day);
			PriceView.CODEC.apply(ByteBufCodecs.list()).encode(buf, prices);
		}

		private static NoteView read(RegistryFriendlyByteBuf buf) {
			return new NoteView(buf.readVarLong(), ComponentSerialization.STREAM_CODEC.decode(buf), Identifier.STREAM_CODEC.decode(buf),
					buf.readVarInt(), buf.readVarInt(), buf.readLong(), PriceView.CODEC.apply(ByteBufCodecs.list()).decode(buf));
		}
	}

	public static final StreamCodec<RegistryFriendlyByteBuf, NotebookS2C> CODEC = StreamCodec.ofMember(NotebookS2C::write, NotebookS2C::read);

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeBoolean(open);
		buf.writeBoolean(reportOn);
		buf.writeBoolean(reportEnabled);
		buf.writeLong(today);
		DayView.CODEC.apply(ByteBufCodecs.list()).encode(buf, days);
		NoteView.CODEC.apply(ByteBufCodecs.list()).encode(buf, notes);
		buf.writeVarInt(staff.size());
		for (StaffS2C.View v : staff) v.write(buf);
		buf.writeBoolean(lectern);
	}

	private static NotebookS2C read(RegistryFriendlyByteBuf buf) {
		boolean open = buf.readBoolean();
		boolean reportOn = buf.readBoolean();
		boolean reportEnabled = buf.readBoolean();
		long today = buf.readLong();
		List<DayView> days = DayView.CODEC.apply(ByteBufCodecs.list()).decode(buf);
		List<NoteView> notes = NoteView.CODEC.apply(ByteBufCodecs.list()).decode(buf);
		int n = buf.readVarInt();
		List<StaffS2C.View> staff = new java.util.ArrayList<>(n);
		for (int i = 0; i < n; i++) staff.add(StaffS2C.View.read(buf));
		boolean lectern = buf.readBoolean();
		return new NotebookS2C(open, reportOn, reportEnabled, today, days, notes, staff, lectern);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
