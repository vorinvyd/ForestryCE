package forestry.gametest;

import java.util.List;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import forestry.api.ForestryConstants;
import forestry.api.core.IProduct;
import forestry.api.core.Product;
import forestry.api.recipes.ICentrifugeRecipe;
import forestry.apiculture.genetics.FireworkProduct;
import forestry.core.features.CoreItems;
import forestry.core.genetics.ProductTypes;
import forestry.core.utils.RecipeUtils;
import forestry.factory.features.FactoryRecipeTypes;

/**
 * Behavioral oracle for "centrifuge products as an IProduct dispatch". Proves that the default {@link Product}
 * serialises byte-for-byte like the plain product codec (no {@code "type"} key) and that a dynamic product
 * ({@link FireworkProduct}) round-trips through its own declared type, that the list stream codec round-trips a mixed
 * list, and that the built-in centrifuge recipes still expose and resolve their products through the migrated path.
 */
@GameTestHolder(ForestryConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public class CentrifugeProductDispatchTest {
	/** A plain Product must encode identically through the dispatch codec (no "type" key), and legacy JSON must decode. */
	@GameTest(template = "empty")
	public static void defaultProductMatchesPlainProductJson(GameTestHelper helper) {
		RegistryOps<JsonElement> ops = helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
		Product product = Product.of(Items.SUGAR, 2, 0.9f);

		JsonElement viaProduct = Product.CODEC.encodeStart(ops, product).getOrThrow();
		JsonElement viaDispatch = ProductTypes.CODEC.encodeStart(ops, product).getOrThrow();
		if (!viaProduct.equals(viaDispatch)) {
			helper.fail("Default product must encode byte-for-byte like the plain product codec. plain=" + viaProduct + " dispatch=" + viaDispatch);
			return;
		}
		if (viaDispatch.getAsJsonObject().has("type")) {
			helper.fail("Default product must not write a \"type\" key: " + viaDispatch);
			return;
		}

		// Legacy JSON (a bare product, no "type") must still decode under the dispatch codec.
		IProduct decoded = ProductTypes.CODEC.parse(ops, viaProduct).getOrThrow();
		if (!product.equals(decoded)) {
			helper.fail("Legacy product JSON decoded to a different product: " + decoded);
			return;
		}

		helper.succeed();
	}

	/** A dynamic product must round-trip through its own declared "type" key. */
	@GameTest(template = "empty")
	public static void dynamicProductRoundTripsWithType(GameTestHelper helper) {
		RegistryOps<JsonElement> ops = helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
		FireworkProduct firework = new FireworkProduct(0.5f);

		JsonElement json = ProductTypes.CODEC.encodeStart(ops, firework).getOrThrow();
		String type = json.getAsJsonObject().has("type") ? json.getAsJsonObject().get("type").getAsString() : null;
		if (!ForestryConstants.forestry("firework").toString().equals(type)) {
			helper.fail("Dynamic firework product must declare its type; got " + json);
			return;
		}

		IProduct decoded = ProductTypes.CODEC.parse(ops, json).getOrThrow();
		if (!(decoded instanceof FireworkProduct decodedFirework) || decodedFirework.chance() != 0.5f) {
			helper.fail("Firework product did not round-trip through the dispatch codec: " + decoded);
			return;
		}

		helper.succeed();
	}

	/** The list stream codec must round-trip a mixed list of a plain and a dynamic product. */
	@GameTest(template = "empty")
	public static void dispatchListStreamRoundTrip(GameTestHelper helper) {
		List<IProduct> products = List.of(Product.of(Items.SUGAR, 2, 0.9f), new FireworkProduct(0.5f));
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());

		ProductTypes.LIST_STREAM_CODEC.encode(buf, products);
		List<IProduct> decoded = ProductTypes.LIST_STREAM_CODEC.decode(buf);
		if (decoded.size() != 2 || !products.get(0).equals(decoded.get(0)) || !(decoded.get(1) instanceof FireworkProduct)) {
			helper.fail("List stream codec round-trip changed the products: " + decoded);
			return;
		}

		helper.succeed();
	}

	/** The built-in centrifuge recipes must still expose and resolve their products through the migrated path. */
	@GameTest(template = "empty")
	public static void centrifugeRecipesResolveProducts(GameTestHelper helper) {
		RecipeManager manager = helper.getLevel().getRecipeManager();

		ICentrifugeRecipe honeyComb = RecipeUtils.getRecipes(manager, FactoryRecipeTypes.CENTRIFUGE)
			.filter(recipe -> recipe.getId().equals(ForestryConstants.forestry("centrifuge/honey_comb")))
			.findFirst()
			.orElse(null);
		if (honeyComb == null) {
			helper.fail("Missing built-in centrifuge/honey_comb recipe");
			return;
		}

		// Beeswax is a guaranteed (chance 1.0) product of the honey comb.
		IProduct beeswax = honeyComb.getAllProducts().stream()
			.filter(product -> product.item() == CoreItems.BEESWAX.item())
			.findFirst()
			.orElse(null);
		if (beeswax == null) {
			helper.fail("Honey comb no longer lists beeswax among its products: " + honeyComb.getAllProducts());
			return;
		}
		if (beeswax.chance() != 1.0f) {
			helper.fail("Beeswax product chance changed: " + beeswax.chance());
			return;
		}

		// A guaranteed product must actually be produced.
		List<ItemStack> produced = honeyComb.getProducts(RandomSource.create(0), 1.0);
		boolean gotBeeswax = produced.stream().anyMatch(stack -> stack.is(CoreItems.BEESWAX.item()) && !stack.isEmpty());
		if (!gotBeeswax) {
			helper.fail("Honey comb did not produce its guaranteed beeswax output: " + produced);
			return;
		}

		helper.succeed();
	}
}
