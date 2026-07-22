package forestry.gametest;

import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.mojang.serialization.JsonOps;

import forestry.api.ForestryConstants;
import forestry.api.apiculture.IFlowerType;
import forestry.apiculture.PhotosynthesisFlowerType;
import forestry.apiculture.TagFlowerType;
import forestry.apiculture.WaterTagFlowerType;
import forestry.apiculture.genetics.FlowerTypeTypes;

@GameTestHolder(ForestryConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public class FlowerTypeCodecTest {
	@GameTest(template = "empty")
	public static void streamRoundTrip(GameTestHelper helper) {
		FlowerTypeTypes.registerBuiltins();
		IFlowerType[] samples = {
			new TagFlowerType(BlockTags.FLOWERS, true),
			new TagFlowerType(BlockTags.FLOWERS, false, BiomeTags.IS_END), // END-style
			new WaterTagFlowerType(BlockTags.FLOWERS, false),
			new PhotosynthesisFlowerType(),
		};
		for (IFlowerType original : samples) {
			RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
			FlowerTypeTypes.STREAM_CODEC.encode(buf, original);
			IFlowerType decoded = FlowerTypeTypes.STREAM_CODEC.decode(buf);
			if (decoded.getClass() != original.getClass() || decoded.isDominant() != original.isDominant()) {
				helper.fail("Stream round-trip mismatch for " + original.getClass().getSimpleName());
				return;
			}
		}
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void jsonRoundTrip(GameTestHelper helper) {
		FlowerTypeTypes.registerBuiltins();
		RegistryOps<com.google.gson.JsonElement> jsonOps = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
		IFlowerType end = new TagFlowerType(BlockTags.FLOWERS, false, BiomeTags.IS_END);
		var json = FlowerTypeTypes.CODEC.encodeStart(jsonOps, end).getOrThrow();
		IFlowerType decoded = FlowerTypeTypes.CODEC.parse(jsonOps, json).getOrThrow();
		if (!(decoded instanceof TagFlowerType t) || t.biomes() == null || t.isDominant()) {
			helper.fail("JSON round-trip lost biomes/dominant on END-style tag flower type: " + json);
			return;
		}
		helper.succeed();
	}
}
