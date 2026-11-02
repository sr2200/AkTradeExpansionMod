package com.akf.tradeexpansion.data.condition;

import com.akf.tradeexpansion.TradeExpansion;
import com.akf.tradeexpansion.data.MarketContext;
import com.akf.tradeexpansion.market.MarketOrigin;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Conditions of {@code market_profile} and goods {@code auto_add} (design D-2).
 * JSON: {@code {"type": "trade_expansion:biome", "biome": "#minecraft:is_jungle"}}.
 */
public interface MarketCondition {
	boolean test(MarketContext context);

	Identifier type();

	Codec<MarketCondition> CODEC = Codec.recursive("market_condition", self -> {
		Codec<List<MarketCondition>> list = self.listOf();
		Map<Identifier, MapCodec<? extends MarketCondition>> types = Map.of(
				TradeExpansion.id("dimension"), Dimension.CODEC,
				TradeExpansion.id("biome"), BiomeIs.CODEC,
				TradeExpansion.id("structure"), InStructure.CODEC,
				TradeExpansion.id("villager_type"), VillagerTypeIs.CODEC,
				TradeExpansion.id("origin"), Origin.CODEC,
				TradeExpansion.id("distance_from_spawn"), DistanceFromSpawn.CODEC,
				TradeExpansion.id("all_of"), list.fieldOf("terms").xmap(AllOf::new, AllOf::terms),
				TradeExpansion.id("any_of"), list.fieldOf("terms").xmap(AnyOf::new, AnyOf::terms),
				TradeExpansion.id("not"), self.fieldOf("term").xmap(Not::new, Not::term));
		return Identifier.CODEC.partialDispatch("type",
				c -> DataResult.success(c.type()),
				id -> Optional.<MapCodec<? extends MarketCondition>>ofNullable(types.get(id))
						.map(DataResult::success)
						.orElseGet(() -> DataResult.error(() -> "Unknown market condition type: " + id)));
	});

	/** "minecraft:plains" or "#minecraft:is_jungle". */
	record IdOrTag(Identifier id, boolean tag) {
		public static final Codec<IdOrTag> CODEC = Codec.STRING.comapFlatMap(IdOrTag::parse, IdOrTag::toString);

		static DataResult<IdOrTag> parse(String s) {
			boolean tag = s.startsWith("#");
			return Identifier.read(tag ? s.substring(1) : s).map(id -> new IdOrTag(id, tag));
		}

		@Override
		public String toString() {
			return (tag ? "#" : "") + id;
		}
	}

	record Dimension(ResourceKey<Level> dimension) implements MarketCondition {
		static final MapCodec<Dimension> CODEC = Level.RESOURCE_KEY_CODEC.fieldOf("dimension").xmap(Dimension::new, Dimension::dimension);

		public boolean test(MarketContext c) {
			return c.world().dimension().equals(dimension);
		}

		public Identifier type() {
			return TradeExpansion.id("dimension");
		}
	}

	record BiomeIs(IdOrTag biome) implements MarketCondition {
		static final MapCodec<BiomeIs> CODEC = IdOrTag.CODEC.fieldOf("biome").xmap(BiomeIs::new, BiomeIs::biome);

		public boolean test(MarketContext c) {
			return biome.tag()
					? c.biome().is(TagKey.create(Registries.BIOME, biome.id()))
					: c.biome().is(ResourceKey.create(Registries.BIOME, biome.id()));
		}

		public Identifier type() {
			return TradeExpansion.id("biome");
		}
	}

	/** The market center lies inside the bounds of such a structure. */
	record InStructure(IdOrTag structure) implements MarketCondition {
		static final MapCodec<InStructure> CODEC = IdOrTag.CODEC.fieldOf("structure").xmap(InStructure::new, InStructure::structure);

		public boolean test(MarketContext c) {
			HolderLookup.RegistryLookup<Structure> lookup = c.world().registryAccess().lookupOrThrow(Registries.STRUCTURE);
			BlockPos pos = c.center();
			if (structure.tag()) {
				return lookup.get(TagKey.create(Registries.STRUCTURE, structure.id()))
						.map(set -> c.world().structureManager().getStructureAt(pos, set).isValid())
						.orElse(false);
			}
			return lookup.get(ResourceKey.create(Registries.STRUCTURE, structure.id()))
					.map(holder -> c.world().structureManager().getStructureAt(pos, holder.value()).isValid())
					.orElse(false);
		}

		public Identifier type() {
			return TradeExpansion.id("structure");
		}
	}

	/** Villager type derived from the biome at the center (plains when the biome has none). */
	record VillagerTypeIs(Identifier villagerType) implements MarketCondition {
		static final MapCodec<VillagerTypeIs> CODEC = Identifier.CODEC.fieldOf("villager_type").xmap(VillagerTypeIs::new, VillagerTypeIs::villagerType);

		public boolean test(MarketContext c) {
			ResourceKey<VillagerType> type = c.biome().unwrapKey()
					.map(VillagerType.BY_BIOME::get)
					.orElse(null);
			if (type == null) type = VillagerType.PLAINS;
			return type.identifier().equals(villagerType);
		}

		public Identifier type() {
			return TradeExpansion.id("villager_type");
		}
	}

	record Origin(MarketOrigin origin) implements MarketCondition {
		static final MapCodec<Origin> CODEC = MarketOrigin.CODEC.fieldOf("origin").xmap(Origin::new, Origin::origin);

		public boolean test(MarketContext c) {
			return c.origin() == origin;
		}

		public Identifier type() {
			return TradeExpansion.id("origin");
		}
	}

	/** Horizontal distance from the world spawn point. */
	record DistanceFromSpawn(Optional<Double> min, Optional<Double> max) implements MarketCondition {
		static final MapCodec<DistanceFromSpawn> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
				Codec.DOUBLE.optionalFieldOf("min").forGetter(DistanceFromSpawn::min),
				Codec.DOUBLE.optionalFieldOf("max").forGetter(DistanceFromSpawn::max)
		).apply(i, DistanceFromSpawn::new));

		public boolean test(MarketContext c) {
			BlockPos spawn = c.world().getRespawnData().pos();
			double dx = spawn.getX() - c.center().getX();
			double dz = spawn.getZ() - c.center().getZ();
			double d = Math.sqrt(dx * dx + dz * dz);
			return min.map(v -> d >= v).orElse(true) && max.map(v -> d <= v).orElse(true);
		}

		public Identifier type() {
			return TradeExpansion.id("distance_from_spawn");
		}
	}

	record AllOf(List<MarketCondition> terms) implements MarketCondition {
		public boolean test(MarketContext c) {
			return terms.stream().allMatch(t -> t.test(c));
		}

		public Identifier type() {
			return TradeExpansion.id("all_of");
		}
	}

	record AnyOf(List<MarketCondition> terms) implements MarketCondition {
		public boolean test(MarketContext c) {
			return terms.stream().anyMatch(t -> t.test(c));
		}

		public Identifier type() {
			return TradeExpansion.id("any_of");
		}
	}

	record Not(MarketCondition term) implements MarketCondition {
		public boolean test(MarketContext c) {
			return !term.test(c);
		}

		public Identifier type() {
			return TradeExpansion.id("not");
		}
	}

	static boolean all(List<MarketCondition> conditions, MarketContext context) {
		return conditions.stream().allMatch(c -> c.test(context));
	}
}
