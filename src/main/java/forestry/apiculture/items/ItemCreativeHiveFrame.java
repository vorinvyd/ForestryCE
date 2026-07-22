package forestry.apiculture.items;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import forestry.api.apiculture.IBeeHousing;
import forestry.api.apiculture.IBeeModifier;
import forestry.api.apiculture.genetics.IBee;
import forestry.api.apiculture.genetics.IBeeSpecies;
import forestry.api.apiculture.hives.IHiveFrame;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.IMutation;
import forestry.core.items.ItemForestry;

import org.jetbrains.annotations.Nullable;

// 100% mutation chance. 100% production chance. 0% lifespan.
public class ItemCreativeHiveFrame extends ItemForestry implements IHiveFrame {
	public static final String NBT_FORCE_MUTATIONS = "force_mutations";
	/**
	 * When present, the id of the single mutation <em>result species</em> this frame forces. Unlike
	 * {@link #NBT_FORCE_MUTATIONS} (which forces a random one of the pair's mutations to 100%), this makes only the
	 * mutation producing that species eligible, so breeding a parent pair with several possible results is deterministic.
	 * The mutation's own conditions (temperature/biome/…) still apply.
	 */
	public static final String NBT_FORCED_MUTATION = "forced_mutation";

	public ItemCreativeHiveFrame() {
		super(new Item.Properties().rarity(Rarity.EPIC));
	}

	@Override
	public ItemStack frameUsed(IBeeHousing housing, ItemStack frame, IBee queen, int wear) {
		return frame;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag advanced) {
		super.appendHoverText(stack, context, tooltip, advanced);

		tooltip.add(Component.translatable("item.forestry.bee.modifier.production", Modifier.PRODUCTION));
		tooltip.add(Component.translatable("item.forestry.bee.modifier.genetic.decay", Modifier.GENETIC_DECAY));

		ResourceLocation forced = getForcedMutation(stack);
		if (forced != null) {
			tooltip.add(Component.literal("Forces mutation: " + forced).withStyle(ChatFormatting.LIGHT_PURPLE));
		} else if (hasForceMutations(stack)) {
			tooltip.add(Component.literal("Maximum mutation chances").withStyle(ChatFormatting.LIGHT_PURPLE));
		} else {
			tooltip.add(Component.literal("Base mutation chances").withStyle(ChatFormatting.GRAY));
		}
	}

	@Override
	public IBeeModifier getBeeModifier(ItemStack frame) {
		ResourceLocation forced = getForcedMutation(frame);
		if (forced != null) {
			return new Modifier(forced);
		}
		return hasForceMutations(frame) ? Modifier.FORCE_ANY : Modifier.BASE;
	}

	public static boolean hasForceMutations(ItemStack stack) {
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		return customData != null && customData.contains(NBT_FORCE_MUTATIONS);
	}

	/** @return the result-species id this frame forces (see {@link #NBT_FORCED_MUTATION}), or {@code null} if none. */
	@Nullable
	public static ResourceLocation getForcedMutation(ItemStack stack) {
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData == null || !customData.contains(NBT_FORCED_MUTATION)) {
			return null;
		}
		return ResourceLocation.tryParse(customData.copyTag().getString(NBT_FORCED_MUTATION));
	}

	/** Writes the forced result species onto the frame (see {@link #NBT_FORCED_MUTATION}). */
	public static void setForcedMutation(ItemStack stack, ResourceLocation result) {
		CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
		tag.putString(NBT_FORCED_MUTATION, result.toString());
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
	}

	/**
	 * The creative frame's bee modifier. Always applies the creative perks (max production, instant aging, sealed/always
	 * active, etc.); its mutation behaviour depends on {@link #forcedResult}:
	 * <ul>
	 *     <li>{@code null} + {@code !forceAny}: base mutation chance (a plain creative frame).</li>
	 *     <li>{@code null} + {@code forceAny}: forces a random one of the pair's mutations to 100%.</li>
	 *     <li>non-null: forces <em>only</em> the mutation producing that species (others get 0), so selection is
	 *     deterministic for a multi-result pair.</li>
	 * </ul>
	 */
	private record Modifier(boolean forceAny, @Nullable ResourceLocation forcedResult) implements IBeeModifier {
		static final float PRODUCTION = 10000f;
		static final float POLLINATION = 100f;
		static final float MUTATION = 100f;
		static final float GENETIC_DECAY = 0f;

		static final Modifier BASE = new Modifier(false, null);
		static final Modifier FORCE_ANY = new Modifier(true, null);

		Modifier(ResourceLocation forcedResult) {
			this(true, forcedResult);
		}

		@Override
		public float modifyMutationChance(IGenome genome, IGenome mate, IMutation<IBeeSpecies> mutation, float currentChance) {
			if (this.forcedResult != null) {
				return mutation.getResult().id().equals(this.forcedResult) ? MUTATION : 0f;
			}
			return this.forceAny ? MUTATION : currentChance;
		}

		@Override
		public float modifyAging(IGenome genome, @Nullable IGenome mate, float currentAging) {
			return -1f;
		}

		@Override
		public float modifyProductionSpeed(IGenome genome, float currentSpeed) {
			return PRODUCTION;
		}

		@Override
		public float modifyPollination(IGenome genome, float currentPollination) {
			return POLLINATION;
		}

		@Override
		public float modifyGeneticDecay(IGenome genome, float currentDecay) {
			return GENETIC_DECAY;
		}

		@Override
		public boolean isSealed() {
			return true;
		}

		@Override
		public boolean isAlwaysActive(IGenome genome) {
			return true;
		}

		@Override
		public boolean isSunlightSimulated() {
			return true;
		}

		@Override
		public boolean isHellish() {
			return true;
		}

		@Override
		public boolean providesFlowers() {
			return true;
		}

		@Override
		public boolean isClimateFullyTolerant() {
			return true;
		}
	}
}
