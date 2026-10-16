package com.undying.core;

import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.connect.IMixinConnector;

public class UndyingMixinConnector implements IMixinConnector {
    @Override
    public void connect() {
        Mixins.addConfiguration("undying.mixins.json");
    }
}
