package com.akf.tradeexpansion.development;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
			Codec.BOOL.optionalFieldOf("villagers_called", false).forGetter(f -> f.villagersCalled)
	).apply(i, (id, kind, state, origin, rotation, front, min, max, core, style, started, due, called) -> {
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
		return f;
	}));

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
