package forestry.api.apiculture;

import com.mojang.serialization.MapCodec;
import forestry.apiculture.genetics.FlowerTypeTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * A code-registered serializer for a kind of {@link IFlowerType}. Mirrors {@code MutationConditionType}: the
 * dispatch key ("type" field in JSON) selects one of these, and its codecs (de)serialize the instance for
 * datapacks and network sync. Registered in {@link FlowerTypeTypes}.
 */
public record FlowerTypeType<T extends IFlowerType>(
	MapCodec<T> codec,
	StreamCodec<RegistryFriendlyByteBuf, T> streamCodec
) {}
