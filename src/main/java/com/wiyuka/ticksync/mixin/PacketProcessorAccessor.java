package com.wiyuka.ticksync.mixin;

import net.minecraft.network.PacketProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Queue;

@Mixin(PacketProcessor.class)
public interface PacketProcessorAccessor {

    @Accessor("runningThread")
    Thread ticksync$runningThread();

    @Accessor("packetsToBeHandled")
    Queue<?> ticksync$queue();
}
