package mrtjp.projectred.expansion.tile;

import codechicken.lib.data.MCDataInput;
import codechicken.lib.data.MCDataOutput;
import codechicken.lib.util.ServerUtils;
import codechicken.lib.vec.Vector3;
import mrtjp.projectred.core.inventory.BaseContainer;
import mrtjp.projectred.expansion.block.BatteryBoxBlock;
import mrtjp.projectred.expansion.init.ExpansionBlocks;
import mrtjp.projectred.expansion.inventory.container.BatteryBoxMenu;
import mrtjp.projectred.expansion.item.IChargable;
import mrtjp.projectred.expansion.item.IRechargableBattery;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Tuple;
import net.minecraft.world.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;

import javax.annotation.Nullable;
import java.util.Objects;

import static mrtjp.projectred.expansion.ProjectRedExpansion.LOGGER;

public class BatteryBoxBlockEntity extends LowLoadPoweredBlockEntity {

    public static final String TAG_KEY_POWER_STORED = "power_stored";
    public static final String TAG_KEY_CHARGE_LEVEL_STATE = "charge_level";

    private final BatteryBoxInventory inventory = new BatteryBoxInventory();
    private final IItemHandler[] handlers = {
            new SidedInvWrapper(inventory, Direction.DOWN),
            new SidedInvWrapper(inventory, Direction.UP),
    };

    private int powerStored = 0;

    public BatteryBoxBlockEntity(BlockPos pos, BlockState state) {
        super(ExpansionBlocks.BATTERY_BOX_BLOCK_ENTITY.get(), pos, state);
        inventory.addListener(c -> setChanged());
    }

    @Override
    public void saveToNBT(CompoundTag tag) {
        super.saveToNBT(tag);
        tag.putInt("storage", powerStored);
        inventory.saveTo(tag, "inventory");
    }

    @Override
    public void loadFromNBT(CompoundTag tag) {
        super.loadFromNBT(tag);
        powerStored = tag.getInt("storage");
        inventory.loadFrom(tag, "inventory");
    }

    @Override
    public void writeDesc(MCDataOutput out) {
        super.writeDesc(out);
    }

    @Override
    public void readDesc(MCDataInput in) {
        super.readDesc(in);
    }

    @Override
    public InteractionResult onBlockActivated(Player player, InteractionHand hand, BlockHitResult hit) {
        if (!getLevel().isClientSide) {
            ServerUtils.openContainer(
                    (ServerPlayer) player,
                    new SimpleMenuProvider(
                            (id, inv, p) -> new BatteryBoxMenu(inv, this, id),
                            getBlockState().getBlock().getName()),
                    p -> p.writePos(getBlockPos()));
        }

        return InteractionResult.sidedSuccess(getLevel().isClientSide);
    }

    @Override
    public void onBlockPlaced(@Nullable LivingEntity player, ItemStack item) {
        super.onBlockPlaced(player, item);
        if (!getBlockLevel().isClientSide && item.hasTag() && Objects.requireNonNull(item.getTag()).contains(TAG_KEY_POWER_STORED)) {
            powerStored = item.getTag().getInt(TAG_KEY_POWER_STORED);
            pushBlockState();
        }
    }

    @Override
    public void onBlockRemoved() {
        super.onBlockRemoved();
        dropInventory(inventory, getLevel(), Vector3.fromBlockPos(getBlockPos()));
    }

    public ItemStack createStackWithStoredPower() {
        ItemStack stack = new ItemStack(ExpansionBlocks.BATTERY_BOX_BLOCK.get(), 1);
        if (powerStored > 0) {
            CompoundTag tag = stack.getOrCreateTag();
            tag.putInt(TAG_KEY_POWER_STORED, powerStored);
            tag.putInt(TAG_KEY_CHARGE_LEVEL_STATE, getPowerStoredScaled(8));
        }
        return stack;
    }

