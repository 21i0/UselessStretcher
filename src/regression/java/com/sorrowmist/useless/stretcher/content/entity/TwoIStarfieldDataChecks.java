package com.sorrowmist.useless.stretcher.content.entity;

import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;

/** Save/reload/revisit checks. Opt-in regression source; never included in a regular release jar. */
public final class TwoIStarfieldDataChecks {
    public static void main(String[] args) {
        run(null); // The starfield ledger does not encode registry-bound objects.
    }

    public static void run(HolderLookup.Provider registries) {
        UUID first = UUID.fromString("7817cb8d-c5d2-4a5d-b9a3-27ebcb682ccc");
        UUID second = UUID.fromString("c7bbf830-ab53-4ccf-8fcb-2375b0f05ffb");
        TwoIStarfieldData data = new TwoIStarfieldData();
        int slot = data.slotFor(first);
        check(slot == data.slotFor(first), "private area is stable");
        check(slot != data.slotFor(second), "private areas do not overlap");
        check(data.rewardsRemaining(first) == 0, "uninitialized players cannot receive reward drops");
        data.beginVisit(first, 1_000L, true);
        check(data.rewardsRemaining(first) == 8, "first visit has exactly eight rewards");
        check(data.celebrationEnd(first) == 0L && data.awaitsCourtyardEntry(first), "arriving outside gate does not begin the show");
        check(data.beginCelebration(first, 1_000L), "entry starts the armed visit");
        check(!data.beginCelebration(first, 1_020L), "standing inside cannot repeatedly restart the show");
        check(data.celebrationEnd(first) == 1_600L, "celebration lasts thirty seconds");
        for (int i = 0; i < 3; i++) data.recordRewardDrop(first);
        TwoIStarfieldData loaded = TwoIStarfieldData.load(data.save(new CompoundTag(), registries), registries);
        check(loaded.slotFor(first) == slot, "area survives world reload");
        check(loaded.rewardsRemaining(first) == 5, "reward progress survives world reload");
        check(loaded.celebrationEnd(first) == 1_600L, "celebration deadline survives world reload");
        loaded.beginVisit(first, 2_000L, false);
        check(loaded.rewardsRemaining(first) == 5, "revisit resumes only unissued rewards");
        loaded = TwoIStarfieldData.load(loaded.save(new CompoundTag(), registries), registries);
        check(loaded.awaitsCourtyardEntry(first) && loaded.celebrationEnd(first) == 0L, "waiting outside survives reload without starting rain");
        for (int i = 0; i < 20; i++) loaded.recordRewardDrop(first);
        check(loaded.rewardsRemaining(first) == 0, "extra attempts never exceed eight drops");
        loaded.beginVisit(first, 3_000L, true);
        check(loaded.rewardsRemaining(first) == 0, "another fresh doll cannot reset reward ledger");
        loaded.beginVisit(second, 3_000L, false);
        check(loaded.rewardsRemaining(second) == 0, "legacy used invitation cannot pay rewards again");
        loaded.endCelebration(first);
        check(loaded.celebrationEnd(first) == 0L, "completion is persisted without repeated messages");
        loaded = TwoIStarfieldData.load(loaded.save(new CompoundTag(), registries), registries);
        check(loaded.rewardsRemaining(first) == 0, "spent reward remains spent after reload");
        check(loaded.celebrationEnd(first) == 0L, "completed celebration does not restart");

        CompoundTag saved = loaded.save(new CompoundTag(), registries);
        ListTag returns = new ListTag();
        returns.add(returnPoint(first, 12.5D));
        returns.add(returnPoint(second, Double.NaN));
        saved.put("return_points", returns);
        loaded = TwoIStarfieldData.load(saved, registries);
        check(loaded.returnPoint(first) != null && loaded.returnPoint(first).x() == 12.5D,
                "valid return point survives reload");
        check(loaded.returnPoint(first).dimension().equals(ResourceLocation.parse("minecraft:overworld")),
                "return dimension survives reload");
        check(loaded.returnPoint(second) == null, "non-finite return coordinates are rejected");
        loaded.clearReturnPoint(first);
        check(loaded.returnPoint(first) == null, "return clears completed visit");
        check(loaded.rewardsRemaining(first) == 0, "return never resets rewards");
        System.out.println("2i starfield data checks passed (24 assertions)");
    }

    private static CompoundTag returnPoint(UUID player, double x) {
        CompoundTag point = new CompoundTag();
        point.putUUID("player", player);
        point.putString("dimension", "minecraft:overworld");
        point.putDouble("x", x);
        point.putDouble("y", 64.0D);
        point.putDouble("z", -7.5D);
        return point;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
