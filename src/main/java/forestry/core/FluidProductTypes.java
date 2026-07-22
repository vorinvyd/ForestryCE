package forestry.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import forestry.api.ForestryConstants;
import forestry.api.core.FluidProduct;
import forestry.api.core.FluidProductType;
import forestry.api.core.IFluidProduct;

/**
 * Registry and dispatch codec for {@link IFluidProduct} types. Mirrors {@code forestry.core.genetics.ProductTypes}:
 * the {@code "type"} key is optional. When absent, the product decodes as the default {@link FluidProduct#TYPE}, and a
 * {@link FluidProduct} encodes without writing a {@code "type"} key at all. This keeps the common case (a fixed fluid)
 * as clean, backwards-compatible JSON, while dynamic products (addon-provided tag/random/chance outputs) round-trip
 * through their own type by declaring {@code "type"}.
 */
public final class FluidProductTypes {
	private static final Map<ResourceLocation, FluidProductType<?>> BY_ID = new ConcurrentHashMap<>();
	private static final Map<FluidProductType<?>, ResourceLocation> ID_OF = new ConcurrentHashMap<>();

	private static final String TYPE_KEY = "type";

	private static boolean builtinsRegistered = false;

	public static void register(ResourceLocation id, FluidProductType<?> type) {
		if (BY_ID.putIfAbsent(id, type) != null) {
			throw new IllegalStateException("Duplicate fluid product type: " + id);
		}
		ID_OF.put(type, id);
	}

	private static DataResult<FluidProductType<?>> byId(ResourceLocation id) {
		FluidProductType<?> type = BY_ID.get(id);
		if (type == null) {
			return DataResult.error(() -> "Unknown fluid product type: " + id);
		}
		return DataResult.success(type);
	}

	/**
	 * The dispatch codec. Unlike a stock {@link Codec#dispatch}, the {@code "type"} key is optional and defaults to
	 * {@link FluidProduct#TYPE}; encoding a {@link FluidProduct} omits the key entirely.
	 */
	public static final MapCodec<IFluidProduct> MAP_CODEC = new MapCodec<>() {
		@Override
		public <T> DataResult<IFluidProduct> decode(DynamicOps<T> ops, MapLike<T> input) {
			T typeValue = input.get(TYPE_KEY);
			DataResult<FluidProductType<?>> type = typeValue == null
				? DataResult.success(FluidProduct.TYPE)
				: ResourceLocation.CODEC.parse(ops, typeValue).flatMap(FluidProductTypes::byId);
			return type.flatMap(t -> t.codec().decode(ops, input).map(product -> (IFluidProduct) product));
		}

		@Override
		@SuppressWarnings({"unchecked", "rawtypes"})
		public <T> RecordBuilder<T> encode(IFluidProduct input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
			FluidProductType<?> type = input.type();
			if (type != FluidProduct.TYPE) {
				ResourceLocation id = ID_OF.get(type);
				if (id == null) {
					return prefix.withErrorsFrom(DataResult.error(() -> "Unregistered fluid product type: " + type));
				}
				prefix.add(TYPE_KEY, ResourceLocation.CODEC.encodeStart(ops, id));
			}
			return ((MapCodec) type.codec()).encode(input, ops, prefix);
		}

		@Override
		public <T> Stream<T> keys(DynamicOps<T> ops) {
			return Stream.of(ops.createString(TYPE_KEY));
		}
	};

	public static final Codec<IFluidProduct> CODEC = MAP_CODEC.codec();

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static final StreamCodec<RegistryFriendlyByteBuf, IFluidProduct> STREAM_CODEC = StreamCodec.of(
		(buf, product) -> {
			ResourceLocation id = ID_OF.get(product.type());
			ResourceLocation.STREAM_CODEC.encode(buf, id);
			((StreamCodec) product.type().streamCodec()).encode(buf, product);
		},
		buf -> {
			ResourceLocation id = ResourceLocation.STREAM_CODEC.decode(buf);
			return byId(id).getOrThrow().streamCodec().decode(buf);
		});

	/**
	 * Registers the built-in fluid product types under the {@code forestry} namespace.
	 * <p>
	 * Must be called before any datapack parse or network sync. Idempotent: repeated calls are no-ops.
	 */
	public static synchronized void registerBuiltins() {
		if (builtinsRegistered) {
			return;
		}
		builtinsRegistered = true;

		register(ForestryConstants.forestry("fluid"), FluidProduct.TYPE);
	}

	private FluidProductTypes() {}
}
