package forestry.core.data.models;

import forestry.api.ForestryConstants;
import forestry.api.client.IForestryClientApi;
import forestry.apiculture.blocks.BlockAlveary;
import forestry.apiculture.blocks.BlockBeeHive;
import forestry.apiculture.blocks.BlockHiveType;
import forestry.apiculture.blocks.BlockTypeApiculture;
import forestry.apiculture.features.ApicultureBlocks;
import forestry.arboriculture.features.ArboricultureBlocks;
import forestry.arboriculture.blocks.ForestryLeafType;
import forestry.arboriculture.features.CharcoalBlocks;
import forestry.core.blocks.EnumResourceType;
import forestry.core.features.CoreBlocks;
import forestry.core.features.CoreItems;
import forestry.core.fluids.ForestryFluids;
import forestry.energy.features.EnergyBlocks;
import forestry.factory.blocks.BlockTypeFactoryPlain;
import forestry.factory.features.FactoryBlocks;
import forestry.core.utils.ModUtil;
import forestry.cultivation.blocks.BlockTypePlanter;
import forestry.cultivation.features.CultivationBlocks;
import forestry.farming.blocks.EnumFarmBlockType;
import forestry.farming.blocks.EnumFarmMaterial;
import forestry.farming.blocks.FarmBlock;
import forestry.farming.features.FarmingBlocks;
import forestry.mail.blocks.BlockTypeMail;
import forestry.mail.features.MailBlocks;
import forestry.worktable.features.WorktableBlocks;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import forestry.core.blocks.BlockBase;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.model.generators.BlockModelProvider;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.client.model.generators.loaders.CompositeModelBuilder;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class ForestryBlockStateProvider extends BlockStateProvider {
	public ForestryBlockStateProvider(PackOutput output, ExistingFileHelper exFileHelper) {
		super(output, ForestryConstants.MOD_ID, exFileHelper);
	}

	@Override
	protected void registerStatesAndModels() {
		// Farm blocks
		for (FarmBlock block : FarmingBlocks.FARM.getBlocks()) {
			if (block.getType() == EnumFarmBlockType.PLAIN) {
				plainFarm(block);
			} else {
				singleFarm(block);
			}

			generic3d(block);
		}

		for (BlockTypePlanter farmType : BlockTypePlanter.values()) {
			ModelFile file = models().getExistingFile(modBlock(farmType.getSerializedName()));
			horizontalForestryBlock(CultivationBlocks.MANAGED_PLANTER.get(farmType).block(), file);
			horizontalForestryBlock(CultivationBlocks.MANUAL_PLANTER.get(farmType).block(), file);
		}

		// Resources
		simpleBlock(CoreBlocks.BOG_EARTH.block());
		simpleBlock(CoreBlocks.HUMUS.block());

		simpleBlock(CoreBlocks.APATITE_ORE.block());
		simpleBlock(CoreBlocks.DEEPSLATE_APATITE_ORE.block());
		simpleBlock(CoreBlocks.TIN_ORE.block());
		simpleBlock(CoreBlocks.DEEPSLATE_TIN_ORE.block());
		simpleBlock(CoreBlocks.RAW_TIN_BLOCK.block());
		generic3d(CoreBlocks.APATITE_ORE.block());
		generic3d(CoreBlocks.DEEPSLATE_APATITE_ORE.block());
		generic3d(CoreBlocks.TIN_ORE.block());
		generic3d(CoreBlocks.DEEPSLATE_TIN_ORE.block());
		generic3d(CoreBlocks.RAW_TIN_BLOCK.block());

		generic2d(CoreItems.RAW_TIN);
		generic2d(CoreItems.INGOT_TIN);
		generic2d(CoreItems.GEAR_TIN);
		generic2d(CoreItems.INGOT_BRONZE);
		generic2d(CoreItems.GEAR_BRONZE);
		generic2d(CoreItems.GEAR_COPPER);

		// Fluids (doesn't actually show in game, but silences the warning spam from Minecraft)
		for (ForestryFluids fluid : ForestryFluids.values()) {
			Block block = fluid.getFeature().fluidBlock().block();
			ModelFile blockModel = particleOnly(models(), path(block), fluid.getFeature().properties().resources[0]);
			singleModelBlock(this, block, blockModel);
		}

		// Leaves (same as with fluids)
		for (ForestryLeafType treeType : ForestryLeafType.values()) {
			Block defaultBlock = ArboricultureBlocks.LEAVES_DEFAULT.get(treeType).block();
			Block defaultFruitBlock = ArboricultureBlocks.LEAVES_DEFAULT_FRUIT.get(treeType).block();
			Block decorativeBlock = ArboricultureBlocks.LEAVES_DECORATIVE.get(treeType).block();
			ResourceLocation particle = IForestryClientApi.INSTANCE.getTreeManager().getLeafSprite(treeType.getIndividual().getSpecies()).get(false, true);
			ModelFile file = models().getBuilder(path(defaultBlock)).texture("particle", particle);

			singleModelBlock(this, defaultBlock, file);
			singleModelBlock(this, defaultFruitBlock, file);
			singleModelBlock(this, decorativeBlock, file);

			generic3d(defaultBlock);
			generic3d(defaultFruitBlock, defaultBlock);
			generic3d(decorativeBlock, defaultBlock);
		}
		singleModelBlock(this, ArboricultureBlocks.LEAVES.block(), particleOnly(models(), ArboricultureBlocks.LEAVES.getName(), blockTexture(Blocks.OAK_LEAVES)));

		for (BlockHiveType type : BlockHiveType.values()) {
			BlockBeeHive feature = ApicultureBlocks.BEEHIVE.get(type).block();
			String path = path(feature);

			ResourceLocation side = modBlock("beehives/" + type.getSerializedName() + ".side");
			ResourceLocation top = modBlock("beehives/" + type.getSerializedName() + ".top");
			ResourceLocation bottom = modBlock("beehives/" + type.getSerializedName() + ".bottom");

			singleModelBlock(this, feature, models().cubeBottomTop(path, side, bottom, top));
			generic3d(feature);
		}

		// Single-variant blocks migrated from hand-authored blockstates. Each renders one existing
		// (hand-authored) model for all states, mirroring the old {"variants":{"":{"model":...}}}.
		// Item models for these blocks stay hand-authored (custom display transforms), so no generic3d here.
		for (Block block : CoreBlocks.BASE.blockArray()) existingModelBlock(block);            // analyzer, escritoire
		for (Block block : FactoryBlocks.TESR.blockArray()) existingModelBlock(block);          // bottler, carpenter, centrifuge, ...
		for (Block block : EnergyBlocks.ENGINES.blockArray()) existingModelBlock(block);        // biogas/clockwork/peat engine
		for (Block block : CoreBlocks.NATURALIST_CHEST.blockArray()) existingModelBlock(block); // apiarist/arborist/lepidopterist chest
		existingModelBlock(CharcoalBlocks.ASH.block());
		existingModelBlock(CharcoalBlocks.CHARCOAL.block());
		existingModelBlock(CharcoalBlocks.LOG_PILE.block());
		existingModelBlock(ArboricultureBlocks.SAPLING_GE.block());
		existingModelBlock(CoreBlocks.PEAT.block());

		// Comb blocks all share the block_bee_combs model.
		ModelFile combModel = models().getExistingFile(modBlock("block_bee_combs"));
		for (Block block : ApicultureBlocks.BEE_COMB.blockArray()) singleModelBlock(this, block, combModel);

		// Resource storage blocks use block/storage/<type>.
		for (EnumResourceType type : EnumResourceType.values()) {
			existingModelBlock(CoreBlocks.RESOURCE_STORAGE.get(type).block(), "storage/" + type.getSerializedName());
		}

		// Alveary components (the single-variant subset of the alveary block group).
		existingModelBlock(ApicultureBlocks.ALVEARY.get(BlockAlveary.Type.HYGRO).block(), "apiculture/alveary_hygroregulator");
		existingModelBlock(ApicultureBlocks.ALVEARY.get(BlockAlveary.Type.STABILISER).block(), "apiculture/alveary_stabilizer");
		existingModelBlock(ApicultureBlocks.ALVEARY.get(BlockAlveary.Type.SIEVE).block(), "apiculture/alveary_sieve");

		// Horizontal-facing machines migrated from hand-authored blockstates + models. Each is a
		// block/cube with per-face textures <prefix>.<n>, rotated by BlockBase.FACING. Item models
		// stay hand-authored (custom display transforms), so no generic3d here.
		horizontalMachine(ApicultureBlocks.BASE.get(BlockTypeApiculture.APIARY).block(), "apiary", 0, 1, 2, 4, 4, 4, 4);
		horizontalMachine(ApicultureBlocks.BASE.get(BlockTypeApiculture.BEE_HOUSE).block(), "beehouse", 0, 1, 2, 4, 4, 4, 4);
		horizontalMachine(MailBlocks.BASE.get(BlockTypeMail.MAILBOX).block(), "mailbox", 0, 1, 2, 2, 2, 2, 2);
		horizontalMachine(MailBlocks.BASE.get(BlockTypeMail.STAMP_COLLETOR).block(), "philatelist", 0, 1, 3, 2, 2, 2, 2);
		horizontalMachine(MailBlocks.BASE.get(BlockTypeMail.TRADE_STATION).block(), "tradestation", 0, 1, 3, 2, 4, 4, 4);
		horizontalMachine(FactoryBlocks.PLAIN.get(BlockTypeFactoryPlain.FABRICATOR).block(), "thermionic_fabricator", 0, 1, 3, 2, 4, 4, 4);
		horizontalMachine(WorktableBlocks.WORKTABLE.block(), "worktable", 0, 1, 3, 2, 4, 4, 4);
	}

	// Builds a block/cube model whose faces map to textures block/<prefix>.<n>, then emits a
	// horizontal-facing blockstate for it. Face suffixes are given in JSON order:
	// down, up, north, south, east, west, particle.
	private void horizontalMachine(Block block, String prefix, int down, int up, int north, int south, int east, int west, int particle) {
		ModelFile model = models().withExistingParent(path(block), mcBlock("cube"))
			.texture("particle", modBlock(prefix + "." + particle))
			.texture("down", modBlock(prefix + "." + down))
			.texture("up", modBlock(prefix + "." + up))
			.texture("north", modBlock(prefix + "." + north))
			.texture("east", modBlock(prefix + "." + east))
			.texture("south", modBlock(prefix + "." + south))
			.texture("west", modBlock(prefix + "." + west));
		horizontalForestryBlock(block, model);
	}

	// Emits a single "" variant pointing at an existing hand-authored model named after the block.
	private void existingModelBlock(Block block) {
		existingModelBlock(block, path(block));
	}

	// Emits a single "" variant pointing at the existing hand-authored model at block/<modelPath>.
	private void existingModelBlock(Block block, String modelPath) {
		singleModelBlock(this, block, models().getExistingFile(modBlock(modelPath)));
	}

	public static void singleModelBlock(ForestryBlockStateProvider states, Block defaultBlock, ModelFile file) {
		states.getVariantBuilder(defaultBlock).partialState().modelForState().modelFile(file).addModel();
	}

	public static ModelFile particleOnly(BlockModelProvider models, String path, ResourceLocation particleTexture) {
		return models.getBuilder(path).texture("particle", particleTexture);
	}

	/**
	 * BlockStateProvider#horizontalBlock keys off vanilla's BlockStateProperties.HORIZONTAL_FACING,
	 * which Forestry's BlockBase doesn't carry — its FACING is a custom EnumProperty<Direction>
	 * (different identity, same name). Build the variant manually using BlockBase.FACING so the
	 * lookup succeeds.
	 */
	private void horizontalForestryBlock(Block block, ModelFile model) {
		getVariantBuilder(block).forAllStates(state -> {
			Direction facing = state.getValue(BlockBase.FACING);
			int yRot = ((int) facing.toYRot() + 180) % 360;
			return ConfiguredModel.builder()
				.modelFile(model)
				.rotationY(yRot)
				.build();
		});
	}

	private void singleFarm(FarmBlock block) {
		EnumFarmMaterial material = block.getFarmMaterial();
		Block base = material.getBase();
		ResourceLocation texture = modLoc("block/farm/" + block.getType().getSerializedName());

		singleModelBlock(this, block, farmPillar(path(block), base, texture, texture));
	}

	private void plainFarm(FarmBlock block) {
		EnumFarmMaterial material = block.getFarmMaterial();
		Block base = material.getBase();

		// todo need to use reverse texture
		getVariantBuilder(block)
			.partialState().with(FarmBlock.BAND, false)
			.modelForState().modelFile(farmPillar(path(block), base, modLoc("block/farm/top"), modLoc("block/farm/plain"))).addModel()
			.partialState().with(FarmBlock.BAND, true)
			.modelForState().modelFile(farmPillar(path(block) + "_band", base, modLoc("block/farm/top"), modLoc("block/farm/band"))).addModel();
	}

	private ModelFile farmPillar(String path, Block base, ResourceLocation top, ResourceLocation side) {
		ModelFile baseModel = file(blockTexture(base));

		return models().getBuilder(path).customLoader(CompositeModelBuilder::begin)
			.child("base", models().nested()
				.parent(baseModel)
				.renderType("solid"))
			.child("overlay", models().nested()
				.parent(mcFile("cube_column"))
				.texture("end", top)
				.texture("side", side)
				// should we use cutout_mipped?
				.renderType("cutout"))
			.itemRenderOrder("base", "overlay")
			.end()
			// reuse the particle
			.parent(baseModel);
	}

	protected static ResourceLocation withSuffix(ResourceLocation loc, String suffix) {
		return loc.withSuffix(suffix);
	}

	protected static ResourceLocation withPrefix(String prefix, ResourceLocation loc) {
		String oldPath = loc.getPath();
		int slash = oldPath.lastIndexOf('/') + 1;

		if (slash != 0) {
			return loc.withPath(oldPath.substring(0, slash) + prefix + oldPath.substring(slash));
		}
		return loc.withPrefix(prefix);
	}

	public void generic3d(Block block, Block otherParent) {
		itemModels().withExistingParent(path(block), modLoc("block/" + path(otherParent)));
	}

	public void generic3d(Block block, ResourceLocation otherParentId) {
		itemModels().withExistingParent(path(block), ResourceLocation.fromNamespaceAndPath(otherParentId.getNamespace(), "block/" + otherParentId.getPath()));
	}

	protected ModelFile existingMcBlock(String path) {
		return models().getExistingFile(mcBlock(path));
	}

	// Everything below this line is boilerplate code adapted from https://github.com/thedarkcolour/ModKit
	// Makes a 3d cube of a block for item model
	public void generic3d(Block block) {
		String path = path(block);
		itemModels().withExistingParent(path, modLoc("block/" + path));
	}

	public static String path(Block block) {
		return ModUtil.getRegistryName(block).getPath();
	}

	public static ModelFile.UncheckedModelFile file(ResourceLocation resourceLoc) {
		return new ModelFile.UncheckedModelFile(resourceLoc);
	}

	public ModelFile.UncheckedModelFile modFile(String path) {
		return file(this.modBlock(path));
	}

	public ModelFile.UncheckedModelFile mcFile(String path) {
		return file(this.mcBlock(path));
	}

	public ResourceLocation modBlock(String name) {
		return this.modLoc("block/" + name);
	}

	public ResourceLocation mcBlock(String name) {
		return this.mcLoc("block/" + name);
	}

	public void generic2d(ItemLike item) {
		generic2d(ModUtil.getRegistryName(item.asItem()));
	}

	/**
	 * Makes a 2d single layer item like hopper, gold ingot, or redstone dust item models
	 */
	public void generic2d(ResourceLocation itemId) {
		layer0(itemId, "item/generated");
	}

	public void layer0(ResourceLocation itemId, String parentName) {
		String path = itemId.getPath();

		itemModels().getBuilder(path)
			.parent(new ModelFile.UncheckedModelFile(parentName))
			.texture("layer0", ResourceLocation.fromNamespaceAndPath(itemId.getNamespace(), "item/" + path));
	}
}
