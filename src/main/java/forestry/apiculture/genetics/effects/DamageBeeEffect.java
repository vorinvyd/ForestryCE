package forestry.apiculture.genetics.effects;

import java.util.List;
import java.util.Locale;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import forestry.api.apiculture.BeeManager;
import forestry.api.apiculture.IBeeHousing;
import forestry.api.genetics.IEffectData;
import forestry.api.genetics.IGenome;
import forestry.core.damage.CoreDamageTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The {@code forestry:damage_entities} primitive: hurts living entities in range for a fixed amount, with
 * optional apiarist-armor scaling (each worn piece reduces the damage). Also expresses the built-in AGGRESSIVE
 * (all entities), MISANTHROPE (players only) and HEROIC (monsters only) effects, which differ only by damage,
 * armor scaling, damage type and target filter. Covers the RADIOACTIVE effect's entity-harm
 * half from JSON (the base {@code radioactive} effect hardcodes damage and also destroys blocks, so this
 * primitive gives datapacks a configurable, block-safe alternative).
 */
public class DamageBeeEffect extends ThrottledBeeEffect {
	/** Which entities in the territory the damage applies to. */
	public sealed interface Target {
		Codec<Target> CODEC = Codec.either(Builtin.CODEC, TagTarget.CODEC)
			.xmap(either -> either.map(builtin -> (Target) builtin, tag -> (Target) tag),
				target -> target instanceof Builtin builtin ? Either.left(builtin) : Either.right((TagTarget) target));

		/** The narrowest class this target can match; the territory scan is pre-filtered on it. */
		Class<? extends LivingEntity> entityClass();

		/** Further filtering within {@link #entityClass()}. */
		default boolean matches(LivingEntity entity) {
			return true;
		}

		/**
		 * The class-based targets. These exist alongside the tag branch because {@link Monster} is a class rather than
		 * a tag: it catches modded monsters automatically, and no vanilla entity-type tag is equivalent, so a tag-only
		 * filter would silently narrow HEROIC.
		 */
		enum Builtin implements Target, StringRepresentable {
			ALL(LivingEntity.class),
			PLAYERS(Player.class),
			MONSTERS(Monster.class);

			public static final Codec<Builtin> CODEC = StringRepresentable.fromEnum(Builtin::values);

			private final Class<? extends LivingEntity> entityClass;

			Builtin(Class<? extends LivingEntity> entityClass) {
				this.entityClass = entityClass;
			}

			@Override
			public Class<? extends LivingEntity> entityClass() {
				return this.entityClass;
			}

			@Override
			public String getSerializedName() {
				return name().toLowerCase(Locale.ROOT);
			}
		}

		/** An entity-type tag: {@code "target": {"tag": "#c:bosses"}}. */
		record TagTarget(TagKey<EntityType<?>> tag) implements Target {
			public static final Codec<TagTarget> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				TagKey.hashedCodec(Registries.ENTITY_TYPE).fieldOf("tag").forGetter(TagTarget::tag)
			).apply(instance, TagTarget::new));

			@Override
			public Class<? extends LivingEntity> entityClass() {
				return LivingEntity.class;
			}

			@Override
			public boolean matches(LivingEntity entity) {
				return entity.getType().is(this.tag);
			}
		}
	}

	public static final MapCodec<DamageBeeEffect> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
		ThrottleSettings.codec(40, false, true).forGetter(ThrottledBeeEffect::settings),
		Codec.floatRange(0f, Float.MAX_VALUE).fieldOf("damage").forGetter(effect -> effect.damage),
		Codec.BOOL.optionalFieldOf("armor_scaling", true).forGetter(effect -> effect.armorScaling),
		Codec.floatRange(0f, 1f).optionalFieldOf("chance", 1.0f).forGetter(effect -> effect.chance),
		ResourceKey.codec(Registries.DAMAGE_TYPE).optionalFieldOf("damage_type", DamageTypes.GENERIC).forGetter(effect -> effect.damageType),
		Target.CODEC.optionalFieldOf("target", Target.Builtin.ALL).forGetter(effect -> effect.target)
	).apply(instance, DamageBeeEffect::new));

	private final float damage;
	private final boolean armorScaling;
	private final float chance;
	private final ResourceKey<DamageType> damageType;
	private final Target target;

	public DamageBeeEffect(ThrottleSettings settings, float damage, boolean armorScaling, float chance, ResourceKey<DamageType> damageType, Target target) {
		super(settings);
		this.damage = damage;
		this.armorScaling = armorScaling;
		this.chance = chance;
		this.damageType = damageType;
		this.target = target;
	}

	@Override
	public MapCodec<DamageBeeEffect> codec() {
		return MAP_CODEC;
	}

	public Target target() {
		return this.target;
	}

	@Override
	public IEffectData doEffectThrottled(IGenome genome, IEffectData storedData, IBeeHousing housing) {
		Level level = housing.getWorldObj();
		RandomSource rand = level.random;
		List<? extends LivingEntity> entities = ThrottledBeeEffect.getEntitiesInRange(genome, housing, this.target.entityClass());
		DamageSource source = CoreDamageTypes.source(level, this.damageType);

		for (LivingEntity entity : entities) {
			if (!this.target.matches(entity)) {
				continue;
			}

			// Skip the RNG draw entirely when chance is 1 so a guaranteed effect (e.g. AGGRESSIVE/MISANTHROPE) does
			// not perturb the shared world RNG state.
			if (this.chance < 1.0f && rand.nextFloat() >= this.chance) {
				continue;
			}

			float damage = this.damage;
			if (this.armorScaling) {
				// Entities wearing apiarist's armor take reduced (or no) damage.
				int count = BeeManager.armorApiaristHelper.wearsItems(entity, this, true);
				damage -= count * (this.damage / 4f);
			}
			if (damage <= 0f) {
				continue;
			}

			entity.hurt(source, damage);
		}

		return storedData;
	}
}
