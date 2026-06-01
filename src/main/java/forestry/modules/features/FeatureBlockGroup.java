package forestry.modules.features;

import com.google.common.base.Preconditions;
import com.mojang.datafixers.util.Function3;
import forestry.api.core.IBlockSubtype;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public class FeatureBlockGroup<B extends Block, S extends IBlockSubtype> extends FeatureGroup<FeatureBlockGroup.Builder<B, S>, FeatureBlock<B, BlockItem>, S> {
	private FeatureBlockGroup(Builder<B, S> builder) {
		super(builder);
	}

	@Override
	protected FeatureBlock<B, BlockItem> createFeature(Builder<B, S> builder, S type) {
		String identifier = builder.getIdentifier(type);
		BlockBehaviour.Properties properties = builder.blockProperties != null ? builder.blockProperties.apply(BlockBehaviour.Properties.of(), type) : BlockBehaviour.Properties.of();
		return builder.registry.block(
			(blockProperties) -> builder.blockConstructor.apply(blockProperties, type),
			() -> properties,
			builder.itemConstructor != null ? (block, itemProperties) -> builder.itemConstructor.apply(block, itemProperties, type) : null,
			identifier
		);
	}

	// todo use immutable collection?
	public List<B> getList() {
		ArrayList<B> blocks = new ArrayList<>(this.featureByType.size());
		for (FeatureBlock<B, BlockItem> value : this.featureByType.values()) {
			blocks.add(value.block());
		}
		return blocks;
	}

	public Collection<BlockItem> getItems() {
		ArrayList<BlockItem> items = new ArrayList<>(this.featureByType.size());
		for (FeatureBlock<B, BlockItem> value : this.featureByType.values()) {
			items.add(value.item());
		}
		return items;
	}

	public Block[] blockArray() {
		return getList().toArray(new Block[0]);
	}

	public static class Builder<B extends Block, S extends IBlockSubtype> extends FeatureGroup.Builder<S, FeatureBlockGroup<B, S>> {
		private final FeatureRegistry registry;
		private final BiFunction<BlockBehaviour.Properties, S, B> blockConstructor;
		@Nullable
		private BiFunction<BlockBehaviour.Properties, S, BlockBehaviour.Properties> blockProperties;
		@Nullable
		private Function3<B, Item.Properties, S, BlockItem> itemConstructor;

		public Builder(FeatureRegistry registry, Collection<S> types, BiFunction<BlockBehaviour.Properties, S, B> blockConstructor) {
			super(registry, types);
			this.registry = registry;
			this.blockConstructor = blockConstructor;
		}

		public Builder<B, S> item(BiFunction<B, Item.Properties, BlockItem> itemConstructor) {
			return item(itemConstructor, UnaryOperator.identity());
		}

		public Builder<B, S> item(Function3<B, Item.Properties, S, BlockItem> itemConstructor, UnaryOperator<Item.Properties> props) {
			this.itemConstructor = (block, p, type) -> itemConstructor.apply(block, props.apply(p), type);
			return this;
		}

		public Builder<B, S> item(BiFunction<B, Item.Properties, BlockItem> itemConstructor, UnaryOperator<Item.Properties> props) {
			this.itemConstructor = (block, p, type) -> itemConstructor.apply(block, props.apply(p));
			return this;
		}

		public Builder<B, S> blockProperties(BiFunction<BlockBehaviour.Properties, S, BlockBehaviour.Properties> props) {
			this.blockProperties = props;
			return this;
		}

		public Builder<B, S> blockProperties(UnaryOperator<BlockBehaviour.Properties> props) {
			this.blockProperties = (p, type) -> props.apply(p);
			return this;
		}

		@Override
		public FeatureBlockGroup<B, S> create() {
			Preconditions.checkNotNull(this.blockConstructor);
			return new FeatureBlockGroup<>(this);
		}
	}
}
