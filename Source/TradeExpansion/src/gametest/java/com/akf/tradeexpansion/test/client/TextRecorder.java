package com.akf.tradeexpansion.test.client;

import com.akf.tradeexpansion.test.mixin.client.GuiTextRenderStateAccessor;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;

import java.util.ArrayList;
import java.util.List;

/**
 * Records the texts of one rendered frame: what they say, the screen area they cover (after scaling and clipping) and
 * their color, so a test can find texts that overlap or leave the screen. Fed by GuiRenderStateMixin on the render
 * thread.
 */
public final class TextRecorder {
	private TextRecorder() {
	}

	/**
	 * One text of the frame; {@code stratum} grows for layers drawn on top (tooltips, overlays). {@code color}: the
	 * text's ARGB color (styled parts of a text may have their own). {@code afterMark}: drawn after the frame's mark
	 * ({@link #onMark}: the "sell all" box over the goods list).
	 */
	public record Text(String text, int x0, int y0, int x1, int y1, int stratum, int color, boolean afterMark) {
		public int width() {
			return x1 - x0;
		}

		public int height() {
			return y1 - y0;
		}

		/** The area of this text overlaps the rectangle. */
		public boolean inside(int rx0, int ry0, int rx1, int ry1) {
			return x1 > rx0 && x0 < rx1 && y1 > ry0 && y0 < ry1;
		}

		@Override
		public String toString() {
			return "\"" + text + "\"@" + x0 + "," + y0 + "-" + x1 + "," + y1;
		}
	}

	private enum State { IDLE, ARMED, RECORDING }

	private static volatile State state = State.IDLE;
	private static final List<Text> current = new ArrayList<>();
	private static volatile List<Text> captured;
	private static int stratum;
	private static int mark = -1;

	/** Start of a frame (GuiRenderState.reset). */
	public static void onFrame() {
		stratum = 0;
		switch (state) {
			case ARMED -> {
				current.clear();
				mark = -1;
				state = State.RECORDING;
			}
			case RECORDING -> {
				List<Text> frame = new ArrayList<>(current);
				if (mark >= 0) {
					for (int i = mark; i < frame.size(); i++) {
						Text t = frame.get(i);
						frame.set(i, new Text(t.text(), t.x0(), t.y0(), t.x1(), t.y1(), t.stratum(), t.color(), true));
					}
				}
				captured = List.copyOf(frame);
				state = State.IDLE;
			}
			default -> {
			}
		}
	}

	public static void onNextStratum() {
		stratum++;
	}

	/** Texts recorded from now on in this frame are {@code afterMark}. */
	public static void onMark() {
		if (state == State.RECORDING && mark < 0) mark = current.size();
	}

	public static void onText(GuiTextRenderState text) {
		if (state != State.RECORDING) return;
		ScreenRectangle bounds = text.bounds();
		if (bounds == null || bounds.width() <= 0 || bounds.height() <= 0) return; // clipped away
		StringBuilder sb = new StringBuilder();
		GuiTextRenderStateAccessor access = (GuiTextRenderStateAccessor) (Object) text;
		access.ak_trade_expansion_test$text().accept((index, style, codePoint) -> {
			sb.appendCodePoint(codePoint);
			return true;
		});
		String s = sb.toString();
		if (s.isBlank()) return;
		current.add(new Text(s, bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), stratum, access.ak_trade_expansion_test$color(), false));
	}

	/**
	 * The layer of the screen itself: the one with the most texts (a screen may draw a blurred background first, so it
	 * is not always 0). Tooltips are drawn in the layers above it.
	 */
	public static int baseStratum(List<Text> texts) {
		java.util.Map<Integer, Long> counts = new java.util.HashMap<>();
		for (Text t : texts) counts.merge(t.stratum(), 1L, Long::sum);
		return counts.entrySet().stream().max(java.util.Map.Entry.comparingByValue()).map(java.util.Map.Entry::getKey).orElse(0);
	}

	/** The texts of the next whole frame. */
	public static List<Text> capture(ClientGameTestContext context) {
		captured = null;
		state = State.ARMED;
		context.waitFor(client -> captured != null, 200);
		return captured;
	}
}
