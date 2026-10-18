package com.sorrowmist.useless.stretcher.content.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Small typed item component: reclaiming a doll must never reset its relationship limits. */
public record TwoIDollMemory(Cooldowns cooldowns, Interaction interaction, long lastCompanionSample) {
    public TwoIDollMemory(Cooldowns cooldowns, Interaction interaction) {
        this(cooldowns, interaction, -1L);
    }

    public static TwoIDollMemory empty() {
        return new TwoIDollMemory(new Cooldowns(0, 0, 0, 0, 0, 0, -1, false), Interaction.empty());
    }
    public static final Codec<TwoIDollMemory> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Cooldowns.CODEC.fieldOf("cooldowns").forGetter(TwoIDollMemory::cooldowns),
            Interaction.CODEC.fieldOf("interaction").forGetter(TwoIDollMemory::interaction),
            Codec.LONG.optionalFieldOf("last_companion_sample", -1L).forGetter(TwoIDollMemory::lastCompanionSample)
    ).apply(instance, TwoIDollMemory::new));

    public record Cooldowns(int pat, int gift, int talk, int prank, int timeStop, int panic,
                            int lastPrank, boolean panicDragonSpawned) {
        public static final Codec<Cooldowns> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(0, 24000).fieldOf("pat").forGetter(Cooldowns::pat),
                Codec.intRange(0, 24000).fieldOf("gift").forGetter(Cooldowns::gift),
                Codec.intRange(0, 24000).fieldOf("talk").forGetter(Cooldowns::talk),
                Codec.intRange(0, 24000).fieldOf("prank").forGetter(Cooldowns::prank),
                Codec.intRange(0, 24000).fieldOf("time_stop").forGetter(Cooldowns::timeStop),
                Codec.intRange(0, 24000).fieldOf("panic").forGetter(Cooldowns::panic),
                Codec.intRange(-1, 11).fieldOf("last_prank").forGetter(Cooldowns::lastPrank),
                Codec.BOOL.fieldOf("panic_dragon_spawned").forGetter(Cooldowns::panicDragonSpawned)
        ).apply(instance, Cooldowns::new));
    }

    public record Interaction(long day, int pats, int talks, int stories, int companionship,
                              int companionTicks, int lastChat, String lastGift, int repeatedGift, int gifts) {
        public Interaction(long day, int pats, int talks, int stories, int companionship,
                           int companionTicks, int lastChat, String lastGift, int repeatedGift) {
            this(day, pats, talks, stories, companionship, companionTicks, lastChat, lastGift, repeatedGift, 0);
        }
        public static final Codec<Interaction> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("day").forGetter(Interaction::day),
                Codec.intRange(0, 2).fieldOf("pats").forGetter(Interaction::pats),
                Codec.intRange(0, 2).fieldOf("talks").forGetter(Interaction::talks),
                Codec.intRange(0, 3).fieldOf("stories").forGetter(Interaction::stories),
                Codec.intRange(0, 2).fieldOf("companionship").forGetter(Interaction::companionship),
                Codec.intRange(0, 12000).fieldOf("companion_ticks").forGetter(Interaction::companionTicks),
                Codec.intRange(-1, 41).fieldOf("last_chat").forGetter(Interaction::lastChat),
                Codec.STRING.fieldOf("last_gift").forGetter(Interaction::lastGift),
                Codec.intRange(0, 4).fieldOf("repeated_gift").forGetter(Interaction::repeatedGift),
                Codec.intRange(0, 1).optionalFieldOf("gifts", 0).forGetter(Interaction::gifts)
        ).apply(instance, Interaction::new));
        public static Interaction empty() { return new Interaction(-1L, 0, 0, 0, 0, 0, -1, "", 0); }

        public Interaction forDay(long gameTime) {
            long today = Math.max(0, gameTime / 24000L);
            return today == day ? this : new Interaction(today, 0, 0, 0, 0, 0, lastChat, "", 0);
        }

    }
}
