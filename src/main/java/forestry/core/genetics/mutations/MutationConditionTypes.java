package forestry.core.genetics.mutations;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import forestry.api.ForestryConstants;
import forestry.api.genetics.IMutationCondition;
import forestry.api.genetics.MutationConditionType;

public final class MutationConditionTypes {
	private static final Map<ResourceLocation, MutationConditionType<?>> BY_ID = new ConcurrentHashMap<>();
	private static final Map<MutationConditionType<?>, ResourceLocation> ID_OF = new ConcurrentHashMap<>();

	private static boolean builtinsRegistered = false;

	public static void register(ResourceLocation id, MutationConditionType<?> type) {
		if (BY_ID.putIfAbsent(id, type) != null) {
			throw new IllegalStateException("Duplicate mutation condition type: " + id);
		}
		ID_OF.put(type, id);
	}

	private static MutationConditionType<?> byId(ResourceLocation id) {
		MutationConditionType<?> type = BY_ID.get(id);
		if (type == null) {
			throw new IllegalArgumentException("Unknown mutation condition type: " + id);
		}
		return type;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static final Codec<IMutationCondition> CODEC = ResourceLocation.CODEC
		.<IMutationCondition>dispatch("type", c -> ID_OF.get(c.type()), id -> ((MutationConditionType) byId(id)).codec());

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static final StreamCodec<RegistryFriendlyByteBuf, IMutationCondition> STREAM_CODEC = StreamCodec.of(
		(buf, condition) -> {
			ResourceLocation id = ID_OF.get(condition.type());
			ResourceLocation.STREAM_CODEC.encode(buf, id);
			((StreamCodec) condition.type().streamCodec()).encode(buf, condition);
		},
		buf -> {
			ResourceLocation id = ResourceLocation.STREAM_CODEC.decode(buf);
			return byId(id).streamCodec().decode(buf);
		});

	public static final Codec<List<IMutationCondition>> LIST_CODEC = CODEC.listOf();
	public static final StreamCodec<RegistryFriendlyByteBuf, List<IMutationCondition>> LIST_STREAM_CODEC =
		STREAM_CODEC.apply(ByteBufCodecs.list());

	/**
	 * Registers all 7 built-in mutation condition types under the {@code forestry} namespace.
	 * <p>
	 * Must be called before any datapack/recipe parse. Idempotent: repeated calls are no-ops.
	 */
	public static synchronized void registerBuiltins() {
		if (builtinsRegistered) {
			return;
		}
		builtinsRegistered = true;

		register(ForestryConstants.forestry("temperature"), MutationConditionTemperature.TYPE);
		register(ForestryConstants.forestry("humidity"), MutationConditionHumidity.TYPE);
		register(ForestryConstants.forestry("biome"), MutationConditionBiome.TYPE);
		register(ForestryConstants.forestry("daytime"), MutationConditionDaytime.TYPE);
		register(ForestryConstants.forestry("time_range"), MutationConditionTimeLimited.TYPE);
		register(ForestryConstants.forestry("requires_resource"), MutationConditionRequiresResource.TYPE);
		register(ForestryConstants.forestry("cave"), MutationConditionCave.TYPE);
	}

	private MutationConditionTypes() {}
}
