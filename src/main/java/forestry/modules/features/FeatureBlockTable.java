package forestry.modules.features;

import forestry.api.core.IBlockSubtype;
import forestry.core.utils.datastructures.TriFunction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public class FeatureBlockTable<B extends Block, R extends IBlockSubtype, C extends IBlockSubtype> extends FeatureTable<FeatureBlockTable.Builder<B, R, C>, FeatureBlock<B, BlockItem>, R, C> {
	public FeatureBlockTable(Builder<B, R, C> builder) {
		super(builder);
	}

	@Override
	protected FeatureBlock<B, BlockItem> createFeature(Builder<B, R, C> builder, R rowType, C columnType) {
		return builder.registry.block(
			(properties) -> builder.constructor.apply(properties, rowType, columnType),
			builder.itemConstructor != null ? (block, properties) -> builder.itemConstructor.apply(block, properties, rowType, columnType) : null,
			builder.getIdentifier(rowType, columnType)
		);
	}

	public Collection<B> getBlocks() {
		ArrayList<B> blocks = new ArrayList<>(this.featureByTypes.size());
		for (FeatureBlock<B, BlockItem> feature : this.featureByTypes.values()) {
			blocks.add(feature.block());
		}
		return blocks;
	}

	public Collection<BlockItem> getItems() {
		ArrayList<BlockItem> list = new ArrayList<>();
		for (FeatureBlock<B, BlockItem> feature : this.featureByTypes.values()) {
			list.add(feature.item());
		}
		return list;
	}

	public Collection<B> getRowBlocks(R rowType) {
		return getRowFeatures(rowType).stream().map(IBlockFeature::block).collect(Collectors.toList());
	}

	public Collection<B> getColumnBlocks(C columnType) {
		return getColumnFeatures(columnType).stream().map(IBlockFeature::block).collect(Collectors.toList());
	}

	public static class Builder<B extends Block, R extends IBlockSubtype, C extends IBlockSubtype> extends FeatureTable.Builder<R, C, FeatureBlockTable<B, R, C>> {
		private final FeatureRegistry registry;
		private final TriFunction<BlockBehaviour.Properties, R, C, B> constructor;
		@Nullable
		private ItemConstructor<B, R, C> itemConstructor;

		public Builder(FeatureRegistry registry, TriFunction<BlockBehaviour.Properties, R, C, B> constructor) {
			super(registry);
			this.registry = registry;
			this.constructor = constructor;
		}

		public Builder<B, R, C> itemWithType(TriFunction<B, R, C, BlockItem> itemConstructor) {
			this.itemConstructor = (block, properties, rowType, columnType) -> itemConstructor.apply(block, rowType, columnType);
			return this;
		}

		public Builder<B, R, C> itemWithType(ItemConstructor<B, R, C> itemConstructor) {
			this.itemConstructor = itemConstructor;
			return this;
		}

		public Builder<B, R, C> item(Function<B, BlockItem> itemConstructor) {
			this.itemConstructor = (block, properties, rowType, columnType) -> itemConstructor.apply(block);
			return this;
		}

		public Builder<B, R, C> item(BiFunction<B, Item.Properties, BlockItem> itemConstructor) {
			this.itemConstructor = (block, properties, rowType, columnType) -> itemConstructor.apply(block, properties);
			return this;
		}

		public FeatureBlockTable<B, R, C> create() {
			return new FeatureBlockTable<>(this);
		}
	}

	@FunctionalInterface
	public interface ItemConstructor<B extends Block, R extends IBlockSubtype, C extends IBlockSubtype> {
		BlockItem apply(B block, Item.Properties properties, R rowType, C columnType);
	}
}
