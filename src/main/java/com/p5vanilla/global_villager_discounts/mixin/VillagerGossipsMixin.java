package com.p5vanilla.global_villager_discounts.mixin;

import com.google.common.collect.Maps;
import net.minecraft.village.VillageGossipType;
import net.minecraft.village.VillagerGossips;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

@Mixin(VillagerGossips.class)
public abstract class VillagerGossipsMixin {

    @Shadow
    private final Map<UUID, VillagerGossips.Reputation> entityReputation = Maps.newHashMap();

    @Shadow
    protected abstract int mergeReputation(VillageGossipType type, int left, int right);

    @Inject(
            method = "getReputationFor(Ljava/util/UUID;Ljava/util/function/Predicate;)I",
            at = @At("HEAD"), cancellable = true
    )
    private void getReputationFor(
            UUID target, Predicate<VillageGossipType> gossipTypeFilter,
            CallbackInfoReturnable<Integer> cir) {
        VillagerGossips.Reputation playerReputation = entityReputation.get(target);
        int playerGossip = 0;
        if (playerReputation != null) {
            playerGossip = playerReputation.getValueFor(gossipType -> {
                if (gossipType == VillageGossipType.MAJOR_POSITIVE || gossipType == VillageGossipType.MINOR_POSITIVE) {
                    return false;
                }
                return gossipTypeFilter.test(gossipType);
            });
        }

        // Calculating global positive gossips
        // To make this mod more compatible with vanilla, we wouldn't change the nbt structure
        // Instead we would attach a gossip to any player, and then use his bonuses as global
        int globalPositiveGossip = 0;
        for (var entry: entityReputation.entrySet()) {
            int positiveGossip = entry.getValue().getValueFor(gossipType -> {
                if (gossipType == VillageGossipType.MAJOR_POSITIVE || gossipType == VillageGossipType.MINOR_POSITIVE) {
                    return gossipTypeFilter.test(gossipType);
                }
                return false;
            });
            if (globalPositiveGossip < positiveGossip) {
                globalPositiveGossip = positiveGossip;
            }
        }
        cir.setReturnValue(globalPositiveGossip + playerGossip);
    }

    @Inject(
            method = "startGossip",
            at = @At("TAIL")
    )
    private void startGossip(UUID target, VillageGossipType type, int value, CallbackInfo ci) {
        if (type != VillageGossipType.MAJOR_POSITIVE && type != VillageGossipType.MINOR_POSITIVE) {
            return;
        }

        var maxEntry = entityReputation.entrySet().stream().max((e1, e2) -> {
           int g1 = e1.getValue().getValueFor(
                   gossipType -> gossipType == VillageGossipType.MAJOR_POSITIVE ||
                           gossipType == VillageGossipType.MINOR_POSITIVE);
           int g2 = e2.getValue().getValueFor(
                   gossipType -> gossipType == VillageGossipType.MAJOR_POSITIVE ||
                           gossipType == VillageGossipType.MINOR_POSITIVE);
           return Integer.compare(g1, g2);
        });
        maxEntry.ifPresent(e -> {
            VillagerGossips.Reputation reputation = e.getValue();
            reputation.associatedGossip.mergeInt(type, value, (left, right) -> mergeReputation(type, left, right));
            reputation.clamp(type);
            if (reputation.isObsolete()) {
                this.entityReputation.remove(target);
            }
        });
    }
}
