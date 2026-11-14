package com.akf.tradeexpansion.test.client;

import com.akf.tradeexpansion.test.mixin.client.GuiTextRenderStateAccessor;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;

import java.util.ArrayList;
import java.util.List;

/**
 * Records the texts of one rendered frame: what they say and the screen area they cover (after scaling and clipping),
 * so a test can find texts that overlap or leave the screen. Fed by GuiRenderStateMixin on the render thread.
 */
public final class TextRecorder {
	private TextRecorder() {
	}

	/** One text of the frame; {@code stratum} grows for layers drawn on top (tooltips, overlays). */
	public record Text(String text, int x0, int y0, int x1, int y1, int stratum) {
		public int width() {
			return x1 - x0;
		}

		public int height() {
			return y1 - y0;
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

	/** Start of a frame (GuiRenderState.reset). */
	public static void onFrame() {
		stratum = 0;
		switch (state) {
			case ARMED -> {
				current.clear();
				state = State.RECORDING;
			}
			case RECORDING -> {
				captured = List.copyOf(current);
				state = State.IDLE;
			}
			default -> {
			}
		}
	}

	public static void onNextStratum() {
		stratum++;
	}

	public static void onText(GuiTextRenderState text) {
		if (state != State.RECORDING) return;
		ScreenRectangle bounds = text.bounds();
		if (bounds == null || bounds.width() <= 0 || bounds.height() <= 0) return; // clipped away
		StringBuilder sb = new StringBuilder();
		((GuiTextRenderStateAccessor) (Object) text).ak_trade_expansion_test$text().accept((index, style, codePoint) -> {
			sb.appendCodePoint(codePoint);
			return true;
		});
		String s = sb.toString();
		if (s.isBlank()) return;
		current.add(new Text(s, bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), stratum));
	}

	/** The texts of the next whole frame. */
	public static List<Text> capture(ClientGameTestContext context) {
		captured = null;
		state = State.ARMED;
		context.waitFor(client -> captured != null, 200);
		return captured;
	}
}
