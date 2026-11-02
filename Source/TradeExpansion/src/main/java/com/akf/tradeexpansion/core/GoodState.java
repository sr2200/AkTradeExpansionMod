package com.akf.tradeexpansion.core;

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

	public GoodState(double priceFactor, double pressure, double stock, long lastTradeGameTime) {
		this.priceFactor = priceFactor;
		this.pressure = pressure;
		this.stock = stock;
		this.lastTradeGameTime = lastTradeGameTime;
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
