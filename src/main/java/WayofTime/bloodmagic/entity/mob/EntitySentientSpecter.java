package WayofTime.bloodmagic.entity.mob;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.block.Block;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityOwnable;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAttackMelee;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWander;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityGhast;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityTippedArrow;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.management.PreYggdrasilConverter;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;
import WayofTime.bloodmagic.api.Constants;
import WayofTime.bloodmagic.api.soul.EnumDemonWillType;
import WayofTime.bloodmagic.demonAura.WorldDemonWillHandler;
import WayofTime.bloodmagic.entity.ai.EntityAIAttackRangedBow;
import WayofTime.bloodmagic.entity.ai.EntityAIFollowOwner;
import WayofTime.bloodmagic.entity.ai.EntityAIGrabEffectsFromOwner;
import WayofTime.bloodmagic.entity.ai.EntityAIHurtByTargetIgnoreTamed;
import WayofTime.bloodmagic.entity.ai.EntityAIOwnerHurtByTarget;
import WayofTime.bloodmagic.entity.ai.EntityAIOwnerHurtTarget;
import WayofTime.bloodmagic.entity.ai.EntityAIRetreatToHeal;
import WayofTime.bloodmagic.item.soul.ItemSentientBow;
import WayofTime.bloodmagic.registry.ModItems;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;

public class EntitySentientSpecter extends EntityCreature implements IEntityOwnable
{
    protected static final DataParameter<Byte> TAMED = EntityDataManager.<Byte>createKey(EntityTameable.class, DataSerializers.BYTE);
    protected static final DataParameter<Optional<UUID>> OWNER_UNIQUE_ID = EntityDataManager.<Optional<UUID>>createKey(EntityTameable.class, DataSerializers.OPTIONAL_UNIQUE_ID);

    @Getter
    @Setter
    protected EnumDemonWillType type = EnumDemonWillType.DESTRUCTIVE;

    @Getter
    @Setter
    protected boolean wasGivenSentientArmour = false;

    private final EntityAIAttackRangedBow aiArrowAttack = new EntityAIAttackRangedBow(this, 1.0D, 20, 15.0F);
    private final EntityAIAttackMelee aiAttackOnCollide = new EntityAIAttackMelee(this, 1.0D, false);

    private final int attackPriority = 3;

    public EntitySentientSpecter(World worldIn)
    {
        super(worldIn);
        this.setSize(0.6F, 1.95F);
//        ((PathNavigateGround) getNavigator()).setCanSwim(false);
        this.tasks.addTask(0, new EntityAISwimming(this));
        this.tasks.addTask(2, new EntityAIRetreatToHeal<EntityCreature>(this, EntityCreature.class, 6.0F, 1.0D, 1.2D));
        this.tasks.addTask(attackPriority, aiAttackOnCollide);
        this.tasks.addTask(4, new EntityAIGrabEffectsFromOwner(this, 2.0D, 1.0F));
        this.tasks.addTask(5, new EntityAIFollowOwner(this, 1.0D, 10.0F, 2.0F));
        this.tasks.addTask(6, new EntityAIWander(this, 1.0D));
        this.tasks.addTask(7, new EntityAIWatchClosest(this, EntityPlayer.class, 6.0F));
        this.tasks.addTask(8, new EntityAILookIdle(this));

        this.targetTasks.addTask(1, new EntityAIOwnerHurtByTarget(this));
        this.targetTasks.addTask(2, new EntityAIOwnerHurtTarget(this));
        this.targetTasks.addTask(3, new EntityAINearestAttackableTarget<EntityPlayer>(this, EntityPlayer.class, true));

        this.targetTasks.addTask(4, new EntityAIHurtByTargetIgnoreTamed(this, false, new Class[0]));

        this.setCombatTask();
//        this.targetTasks.addTask(8, new EntityAINearestAttackableTarget<EntityMob>(this, EntityMob.class, 10, true, false, new TargetPredicate(this)));
    }

    @Override
    protected void entityInit()
    {
        super.entityInit();
        this.dataManager.register(TAMED, Byte.valueOf((byte) 0));
        this.dataManager.register(OWNER_UNIQUE_ID, Optional.<UUID>absent());
    }

