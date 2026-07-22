package forestry.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import forestry.api.ForestryConstants;
import forestry.api.apiculture.IBeeHousing;
import forestry.api.apiculture.IBeeListener;
import forestry.api.apiculture.IBeeModifier;
import forestry.api.apiculture.IBeeHousingInventory;
import forestry.api.apiculture.IBeekeepingLogic;
import forestry.api.apiculture.genetics.IBeeSpecies;
import forestry.api.apiculture.genetics.IBeeSpeciesType;
import forestry.api.core.HumidityType;
import forestry.api.core.IErrorLogic;
import forestry.api.core.IProduct;
import forestry.api.core.TemperatureType;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.ISpecies;
import forestry.api.genetics.alleles.Allele;
import forestry.api.genetics.alleles.AllelePair;
import forestry.api.genetics.alleles.IChromosome;
import forestry.apiculture.BeeSpecies;
import forestry.apiculture.genetics.BeeSpeciesDefinition;
import forestry.apiculture.genetics.BeeSpeciesProjector;
import forestry.core.utils.SpeciesUtil;

/**
 * The pivotal "no behavior change" proof for Stage 3: for every built-in bee species, decodes the
 * {@code BeeSpeciesProvider}-generated JSON from the test classpath, projects it via {@link BeeSpeciesProjector},
 * and asserts the projected {@link BeeSpecies} is equivalent to the live code-built species from
 * {@link IBeeSpeciesType#getAllSpecies()} - the runtime still uses the code-built path, so this proves the data-driven
 * definition is a faithful parallel artifact, not yet that it's wired up.
 * <p>
 * The default-genome comparison is the heart of the guarantee: every chromosome's active+inactive allele must match
 * exactly, including reference-vs-data dispatch (a bug there would silently corrupt breeding).
 */
