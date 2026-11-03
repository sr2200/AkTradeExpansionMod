package com.akf.tradeexpansion.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Mutable state of one good in one market (design B-1 / F-1). Minecraft-independent.
 */
public final class GoodState {
	/** priceFactor: per-market jitter of the base price, rolled once at market creation (D-1). */
	private final double priceFactor;
	/** X: supply pressure. Positive = oversold (cheaper), negative = overbought (more expensive). */
	private double pressure;
	/** S: current stock. */
	private double stock;
	private long lastTradeGameTime;
	/** Price graph (A-4, Q17): X sampled at most once per in-game day, oldest first. */
	private final List<Double> history;
	private long lastSampleDay;

	public GoodState(double priceFactor, double pressure, double stock, long lastTradeGameTime) {
		this(priceFactor, pressure, stock, lastTradeGameTime, List.of(), Long.MIN_VALUE);
	}

	public GoodState(double priceFactor, double pressure, double stock, long lastTradeGameTime, List<Double> history, long lastSampleDay) {
		this.priceFactor = priceFactor;
		this.pressure = pressure;
		this.stock = stock;
		this.lastTradeGameTime = lastTradeGameTime;
		this.history = new ArrayList<>(history);
		this.lastSampleDay = lastSampleDay;
	}

	public List<Double> history() {
		return history;
	}

	public long lastSampleDay() {
		return lastSampleDay;
	}

	/**
	 * Records the current X for {@code day} unless that day is already recorded; keeps the newest {@code max} samples.
	 */
	public void sample(long day, int max) {
		if (day <= lastSampleDay) return;
		lastSampleDay = day;
		history.add(pressure);
		while (history.size() > max) history.removeFirst();
	}

	public double priceFactor() {
		return priceFactor;
	}

	public double pressure() {
		return pressure;
	}

	public void setPressure(double pressure) {
		this.pressure = pressure;
	}

	public double stock() {
		return stock;
	}

	public void setStock(double stock) {
		this.stock = stock;
	}

	public long lastTradeGameTime() {
		return lastTradeGameTime;
	}

	public void setLastTradeGameTime(long lastTradeGameTime) {
		this.lastTradeGameTime = lastTradeGameTime;
	}
}
