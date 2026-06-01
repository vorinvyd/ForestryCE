package forestry.modules.features;

import forestry.api.core.IBlockSubtype;
import forestry.api.core.IItemSubtype;
import forestry.api.storage.EnumBackpackType;
import forestry.api.storage.IBackpackDefinition;
import forestry.core.utils.datastructures.TriFunction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.network.IContainerFactory;
import net.neoforged.neoforge.registries.DeferredRegister;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public interface IFeatureRegistry {
	<V> DeferredRegister<V> getRegistry(ResourceKey<? extends Registry<V>> registryKey);

	@Nullable
	<V> DeferredRegister<V> getRegistryNullable(ResourceKey<? extends Registry<V>> registry);

	<B extends Block, I extends BlockItem> FeatureBlock<B, I> block(Function<BlockBehaviour.Properties, B> constructor, String name);

	<B extends Block, I extends BlockItem> FeatureBlock<B, I> block(Function<BlockBehaviour.Properties, B> constructor, @Nullable BiFunction<B, Item.Properties, I> itemConstructor, String name);

	<B extends Block, I extends BlockItem> FeatureBlock<B, I> block(Function<BlockBehaviour.Properties, B> constructor, Supplier<BlockBehaviour.Properties> properties, @Nullable BiFunction<B, Item.Properties, I> itemConstructor, String name);

	<B extends Block, I extends BlockItem> FeatureBlock<B, I> block(Function<BlockBehaviour.Properties, B> constructor, Supplier<BlockBehaviour.Properties> blockProperties, @Nullable BiFunction<B, Item.Properties, I> itemConstructor, Supplier<Item.Properties> itemProperties, String name);

	<B extends Block, S extends IBlockSubtype> FeatureBlockGroup.Builder<B, S> blockGroup(Function<S, B> constructor, Collection<S> types);

	<B extends Block, S extends IBlockSubtype> FeatureBlockGroup.Builder<B, S> blockGroup(BiFunction<BlockBehaviour.Properties, S, B> constructor, Collection<S> types);

	<B extends Block, S extends IBlockSubtype> FeatureBlockGroup.Builder<B, S> blockGroup(Collection<S> types);

	<B extends Block, S extends IBlockSubtype> FeatureBlockGroup.Builder<B, S> blockGroup(S[] types);

	<I extends Item> FeatureItem<I> item(Supplier<I> constructor, String name);

	FeatureItem<Item> item(String name);

	<I extends Item> FeatureItem<I> item(Function<Item.Properties, I> constructor, Supplier<Item.Properties> properties, String identifier);

	FeatureItem<Item> backpack(IBackpackDefinition definition, EnumBackpackType type, String identifier);

	FeatureItem<Item> naturalistBackpack(IBackpackDefinition definition, ResourceLocation speciesTypeId, CreativeModeTab tab, String identifier);

	<I extends Item, S extends IItemSubtype> FeatureItemGroup<I, S> itemGroup(Function<S, I> constructor, String identifier, S[] subTypes);

	<I extends Item, S extends IItemSubtype> FeatureItemGroup.Builder<I, S> itemGroup(Function<S, I> constructor, S[] subTypes);

	<I extends Item, S extends IItemSubtype> FeatureItemGroup.Builder<I, S> itemGroup(BiFunction<S, Item.Properties, I> constructor, Function<S, Item.Properties> properties, S[] subTypes);

	<I extends Item, R extends IItemSubtype, C extends IItemSubtype> FeatureItemTable<I, R, C> itemTable(BiFunction<R, C, I> constructor, R[] rowTypes, C[] columnTypes, String identifier);

	<I extends Item, R extends IItemSubtype, C extends IItemSubtype> FeatureItemTable.Builder<I, R, C> itemTable(BiFunction<R, C, I> constructor, R[] rowTypes, C[] columnTypes);

	<B extends Block, R extends IBlockSubtype, C extends IBlockSubtype> FeatureBlockTable.Builder<B, R, C> blockTable(BiFunction<R, C, B> constructor, R[] rowTypes, C[] columnTypes);

	<B extends Block, R extends IBlockSubtype, C extends IBlockSubtype> FeatureBlockTable.Builder<B, R, C> blockTable(TriFunction<BlockBehaviour.Properties, R, C, B> constructor, R[] rowTypes, C[] columnTypes);

	FeatureFluid.Builder fluid(String identifier);

	<R extends Recipe<?>> FeatureRecipeType<R> recipeType(String name, Supplier<RecipeSerializer<? extends R>> serializer);

	void addRegistryListener(ResourceKey<? extends Registry<?>> type, Runnable listener);

	<F extends IModFeature> F register(F feature);

	<T extends BlockEntity> FeatureTileType<T> tile(BlockEntityType.BlockEntitySupplier<T> constructor, String identifier, Supplier<Collection<? extends Block>> validBlocks);

	<C extends AbstractContainerMenu> FeatureMenuType<C> menuType(IContainerFactory<C> factory, String identifier);

	<E extends Entity> FeatureEntityType<E> entity(EntityType.EntityFactory<E> factory, net.minecraft.world.entity.MobCategory classification, String identifier);

	<E extends Entity> FeatureEntityType<E> entity(EntityType.EntityFactory<E> factory, net.minecraft.world.entity.MobCategory classification, String identifier, UnaryOperator<EntityType.Builder<E>> consumer);

	<E extends Entity> FeatureEntityType<E> entity(EntityType.EntityFactory<E> factory, net.minecraft.world.entity.MobCategory classification, String identifier, UnaryOperator<EntityType.Builder<E>> consumer, Supplier<AttributeSupplier.Builder> attributes);

	FeatureCreativeTab creativeTab(String id, Consumer<CreativeModeTab.Builder> builder);

	Collection<IModFeature> getFeatures();

	Collection<IModFeature> getFeatures(ResourceKey<? extends Registry<?>> type);

	ResourceLocation getModuleId();

	static BlockBehaviour.Properties setBlockId(ResourceLocation id, BlockBehaviour.Properties properties) {
		return setId(Registries.BLOCK, id, properties);
	}

	static Item.Properties setItemId(ResourceLocation id, Item.Properties properties) {
		return setId(Registries.ITEM, id, properties);
	}

	@SuppressWarnings("unchecked")
	private static <T, P> P setId(ResourceKey<? extends Registry<T>> registry, ResourceLocation id, P properties) {
		try {
			properties.getClass().getMethod("setId", ResourceKey.class).invoke(properties, ResourceKey.create((ResourceKey<? extends Registry<T>>) registry, id));
		} catch (NoSuchMethodException ignored) {
			// Minecraft 1.21.1 does not require registry keys on block/item properties.
		} catch (ReflectiveOperationException exception) {
			throw new IllegalStateException("Failed to set registry id " + id + " on " + properties.getClass().getName(), exception);
		}
		return properties;
	}
}