@GameTestHolder(ForestryConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public class BeeSpeciesEquivalenceTest {
	@GameTest(template = "empty")
	public static void projectedMatchesCodeBuilt(GameTestHelper helper) {
		IBeeSpeciesType type = SpeciesUtil.BEE_TYPE.get();
		RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());

		List<IBeeSpecies> allSpecies = type.getAllSpecies();
		// Canary against a silent species drop. 69 is the current number of DefaultBeeSpecies registrations, which is
		// exactly the number of bee_species/*.json files runData generates (one per builder) - so this floor also
		// tracks the generated-JSON count. A tight floor (not a soft "~50") makes any accidentally-dropped species
		// fail here immediately rather than passing by iterating a quietly-shortened list; adding a new built-in bee
		// is expected to bump this constant in lockstep with regenerating the JSON.
		int expectedBuiltinCount = 69;
		if (allSpecies.size() < expectedBuiltinCount) {
			helper.fail("Expected at least " + expectedBuiltinCount + " built-in bee species, but only found " + allSpecies.size() + " (a species was silently dropped?)");
			return;
		}

		for (IBeeSpecies codeBuilt : allSpecies) {
			ResourceLocation id = codeBuilt.id();

			JsonElement json = readGeneratedJson(id);
			if (json == null) {
				helper.fail("Missing generated bee species JSON for " + id + " (run runData?)");
				return;
			}

			BeeSpeciesDefinition def = BeeSpeciesDefinition.codec().parse(ops, json).getOrThrow();
			BeeSpecies projected = BeeSpeciesProjector.project(type, id, def);
			if (projected == null) {
				helper.fail("Projection failed for " + id + " (see log for the projector's failure cause)");
				return;
			}

			String mismatch = compare(type, codeBuilt, projected, def, helper);
			if (mismatch != null) {
				helper.fail("Mismatch for " + id + ": " + mismatch);
				return;
			}
		}

		helper.succeed();
	}

	@Nullable
	private static String compare(IBeeSpeciesType type, IBeeSpecies codeBuilt, BeeSpecies projected, BeeSpeciesDefinition def, GameTestHelper helper) {
		if (codeBuilt.getBody() != projected.getBody()) {
			return "body " + codeBuilt.getBody() + " != " + projected.getBody();
		}
		if (codeBuilt.getStripes() != projected.getStripes()) {
			return "stripes " + codeBuilt.getStripes() + " != " + projected.getStripes();
		}
		if (codeBuilt.getOutline() != projected.getOutline()) {
			return "outline " + codeBuilt.getOutline() + " != " + projected.getOutline();
		}
		if (codeBuilt.getEscritoireColor() != projected.getEscritoireColor()) {
			return "escritoireColor " + codeBuilt.getEscritoireColor() + " != " + projected.getEscritoireColor();
		}
		if (codeBuilt.getTemperature() != projected.getTemperature()) {
			return "temperature " + codeBuilt.getTemperature() + " != " + projected.getTemperature();
		}
		if (codeBuilt.getHumidity() != projected.getHumidity()) {
			return "humidity " + codeBuilt.getHumidity() + " != " + projected.getHumidity();
		}
		if (codeBuilt.isDominant() != projected.isDominant()) {
			return "dominant " + codeBuilt.isDominant() + " != " + projected.isDominant();
		}
		if (codeBuilt.hasGlint() != projected.hasGlint()) {
			return "glint " + codeBuilt.hasGlint() + " != " + projected.hasGlint();
		}
		if (codeBuilt.isSecret() != projected.isSecret()) {
			return "secret " + codeBuilt.isSecret() + " != " + projected.isSecret();
		}
		if (!codeBuilt.getAuthority().equals(projected.getAuthority())) {
			return "authority " + codeBuilt.getAuthority() + " != " + projected.getAuthority();
		}
		if (!codeBuilt.getSpeciesName().equals(projected.getSpeciesName())) {
			return "species " + codeBuilt.getSpeciesName() + " != " + projected.getSpeciesName();
		}
		if (!codeBuilt.getGenus().equals(projected.getGenus())) {
			return "genus " + codeBuilt.getGenus().name() + " != " + projected.getGenus().name();
		}
		// Complexity is NOT compared projected-vs-code-built like the fields above; this is a weaker invariant guard,
		// called out explicitly so a future reader does not mistake its strength for the surrounding equivalence
		// assertions. Two facts force this:
		//   (a) Forestry bee "complexity" is not an authored/stored property - every built-in authors 0, and
		//       Species#getComplexity() lazily *derives* a research value by walking the mutation tree
		//       (GeneticsUtil#getResearchComplexity) on first read.
		//   (b) That derivation reads the *live* MutationManager, which indexes species by object identity
		//       (IdentityHashMap). The freshly-projected BeeSpecies here was never inserted into that index, so
		//       projected.getComplexity() would derive a constant (1, no known ancestors) while
		//       codeBuilt.getComplexity() derives the real mutation-chain depth - an identity-based divergence that
		//       has nothing to do with whether projection is faithful, and cannot be reconciled at this layer.
		// The stored raw value (0) also can't be read back honestly: getComplexity() self-derives precisely when the
		// stored field is 0, and no raw accessor exists (adding one would be a production change, out of scope for a
		// test fix). So the achievable, honest check is: the generated definition faithfully captured the builder's
		// authored complexity (0 for all built-ins). DefinitionBeeSpeciesBuilder#getComplexity() returns exactly this
		// def.complexity() into the Species constructor, so a non-zero here would mean the datagen provider fabricated
		// a complexity the builder never authored.
		if (def.complexity() != 0) {
			return "complexity invariant: generated definition should preserve the builder's authored complexity 0, but got " + def.complexity();
		}

		String productsMismatch = compareProducts("products", codeBuilt.getProducts(), projected.getProducts());
		if (productsMismatch != null) {
			return productsMismatch;
		}
		String specialtiesMismatch = compareProducts("specialties", codeBuilt.getSpecialties(), projected.getSpecialties());
		if (specialtiesMismatch != null) {
			return specialtiesMismatch;
		}

		String jubilanceMismatch = compareJubilance(codeBuilt, projected, def, helper);
		if (jubilanceMismatch != null) {
			return jubilanceMismatch;
		}

		String genomeMismatch = compareGenomes(codeBuilt.getDefaultGenome(), projected.getDefaultGenome());
		if (genomeMismatch != null) {
			return "default genome: " + genomeMismatch;
		}

		return null;
	}

	@Nullable
	private static String compareProducts(String label, List<IProduct> a, List<IProduct> b) {
		if (a.size() != b.size()) {
			return label + " size " + a.size() + " != " + b.size();
		}
		for (int i = 0; i < a.size(); i++) {
			String mismatch = compareProduct(a.get(i), b.get(i));
			if (mismatch != null) {
				return label + "[" + i + "] " + mismatch;
			}
		}
		return null;
	}

	/**
	 * Compares products structurally via the {@link IProduct} interface surface (item/count/components/chance from a
	 * static snapshot), not {@link Object#equals}: the code-built side may hold a non-{@code Product} {@code IProduct}
	 * (the secret Patriotic bee's {@code FireworkProduct}), which {@code BeeSpeciesProvider} captures as a plain
	 * {@code Product} snapshot - a different class, but an equivalent static product.
	 */
	@Nullable
	private static String compareProduct(IProduct a, IProduct b) {
		if (a.item() != b.item()) {
			return "item " + a.item() + " != " + b.item();
		}
		if (Math.abs(a.chance() - b.chance()) > 1.0e-6f) {
			return "chance " + a.chance() + " != " + b.chance();
		}
		ItemStack stackA = a.createStack();
		ItemStack stackB = b.createStack();
		if (stackA.getCount() != stackB.getCount()) {
			return "count " + stackA.getCount() + " != " + stackB.getCount();
		}
		DataComponentPatch patchA = stackA.getComponentsPatch();
		DataComponentPatch patchB = stackB.getComponentsPatch();
		if (!patchA.equals(patchB)) {
			return "components " + patchA + " != " + patchB;
		}
		return null;
	}

	/**
	 * Proves the jubilance id was resolved back correctly by exercising {@link IBeeSpecies#isJubilant} under a
	 * housing whose climate deliberately does NOT match the species' preferred climate. Under a mismatched climate,
	 * {@code DefaultBeeJubilance} returns {@code false} while {@code HermitBeeJubilance} (used only by the Monastic
	 * line) is climate-independent and returns {@code true} in the mob-free "empty" test template - so a resolution
	 * bug (wrong jubilance id) flips the boolean and is caught, in either direction.
	 */
	@Nullable
	private static String compareJubilance(IBeeSpecies codeBuilt, BeeSpecies projected, BeeSpeciesDefinition def, GameTestHelper helper) {
		TemperatureType mismatchedTemperature = def.temperature() == TemperatureType.HELLISH ? TemperatureType.ICY : TemperatureType.HELLISH;
		HumidityType mismatchedHumidity = def.humidity() == HumidityType.ARID ? HumidityType.DAMP : HumidityType.ARID;
		TestBeeHousing housing = new TestBeeHousing(helper, mismatchedTemperature, mismatchedHumidity);

		IGenome codeGenome = codeBuilt.getDefaultGenome();
		IGenome projectedGenome = projected.getDefaultGenome();

		boolean codeJubilant = codeBuilt.isJubilant(codeGenome, housing);
		boolean projectedJubilant = projected.isJubilant(projectedGenome, housing);
		if (codeJubilant != projectedJubilant) {
			return "jubilance behavior under mismatched climate: code-built=" + codeJubilant + " projected=" + projectedJubilant + " (jubilance id " + def.jubilance() + ")";
		}
		return null;
	}

	@Nullable
	private static String compareGenomes(IGenome a, IGenome b) {
		String canonicalA = canonicalGenome(a);
		String canonicalB = canonicalGenome(b);
		if (!canonicalA.equals(canonicalB)) {
			return "\n  code-built: " + canonicalA.replace("\n", "\n              ")
				+ "\n  projected:  " + canonicalB.replace("\n", "\n              ");
		}
		return null;
	}

	/**
	 * Canonical, sorted, per-chromosome rendering of a genome's active/inactive alleles - order-independent so
	 * chromosome iteration order never matters. Mirrors {@code GenomeBaselineTest}'s canonicalization.
	 */
	private static String canonicalGenome(IGenome genome) {
		List<String> lines = new ArrayList<>();
		for (Map.Entry<IChromosome<?>, AllelePair<?>> entry : genome.getChromosomes().entrySet()) {
			lines.add(entry.getKey().id() + " = " + canonicalAllele(entry.getValue().active()) + " | " + canonicalAllele(entry.getValue().inactive()));
		}
		lines.sort(String::compareTo);
		return String.join("\n", lines);
	}

	private static String canonicalAllele(Allele<?> allele) {
		return String.valueOf(allele.value()) + ':' + allele.dominant();
	}

	@Nullable
	private static JsonElement readGeneratedJson(ResourceLocation id) {
		String resourcePath = "/data/" + id.getNamespace() + "/bee_species/" + id.getPath() + ".json";
		try (InputStream in = BeeSpeciesEquivalenceTest.class.getResourceAsStream(resourcePath)) {
			if (in == null) {
				return null;
			}
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/**
	 * Minimal {@link IBeeHousing} test double: supports {@code temperature()}/{@code humidity()} (for
	 * {@code DefaultBeeJubilance}) and {@code getWorldObj()}/{@code getCoordinates()}/{@code getBeeModifiers()} (for
	 * {@code HermitBeeJubilance}'s entity-range check via {@code ThrottledBeeEffect}); every other member throws,
	 * since neither jubilance implementation touches it.
	 */
	private static final class TestBeeHousing implements IBeeHousing {
		private final GameTestHelper helper;
		private final TemperatureType temperature;
		private final HumidityType humidity;

		TestBeeHousing(GameTestHelper helper, TemperatureType temperature, HumidityType humidity) {
			this.helper = helper;
			this.temperature = temperature;
			this.humidity = humidity;
		}

		@Override
		public TemperatureType temperature() {
			return this.temperature;
		}

		@Override
		public HumidityType humidity() {
			return this.humidity;
		}

		@Override
		public Iterable<IBeeModifier> getBeeModifiers() {
			return List.of();
		}

		@Override
		public Iterable<IBeeListener> getBeeListeners() {
			throw new UnsupportedOperationException();
		}

		@Override
		public IBeeHousingInventory getBeeInventory() {
			throw new UnsupportedOperationException();
		}

		@Override
		public IBeekeepingLogic getBeekeepingLogic() {
			throw new UnsupportedOperationException();
		}

		@Override
		public int getBlockLightValue() {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean canBlockSeeTheSky() {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean isRaining() {
			throw new UnsupportedOperationException();
		}

		@Override
		public com.mojang.authlib.GameProfile getOwner() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Vec3 getBeeFXCoordinates() {
			throw new UnsupportedOperationException();
		}

		@Override
		public Holder<Biome> getBiome() {
			throw new UnsupportedOperationException();
		}

		@Override
		public BlockPos getCoordinates() {
			return this.helper.absolutePos(BlockPos.ZERO);
		}

		@Override
		public Level getWorldObj() {
			return this.helper.getLevel();
		}

		@Override
		public IErrorLogic getErrorLogic() {
			throw new UnsupportedOperationException();
		}
	}
}
