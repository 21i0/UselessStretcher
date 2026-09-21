package com.sorrowmist.useless.stretcher.content.mold;

import com.sorrowmist.useless.content.recipe.AlloyFurnaceRecipeCatalog;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.WeakHashMap;

/** Session-wide inverted index. Fetch completion, cancellation and block removal never evict it. */
public final class MoldRecipeIndex {
    private static final Map<MinecraftServer, MoldRecipeIndex> SERVERS = new WeakHashMap<>();
    private final List<AlloyFurnaceRecipeCatalog.Entry> recipes;
    private final Map<Ingredient, List<Posting>> ingredients = new HashMap<>();
    private final Map<ResourceLocation, Posting> byMold = new HashMap<>();
    private final List<Posting> custom = new ArrayList<>();
    private final Map<ResourceLocation, Resolution> resolved = new HashMap<>();
    private int built;
    private RuntimeException failure;

    public MoldRecipeIndex(List<AlloyFurnaceRecipeCatalog.Entry> recipes) {
        this.recipes = recipes;
    }

    public static MoldRecipeIndex get(ServerLevel level) {
        var snapshot = AlloyFurnaceRecipeCatalog.entries(level);
        var index = SERVERS.get(level.getServer());
        if (index == null || !index.isFor(snapshot)) {
            index = new MoldRecipeIndex(snapshot);
            SERVERS.put(level.getServer(), index);
        }
        return index;
    }

    /** Release only on server shutdown; recipe reload replaces the old snapshot in get(). */
    public static void removeServer(MinecraftServer server) { SERVERS.remove(server); }
    public boolean isFor(List<AlloyFurnaceRecipeCatalog.Entry> snapshot) { return recipes == snapshot; }
    public boolean ready() { return built >= recipes.size(); }
    public boolean failed() { return failure != null; }
    public void checkHealthy() {
        if (failure != null) throw new IllegalStateException("Cannot build mold recipe index", failure);
    }
    public String progress() { return built + "/" + recipes.size(); }

    /** One recipe per unit; each distinct ingredient is expanded just once for this snapshot. */
    public void advance() {
        checkHealthy();
        try { advanceRecipe(); }
        catch (RuntimeException exception) { failure = exception; throw exception; }
    }

    private void advanceRecipe() {
        if (ready()) return;
        int ordinal = built++;
        var recipe = recipes.get(ordinal).recipe();
        if (recipe != null) for (Ingredient ingredient : recipe.molds()) {
            if (ingredient == null || ingredient.isEmpty()) continue;
            for (Posting posting : ingredients.computeIfAbsent(ingredient, this::indexIngredient)) {
                if (posting.recipes.isEmpty() || posting.recipes.getInt(posting.recipes.size() - 1) != ordinal) {
                    posting.recipes.add(ordinal);
                }
            }
        }
        if (ready()) ingredients.clear();
    }

    private List<Posting> indexIngredient(Ingredient ingredient) {
        // Custom predicates need not enumerate all accepted items. Never guess their domain.
        if (ingredient.isCustom()) {
            Posting posting = new Posting(ingredient);
            custom.add(posting);
            return List.of(posting);
        }
        ItemStack[] displayed;
        try { displayed = ingredient.getItems(); }
        catch (RuntimeException ignored) { return List.of(); }
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (ItemStack stack : displayed) {
            if (stack != null && !stack.isEmpty()) ids.add(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        }
        List<Posting> postings = new ArrayList<>(ids.size());
        for (ResourceLocation id : ids) postings.add(byMold.computeIfAbsent(id, ignored -> new Posting(null)));
        return postings;
    }

    public Query query(Collection<ResourceLocation> molds) {
        checkHealthy();
        if (!ready()) throw new IllegalStateException("Mold recipe index is not ready");
        return new Query(molds);
    }

    private static final class Posting {
        private final Ingredient ingredient;
        private final IntArrayList recipes = new IntArrayList();
        private Posting(Ingredient ingredient) { this.ingredient = ingredient; }
    }

    private final class Resolution {
        private final ResourceLocation mold;
        private final List<Posting> postings;
        private int checked;

        private Resolution(ResourceLocation mold) {
            this.mold = mold;
            postings = new ArrayList<>();
            Posting ordinary = byMold.get(mold);
            if (ordinary != null) postings.add(ordinary);
        }

        private boolean ready() { return checked >= custom.size(); }

        private void advance() {
            Posting posting = custom.get(checked++);
            if (MoldMatch.matches(posting.ingredient, mold)) postings.add(posting);
        }
    }

    public record Match(int ordinal, AlloyFurnaceRecipeCatalog.Entry entry, Set<ResourceLocation> molds) { }

    /** K-way merge reads only relevant postings, preserves catalog order, and encodes shared recipes once. */
    public final class Query {
        private final Iterator<ResourceLocation> requested;
        private final Set<ResourceLocation> cancelled = new LinkedHashSet<>();
        private final PriorityQueue<Cursor> next = new PriorityQueue<>(Comparator.comparingInt(Cursor::ordinal));
        private Resolution preparing;
        private int postingCursor;
        private boolean prepared;
        private long total;
        private long consumed;

        private Query(Collection<ResourceLocation> molds) {
            requested = new LinkedHashSet<>(molds).iterator();
        }

        public boolean prepared() { return prepared; }
        public long totalReferences() { return total; }
        public long consumedReferences() { return consumed; }
        public void cancel(ResourceLocation mold) { cancelled.add(mold); }

        /** One custom predicate or one posting-list cursor per unit, amortized across all blocks. */
        public void prepareStep() {
            if (prepared) return;
            if (preparing == null) {
                if (!requested.hasNext()) { prepared = true; return; }
                preparing = resolved.computeIfAbsent(requested.next(), Resolution::new);
                postingCursor = 0;
            }
            if (cancelled.contains(preparing.mold)) { preparing = null; return; }
            if (!preparing.ready()) { preparing.advance(); return; }
            if (postingCursor < preparing.postings.size()) {
                Posting posting = preparing.postings.get(postingCursor++);
                if (!posting.recipes.isEmpty()) {
                    next.add(new Cursor(preparing.mold, posting));
                    total += posting.recipes.size();
                }
                return;
            }
            preparing = null;
        }

        public boolean hasNext() {
            while (!next.isEmpty() && cancelled.contains(next.peek().mold)) {
                Cursor cursor = next.remove();
                total -= cursor.posting.recipes.size() - cursor.offset;
            }
            return !next.isEmpty();
        }

        public Match next() {
            if (!prepared || !hasNext()) throw new IllegalStateException("Query not ready or exhausted");
            int ordinal = next.peek().ordinal();
            Set<ResourceLocation> molds = new LinkedHashSet<>();
            do {
                Cursor cursor = next.remove();
                molds.add(cursor.mold);
                consumed++;
                if (++cursor.offset < cursor.posting.recipes.size()) next.add(cursor);
            } while (hasNext() && next.peek().ordinal() == ordinal);
            return new Match(ordinal, recipes.get(ordinal), molds);
        }
    }

    private static final class Cursor {
        private final ResourceLocation mold;
        private final Posting posting;
        private int offset;
        private Cursor(ResourceLocation mold, Posting posting) { this.mold = mold; this.posting = posting; }
        private int ordinal() { return posting.recipes.getInt(offset); }
    }
}
