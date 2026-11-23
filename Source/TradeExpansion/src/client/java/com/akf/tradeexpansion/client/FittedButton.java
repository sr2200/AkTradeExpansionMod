package com.akf.tradeexpansion.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * A button whose label is shrunk (down to {@link #MIN_SCALE}) to fit instead of scrolling: small buttons such as the
 * staff tab's "+1,000c" or "Change" would otherwise show only a moving part of their label. A wrapping button
 * ({@link #createWrapping}) may instead split a long label into two lines, when that keeps the letters larger
 * (the level 2 directions of a building: "旅人の溜まり場" in a half-width button).
 */
public final class FittedButton extends Button {
	/** Smallest scale of a label; a label still wider than that scrolls as usual. */
	public static final float MIN_SCALE = 0.6f;
	/** A one-line label shrunk to this scale or more is kept on one line even when two lines would be larger. */
	private static final float ONE_LINE_SCALE = 0.85f;
	/** Height of two lines of text at scale 1: two lines of 9 pixels, less the empty row under the last one. */
	private static final int TWO_LINES_HEIGHT = 17;
	private static final int COLOR_ACTIVE = 0xFFFFFFFF;
	private static final int COLOR_INACTIVE = 0xFFA0A0A0;

	private final boolean wrap;

	private FittedButton(int x, int y, int width, int height, Component message, OnPress onPress, boolean wrap) {
		super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
		this.wrap = wrap;
	}

	public static FittedButton create(Component message, OnPress onPress, int x, int y, int width, int height) {
		return new FittedButton(x, y, width, height, message, onPress, false);
	}

	/** A button that may split a long label into two lines (see the class comment). */
	public static FittedButton createWrapping(Component message, OnPress onPress, int x, int y, int width, int height) {
		return new FittedButton(x, y, width, height, message, onPress, true);
	}

	/** How the label is drawn: its lines (one or two) and their scale; {@code scale < MIN_SCALE} means it scrolls. */
	public record Label(String[] lines, float scale) {}

	public Label label(Font font) {
		int room = getWidth() - 2 * TEXT_MARGIN;
		String text = getMessage().getString();
		int textWidth = font.width(text);
		float one = textWidth <= room ? 1 : room / (float) textWidth;
		if (!wrap || one >= ONE_LINE_SCALE) return new Label(new String[] {text}, one);
		float tallest = Math.min(1, (getHeight() - 2) / (float) TWO_LINES_HEIGHT);
		int split = bestSplit(font, text, (int) (room / tallest));
		if (split <= 0) return new Label(new String[] {text}, one);
		String first = text.substring(0, split).stripTrailing();
		String second = text.substring(split).stripLeading();
		int widest = Math.max(font.width(first), font.width(second));
		float two = Math.min(room / (float) widest, tallest);
		return two > one ? new Label(new String[] {first, second}, two) : new Label(new String[] {text}, one);
	}

	/**
	 * Where to split {@code text} into two lines: at a space when there is one; in Japanese, after "の" or where kana
	 * turns into a kanji ("荷さばき|場", not "荷さ|ばき場") when both lines then fit in {@code fit} pixels, else
	 * where the lines are most even.
	 */
	private static int bestSplit(Font font, String text, int fit) {
		boolean spaces = text.indexOf(' ') > 0;
		int best = -1;
		int bestWidth = Integer.MAX_VALUE;
		int word = -1;
		int wordWidth = Integer.MAX_VALUE;
		for (int i = 1; i < text.length(); i++) {
			if (Character.isLowSurrogate(text.charAt(i))) continue;
			if (spaces && text.charAt(i - 1) != ' ') continue;
			int widest = Math.max(font.width(text.substring(0, i).stripTrailing()), font.width(text.substring(i).stripLeading()));
			if (widest < bestWidth) {
				bestWidth = widest;
				best = i;
			}
			if (!spaces && widest <= fit && widest < wordWidth && wordBreak(text.charAt(i - 1), text.charAt(i))) {
				wordWidth = widest;
				word = i;
			}
		}
		return word > 0 ? word : best;
	}

	/** Whether a Japanese word likely ends between {@code before} and {@code after}. */
	private static boolean wordBreak(char before, char after) {
		return before == 'の' || kana(before) && Character.isIdeographic(after);
	}

	private static boolean kana(char c) {
		Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
		return block == Character.UnicodeBlock.HIRAGANA || block == Character.UnicodeBlock.KATAKANA;
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		extractDefaultSprite(g);
		Font font = Minecraft.getInstance().font;
		Label label = label(font);
		float scale = label.scale();
		if (label.lines().length == 1 && (scale >= 1 || scale < MIN_SCALE)) {
			extractDefaultLabel(g.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
			return;
		}
		int color = active ? COLOR_ACTIVE : COLOR_INACTIVE;
		float textHeight = label.lines().length == 1 ? font.lineHeight * scale : TWO_LINES_HEIGHT * scale;
		g.pose().pushMatrix();
		g.pose().translate(getX() + getWidth() / 2.0f, getY() + (getHeight() - textHeight) / 2.0f + 0.5f);
		g.pose().scale(scale, scale);
		if (label.lines().length == 1) {
			g.text(font, getMessage(), -font.width(getMessage()) / 2, 0, color, true);
		} else {
			for (int i = 0; i < label.lines().length; i++) {
				Component line = Component.literal(label.lines()[i]).withStyle(getMessage().getStyle());
				g.text(font, line, -font.width(line) / 2, i * font.lineHeight, color, true);
			}
		}
		g.pose().popMatrix();
	}
}
