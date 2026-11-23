package com.akf.tradeexpansion.development;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.akf.tradeexpansion.core.FacilityLevelMath;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.Optional;

/**
 * A facility of one market (design N-4 / N-7), saved with the market. Mutable: the same record goes from construction to
 * completed, may be destroyed and is reused when it is rebuilt (so the inn's villagers come only once).
 */
public final class Facility {
	public enum State implements StringRepresentable {
		CONSTRUCTING("constructing"), COMPLETE("complete"), DESTROYED("destroyed");

		public static final Codec<State> CODEC = StringRepresentable.fromEnum(State::values);
		private final String name;

		State(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}

	public static final Codec<Facility> CODEC = RecordCodecBuilder.create(i -> i.group(
			Identifier.CODEC.fieldOf("id").forGetter(f -> f.id),
			FacilityKind.CODEC.fieldOf("kind").forGetter(f -> f.kind),
			State.CODEC.fieldOf("state").forGetter(f -> f.state),
			BlockPos.CODEC.fieldOf("origin").forGetter(f -> f.origin),
			Codec.intRange(0, 3).fieldOf("rotation").forGetter(f -> f.rotation.ordinal()),
			BlockPos.CODEC.fieldOf("front").forGetter(f -> f.front),
			BlockPos.CODEC.fieldOf("min").forGetter(f -> f.min),
			BlockPos.CODEC.fieldOf("max").forGetter(f -> f.max),
			BlockPos.CODEC.optionalFieldOf("core").forGetter(f -> Optional.ofNullable(f.core)),
			Codec.STRING.optionalFieldOf("style", "plains").forGetter(f -> f.style),
			Codec.LONG.fieldOf("started").forGetter(f -> f.started),
			Codec.LONG.fieldOf("due").forGetter(f -> f.due),
			Codec.BOOL.optionalFieldOf("villagers_called", false).forGetter(f -> f.villagersCalled),
			Levels.MAP_CODEC.forGetter(f -> new Levels(f.level, f.branch, f.tall, f.upgradeDue, f.upgradeTall))
	).apply(i, (id, kind, state, origin, rotation, front, min, max, core, style, started, due, called, levels) -> {
		Facility f = new Facility(id, kind);
		f.state = state;
		f.origin = origin;
		f.rotation = Rotation.values()[rotation];
		f.front = front;
		f.min = min;
		f.max = max;
		f.core = core.orElse(null);
		f.style = style;
		f.started = started;
		f.due = due;
		f.villagersCalled = called;
		f.level = Math.clamp(levels.level(), 1, FacilityLevelMath.MAX_LEVEL);
		f.branch = levels.branch();
		f.tall = levels.tall();
		f.upgradeDue = levels.upgradeDue();
		f.upgradeTall = levels.upgradeTall();
		return f;
	}));

