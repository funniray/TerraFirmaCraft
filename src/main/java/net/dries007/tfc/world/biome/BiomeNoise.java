/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */

package net.dries007.tfc.world.biome;

import net.dries007.tfc.world.IBiomeNoiseSampler;
import net.dries007.tfc.world.noise.*;

import static net.dries007.tfc.world.TFCChunkGenerator.SEA_LEVEL;

/**
 * Collections of biome noise factories
 * These are built by hand and assigned to different biomes
 */
public final class BiomeNoise
{
    /**
     * A flat base, with inverse exponential scaled ridge noise subtracted from it
     * Relief carving generates twisting canyons similar to vanilla mesas
     */
    public static INoise2D badlands(long seed)
    {
        final int seaLevel = SEA_LEVEL;
        return new OpenSimplex2D(seed)
            .octaves(4)
            .spread(0.025f)
            .scaled(seaLevel + 22, seaLevel + 32)
            .add(new OpenSimplex2D(seed + 1)
                .octaves(4)
                .ridged()
                .spread(0.04f)
                .map(x -> 1.3f * -(x > 0 ? x * x * x : 0.5f * x))
                .scaled(-1f, 0.3f, -1f, 1f)
                .terraces(15)
                .scaled(-19.5f, 0)
            )
            .map(x -> x < seaLevel ? seaLevel - 0.3f * (seaLevel - x) : x);
    }

    /**
     * Domain warping creates twisting land patterns
     */
    public static INoise2D canyons(long seed, int minHeight, int maxHeight)
    {
        final INoise2D warpX = new OpenSimplex2D(seed).octaves(4).spread(0.1f).scaled(-30, 30);
        final INoise2D warpZ = new OpenSimplex2D(seed + 1).octaves(4).spread(0.1f).scaled(-30, 30);
        return new OpenSimplex2D(seed).octaves(4).spread(0.2f).warped(warpX, warpZ).map(x -> x > 0.4 ? x - 0.8f : -x).scaled(-0.4f, 0.8f, SEA_LEVEL + minHeight, SEA_LEVEL + maxHeight).spread(0.3f);
    }

    /**
     * Simple noise with little variance.
     */
    public static INoise2D hills(long seed, int minHeight, int maxHeight)
    {
        return new OpenSimplex2D(seed).octaves(4).spread(0.05f).scaled(SEA_LEVEL + minHeight, SEA_LEVEL + maxHeight);
    }

    public static INoise2D lake(long seed)
    {
        return new OpenSimplex2D(seed).octaves(4).spread(0.15f).scaled(SEA_LEVEL - 12, SEA_LEVEL - 2);
    }

    public static INoise2D river(long seed)
    {
        return new OpenSimplex2D(seed).octaves(4).spread(0.2f).scaled(SEA_LEVEL - 8, SEA_LEVEL - 2);
    }

    public static IBiomeNoiseSampler riverSampler(long seed)
    {
        INoise2D riverHeight = new OpenSimplex2D(seed).octaves(4).spread(0.2f).scaled(SEA_LEVEL - 11, SEA_LEVEL - 5);
        INoise3D cliffNoise = new OpenSimplex3D(seed).octaves(2).spread(0.1f).scaled(0, 6);

        return new IBiomeNoiseSampler()
        {
            private double height;
            private int x, z;

            @Override
            public void setColumn(int x, int z)
            {
                height = riverHeight.noise(x, z);
                this.x = x;
                this.z = z;
            }

            @Override
            public double height()
            {
                return height;
            }

            @Override
            public double noise(int y)
            {
                if (y > height + 20 && y < height + 40)
                {
                    double easing = 1 - (y - height - 20) / 20f;
                    return easing * cliffNoise.noise(x, y, z);
                }
                if (y > height)
                {
                    return cliffNoise.noise(x, y, z);
                }
                if (y > height - 8)
                {
                    double easing = (y - height + 8) / 8d;
                    return easing * cliffNoise.noise(x, y, z);
                }
                return FULL;
            }
        };
    }

    /**
     * Noise right around sea level which has been flattened, to produce lots of small pockets above and below water
     */
    public static INoise2D lowlands(long seed)
    {
        return new OpenSimplex2D(seed).octaves(6).spread(0.55f).scaled(SEA_LEVEL - 6, SEA_LEVEL + 7).flattened(SEA_LEVEL - 4, SEA_LEVEL + 3);
    }

    public static INoise2D mountains(long seed, int baseHeight, int scaleHeight)
    {
        final INoise2D baseNoise = new OpenSimplex2D(seed) // A simplex noise forms the majority of the base
            .octaves(6) // High octaves to create highly fractal terrain
            .spread(0.14f)
            .add(new OpenSimplex2D(seed + 1) // Ridge noise is added to mimic real mountain ridges. It is scaled smaller than the base noise to not be overpowering
                .octaves(4)
                .ridged() // Ridges are applied after octaves as it creates less directional artifacts this way
                .spread(0.02f)
                .scaled(-0.7f, 0.7f))
            .map(x -> 0.125f * (x + 1) * (x + 1) * (x + 1)) // Power scaled, flattens most areas but maximizes peaks
            .map(x -> SEA_LEVEL + baseHeight + scaleHeight * x); // Scale the entire thing to mountain ranges

        // Cliff noise consists of noise that's been artificially clamped over half the domain, which is then selectively added above a base height level
        // This matches up with the distinction between dirt and stone
        final INoise2D cliffNoise = new OpenSimplex2D(seed + 2).octaves(2).map(x -> x > 0 ? x : 0).spread(0.01f).scaled(-25, 25);
        final INoise2D cliffHeightNoise = new OpenSimplex2D(seed + 3).octaves(2).spread(0.01f).scaled(-20, 20);

        return (x, z) -> {
            float height = baseNoise.noise(x, z);
            if (height > 120) // Only sample each cliff noise layer if the base noise could be influenced by it
            {
                float cliffHeight = cliffHeightNoise.noise(x, z);
                if (height > 140 + cliffHeight)
                {
                    float cliff = cliffNoise.noise(x, z);
                    return height + cliff;
                }
            }
            return height;
        };
    }