    @Override
    protected void applyEntityAttributes()
    {
        super.applyEntityAttributes();
        this.getAttributeMap().registerAttribute(SharedMonsterAttributes.ATTACK_DAMAGE);
        getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(40.0D);
        getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(6.0D);
        getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.27D);
    }

    public void setCombatTask()
    {
        if (this.worldObj != null && !this.worldObj.isRemote)
        {
            this.tasks.removeTask(this.aiAttackOnCollide);
            this.tasks.removeTask(this.aiArrowAttack);
            ItemStack itemstack = this.getHeldItemMainhand();

            if (itemstack != null && itemstack.getItem() instanceof ItemBow)
            {
                int i = 20;

                if (this.worldObj.getDifficulty() != EnumDifficulty.HARD)
                {
                    i = 40;
                }

                this.aiArrowAttack.setAttackCooldown(i);
                this.tasks.addTask(attackPriority, this.aiArrowAttack);
            } else
            {
                this.tasks.addTask(attackPriority, this.aiAttackOnCollide);
            }
        }
    }

    @Override
    public boolean isPotionApplicable(PotionEffect effect)
    {
        Potion potion = effect.getPotion();

        if (potion == MobEffects.REGENERATION || potion == MobEffects.INSTANT_HEALTH) //Specter cannot be healed by normal means
        {
            return false;
        }

        return super.isPotionApplicable(effect);
    }

    public boolean canStealEffectFromOwner(EntityLivingBase owner, PotionEffect effect)
    {
        return effect.getPotion().isBadEffect() && this.type == EnumDemonWillType.CORROSIVE;
    }

    public boolean canStealEffectFromOwner(EntityLivingBase owner)
    {
        if (this.type != EnumDemonWillType.CORROSIVE)
        {
            return false;
        }

        for (PotionEffect eff : owner.getActivePotionEffects())
        {
            if (canStealEffectFromOwner(owner, eff))
            {
                return true;
            }
        }

        return false;
    }

    public boolean stealEffectsFromOwner(EntityLivingBase owner)
    {
        if (this.type != EnumDemonWillType.CORROSIVE)
        {
            return false;
        }

        boolean hasStolenEffect = false;

        List<PotionEffect> removedEffects = new ArrayList<PotionEffect>();

        for (PotionEffect eff : owner.getActivePotionEffects())
        {
            if (canStealEffectFromOwner(owner, eff))
            {
                removedEffects.add(eff);
                hasStolenEffect = true;
            }
        }

        for (PotionEffect eff : removedEffects)
        {
            owner.removePotionEffect(eff.getPotion());
            this.addPotionEffect(eff);
        }

        return hasStolenEffect;
    }

    public boolean applyNegativeEffectsToAttacked(EntityLivingBase attackedEntity, float percentTransmitted)
    {
        boolean hasProvidedEffect = false;
        List<PotionEffect> removedEffects = new ArrayList<PotionEffect>();
        for (PotionEffect eff : this.getActivePotionEffects())
        {
            if (eff.getPotion().isBadEffect() && attackedEntity.isPotionApplicable(eff))
            {
                if (!attackedEntity.isPotionActive(eff.getPotion()))
                {
                    removedEffects.add(eff);
                    hasProvidedEffect = true;
                } else
                {
                    PotionEffect activeEffect = attackedEntity.getActivePotionEffect(eff.getPotion());
                    if (activeEffect.getAmplifier() < eff.getAmplifier() || activeEffect.getDuration() < eff.getDuration() * percentTransmitted)
                    {
                        removedEffects.add(eff);
                        hasProvidedEffect = true;
                    }
                }
            }
        }

        for (PotionEffect eff : removedEffects)
        {
            if (!attackedEntity.isPotionActive(eff.getPotion()))
            {
                PotionEffect newEffect = new PotionEffect(eff.getPotion(), (int) (eff.getDuration() * percentTransmitted), eff.getAmplifier(), eff.getIsAmbient(), eff.doesShowParticles());
                attackedEntity.addPotionEffect(newEffect);

                PotionEffect newSentientEffect = new PotionEffect(eff.getPotion(), (int) (eff.getDuration() * (1 - percentTransmitted)), eff.getAmplifier(), eff.getIsAmbient(), eff.doesShowParticles());
                this.removePotionEffect(eff.getPotion());
                this.addPotionEffect(newSentientEffect);
            } else
            {
                PotionEffect activeEffect = attackedEntity.getActivePotionEffect(eff.getPotion());

                PotionEffect newEffect = new PotionEffect(eff.getPotion(), (int) (eff.getDuration() * percentTransmitted), eff.getAmplifier(), activeEffect.getIsAmbient(), activeEffect.doesShowParticles());
                attackedEntity.addPotionEffect(newEffect);

                PotionEffect newSentientEffect = new PotionEffect(eff.getPotion(), (int) (eff.getDuration() * (1 - percentTransmitted)), eff.getAmplifier(), eff.getIsAmbient(), eff.doesShowParticles());
                this.removePotionEffect(eff.getPotion());
                this.addPotionEffect(newSentientEffect);
            }
        }

        return hasProvidedEffect;
    }

    public List<PotionEffect> getPotionEffectsForArrowRemovingDuration(float percentTransmitted)
    {
        List<PotionEffect> arrowEffects = new ArrayList<PotionEffect>();

        if (type != EnumDemonWillType.CORROSIVE)
        {
            return arrowEffects;
        }

        List<PotionEffect> removedEffects = new ArrayList<PotionEffect>();
        for (PotionEffect eff : this.getActivePotionEffects())
        {
            if (eff.getPotion().isBadEffect())
            {
                removedEffects.add(eff);
            }
        }

        for (PotionEffect eff : removedEffects)
        {
            PotionEffect newEffect = new PotionEffect(eff.getPotion(), (int) (eff.getDuration() * percentTransmitted), eff.getAmplifier(), eff.getIsAmbient(), eff.doesShowParticles());
            arrowEffects.add(newEffect);

            PotionEffect newSentientEffect = new PotionEffect(eff.getPotion(), (int) (eff.getDuration() * (1 - percentTransmitted)), eff.getAmplifier(), eff.getIsAmbient(), eff.doesShowParticles());
            this.removePotionEffect(eff.getPotion());
            this.addPotionEffect(newSentientEffect);
        }

        return arrowEffects;
    }

    @Override
    public void onLivingUpdate()
    {
        this.updateArmSwingProgress();
        float f = this.getBrightness(1.0F);

        if (f > 0.5F)
        {
            this.entityAge += 2;
        }

        super.onLivingUpdate();
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount)
    {
        return this.isEntityInvulnerable(source) ? false : super.attackEntityFrom(source, amount);
    }

    /**
     * Redone from EntityMob to prevent despawning on peaceful.
     */
    @Override
    public boolean attackEntityAsMob(Entity attackedEntity)
    {
        float f = (float) this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getAttributeValue();
        int i = 0;

        if (attackedEntity instanceof EntityLivingBase)
        {
            f += EnchantmentHelper.getModifierForCreature(this.getHeldItemMainhand(), ((EntityLivingBase) attackedEntity).getCreatureAttribute());
            i += EnchantmentHelper.getKnockbackModifier(this);
        }

        boolean flag = attackedEntity.attackEntityFrom(DamageSource.causeMobDamage(this), f);

        if (flag)
        {
            if (i > 0 && attackedEntity instanceof EntityLivingBase)
            {
                ((EntityLivingBase) attackedEntity).knockBack(this, (float) i * 0.5F, (double) MathHelper.sin(this.rotationYaw * 0.017453292F), (double) (-MathHelper.cos(this.rotationYaw * 0.017453292F)));
                this.motionX *= 0.6D;
                this.motionZ *= 0.6D;
            }

            int j = EnchantmentHelper.getFireAspectModifier(this);

            if (j > 0)
            {
                attackedEntity.setFire(j * 4);
            }

            if (attackedEntity instanceof EntityPlayer)
            {
                EntityPlayer entityplayer = (EntityPlayer) attackedEntity;
                ItemStack itemstack = this.getHeldItemMainhand();
                ItemStack itemstack1 = entityplayer.isHandActive() ? entityplayer.getActiveItemStack() : null;

                if (itemstack != null && itemstack1 != null && itemstack.getItem() instanceof ItemAxe && itemstack1.getItem() == Items.SHIELD)
                {
                    float f1 = 0.25F + (float) EnchantmentHelper.getEfficiencyModifier(this) * 0.05F;

                    if (this.rand.nextFloat() < f1)
                    {
                        entityplayer.getCooldownTracker().setCooldown(Items.SHIELD, 100);
                        this.worldObj.setEntityState(entityplayer, (byte) 30);
                    }
                }
            }

            this.applyEnchantments(this, attackedEntity);
        }

        if (flag)
        {
            if (this.type == EnumDemonWillType.CORROSIVE && attackedEntity instanceof EntityLivingBase)
            {
//                ((EntityLivingBase) attackedEntity).addPotionEffect(new PotionEffect(MobEffects.WITHER, 200));
                applyNegativeEffectsToAttacked((EntityLivingBase) attackedEntity, 1);
            }

            return true;
        } else
        {
            return false;
        }
    }

    @Override
    public void setItemStackToSlot(EntityEquipmentSlot slotIn, ItemStack stack)
    {
        super.setItemStackToSlot(slotIn, stack);

        if (!this.worldObj.isRemote && slotIn == EntityEquipmentSlot.MAINHAND)
        {
            this.setCombatTask();
        }
    }

    @Override
    public void onDeath(DamageSource cause)
    {
        super.onDeath(cause);

        if (!worldObj.isRemote)
        {
            this.entityDropItem(getHeldItemMainhand(), 0);
        }
    }

    public boolean isStationary()
    {
        return false;
    }

    public boolean absorbExplosion(Explosion explosion)
    {
        if (this.type == EnumDemonWillType.DESTRUCTIVE)
        {
            this.addPotionEffect(new PotionEffect(MobEffects.STRENGTH, 600, 1));

            explosion.doExplosionB(true);

            return true;
        }

        return false;
    }

    @Override
    public boolean processInteract(EntityPlayer player, EnumHand hand, @Nullable ItemStack stack)
    {
        if (this.isTamed() && player.equals(this.getOwner()) && hand == EnumHand.MAIN_HAND)
        {
            if (stack == null && player.isSneaking()) //Should return to the entity
            {
                if (!worldObj.isRemote)
                {
                    if (getHeldItemMainhand() != null)
                    {
                        this.entityDropItem(getHeldItemMainhand(), 0);
                    }

                    if (getHeldItemOffhand() != null)
                    {
                        this.entityDropItem(getHeldItemOffhand(), 0);
                    }

                    if (wasGivenSentientArmour)
                    {
                        this.entityDropItem(new ItemStack(ModItems.sentientArmourGem), 0);
                    }

                    this.setDead();
                }
            }
        }

        return super.processInteract(player, hand, stack);
    }

    public boolean isEntityInvulnerable(DamageSource source)
    {
        return super.isEntityInvulnerable(source) && (this.type == EnumDemonWillType.DESTRUCTIVE && source.isExplosion());
    }

    public void performEmergencyHeal(double toHeal)
    {
        this.heal((float) toHeal);

        double d0 = this.rand.nextGaussian() * 0.02D;
        double d1 = this.rand.nextGaussian() * 0.02D;
        double d2 = this.rand.nextGaussian() * 0.02D;
        this.worldObj.spawnParticle(EnumParticleTypes.HEART, this.posX + (double) (this.rand.nextFloat() * this.width * 2.0F) - (double) this.width, this.posY + 0.5D + (double) (this.rand.nextFloat() * this.height), this.posZ + (double) (this.rand.nextFloat() * this.width * 2.0F) - (double) this.width, d0, d1, d2, new int[0]);
    }

    /**
     * 
     * @param toHeal
     * @return Amount of Will consumed from the Aura to heal
     */
    public double absorbWillFromAuraToHeal(double toHeal)
    {
        if (worldObj.isRemote)
        {
            return 0;
        }

        double healthMissing = this.getMaxHealth() - this.getHealth();
        if (healthMissing <= 0)
        {
            return 0;
        }

        double will = WorldDemonWillHandler.getCurrentWill(worldObj, getPosition(), getType());

        toHeal = Math.min(healthMissing, Math.min(toHeal, will / getWillToHealth()));
        if (toHeal > 0)
        {
            this.heal((float) toHeal);
            return WorldDemonWillHandler.drainWill(worldObj, getPosition(), getType(), toHeal * getWillToHealth(), true);
        }

        return 0;
    }

    public boolean shouldSelfHeal()
    {
        return this.getHealth() < this.getMaxHealth() * 0.5;
    }

    public double getWillToHealth()
    {
        return 2;
    }

    @Override
    protected boolean canDespawn()
    {
        return !this.isTamed() && super.canDespawn();
    }

    public void onUpdate()
    {
        if (!this.worldObj.isRemote && this.ticksExisted % 20 == 0)
        {
            absorbWillFromAuraToHeal(2);
        }

        super.onUpdate();
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag)
    {
        super.writeEntityToNBT(tag);

        if (this.getOwnerId() == null)
        {
            tag.setString("OwnerUUID", "");
        } else
        {
            tag.setString("OwnerUUID", this.getOwnerId().toString());
        }

        tag.setString(Constants.NBT.WILL_TYPE, type.toString());

        tag.setBoolean("sentientArmour", wasGivenSentientArmour);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag)
    {
        super.readEntityFromNBT(tag);

        String s = "";

        if (tag.hasKey("OwnerUUID", 8))
        {
            s = tag.getString("OwnerUUID");
        } else
        {
            String s1 = tag.getString("Owner");
            s = PreYggdrasilConverter.convertMobOwnerIfNeeded(this.getServer(), s1);
        }

        if (!s.isEmpty())
        {
            try
            {
                this.setOwnerId(UUID.fromString(s));
                this.setTamed(true);
            } catch (Throwable var4)
            {
                this.setTamed(false);
            }
        }

        if (!tag.hasKey(Constants.NBT.WILL_TYPE))
        {
            type = EnumDemonWillType.DEFAULT;
        } else
        {
            type = EnumDemonWillType.valueOf(tag.getString(Constants.NBT.WILL_TYPE));
        }

        wasGivenSentientArmour = tag.getBoolean("sentientArmour");

        this.setCombatTask();
    }

    //TODO: Change to fit the given AI
    public boolean shouldAttackEntity(EntityLivingBase attacker, EntityLivingBase owner)
    {
        if (!(attacker instanceof EntityCreeper) && !(attacker instanceof EntityGhast))
        {
            if (attacker instanceof IEntityOwnable)
            {
                IEntityOwnable entityOwnable = (IEntityOwnable) attacker;

                if (entityOwnable.getOwner() == owner)
                {
                    return false;
                }
            }

            return attacker instanceof EntityPlayer && owner instanceof EntityPlayer && !((EntityPlayer) owner).canAttackPlayer((EntityPlayer) attacker) ? false : !(attacker instanceof EntityHorse) || !((EntityHorse) attacker).isTame();
        } else
        {
            return false;
        }
    }

    public void attackEntityWithRangedAttack(EntityLivingBase target, float velocity)
    {
        ItemStack heldStack = this.getItemStackFromSlot(EntityEquipmentSlot.MAINHAND);
        if (heldStack != null && heldStack.getItem() == ModItems.sentientBow)
        {
            EntityTippedArrow arrowEntity = ((ItemSentientBow) heldStack.getItem()).getArrowEntity(worldObj, heldStack, target, this, velocity);
            if (arrowEntity != null)
            {
                List<PotionEffect> effects = getPotionEffectsForArrowRemovingDuration(0.2f);
                for (PotionEffect eff : effects)
                {
                    arrowEntity.addEffect(eff);
                }

                this.playSound(SoundEvents.ENTITY_SKELETON_SHOOT, 1.0F, 1.0F / (this.getRNG().nextFloat() * 0.4F + 0.8F));
                this.worldObj.spawnEntityInWorld(arrowEntity);
            }
        } else
        {
            EntityTippedArrow entitytippedarrow = new EntityTippedArrow(this.worldObj, this); //TODO: Change to an arrow created by the Sentient Bow
            double d0 = target.posX - this.posX;
            double d1 = target.getEntityBoundingBox().minY + (double) (target.height / 3.0F) - entitytippedarrow.posY;
            double d2 = target.posZ - this.posZ;
            double d3 = (double) MathHelper.sqrt_double(d0 * d0 + d2 * d2);
            entitytippedarrow.setThrowableHeading(d0, d1 + d3 * 0.2, d2, 1.6F, 0); //TODO: Yes, it is an accurate arrow. Don't be hatin'
            int i = EnchantmentHelper.getMaxEnchantmentLevel(Enchantments.POWER, this);
            int j = EnchantmentHelper.getMaxEnchantmentLevel(Enchantments.PUNCH, this);
            entitytippedarrow.setDamage((double) (velocity * 2.0F) + this.rand.nextGaussian() * 0.25D + (double) ((float) this.worldObj.getDifficulty().getDifficultyId() * 0.11F));

            if (i > 0)
            {
                entitytippedarrow.setDamage(entitytippedarrow.getDamage() + (double) i * 0.5D + 0.5D);
            }

            if (j > 0)
            {
                entitytippedarrow.setKnockbackStrength(j);
            }

            boolean burning = this.isBurning();
            burning = burning || EnchantmentHelper.getMaxEnchantmentLevel(Enchantments.FLAME, this) > 0;

            if (burning)
            {
                entitytippedarrow.setFire(100);
            }

            if (true) //TODO: Add potion effects to the arrows
            {
                entitytippedarrow.addEffect(new PotionEffect(MobEffects.SLOWNESS, 600));
            }

            this.playSound(SoundEvents.ENTITY_SKELETON_SHOOT, 1.0F, 1.0F / (this.getRNG().nextFloat() * 0.4F + 0.8F));
            this.worldObj.spawnEntityInWorld(entitytippedarrow);
        }
    }

    public boolean isTamed()
    {
        return (((Byte) this.dataManager.get(TAMED)).byteValue() & 4) != 0;
    }

    public void setTamed(boolean tamed)
    {
        byte b0 = ((Byte) this.dataManager.get(TAMED)).byteValue();

        if (tamed)
        {
            this.dataManager.set(TAMED, Byte.valueOf((byte) (b0 | 4)));
        } else
        {
            this.dataManager.set(TAMED, Byte.valueOf((byte) (b0 & -5)));
        }

//        this.setupTamedAI();
    }

    @Override
    protected SoundEvent getAmbientSound()
    {
        return SoundEvents.ENTITY_COW_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound()
    {
        return SoundEvents.ENTITY_COW_HURT;
    }

    @Override
    protected SoundEvent getDeathSound()
    {
        return SoundEvents.ENTITY_COW_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, Block block)
    {
        this.playSound(SoundEvents.ENTITY_COW_STEP, 0.15F, 1.0F);
    }

    /**
     * Returns the volume for the sounds this mob makes.
     */
    @Override
    protected float getSoundVolume()
    {
        return 0.4F;
    }

    @Override
    public UUID getOwnerId()
    {
        return (UUID) (this.dataManager.get(OWNER_UNIQUE_ID)).orNull();
    }

    public void setOwnerId(UUID uuid)
    {
        this.dataManager.set(OWNER_UNIQUE_ID, Optional.fromNullable(uuid));
    }

    @Override
    public EntityLivingBase getOwner()
    {
        try
        {
            UUID uuid = this.getOwnerId();
            return uuid == null ? null : this.worldObj.getPlayerEntityByUUID(uuid);
        } catch (IllegalArgumentException var2)
        {
            return null;
        }
    }

    public void setOwner(EntityPlayer player)
    {
        setOwnerId(player.getUniqueID());
    }

    public class TargetPredicate implements Predicate<EntityMob>
    {
        EntitySentientSpecter entity;

        public TargetPredicate(EntitySentientSpecter entity)
        {
            this.entity = entity;
        }

        @Override
        public boolean apply(EntityMob input)
        {
            return entity.shouldAttackEntity(input, this.entity.getOwner());
        }
    }
}