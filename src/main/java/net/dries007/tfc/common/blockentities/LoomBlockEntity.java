package net.dries007.tfc.common.blockentities;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;

import net.dries007.tfc.common.recipes.LoomRecipe;
import net.dries007.tfc.common.recipes.inventory.ItemStackInventory;

import static net.dries007.tfc.TerraFirmaCraft.MOD_ID;

public class LoomBlockEntity extends InventoryBlockEntity<ItemStackHandler>
{

    private static final Component NAME = new TranslatableComponent(MOD_ID + ".tile_entity.loom");
    private static final int SLOT_RECIPE = 0;
    private static final int SLOT_OUTPUT = 1;

    private int progress = 0;

    private LoomRecipe recipe = null;
    private long lastPushed = 0L;
    private boolean needsUpdate = false;

    public LoomBlockEntity(BlockPos pos, BlockState state)
    {
        super(TFCBlockEntities.LOOM.get(), pos, state, defaultInventory(2), NAME);
    }

    @OnlyIn(Dist.CLIENT)
    public boolean currentBoolean()
    {
        return progress % 2 == 0;
    }

    public InteractionResult onRightClick(Player player)
    {
        assert level != null;
        ItemStack heldItem = player.getMainHandItem();

        // when shifting we let you remove items
        if (player.isShiftKeyDown())
        {
            if (!inventory.getStackInSlot(SLOT_RECIPE).isEmpty() && progress == 0)
            {
                if (heldItem.isEmpty() && !level.isClientSide)
                {
                    ItemStack temp = inventory.getStackInSlot(SLOT_RECIPE).copy();
                    temp.setCount(1);
                    ItemHandlerHelper.giveItemToPlayer(player, temp);
                    inventory.getStackInSlot(SLOT_RECIPE).shrink(1);
                    markForBlockUpdate();
                }
                updateCachedRecipe();
                return InteractionResult.SUCCESS;
            }
        }
        else
        {
            // everything is empty let's initialize
            if (inventory.getStackInSlot(SLOT_RECIPE).isEmpty() && inventory.getStackInSlot(SLOT_OUTPUT).isEmpty() && LoomRecipe.getRecipe(level, new ItemStackInventory(heldItem)) != null)
            {
                if (!level.isClientSide)
                {
                    inventory.setStackInSlot(SLOT_RECIPE, heldItem.copy());
                    inventory.getStackInSlot(SLOT_RECIPE).setCount(1);
                    heldItem.shrink(1);
                    markForBlockUpdate();
                }
                updateCachedRecipe();
                return InteractionResult.SUCCESS;
            }
            else if (!inventory.getStackInSlot(SLOT_RECIPE).isEmpty()) // we are holding something that can be added to the current loom inventory
            {
                if (heldItem.sameItem(inventory.getStackInSlot(SLOT_RECIPE)) && recipe.getInputCount() > inventory.getStackInSlot(SLOT_RECIPE).getCount())
                {
                    if (!level.isClientSide)
                    {
                        heldItem.shrink(1);
                        inventory.getStackInSlot(0).grow(1);
                        markForBlockUpdate();
                    }
                    return InteractionResult.SUCCESS;
                }
            }
            // actual pushing function of the loom
            if (recipe != null && heldItem.isEmpty() && recipe.getInputCount() == inventory.getStackInSlot(SLOT_RECIPE).getCount() && progress < recipe.getStepCount() && !needsUpdate)
            {
                long time = level.getGameTime() - lastPushed;
                if (time < 20) // we only let you update once a second
                {
                    return InteractionResult.SUCCESS;
                }
                lastPushed = level.getGameTime();
                needsUpdate = true;
                markForSync();
                return InteractionResult.sidedSuccess(level.isClientSide); // we want to swing the player's arm
            }

            if (!inventory.getStackInSlot(SLOT_OUTPUT).isEmpty() && heldItem.isEmpty()) // loom is complete
            {
                if (!level.isClientSide)
                {
                    ItemHandlerHelper.giveItemToPlayer(player, inventory.getStackInSlot(SLOT_OUTPUT).copy());
                    inventory.setStackInSlot(SLOT_OUTPUT, ItemStack.EMPTY);
                    markForBlockUpdate();
                }
                progress = 0;
                recipe = null;
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, LoomBlockEntity loom)
    {
        assert level != null;
        if (loom.recipe != null)
        {
            LoomRecipe recipe = loom.recipe; // Avoids NPE on slot changes
            if (loom.needsUpdate)
            {
                if (level.getGameTime() - loom.lastPushed >= 20)
                {
                    loom.needsUpdate = false;
                    loom.progress++;

                    if (loom.progress == recipe.getStepCount())
                    {
                        loom.inventory.setStackInSlot(SLOT_RECIPE, ItemStack.EMPTY);
                        loom.inventory.setStackInSlot(SLOT_OUTPUT, recipe.assemble(new ItemStackInventory(loom.inventory.getStackInSlot(SLOT_RECIPE))));
                    }
                    loom.markForBlockUpdate();
                }
            }
        }
    }

    public int getMaxInputCount()
    {
        return (recipe == null) ? 1 : recipe.getInputCount();
    }

    public int getCount()
    {
        return inventory.getStackInSlot(0).getCount();
    }

    public int getMaxProgress()
    {
        return (recipe == null) ? 1 : recipe.getStepCount();
    }

    public int getProgress()
    {
        return progress;
    }

    public boolean hasRecipe()
    {
        return recipe != null;
    }

    public ResourceLocation getInProgressTexture()
    {
        return recipe.getInProgressTexture();
    }

    private void updateCachedRecipe()
    {
        assert level != null;
        recipe = LoomRecipe.getRecipe(level, new ItemStackInventory(inventory.getStackInSlot(SLOT_RECIPE)));
    }

    @OnlyIn(Dist.CLIENT)
    public double getAnimPos()
    {
        assert level != null;
        if (recipe == null) return 0;
        int time = (int) (level.getGameTime()- lastPushed); // level.getTimeOfDay() is the wrong thing to use here
        if (time < 10)
        {
            return Math.sin((Math.PI / 20) * time) * 0.23125;
        }
        else if (time < 20)
        {
            return Math.sin((Math.PI / 20) * (20 - time)) * 0.23125;
        }
        return 0;
    }
}
