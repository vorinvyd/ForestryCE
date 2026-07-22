package forestry.core.genetics;

import com.mojang.datafixers.Products;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import forestry.api.genetics.IGenome;
import forestry.api.genetics.IIndividualLiving;
import forestry.api.genetics.ISpecies;
import forestry.api.genetics.ISpeciesType;
import forestry.api.genetics.alleles.IChromosome;
import forestry.core.features.CoreDataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.OverridingMethodsMustInvokeSuper;
import java.util.Optional;

public abstract class IndividualLiving<S extends ISpecies<I>, I extends IIndividualLiving, T extends ISpeciesType<S, I>> extends Individual<S, I, T> implements IIndividualLiving {
	protected int health;
	protected int maxHealth;

	protected IndividualLiving(IGenome genome) {
		super(genome);

		int health = genome.getActiveValue(getLifespanChromosome());
		this.health = health;
		this.maxHealth = health;
	}

	// For codec
	protected IndividualLiving(IGenome genome, Optional<IGenome> mate, boolean analyzed, int health, int maxHealth) {
		super(genome, mate, analyzed);

		this.health = health;
		this.maxHealth = maxHealth;
	}

	// For "inheritance" in codecs
	protected static <I extends IIndividualLiving> Products.P5<RecordCodecBuilder.Mu<I>, IGenome, Optional<IGenome>, Boolean, Integer, Integer> livingFields(RecordCodecBuilder.Instance<I> instance, Codec<IGenome> genomeCodec) {
		return Individual.fields(instance, genomeCodec).and(instance.group(
			Codec.INT.fieldOf("health").forGetter(I::getHealth),
			// todo rename to "max_health" in 1.21.1
			Codec.INT.fieldOf("max_heath").forGetter(I::getMaxHealth)
		));
	}

	protected abstract IChromosome<Integer> getLifespanChromosome();

	/* GENERATION */
	@Override
	public boolean isAlive() {
		return this.health > 0;
	}

	@Override
	public int getHealth() {
		return this.health;
	}

	@Override
	public final void setHealth(int health) {
		this.health = Mth.clamp(health, 0, getMaxHealth());
	}

	@Override
	public int getMaxHealth() {
		return this.maxHealth;
	}

	@Override
	public void age(Level level, float ageStep) {
		if (ageStep < 0f) {
			setHealth(0);
			return;
		}
		// don't age
		if (ageStep == 0f) {
			return;
		}

		// whole number of aging steps
		while (ageStep > 1.0f) {
			decreaseHealth();
			ageStep--;
		}
		// percentage chance to age again
		if (level.random.nextFloat() < ageStep) {
			decreaseHealth();
		}
	}

	@OverridingMethodsMustInvokeSuper
	@Override
	protected void copyPropertiesTo(I other) {
		super.copyPropertiesTo(other);

		IndividualLiving<?, ?, ?> living = (IndividualLiving<?, ?, ?>) other;
		living.health = this.health;
		living.maxHealth = this.maxHealth;
	}

	@Override
	protected void savePropertiesToStack(ItemStack stack) {
		super.savePropertiesToStack(stack);
		stack.set(CoreDataComponents.HEALTH, this.health);
		stack.set(CoreDataComponents.MAX_HEALTH, this.maxHealth);
	}

	@Override
	public void loadPropertiesFromStack(ItemStack stack) {
		super.loadPropertiesFromStack(stack);

		Integer maxHealth = stack.get(CoreDataComponents.MAX_HEALTH);
		if (maxHealth != null) {
			this.maxHealth = maxHealth;
		}

		Integer health = stack.get(CoreDataComponents.HEALTH);
		if (health != null) {
			setHealth(health);
		}
	}

	private void decreaseHealth() {
		if (this.health > 0) {
			setHealth(this.health - 1);
		}
	}
}
