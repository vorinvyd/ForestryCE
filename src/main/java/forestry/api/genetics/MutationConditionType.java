package forestry.api.genetics;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record MutationConditionType<T extends IMutationCondition>(
	MapCodec<T> codec,
	StreamCodec<RegistryFriendlyByteBuf, T> streamCodec
) {}
