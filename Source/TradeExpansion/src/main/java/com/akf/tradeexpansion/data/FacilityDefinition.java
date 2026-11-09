package com.akf.tradeexpansion.data;

import com.akf.tradeexpansion.development.FacilityKind;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * A facility of design N-4, from {@code data/<ns>/ak_trade_expansion/facility/<id>.json}.
 *
 * @param kind      which effect it has (one facility per kind and market)
 * @param order     position in the development tab
 * @param icon      item shown in the development tab
 * @param cost      development points it costs to build
 * @param structure template name: {@code <ns>:facility/<style>/<structure>} (style from the market's place, falling back
 *                  to {@code plains})
 * @param wanted    materials the village wants for it (N-2)
 * @param waterside v0.51 (harbor): rows from the front that stand on land; the rows behind stand over water
 */
public record FacilityDefinition(FacilityKind kind, int order, Identifier icon, long cost, String structure, List<Wanted> wanted,
		Optional<Integer> waterside) {
	/** Rows on land of a waterside building, or -1 for a building on land. */
	public int landRows() {
		return waterside.orElse(-1);
	}

	/**
	 * One wanted material: an item or an item tag, how many, and its value per item for the development points when it is
	 * not a good the market prices (tags always use the value).
	 */
	public record Wanted(Optional<Identifier> item, Optional<Identifier> tag, int count, double value) {
		public static final Codec<Wanted> CODEC = RecordCodecBuilder.create(i -> i.group(
				Identifier.CODEC.optionalFieldOf("item").forGetter(Wanted::item),
				Identifier.CODEC.optionalFieldOf("tag").forGetter(Wanted::tag),
				Codec.intRange(1, 100_000).fieldOf("count").forGetter(Wanted::count),
				Codec.doubleRange(0, 1e9).optionalFieldOf("value", 1.0).forGetter(Wanted::value)
		).apply(i, Wanted::new));

		/** Key of the donation counter: the item id, or {@code #} and the tag id. */
		public String key() {
			return item.map(Identifier::toString).orElseGet(() -> "#" + tag.map(Identifier::toString).orElse("?"));
		}

		public boolean matches(ItemStack stack) {
			if (stack.isEmpty() || !stack.getComponentsPatch().isEmpty()) return false; // plain items only (N-2)
			if (item.isPresent()) return BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(item.get());
			return tag.isPresent() && stack.is(TagKey.create(Registries.ITEM, tag.get()));
		}

		/** Item shown for it: the item, or the first item of the tag. */
		public Item displayItem() {
			if (item.isPresent()) return BuiltInRegistries.ITEM.getValue(item.get());
			if (tag.isPresent()) {
				return BuiltInRegistries.ITEM.get(TagKey.create(Registries.ITEM, tag.get()))
						.flatMap(set -> set.stream().findFirst()).map(h -> h.value()).orElse(net.minecraft.world.item.Items.BARRIER);
			}
			return net.minecraft.world.item.Items.BARRIER;
		}
	}

	public static final Codec<FacilityDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
			FacilityKind.CODEC.fieldOf("kind").forGetter(FacilityDefinition::kind),
			Codec.INT.optionalFieldOf("order", 0).forGetter(FacilityDefinition::order),
			Identifier.CODEC.fieldOf("icon").forGetter(FacilityDefinition::icon),
			Codec.LONG.fieldOf("cost").forGetter(FacilityDefinition::cost),
			Codec.STRING.fieldOf("structure").forGetter(FacilityDefinition::structure),
			Wanted.CODEC.listOf().optionalFieldOf("wanted", List.of()).forGetter(FacilityDefinition::wanted),
			Codec.intRange(1, 64).optionalFieldOf("waterside").forGetter(FacilityDefinition::waterside)
	).apply(i, FacilityDefinition::new));
}
