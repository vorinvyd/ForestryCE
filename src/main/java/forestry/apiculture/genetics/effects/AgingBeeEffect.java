package forestry.apiculture.genetics.effects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import forestry.api.apiculture.IBeeHousing;
import forestry.api.apiculture.genetics.BeeLifeStage;
import forestry.api.apiculture.genetics.IBeeEffect;
import forestry.api.genetics.IIndividual;
import forestry.api.genetics.IIndividualLiving;
import forestry.api.genetics.alleles.ForestryAlleles;
import forestry.api.genetics.capability.IIndividualHandlerItem;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class AgingBeeEffect extends NonStackingBeeEffect {
	/**
	 * The {@code forestry:aging} primitive: adjusts the housed queen's remaining life each cycle. The {@code aging}
	 * flag is the direction - {@code true} ages the queen toward death (CHRONOPHAGE), {@code false} rejuvenates her
	 * (REJUVENATION) - and the optional {@code strength} multiplier scales how much life is moved per cycle (the base
	 * amount is {@code maxHealth / normal-lifespan}). Both built-ins use {@code strength = 1}, so they differ only by
	 * the flag; a pack can tune the multiplier for a faster/slower aging or rejuvenating bee.
	 */
	public static final MapCodec<AgingBeeEffect> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		Codec.BOOL.optionalFieldOf("dominant", true).forGetter(IBeeEffect::isDominant),
		Codec.BOOL.fieldOf("aging").forGetter(effect -> effect.aging),
		Codec.floatRange(0f, Float.MAX_VALUE).optionalFieldOf("strength", 1.0f).forGetter(effect -> effect.strength)
	).apply(instance, AgingBeeEffect::new));

	protected final boolean aging;
	private final float strength;

	public AgingBeeEffect(boolean dominant, boolean aging) {
		this(dominant, aging, 1.0f);
	}

	public AgingBeeEffect(boolean dominant, boolean aging, float strength) {
		super(dominant);
		this.aging = aging;
		this.strength = strength;
	}

	@Override
	public MapCodec<? extends IBeeEffect> codec() {
		return MAP_CODEC;
	}

	@Override
	protected void doEffectForHive(Level level, IBeeHousing housing) {
		if (!housing.getErrorLogic().hasErrors()) {
			ItemStack queenStack = housing.getBeeInventory().getQueen();
			if (IIndividualHandlerItem.getLifeStage(queenStack) == BeeLifeStage.QUEEN) {
				IIndividual individual = IIndividualHandlerItem.getIndividual(queenStack);

				if (individual instanceof IIndividualLiving queen) {
					RandomSource rand = level.getRandom();
					int life = queen.getMaxHealth() / ForestryAlleles.LIFESPAN_NORMAL.value();

					if (rand.nextInt(ForestryAlleles.LIFESPAN_NORMAL.value()) < queen.getMaxHealth() % ForestryAlleles.LIFESPAN_NORMAL.value()) {
						// Ensure below normal lifespans are still affected, though to a lesser degree
						life++;
					}
					// strength scales the per-cycle amount; == 1 reproduces the historical base rate exactly.
					int amount = Math.round(life * this.strength);
					if (this.aging) {
						queen.setHealth(Math.max(1, queen.getHealth() - amount));
					} else {
						queen.setHealth((int) Math.min(queen.getMaxHealth(), Math.min(Integer.MAX_VALUE, queen.getHealth() + (long) amount)));
					}
					queen.saveToStack(queenStack);
				}
			}
		}
	}
}
