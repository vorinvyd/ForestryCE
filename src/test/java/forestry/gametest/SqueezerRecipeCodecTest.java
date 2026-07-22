package forestry.gametest;

import java.util.List;

import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import com.mojang.serialization.JsonOps;

import forestry.api.ForestryConstants;
import forestry.api.core.FluidProduct;
import forestry.core.FluidProductTypes;
import forestry.factory.recipes.SqueezerRecipe;

@GameTestHolder(ForestryConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public class SqueezerRecipeCodecTest {
	private static SqueezerRecipe sample() {
		return new SqueezerRecipe(
			ResourceLocation.fromNamespaceAndPath("forestry", "test_squeeze"),
			20,
			List.of(Ingredient.of(Items.APPLE)),
			FluidProduct.of(Fluids.WATER, 1000),
			new ItemStack(Items.STICK),
			0.5f
		);
	}

	@GameTest(template = "empty")
	public static void jsonRoundTrip(GameTestHelper helper) {
		FluidProductTypes.registerBuiltins();
		RegistryOps<com.google.gson.JsonElement> jsonOps = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
		SqueezerRecipe.Serializer serializer = new SqueezerRecipe.Serializer();
		SqueezerRecipe original = sample();
		var json = serializer.codec().codec().encodeStart(jsonOps, original).getOrThrow();
		SqueezerRecipe decoded = serializer.codec().codec().parse(jsonOps, json).getOrThrow();
		var fluid = decoded.getFluidOutput().createFluidStack();
		if (fluid.getFluid() != Fluids.WATER || fluid.getAmount() != 1000) {
			helper.fail("Recipe JSON round-trip lost fluid output: " + json);
			return;
		}
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void streamRoundTrip(GameTestHelper helper) {
		FluidProductTypes.registerBuiltins();
		SqueezerRecipe.Serializer serializer = new SqueezerRecipe.Serializer();
		SqueezerRecipe original = sample();
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		serializer.streamCodec().encode(buf, original);
		SqueezerRecipe decoded = serializer.streamCodec().decode(buf);
		var fluid = decoded.getFluidOutput().createFluidStack();
		if (fluid.getFluid() != Fluids.WATER || fluid.getAmount() != 1000) {
			helper.fail("Recipe stream round-trip lost fluid output");
			return;
		}
		helper.succeed();
	}
}