    /**
     * Uses domain warping to achieve a swirly hills effect
     */
    public static INoise2D ocean(long seed, int depthMin, int depthMax)
    {
        final INoise2D warpX = new OpenSimplex2D(seed).octaves(2).spread(0.015f).scaled(-30, 30);
        final INoise2D warpZ = new OpenSimplex2D(seed + 1).octaves(2).spread(0.015f).scaled(-30, 30);
        return new OpenSimplex2D(seed + 2).octaves(4).spread(0.11f).warped(warpX, warpZ).scaled(SEA_LEVEL + depthMin, SEA_LEVEL + depthMax);
    }

    /**
     * Applies elements from deep ocean and badlands.
     * Inverse power scaled ridge noise (cubic) is used to create ridges, inside the domain warped ocean noise
     */
    public static INoise2D oceanRidge(long seed, int depthMin, int depthMax)
    {
        final INoise2D warpX = new OpenSimplex2D(seed).octaves(2).spread(0.015f).scaled(-30, 30);
        final INoise2D warpZ = new OpenSimplex2D(seed + 1).octaves(2).spread(0.015f).scaled(-30, 30);
        final INoise2D ridgeNoise = new OpenSimplex2D(seed + 1).octaves(4).ridged().spread(0.015f).map(x -> { // In [-1, 1]
            if (x > -0.3f)
            {
                x = (x + 0.3f) / 1.3f;  // In [0, 1]
                x = x * x * x; // Power scaled
                return -35f * x; // In [0, -35]
            }
            return 0; // No modifications outside of ridge area
        });
        return new OpenSimplex2D(seed + 2).octaves(4).spread(0.11f).scaled(SEA_LEVEL + depthMin, SEA_LEVEL + depthMax).add(ridgeNoise).warped(warpX, warpZ);
    }

    public static INoise2D shore(long seed)
    {
        return new OpenSimplex2D(seed).octaves(4).spread(0.17f).scaled(SEA_LEVEL, SEA_LEVEL + 1.8f);
    }

    /**
     * Adds volcanoes to a base noise height map
     */
    public static INoise2D addVolcanoes(long seed, INoise2D baseNoise, int frequency, int baseVolcanoHeight, int scaleVolcanoHeight)
    {
        final Cellular2D volcanoNoise = VolcanoNoise.cellNoise(seed);
        final INoise2D volcanoJitterNoise = VolcanoNoise.distanceVariationNoise(seed);
        final float volcanoChance = 1f / frequency;

        return (x, z) -> {
            final float distance = volcanoNoise.noise(x, z, CellularNoiseType.F1);
            final float value = volcanoNoise.noise(x, z, CellularNoiseType.VALUE);
            final float baseHeight = baseNoise.noise(x, z);
            final float t = VolcanoNoise.calculateEasing(distance);
            if (value < volcanoChance && t > 0)
            {
                final float th = VolcanoNoise.calculateHeight(distance + volcanoJitterNoise.noise(x, z));
                final float height = SEA_LEVEL + baseVolcanoHeight + th * scaleVolcanoHeight;
                return NoiseUtil.lerp(baseHeight, 0.5f * (height + NoiseUtil.fastMax(height, baseHeight)), t);
            }
            return baseHeight;
        };
    }

    public static IBiomeNoiseSampler undergroundRivers(long seed, INoise2D heightNoise)
    {
        final INoise2D carvingCenterNoise = new OpenSimplex2D(seed).octaves(2).spread(0.02f).scaled(SEA_LEVEL - 3, SEA_LEVEL + 3);
        final INoise2D carvingHeightNoise = new OpenSimplex2D(seed).octaves(4).spread(0.15f).scaled(8, 14);

        return IBiomeNoiseSampler.fromHeightAndCarvingNoise(heightNoise, carvingCenterNoise, carvingHeightNoise);
    }

    public static IBiomeNoiseSampler undergroundLakes(long seed, INoise2D heightNoise)
    {
        final float maxColumnThreshold = 0.5f;

        final INoise2D centerNoise = new OpenSimplex2D(seed).octaves(2).spread(0.02f).scaled(SEA_LEVEL - 3, SEA_LEVEL + 3);
        final INoise2D baseNoise = new OpenSimplex2D(seed + 827349183L).octaves(4).spread(0.15f).scaled(8, 14);
        final INoise2D columnNoise = new Cellular2D(seed + 57238179321L, 1f, CellularNoiseType.F1).spread(0.065f);

        final INoise2D baseAndColumnNoise = (x, z) -> {
            float maxBaseValue = 14;
            final float columnValue = columnNoise.noise(x, z);
            if (columnValue < maxColumnThreshold)
            {
                // Near a column, scale the base noise to quickly clamp off inside the column radius
                final float t = (columnValue - maxColumnThreshold) / (maxColumnThreshold - 0.1f);
                maxBaseValue = NoiseUtil.lerp(14, 0, t * t);
                if (maxBaseValue < 0)
                {
                    return 0;
                }
            }
            float baseValue = baseNoise.noise(x, z);
            return Math.min(maxBaseValue, baseValue);
        };

        return IBiomeNoiseSampler.fromHeightAndCarvingNoise(heightNoise, centerNoise, baseAndColumnNoise);
    }
}
