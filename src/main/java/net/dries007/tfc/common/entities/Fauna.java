/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */

package net.dries007.tfc.common.entities;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacements;

import net.minecraftforge.registries.ForgeRegistries;

import net.dries007.tfc.util.Helpers;
import net.dries007.tfc.util.JsonHelpers;
import net.dries007.tfc.util.RegisteredDataManager;
import net.dries007.tfc.world.chunkdata.ForestType;
import net.dries007.tfc.world.decorator.ClimateConfig;

/**
 * A data driven way to make spawning conditions for animals player configurable.
 */
public class Fauna
{
    public static final RegisteredDataManager<Fauna> MANAGER = new RegisteredDataManager<>(Fauna::new, Fauna::new, "fauna", "fauna");

    private final ResourceLocation id;
    private final EntityType<Mob> entity;
    private final int chance;
    private final int distanceBelowSeaLevel;
    private final ClimateConfig climateConfig;
    private final SpawnPlacements.Type placement;
    private final boolean solidGround;

    public Fauna(ResourceLocation id)
    {
        this.id = id;
        this.entity = Helpers.notNull();
        this.chance = 1;
        this.distanceBelowSeaLevel = -1;
        this.climateConfig = new ClimateConfig(0, 0, 0, 0, ForestType.NONE, ForestType.NONE, false);
        this.placement = SpawnPlacements.Type.ON_GROUND;
        this.solidGround = false;
    }

    @SuppressWarnings("unchecked")
    public Fauna(ResourceLocation id, JsonObject json)
    {
        this.id = id;
        this.entity = (EntityType<Mob>) JsonHelpers.getRegistryEntry(json, "entity", ForgeRegistries.ENTITIES);
        this.chance = JsonHelpers.getAsInt(json, "chance", 1);
        this.distanceBelowSeaLevel = JsonHelpers.getAsInt(json, "distance_below_sea_level", -1);
        this.climateConfig = JsonHelpers.decodeCodec(json, ClimateConfig.CODEC, "climate");
        this.placement = JsonHelpers.getEnum(json, "placement", SpawnPlacements.Type.class, SpawnPlacements.Type.ON_GROUND);
        this.solidGround = JsonHelpers.getAsBoolean(json, "solid_ground", false);
    }

    public ResourceLocation getId()
    {
        return id;
    }

    public EntityType<Mob> getEntity()
    {
        return entity;
    }

    public SpawnPlacements.Type getType()
    {
        return placement;
    }


    public int getChance()
    {
        return chance;
    }

    public int getDistanceBelowSeaLevel()
    {
        return distanceBelowSeaLevel;
    }

    public ClimateConfig getClimateConfig()
    {
        return climateConfig;
    }

    public boolean isSolidGround()
    {
        return solidGround;
    }
}
