package forestry.gametest;

import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import forestry.api.ForestryConstants;
import forestry.api.apiculture.IBeeHousing;
import forestry.api.apiculture.IBeeListener;
import forestry.api.apiculture.IBeeModifier;
import forestry.api.apiculture.IBeekeepingLogic;
import forestry.api.apiculture.IBeeHousingInventory;
import forestry.api.apiculture.genetics.IBeeSpeciesType;
import forestry.api.core.HumidityType;
import forestry.api.core.IErrorLogic;
import forestry.api.core.Product;
import forestry.api.core.TemperatureType;
import forestry.api.genetics.ForestryTaxa;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.alleles.BeeChromosomes;
import forestry.api.genetics.alleles.ForestryAlleles;
import forestry.apiculture.BeeSpecies;
import forestry.apiculture.genetics.BeeSpeciesDefinition;
import forestry.apiculture.genetics.BeeSpeciesProjector;
import forestry.core.utils.SpeciesUtil;

/**
 * Behavioral oracle for {@link BeeSpeciesProjector}: proves a hand-built {@link BeeSpeciesDefinition} (modeled on
 * the code-registered Forest bee) projects into a runtime {@link BeeSpecies} whose fields and default genome match
 * the definition exactly, including the sparse genome override dispatch (data chromosome, POLLINATION).
 */
@GameTestHolder(ForestryConstants.MOD_ID)
@PrefixGameTestTemplate(false)
public class BeeSpeciesProjectorTest {
	@GameTest(template = "empty")
	public static void projectsMatchingBeeSpecies(GameTestHelper helper) {
		BeeSpeciesDefinition def = TestSpeciesDefinitions.bee(ForestryTaxa.GENUS_HONEY, ForestryTaxa.SPECIES_FOREST)
			.dominant(true)
			.outline(0x19d0ec)
			.products(List.of(Product.of(Items.HONEY_BOTTLE, 1, 0.3f)))
			.genome(Map.of(BeeChromosomes.POLLINATION.id(), ForestryAlleles.POLLINATION_SLOWER))
			.build();

		IBeeSpeciesType type = SpeciesUtil.BEE_TYPE.get();
		BeeSpecies species = BeeSpeciesProjector.project(type, ForestryConstants.forestry("test_bee_species_projector"), def);

		if (species == null) {
			helper.fail("Projection returned null for a valid definition");
			return;
		}

		if (species.getBody() != def.body()) {
			helper.fail("Expected body " + def.body() + " but got " + species.getBody());
			return;
		}
		if (species.getStripes() != def.stripes()) {
			helper.fail("Expected stripes " + def.stripes() + " but got " + species.getStripes());
			return;
		}
		if (species.getOutline() != def.outline()) {
			helper.fail("Expected outline " + def.outline() + " but got " + species.getOutline());
			return;
		}
		if (species.getTemperature() != def.temperature()) {
			helper.fail("Expected temperature " + def.temperature() + " but got " + species.getTemperature());
			return;
		}
		if (species.getHumidity() != def.humidity()) {
			helper.fail("Expected humidity " + def.humidity() + " but got " + species.getHumidity());
			return;
		}
		if (species.getProducts().size() != 1 || species.getProducts().get(0).item() != Items.HONEY_BOTTLE) {
			helper.fail("Expected a single honey bottle product but got " + species.getProducts());
			return;
		}
		if (species.getSpecialties().size() != 0) {
			helper.fail("Expected no specialties but got " + species.getSpecialties());
			return;
		}

		IGenome genome = species.getDefaultGenome();
		int pollination = genome.getActiveValue(BeeChromosomes.POLLINATION);
		if (pollination != ForestryAlleles.POLLINATION_SLOWER.value()) {
			helper.fail("Expected default genome POLLINATION active value " + ForestryAlleles.POLLINATION_SLOWER.value() + " but got " + pollination);
			return;
		}

		// Proves the resolved IBeeJubilance was correctly wired through the builder, not just non-null: the
		// default jubilance is jubilant exactly when the housing's climate matches the species' preferred climate.
		if (!species.isJubilant(genome, new TestBeeHousing(def.temperature(), def.humidity()))) {
			helper.fail("Expected species to be jubilant in its preferred climate");
			return;
		}
		if (species.isJubilant(genome, new TestBeeHousing(TemperatureType.HELLISH, def.humidity()))) {
			helper.fail("Expected species to not be jubilant outside its preferred climate");
			return;
		}

		helper.succeed();
	}

	/**
	 * Minimal {@link IBeeHousing} test double: only {@link #temperature()}/{@link #humidity()} are exercised by
	 * {@link forestry.apiculture.genetics.DefaultBeeJubilance}, so every other member throws.
	 */
	private static final class TestBeeHousing implements IBeeHousing {
		private final TemperatureType temperature;
		private final HumidityType humidity;

		TestBeeHousing(TemperatureType temperature, HumidityType humidity) {
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
			throw new UnsupportedOperationException();
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
			throw new UnsupportedOperationException();
		}

		@Override
		public Level getWorldObj() {
			throw new UnsupportedOperationException();
		}

		@Override
		public IErrorLogic getErrorLogic() {
			throw new UnsupportedOperationException();
		}
	}
}