	/**
	 * N-11 (v0.86): the level saved with the building (all optional: a building saved before has level 1).
	 *
	 * @param branch      direction chosen when it was raised to level 2: {@code a} or {@code b}, empty before
	 * @param tall        its level 3 look has the second storey
	 * @param upgradeDue  market-clock time the raise to the next level is done, {@code Long.MIN_VALUE} when none is under way
	 * @param upgradeTall the raise under way gets the second storey
	 */
	record Levels(int level, String branch, boolean tall, long upgradeDue, boolean upgradeTall) {
		static final com.mojang.serialization.MapCodec<Levels> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
				Codec.INT.optionalFieldOf("level", 1).forGetter(Levels::level),
				Codec.STRING.optionalFieldOf("branch", "").forGetter(Levels::branch),
				Codec.BOOL.optionalFieldOf("tall", false).forGetter(Levels::tall),
				Codec.LONG.optionalFieldOf("upgrade_due", Long.MIN_VALUE).forGetter(Levels::upgradeDue),
				Codec.BOOL.optionalFieldOf("upgrade_tall", false).forGetter(Levels::upgradeTall)
		).apply(i, Levels::new));
	}

	private final Identifier id;
	private final FacilityKind kind;
	private State state = State.CONSTRUCTING;
	/** Template origin and rotation (pivot = 0, see {@code FacilityBuilder}). */
	private BlockPos origin = BlockPos.ZERO;
	private Rotation rotation = Rotation.NONE;
	/** Front center at floor level: where the construction sign stands. */
	private BlockPos front = BlockPos.ZERO;
	/** Bounding box of the building (inclusive). */
	private BlockPos min = BlockPos.ZERO;
	private BlockPos max = BlockPos.ZERO;
	/** The facility sign inside the completed building; breaking it ends the effect. */
	private BlockPos core;
	private String style = "plains";
	private long started;
	private long due;
	private boolean villagersCalled;
	/** N-11 (v0.86): level 1..3, the direction chosen at level 2 ({@code a} / {@code b}) and the raise under way. */
	private int level = 1;
	private String branch = "";
	private boolean tall;
	private long upgradeDue = Long.MIN_VALUE;
	private boolean upgradeTall;

	public Facility(Identifier id, FacilityKind kind) {
		this.id = id;
		this.kind = kind;
	}

	/** Starts (or restarts, after it was destroyed) the construction at a site. */
	public void start(BlockPos origin, Rotation rotation, BlockPos front, BoundingBox box, String style, long now, long due) {
		this.state = State.CONSTRUCTING;
		this.origin = origin.immutable();
		this.rotation = rotation;
		this.front = front.immutable();
		this.min = new BlockPos(box.minX(), box.minY(), box.minZ());
		this.max = new BlockPos(box.maxX(), box.maxY(), box.maxZ());
		this.core = null;
		this.style = style;
		this.started = now;
		this.due = due;
	}

	public void complete(BlockPos core) {
		this.state = State.COMPLETE;
		this.core = core == null ? null : core.immutable();
	}

	public void destroy() {
		this.state = State.DESTROYED;
		this.upgradeDue = Long.MIN_VALUE; // a raise under way ends with the building (the level stays)
	}

	public int level() {
		return level;
	}

	/** The direction chosen at level 2: {@code a}, {@code b}, or empty at level 1. */
	public String branch() {
		return branch;
	}

	public boolean tall() {
		return tall;
	}

	/** A raise to the next level is under way (the building stays complete and keeps its effect meanwhile). */
	public boolean upgrading() {
		return state == State.COMPLETE && upgradeDue != Long.MIN_VALUE;
	}

	public long upgradeDue() {
		return upgradeDue;
	}

	public boolean upgradeTall() {
		return upgradeTall;
	}

	/** Starts raising the building to the next level; {@code branch} is set at the raise to level 2. */
	public void startUpgrade(String branch, boolean tall, long due) {
		if (level == 1) this.branch = branch;
		this.upgradeTall = tall;
		this.upgradeDue = due;
	}

	/** The raise is done: the next level, with the look it was raised to. */
	public void finishUpgrade(boolean tall) {
		this.level = Math.min(FacilityLevelMath.MAX_LEVEL, level + 1);
		this.tall = tall;
		this.upgradeDue = Long.MIN_VALUE;
		this.upgradeTall = false;
	}

	/** Rebuilt at a new place: the box of the look it now has (a level 3 that lost its second storey is no longer tall). */
	public void setTall(boolean tall) {
		this.tall = tall;
	}

	/** The box changes when the second storey goes up. */
	public void setBox(BoundingBox box) {
		this.min = new BlockPos(box.minX(), box.minY(), box.minZ());
		this.max = new BlockPos(box.maxX(), box.maxY(), box.maxZ());
	}

	public Identifier id() {
		return id;
	}

	public FacilityKind kind() {
		return kind;
	}

	public State state() {
		return state;
	}

	public boolean complete() {
		return state == State.COMPLETE;
	}

	public BlockPos origin() {
		return origin;
	}

	public Rotation rotation() {
		return rotation;
	}

	public BlockPos front() {
		return front;
	}

	public BoundingBox box() {
		return BoundingBox.fromCorners(min, max);
	}

	public BlockPos core() {
		return core;
	}

	public String style() {
		return style;
	}

	public long started() {
		return started;
	}

	public long due() {
		return due;
	}

	public boolean villagersCalled() {
		return villagersCalled;
	}

	public void setVillagersCalled() {
		this.villagersCalled = true;
	}
}
