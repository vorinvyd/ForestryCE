package forestry.arboriculture.models;

import com.google.common.base.Preconditions;
import forestry.api.arboriculture.ITreeSpecies;
import forestry.api.arboriculture.genetics.IFruit;
import forestry.api.client.IForestryClientApi;
import forestry.api.client.arboriculture.ILeafSprite;
import forestry.api.genetics.alleles.TreeChromosomes;
import forestry.arboriculture.blocks.BlockAbstractLeaves;
import forestry.arboriculture.blocks.BlockDecorativeLeaves;
import forestry.core.models.ModelBlockCached;
import forestry.core.models.baker.ModelBaker;
import forestry.core.utils.ResourceUtil;
import forestry.core.utils.SpeciesUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

public class ModelDecorativeLeaves<B extends Block> extends ModelBlockCached<B, ModelDefaultLeaves.Key> {
	public ModelDecorativeLeaves(Class<B> blockClass) {
		super(blockClass);
	}

	@Override
	protected ModelDefaultLeaves.Key getInventoryKey(ItemStack stack) {
		Block block = Block.byItem(stack.getItem());
		Preconditions.checkArgument(block instanceof BlockDecorativeLeaves, "ItemStack must be for decorative leaves.");
		BlockDecorativeLeaves bBlock = (BlockDecorativeLeaves) block;
		return new ModelDefaultLeaves.Key(bBlock.getSpeciesId(), Minecraft.useFancyGraphics());
	}

	@Override
	protected ModelDefaultLeaves.Key getWorldKey(BlockState state, ModelData extraData) {
		Block block = state.getBlock();
		Preconditions.checkArgument(block instanceof BlockDecorativeLeaves, "state must be for decorative leaves.");
		BlockDecorativeLeaves bBlock = (BlockDecorativeLeaves) block;
		return new ModelDefaultLeaves.Key(bBlock.getSpeciesId(), Minecraft.useFancyGraphics());
	}

	@Override
	protected void bakeBlock(B block, ModelData extraData, ModelDefaultLeaves.Key key, ModelBaker baker, boolean inventory) {
		ResourceLocation speciesId = key.speciesId;

		// Resolve fail-soft: a datapack may have removed this species while its leaf block is still placed, so use
		// getSpeciesSafe (not the throwing getTreeSpecies) and fall the species itself back to default - it's reused
		// for the fruit-overlay lookup below, so it must be non-null.
		ITreeSpecies species = SpeciesUtil.TREE_TYPE.get().getSpeciesSafe(speciesId);
		if (species == null) {
			species = SpeciesUtil.TREE_TYPE.get().getDefaultSpecies();
		}
		ILeafSprite sprite = IForestryClientApi.INSTANCE.getTreeManager().getLeafSprite(species);
		if (sprite == null) {
			// species exists but registered no client sprite: keep it as-is (used below for the fruit overlay
			// lookup); only the sprite falls back, so an unregistered-client species doesn't NPE the leaf render.
			sprite = IForestryClientApi.INSTANCE.getTreeManager().getLeafSprite(SpeciesUtil.TREE_TYPE.get().getDefaultSpecies());
		}

		ResourceLocation textureLocation = sprite.get(false, key.fancy);
		TextureAtlasSprite textureSprite = ResourceUtil.getBlockSprite(textureLocation);
		// Render the plain leaf block.
		baker.addBlockModel(textureSprite, BlockAbstractLeaves.FOLIAGE_COLOR_INDEX);

		// Render overlay for fruit leaves.
		IFruit fruit = species.getDefaultGenome().resolveActive(TreeChromosomes.FRUIT);
		ResourceLocation fruitSpriteLocation = fruit.getDecorativeSprite();
		if (fruitSpriteLocation != null) {
			TextureAtlasSprite fruitSprite = ResourceUtil.getBlockSprite(fruitSpriteLocation);
			baker.addBlockModel(fruitSprite, BlockAbstractLeaves.FRUIT_COLOR_INDEX);
		}

		// Set the particle sprite
		ResourceLocation particleLocation = sprite.getParticle();
		TextureAtlasSprite particleSprite = ResourceUtil.getBlockSprite(particleLocation);
		baker.setParticleSprite(particleSprite);
	}

	@Override
	protected BakedModel bakeModel(BlockState state, ModelDefaultLeaves.Key key, B block, ModelData extraData) {
		ModelBaker baker = new ModelBaker();

		bakeBlock(block, extraData, key, baker, false);

        this.blockModel = baker.bake(false);
		onCreateModel(this.blockModel);
		return this.blockModel;
	}

	@Override
	public ItemTransforms getTransforms() {
		return ModelLeaves.TRANSFORMS;
	}
}
