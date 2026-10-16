package com.undying.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * World-level save flag: gear + tools are granted only once, on the first entry
 * into each save. The data lives in the world save (data attached to level.dat),
 * so rejoining the same save does not grant it again while a new save starts over.
 */
public class HiddenGearSaveData extends SavedData {
    public static final String ID = "undying_hidden_gear";
    private boolean gearGiven = false;

    public static HiddenGearSaveData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(HiddenGearSaveData::load, HiddenGearSaveData::new, ID);
    }

    public static HiddenGearSaveData load(CompoundTag tag) {
        HiddenGearSaveData d = new HiddenGearSaveData();
        d.gearGiven = tag.getBoolean("gearGiven");
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("gearGiven", gearGiven);
        return tag;
    }

    public boolean isGearGiven() { return gearGiven; }

    public void markGearGiven() {
        gearGiven = true;
        setDirty();
    }
}
