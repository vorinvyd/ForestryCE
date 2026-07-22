package forestry.gametest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;

import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import forestry.api.ForestryConstants;
import forestry.api.core.HumidityType;
import forestry.api.core.Product;
import forestry.api.core.TemperatureType;
import forestry.api.genetics.ForestryTaxa;
import forestry.api.genetics.alleles.Allele;
import forestry.api.genetics.alleles.ButterflyChromosomes;
import forestry.api.genetics.alleles.ForestryAlleles;
import forestry.api.lepidopterology.ForestryButterflyEffects;
import forestry.lepidopterology.genetics.ButterflySpeciesDefinition;

/**
 * Behavioral oracle for {@link ButterflySpeciesDefinition}: proves that a definition with a data-chromosome genome
 * override (size), a reference-chromosome genome override (butterfly effect), and an optional biome tag survives
 * both the lazily-built JSON codec and the lazily-built network stream codec unchanged.
 */
@GameTestHolder(ForestryConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public class ButterflySpeciesDefinitionTest {
	private static ButterflySpeciesDefinition sample() {
		return new ButterflySpeciesDefinition(
			ForestryTaxa.GENUS_DANAUS,
			ForestryTaxa.SPECIES_MONARCH,
			true,
			false,
			false,
			0,
			"Sengir",
			0xffa722,
			TemperatureType.NORMAL,
			HumidityType.NORMAL,
			false,
			false,
			0.2f,
			5.0f,
			0xffa722,
			Optional.of(BiomeTags.IS_FOREST),
			List.of(Product.of(Items.PAPER)),
			List.of(),
			Map.of(
				// one inline-value chromosome
				ButterflyChromosomes.SIZE.id(), ForestryAlleles.SIZE_AVERAGE,
				// one reference chromosome
				ButterflyChromosomes.EFFECT.id(), Allele.reference(ForestryButterflyEffects.NONE)
			)
		);
	}

	@GameTest(template = "empty")
	public static void codecRoundTrips(GameTestHelper helper) {
		ButterflySpeciesDefinition def = sample();
		Codec<ButterflySpeciesDefinition> codec = ButterflySpeciesDefinition.codec();
		JsonElement json = codec.encodeStart(JsonOps.INSTANCE, def).getOrThrow();
		ButterflySpeciesDefinition decoded = codec.parse(JsonOps.INSTANCE, json).getOrThrow();
		if (!decoded.equals(def)) {
			helper.fail("Codec round-trip mismatch: " + decoded + " != " + def);
			return;
		}
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void streamCodecRoundTrips(GameTestHelper helper) {
		ButterflySpeciesDefinition def = sample();
		StreamCodec<RegistryFriendlyByteBuf, ButterflySpeciesDefinition> streamCodec = ButterflySpeciesDefinition.streamCodec();
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		streamCodec.encode(buf, def);
		ButterflySpeciesDefinition decoded = streamCodec.decode(buf);
		if (!decoded.equals(def)) {
			helper.fail("Stream codec round-trip mismatch: " + decoded + " != " + def);
			return;
		}
		helper.succeed();
	}
}
