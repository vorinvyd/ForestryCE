package forestry.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import forestry.api.ForestryConstants;
import forestry.arboriculture.blocks.ForestryLeafType;
import forestry.arboriculture.features.ArboricultureBlocks;

/**
 * Regression test for {@code *_default_leaves} items showing the raw grammar key "trees.grammar.leaves.type" as their
 * name (and, in-inventory, a uniform default-green tint) instead of a per-species name/tint.
 * <p>
 * Root cause: the default-leaves blocks were registered with {@code ItemBlockLeaves}, whose no-NBT fallback returns
 * {@code Component.translatable("trees.grammar.leaves.type")}. Because these blocks have no tile entity, their stacks
 * never carry NBT, so every one hit that fallback. The fix registers them with {@code ItemBlockDefaultLeaves}, which
 * resolves the species from the block's {@link ForestryLeafType} (like decorative leaves).
 * <p>
 * This asserts, server-side (name resolution needs no client), that two different default-leaves items produce the
 * COMPOSED grammar key {@code for.trees.grammar.leaves} with DIFFERENT species-name arguments - catching both the raw
 * unprefixed key and the "every species looks identical" symptom. The tint half of the bug is the client-only sibling
 * of this same species-resolution fix and can't be asserted headless.
 */
@GameTestHolder(ForestryConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public class DefaultLeavesItemTest {
	@GameTest(template = "empty")
	public static void defaultLeavesItemNameIsPerSpecies(GameTestHelper helper) {
		java.util.List<ForestryLeafType> types = ForestryLeafType.values();
		if (types.size() < 2) {
			helper.succeed();
			return;
		}

		Object arg0 = assertComposedNameArg(helper, ArboricultureBlocks.LEAVES_DEFAULT.get(types.get(0)).item());
		Object arg1 = assertComposedNameArg(helper, ArboricultureBlocks.LEAVES_DEFAULT.get(types.get(1)).item());

		// Per-species: two different leaf types must yield different species-name arguments (the bug made them all the
		// same raw key). Also covers the fruit variant, which shares ItemBlockDefaultLeaves.
		assertComposedNameArg(helper, ArboricultureBlocks.LEAVES_DEFAULT_FRUIT.get(types.get(0)).item());

		helper.assertTrue(!arg0.equals(arg1),
			"expected different default-leaves species to have different names, but both were " + arg0);
		helper.succeed();
	}

	/** Asserts the item's name is the composed grammar (not the raw fallback key) and returns its species-name arg. */
	private static Object assertComposedNameArg(GameTestHelper helper, Item item) {
		Component name = item.getName(new ItemStack(item));
		if (!(name.getContents() instanceof TranslatableContents tc)) {
			helper.fail("Expected a translatable name for " + item + ", got " + name.getContents());
			return "";
		}
		helper.assertTrue(tc.getKey().equals("for.trees.grammar.leaves"),
			"expected composed grammar key 'for.trees.grammar.leaves' but got '" + tc.getKey()
				+ "' (the raw fallback means the species wasn't resolved)");
		helper.assertTrue(tc.getArgs().length >= 1, "expected the composed name to carry a species argument");
		return tc.getArgs()[0];
	}
}
