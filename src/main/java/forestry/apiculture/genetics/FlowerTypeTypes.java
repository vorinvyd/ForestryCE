package forestry.apiculture.genetics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.serialization.Codec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import forestry.api.ForestryConstants;
import forestry.api.apiculture.FlowerTypeType;
import forestry.api.apiculture.IFlowerType;
import forestry.apiculture.PhotosynthesisFlowerType;
import forestry.apiculture.TagFlowerType;
import forestry.apiculture.WaterTagFlowerType;

/**
 * Code registry of flower-type serializers ({@link FlowerTypeType}). The three built-ins are registered by
 * {@link #registerBuiltins()}; {@link #CODEC}/{@link #STREAM_CODEC} dispatch on a {@code "type"} field, exactly
 * like {@code MutationConditionTypes}.
 */
public class FlowerTypeTypes {
	private static final Map<ResourceLocation, FlowerTypeType<?>> BY_ID = new ConcurrentHashMap<>();
	private static final Map<FlowerTypeType<?>, ResourceLocation> ID_OF = new ConcurrentHashMap<>();

	private static boolean builtinsRegistered = false;

	public static void register(ResourceLocation id, FlowerTypeType<?> type) {
		if (BY_ID.putIfAbsent(id, type) != null) {
			throw new IllegalStateException("Duplicate flower type serializer: " + id);
		}
		ID_OF.put(type, id);
	}

	private static FlowerTypeType<?> byId(ResourceLocation id) {
		FlowerTypeType<?> type = BY_ID.get(id);
		if (type == null) {
			throw new IllegalArgumentException("Unknown flower type serializer: " + id);
		}
		return type;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static final Codec<IFlowerType> CODEC = ResourceLocation.CODEC
		.<IFlowerType>dispatch("type", c -> ID_OF.get(c.type()), id -> ((FlowerTypeType) byId(id)).codec());

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static final StreamCodec<RegistryFriendlyByteBuf, IFlowerType> STREAM_CODEC = StreamCodec.of(
		(buf, flowerType) -> {
			ResourceLocation id = ID_OF.get(flowerType.type());
			ResourceLocation.STREAM_CODEC.encode(buf, id);
			((StreamCodec) flowerType.type().streamCodec()).encode(buf, flowerType);
		},
		buf -> {
			ResourceLocation id = ResourceLocation.STREAM_CODEC.decode(buf);
			return byId(id).streamCodec().decode(buf);
		});

	/**
	 * Registers the three built-in flower-type serializers. Idempotent; must run before any datapack parse.
	 */
	public static synchronized void registerBuiltins() {
		if (builtinsRegistered) {
			return;
		}
		builtinsRegistered = true;

		register(ForestryConstants.forestry("tag_flower_type"), TagFlowerType.TYPE);
		register(ForestryConstants.forestry("water_tag_flower_type"), WaterTagFlowerType.TYPE);
		register(ForestryConstants.forestry("photosynthesis_flower_type"), PhotosynthesisFlowerType.TYPE);
	}

	private FlowerTypeTypes() {}
}