    @Override
    public void tick() {
        super.tick();
        if (getLevel().isClientSide) return;

        boolean changed = false;

        // Attempt to keep conductor charge between UpperChargeTarget and LowerChargeTarget by
        // respectively drawing from or to internal storage
        if (getConductorCharge() > getConductorUpperChargeTarget() && powerStored < getMaxStorage()) {
            int n = Math.min(getConductorCharge() - getConductorUpperChargeTarget(), getConductorChargeSpeed()) / 10;
            n = Math.min(n, getMaxStorage() - powerStored);
            conductor.applyPower(n * -1000);
            powerStored += n;
            if (n != 0) changed = true;
        } else if (getConductorCharge() < getConductorLowerChargeTarget() && powerStored > 0) {
            int n = Math.min(getConductorLowerChargeTarget() - getConductorCharge(), getConductorChargeSpeed()) / 10;
            n = Math.min(n, powerStored);
            conductor.applyPower(n * 1000);
            powerStored -= n;
            if (n != 0) changed = true;
        }

        // Charge and discharge items in inventory
        changed |= tryChargeBattery();
        changed |= tryDischargeBattery();

        // Sanity check powerStored
        // TODO Remove: temporary mitigation for #1789
        if (powerStored < 0) {
            LOGGER.warn("Power stored is negative! BatteryBoxTile @{}", getBlockPos());
            powerStored = 0;
        }

        // Update block state if render level changed
        int prevPowerLevel = getBlockState().getValue(BatteryBoxBlock.CHARGE_LEVEL);
        int newPowerLevel = getPowerStoredScaled(8);
        if (prevPowerLevel != newPowerLevel) {
            pushBlockState();
        }

        // Mark chunk data changed
        if (changed) {
            setChanged();
        }
    }

    public int getMaxStorage() {
        return 8000;
    }

    public int getConductorUpperChargeTarget() {
        return 900;
    }

    public int getConductorLowerChargeTarget() {
        return 800;
    }

    protected int getConductorChargeSpeed() {
        return 100;
    }

    protected int getBatteryChargeSpeed() {
        return 25;
    }

    protected int getPowerStoredScaled(int i) {
        return Math.min(i, i * powerStored / getMaxStorage());
    }

    @Override
    public BlockState storeBlockState(BlockState defaultState) {
        return super.storeBlockState(defaultState)
                .setValue(BatteryBoxBlock.CHARGE_LEVEL, getPowerStoredScaled(8));
    }

    private boolean tryChargeBattery() {
        ItemStack stack = inventory.getItem(0);
        if (!stack.isEmpty() && stack.getItem() instanceof IChargable) {
            int toAdd = Math.min(powerStored, getBatteryChargeSpeed());
            Tuple<ItemStack, Integer> result = ((IChargable) stack.getItem()).addPower(stack, toAdd);
            inventory.setItem(0, result.getA());
            powerStored -= result.getB();
            return result.getB() != 0;
        }
        return false;
    }

    private boolean tryDischargeBattery() {
        ItemStack stack = inventory.getItem(1);
        if (!stack.isEmpty() && stack.getItem() instanceof IChargable) {
            int toDraw = Math.min(getMaxStorage() - powerStored, getBatteryChargeSpeed());
            Tuple<ItemStack, Integer> result = ((IChargable) stack.getItem()).drawPower(stack, toDraw);
            inventory.setItem(1, result.getA());
            powerStored += result.getB();
            return result.getB() != 0;
        }
        return false;
    }

    //region Capabilities
    public IItemHandler getHandler(Direction side) {
        return handlers[side == Direction.UP ? 1 : 0];
    }
    //endregion

    //region Container getters
    public Container getInventory() {
        return inventory;
    }
    public int getPowerStored() {
        return powerStored;
    }
    //endregion

    private static class BatteryBoxInventory extends BaseContainer implements WorldlyContainer {

        private static final int[] TOP_SLOTS = new int[]{0};
        private static final int[] BOTTOM_SLOTS = new int[]{1};

        public BatteryBoxInventory() {
            super(2);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return stack.getItem() instanceof IRechargableBattery;
        }

        @Override
        public int[] getSlotsForFace(Direction direction) {
            return direction == Direction.UP ? TOP_SLOTS : BOTTOM_SLOTS;
        }

        @Override
        public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) {
            return true;
        }

        @Override
        public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
            return true;
        }
    }
}
