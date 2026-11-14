package com.twoi.stretcher.verification;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.sorrowmist.useless.stretcher.client.StaffScreen;
import com.sorrowmist.useless.stretcher.client.StretcherKeyBindings;
import com.sorrowmist.useless.stretcher.client.WondrousStaffConfigScreen;
import com.sorrowmist.useless.stretcher.init.ModItems;
import com.sorrowmist.useless.stretcher.init.StretcherComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Stream;

/** Private diagnostic mod. Never include this namespace in a production artifact. */
@Mod(StaffCompatSmoke.ID)
@EventBusSubscriber(modid = StaffCompatSmoke.ID, value = Dist.CLIENT)
public final class StaffCompatSmoke {
    public static final String ID = "stretcher_staff_smoke";
    private static final AtomicReference<Throwable> SERVER_FAILURE = new AtomicReference<>();
    private static final AtomicReference<String> SERVER_ACK = new AtomicReference<>();
    private static final java.util.concurrent.atomic.AtomicInteger SERVER_SELECTED_SLOT = new java.util.concurrent.atomic.AtomicInteger(-1);
    private static final JsonArray RESULTS = new JsonArray();
    private static final ConcurrentLinkedQueue<Delayed> SERVER_ACTIONS = new ConcurrentLinkedQueue<>();
    private static final String STAFF = "useless_stretcher:wondrous_staff";
    private static final String BASE = "useless_mod:endless_beaf_item";
    // These cases exercise base/stretcher mixed hands as well as main-hand precedence.
    private static final String[][] CASES = {
            {STAFF, "minecraft:air"}, {"minecraft:stick", STAFF},
            {STAFF, STAFF}, {BASE, STAFF}, {STAFF, BASE},
            {BASE, "minecraft:air"}, {"minecraft:stick", BASE},
            {"minecraft:stick", "minecraft:air"}
    };
    private static JsonObject contract;
    private static Path evidence;
    private static int ticks, stage, age, testCase, journeys;
    private static UUID playerId;
    private static boolean requested, finished, identityDrift;
    private static InteractionHand staffHand;
    private static boolean baseExpected;
    private static Object heldScreen;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post ignored) {
        if (!Boolean.getBoolean("stretcher.staff_smoke") || finished) return;
        Minecraft client = Minecraft.getInstance();
        try {
            check(++ticks < 7200, "client fixture tick timeout at stage " + stage);
            if (SERVER_FAILURE.get() != null) throw new AssertionError("server assertion failed", SERVER_FAILURE.get());
            if (ticks % 100 == 0) LogUtils.getLogger().info("STAFF COMPAT PROGRESS stage={} case={} age={} dimension={} screen={}",
                    stage, testCase, age, client.level == null ? "none" : client.level.dimension().location(),
                    client.screen == null ? "none" : client.screen.getClass().getName());
            if (client.getOverlay() != null) return;
            if (contract == null) loadContract(client);
            if (!requested) {
                if (!(client.screen instanceof TitleScreen)) return;
                requested = true;
                client.options.pauseOnLostFocus = false;
                client.options.guiScale().set(1);
                client.resizeDisplay();
                var settings = new LevelSettings("Staff G X compatibility", GameType.CREATIVE,
                        false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
                client.createWorldOpenFlows().createFreshLevel("staff-compat-" + System.currentTimeMillis(), settings,
                        new WorldOptions(6111401L, false, false), access -> access.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), client.screen);
                return;
            }
            if (client.player == null || client.level == null || client.getSingleplayerServer() == null) return;
            playerId = client.player.getUUID();
            if (stage == 0) {
                if (!jei(client)) return;
                record("JEI_RUNTIME_AND_NATIVE_ALLOY_CATEGORY", "PASS", "Actual runtime, category lookup, nonempty recipes and staff ingredient");
                stage = 1;
            }
            switch (stage) {
                case 1 -> {
                    client.setScreen(null);
                    install(client, CASES[testCase][0], CASES[testCase][1], "case-" + testCase);
                    stage = 2; age = 0;
                }
                case 2 -> {
                    if (!readyInventory(client, CASES[testCase][0], CASES[testCase][1], "case-" + testCase)) return;
                    staffHand = CASES[testCase][0].equals(STAFF) ? InteractionHand.MAIN_HAND
                            : CASES[testCase][1].equals(STAFF) ? InteractionHand.OFF_HAND : null;
                    baseExpected = CASES[testCase][0].equals(STAFF) || CASES[testCase][0].equals(BASE)
                            || CASES[testCase][1].equals(STAFF) || CASES[testCase][1].equals(BASE);
                    key(GLFW.GLFW_KEY_G);
                    if (baseExpected) {
                        check(client.screen != null && client.screen.getClass().getName().equals("com.sorrowmist.useless.client.gui.ModeWheelScreen"), "G opens native wheel case=" + testCase);
                        String expected = CASES[testCase][0].equals(STAFF) || CASES[testCase][0].equals(BASE)
                                ? CASES[testCase][0] : CASES[testCase][1];
                        check(id((ItemStack) field(client.screen, "targetItem")).equals(expected), "native G target main-hand precedence");
                        stage = 3; age = 0;
                    } else { check(client.screen == null, "G leaves unrelated hands alone"); stage = 5; }
                }
                case 3 -> {
                    check(++age < 160, "native G layout response timeout");
                    if ((Boolean) field(client.screen, "awaitingLayout")) return;
                    // Send the real base packet through the installed channel, not direct handle().
                    Object enumValue = Class.forName("com.sorrowmist.useless.network.ModeTogglePacket$ModeType")
                            .getField("AE_STORAGE_PRIORITY").get(null);
                    Object payload = Class.forName("com.sorrowmist.useless.network.ModeTogglePacket")
                            .getConstructor(enumValue.getClass(), boolean.class).newInstance(enumValue, true);
                    PacketDistributor.sendToServer((CustomPacketPayload) payload);
                    server(client, "base-packet-" + testCase, player -> {
                        Object entry = ((Optional<?>) invokeStatic("com.sorrowmist.useless.utils.UselessItemUtils", "findTargetToolInHands",
                                new Class<?>[]{net.minecraft.world.entity.player.Player.class}, player)).orElseThrow();
                        ItemStack stack = (ItemStack) entry.getClass().getMethod("getKey").invoke(entry);
                        check(booleanComponent(stack, "com.sorrowmist.useless.core.component.UComponents", "AEStoragePriorityComponent"), "base packet reached server target");
                    });
                    stage = 4; age = 0;
                }
                case 4 -> {
                    if (!ack("base-packet-" + testCase)) return;
                    key(GLFW.GLFW_KEY_G);
                    check(client.screen == null, "second G closes wheel");
                    record("G_CASE_" + testCase, "PASS", CASES[testCase][0] + " | " + CASES[testCase][1] + "; real layout request/response and mode packet");
                    stage = 5;
                }
                case 5 -> {
                    key(GLFW.GLFW_KEY_X);
                    if (staffHand == null) {
                        check(client.screen == null, "X does not open for native-only or unrelated item");
                        nextCase(); return;
                    }
                    check(client.screen instanceof StaffScreen, "X opens staff screen");
                    check(StaffScreen.staffHand(client.screen) == staffHand, "X target hand");
                    ((StaffScreen) client.screen).navigate(StaffScreen.Tab.MAIN);
                    check(client.screen instanceof WondrousStaffConfigScreen, "main tab exists");
                    // Exercise the production button action, including its actual sendSelection().
                    Method select = client.screen.getClass().getDeclaredMethod("selectSpeed", int.class);
                    select.setAccessible(true); select.invoke(client.screen, 32);
                    server(client, "staff-packet-" + testCase, player -> {
                        check(player.getItemInHand(staffHand).getOrDefault(StretcherComponents.WONDROUS_STAFF_SPEED.get(), 0) == 32,
                                "production screen packet reached correct server hand");
                        InteractionHand other = staffHand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                        if (player.getItemInHand(other).is(ModItems.WONDROUS_STAFF.get()))
                            check(player.getItemInHand(other).getOrDefault(StretcherComponents.WONDROUS_STAFF_SPEED.get(), 0) == 4, "other staff unmodified");
                    });
                    stage = 6; age = 0;
                }
                case 6 -> {
                    if (!ack("staff-packet-" + testCase)) return;
                    record("X_CASE_" + testCase, "PASS", "real input event + production selectSpeed + server component; hand=" + staffHand);
                    nextCase();
                }
                case 10 -> {
                    // Test switching away from an open UI; non-staff items must not gain staff components.
                    client.setScreen(null); install(client, STAFF, STAFF, "switch-before"); stage = 11; age = 0;
                }
                case 11 -> {
                    if (!readyInventory(client, STAFF, STAFF, "switch-before")) return;
                    key(GLFW.GLFW_KEY_X); check(client.screen instanceof StaffScreen, "open before switching hands");
                    ((StaffScreen) client.screen).navigate(StaffScreen.Tab.MAIN); heldScreen = client.screen;
                    server(client, "swap-open", player -> {
                        ItemStack a = player.getMainHandItem(); ItemStack b = player.getOffhandItem();
                        player.setItemInHand(InteractionHand.MAIN_HAND, b); player.setItemInHand(InteractionHand.OFF_HAND, a);
                        player.inventoryMenu.broadcastChanges();
                    }); stage = 12; age = 0;
                }
                case 12 -> {
                    if (!ack("swap-open") || client.player.getMainHandItem().getOrDefault(StretcherComponents.WONDROUS_STAFF_SPEED.get(), 0) != 4) return;
                    Method select = heldScreen.getClass().getDeclaredMethod("selectSpeed", int.class);
                    select.setAccessible(true); select.invoke(heldScreen, 64);
                    server(client, "swap-observation", player -> {
                        int main = player.getMainHandItem().getOrDefault(StretcherComponents.WONDROUS_STAFF_SPEED.get(), 0);
                        int off = player.getOffhandItem().getOrDefault(StretcherComponents.WONDROUS_STAFF_SPEED.get(), 0);
                        identityDrift = main == 64 && off == 2;
                        check((main == 64 && off == 2) || (main == 4 && off == 64) || (main == 4 && off == 2),
                                "unexpected duplicate/multihand write after live swap main=" + main + " off=" + off);
                    }); stage = 13; age = 0;
                }
                case 13 -> {
                    if (!ack("swap-observation")) return;
                    record("OPEN_UI_LIVE_HAND_SWAP_IDENTITY", identityDrift ? "OBSERVED_FIXED_HAND_IDENTITY_DRIFT" : "NO_DRIFT", "Separate release decision required; ordinary re-open tests do not cover this race");
                    install(client, "minecraft:stick", "minecraft:air", "switch-away"); stage = 14; age = 0;
                }
                case 14 -> {
                    if (!readyInventory(client, "minecraft:stick", "minecraft:air", "switch-away")) return;
                    Method select = heldScreen.getClass().getDeclaredMethod("selectSpeed", int.class);
                    select.setAccessible(true); select.invoke(heldScreen, 128);
                    server(client, "nonstaff-guard", player -> check(!player.getMainHandItem().has(StretcherComponents.WONDROUS_STAFF_SPEED.get()), "nonstaff item stays unmodified"));
                    stage = 15; age = 0;
                }
                case 15 -> {
                    if (!ack("nonstaff-guard")) return;
                    record("OPEN_UI_SWITCH_TO_NONSTAFF", "PASS", "production action + server guard retain unrelated item");
                    client.setScreen(null); install(client, BASE, STAFF, "f-before"); stage = 30; age = 0;
                }
                case 30 -> {
                    if (!readyInventory(client, BASE, STAFF, "f-before")) return;
                    // Vanilla's keybinding click path sends SWAP_ITEM_WITH_OFFHAND on the next client tick.
                    net.minecraft.client.KeyMapping.click(client.options.keySwapOffhand.getKey());
                    stage = 31; age = 0;
                }
                case 31 -> {
                    check(++age < 160, "vanilla F swap sync timeout");
                    if (!id(client.player.getMainHandItem()).equals(STAFF) || !id(client.player.getOffhandItem()).equals(BASE)) return;
                    key(GLFW.GLFW_KEY_X); check(client.screen instanceof StaffScreen, "X after actual F swap");
                    check(StaffScreen.staffHand(client.screen) == InteractionHand.MAIN_HAND, "F swap reopened X chooses current main hand");
                    ((StaffScreen) client.screen).navigate(StaffScreen.Tab.MAIN);
                    Method select = client.screen.getClass().getDeclaredMethod("selectSpeed", int.class);
                    select.setAccessible(true); select.invoke(client.screen, 32);
                    server(client, "f-network", player -> check(player.getMainHandItem().getOrDefault(StretcherComponents.WONDROUS_STAFF_SPEED.get(), 0) == 32,
                            "server receives current main-hand edit after real F swap"));
                    stage = 32; age = 0;
                }
                case 32 -> {
                    if (!ack("f-network")) return;
                    record("CLOSE_F_SWAP_REOPEN", "PASS", "vanilla keymapping click, server swap inventory sync, production X event and speed action");
                    client.setScreen(null);
                    server(client, "hotbar-before", player -> {
                        player.getInventory().clearContent(); player.getInventory().selected = 0;
                        player.getInventory().setItem(0, item(BASE));
                        player.getInventory().setItem(1, item(STAFF));
                        player.inventoryMenu.broadcastChanges();
                    }); stage = 40; age = 0;
                }
                case 40 -> {
                    if (!readyInventory(client, BASE, "minecraft:air", "hotbar-before")) return;
                    if (!id(client.player.getInventory().getItem(1)).equals(STAFF)) return;
                    net.minecraft.client.KeyMapping.click(client.options.keyHotbarSlots[1].getKey());
                    stage = 41; age = 0;
                }
                case 41 -> {
                    check(++age < 160, "vanilla hotbar change timeout");
                    if (client.player.getInventory().selected != 1 || !id(client.player.getMainHandItem()).equals(STAFF)) return;
                    // A real user cannot click the not-yet-rendered X UI in this same Post.
                    // Wait for the ordinary carried-item packet to reach the actual server first.
                    server(client, "hotbar-selected", player -> SERVER_SELECTED_SLOT.set(player.getInventory().selected));
                    stage = 43; age = 0;
                }
                case 43 -> {
                    check(++age < 160, "server selected-slot acknowledgement timeout");
                    if (!ack("hotbar-selected")) return;
                    if (SERVER_SELECTED_SLOT.get() != 1) {
                        server(client, "hotbar-selected", player -> SERVER_SELECTED_SLOT.set(player.getInventory().selected));
                        return;
                    }
                    key(GLFW.GLFW_KEY_X); check(client.screen instanceof StaffScreen, "X after actual hotbar change");
                    check(StaffScreen.staffHand(client.screen) == InteractionHand.MAIN_HAND, "hotbar reopened X chooses current hand");
                    ((StaffScreen) client.screen).navigate(StaffScreen.Tab.MAIN);
                    Method select = client.screen.getClass().getDeclaredMethod("selectSpeed", int.class);
                    select.setAccessible(true); select.invoke(client.screen, 32);
                    server(client, "hotbar-network", player -> {
                        check(player.getInventory().selected == 1, "vanilla carried item change reaches server");
                        check(player.getMainHandItem().getOrDefault(StretcherComponents.WONDROUS_STAFF_SPEED.get(), 0) == 32,
                                "server receives edit of selected hotbar staff");
                        check(!player.getInventory().getItem(0).has(StretcherComponents.WONDROUS_STAFF_SPEED.get()), "previous hotbar native item stays unmodified");
                    }); stage = 42; age = 0;
                }
                case 42 -> {
                    if (!ack("hotbar-network")) return;
                    record("CLOSE_HOTBAR_CHANGE_REOPEN", "PASS", "vanilla hotbar keymapping + carried item packet, production X event and speed action");
                    client.setScreen(null);
                    // Reset selected slot locally as part of the next independent fixed-layout case.
                    client.player.getInventory().selected = 0;
                    install(client, STAFF, BASE, "journey-inventory-0"); stage = 20; age = 0;
                }
                case 20 -> {
                    if (!readyInventory(client, STAFF, BASE, "journey-inventory-" + journeys)) return;
                    var target = journeys == 0 ? Level.NETHER : Level.OVERWORLD;
                    server(client, "journey-" + journeys, player -> {
                        var destination = player.getServer().getLevel(target);
                        check(destination != null, "destination exists");
                        player.teleportTo(destination, 0.5, 100.0, 0.5, Set.of(), 0, 0);
                    }); stage = 21; age = 0;
                }
                case 21 -> {
                    var target = journeys == 0 ? Level.NETHER : Level.OVERWORLD;
                    check(++age < 600, "dimension arrival timeout");
                    if (!ack("journey-" + journeys) || !client.level.dimension().equals(target)) return;
                    client.setScreen(null); key(GLFW.GLFW_KEY_G);
                    check(client.screen != null && client.screen.getClass().getName().endsWith("ModeWheelScreen"), "G reopens after dimension transfer");
                    stage = 22; age = 0;
                }
                case 22 -> {
                    check(++age < 160, "G layout after dimension transfer timeout");
                    if ((Boolean) field(client.screen, "awaitingLayout")) return;
                    key(GLFW.GLFW_KEY_G); check(client.screen == null, "G closes after transfer");
                    key(GLFW.GLFW_KEY_X); check(client.screen instanceof StaffScreen, "X reopens after transfer");
                    ((StaffScreen) client.screen).navigate(StaffScreen.Tab.MAIN);
                    Method select = client.screen.getClass().getDeclaredMethod("selectSpeed", int.class);
                    final int expectedSpeed = journeys == 0 ? 16 : 64;
                    select.setAccessible(true); select.invoke(client.screen, expectedSpeed);
                    server(client, "journey-network-" + journeys, player -> {
                        check(player.getMainHandItem().getOrDefault(StretcherComponents.WONDROUS_STAFF_SPEED.get(), 0) == expectedSpeed,
                                "X packet after transfer reaches server");
                    }); stage = 23; age = 0;
                }
                case 23 -> {
                    if (!ack("journey-network-" + journeys)) return;
                    record("DIMENSION_UI_ROUND_" + journeys, "PASS", client.level.dimension().location() + "; native layout ACK and changed staff speed ACK=" + (journeys == 0 ? 16 : 64));
                    client.setScreen(null); journeys++;
                    if (journeys == 1) {
                        // This is a new observation ACK, never the stale initial inventory ACK.
                        // Do not reinstall or reset items between the two actual dimension trips.
                        server(client, "journey-inventory-1", player -> {
                            check(id(player.getMainHandItem()).equals(STAFF), "return journey preserves actual main-hand staff");
                            check(id(player.getOffhandItem()).equals(BASE), "return journey preserves actual offhand native staff");
                        });
                        stage = 20; age = 0; return;
                    }
                    stage = 24;
                }
                case 24 -> {
                    if (!jei(client)) return;
                    finish(client, "PASS_WITH_SEPARATE_IDENTITY_OBSERVATION", null);
                }
                default -> { }
            }
        } catch (Throwable failure) {
            finish(client, "FAIL", failure);
        }
    }

    private static void loadContract(Minecraft client) throws Exception {
        Path path = Path.of(System.getProperty("stretcher.staff_smoke.contract"));
        contract = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        check(contract.get("execute").getAsBoolean(), "external execution contract required");
        evidence = Path.of(contract.get("evidence").getAsString()).toAbsolutePath();
        check(evidence.startsWith(Path.of("D:/AI")) && !Files.exists(evidence), "new D-drive evidence only");
        Files.createDirectories(evidence.getParent());
        for (var entry : contract.getAsJsonObject("modVersions").entrySet()) {
            String actual = ModList.get().getModContainerById(entry.getKey()).orElseThrow().getModInfo().getVersion().toString();
            check(actual.equals(entry.getValue().getAsString()), "mod version mismatch " + entry.getKey() + " actual=" + actual);
        }
        check(client.level == null, "fixture must create a fresh isolated world");
    }

    private static boolean jei(Minecraft client) throws Exception {
        Object runtime = ((Optional<?>) invokeStatic("mezz.jei.common.Internal", "getOptionalJeiRuntime", new Class<?>[0])).orElse(null);
        if (runtime == null) return false;
        Object baseRuntime = invokeStatic("com.sorrowmist.useless.compat.jei.JEIPlugin", "getRuntime", new Class<?>[0]);
        if (baseRuntime == null) return false;
        check(baseRuntime == runtime, "base JEI plugin attached to actual current runtime");
        if (!(Boolean) invokeStatic("com.sorrowmist.useless.stretcher.client.CatalogJeiPublication", "ready", new Class<?>[0])) return false;
        Class<?> runtimeApi = Class.forName("mezz.jei.api.runtime.IJeiRuntime");
        Object manager = runtimeApi.getMethod("getRecipeManager").invoke(runtime);
        Object recipeType = Class.forName("com.sorrowmist.useless.compat.jei.AdvancedAlloyFurnaceRecipeCategory").getField("TYPE").get(null);
        Object lookup = Class.forName("mezz.jei.api.recipe.IRecipeManager").getMethod("createRecipeLookup", Class.forName("mezz.jei.api.recipe.RecipeType")).invoke(manager, recipeType);
        try (Stream<?> recipes = (Stream<?>) Class.forName("mezz.jei.api.recipe.IRecipeLookup").getMethod("get").invoke(lookup)) {
            check(recipes.findAny().isPresent(), "native advanced alloy category has published recipes");
        }
        Object type = Class.forName("mezz.jei.api.constants.VanillaTypes").getField("ITEM_STACK").get(null);
        Object filter = runtimeApi.getMethod("getIngredientFilter").invoke(runtime);
        Class<?> filterApi = Class.forName("mezz.jei.api.runtime.IIngredientFilter");
        filterApi.getMethod("setFilterText", String.class).invoke(filter, "");
        var ingredients = (java.util.List<?>) filterApi.getMethod("getFilteredIngredients", Class.forName("mezz.jei.api.ingredients.IIngredientType")).invoke(filter, type);
        check(ingredients.stream().anyMatch(value -> value instanceof ItemStack stack && stack.is(ModItems.WONDROUS_STAFF.get())), "staff exists in actual JEI filtered ingredients");
        return true;
    }

    private static void install(Minecraft client, String main, String off, String ack) {
        server(client, ack, player -> {
            player.setGameMode(GameType.CREATIVE);
            player.getInventory().clearContent(); player.getInventory().selected = 0;
            ItemStack a = item(main), b = item(off);
            if (a.is(ModItems.WONDROUS_STAFF.get())) a.set(StretcherComponents.WONDROUS_STAFF_SPEED.get(), 2);
            if (b.is(ModItems.WONDROUS_STAFF.get())) b.set(StretcherComponents.WONDROUS_STAFF_SPEED.get(), 4);
            player.setItemInHand(InteractionHand.MAIN_HAND, a); player.setItemInHand(InteractionHand.OFF_HAND, b);
            player.inventoryMenu.broadcastChanges();
        });
    }

    private static boolean readyInventory(Minecraft client, String main, String off, String expectedAck) {
        check(++age < 160, "inventory sync timeout " + expectedAck);
        client.player.getInventory().selected = 0;
        return ack(expectedAck) && id(client.player.getMainHandItem()).equals(main) && id(client.player.getOffhandItem()).equals(off);
    }

    private static void nextCase() {
        Minecraft.getInstance().setScreen(null); age = 0;
        testCase++; stage = testCase < CASES.length ? 1 : 10;
    }

    private static void key(int key) {
        NeoForge.EVENT_BUS.post(new InputEvent.Key(key, 0, GLFW.GLFW_PRESS, 0));
        NeoForge.EVENT_BUS.post(new InputEvent.Key(key, 0, GLFW.GLFW_RELEASE, 0));
        // The test only injects the actual event; it never directly constructs either UI.
    }

    private static void server(Minecraft client, String label, CheckedPlayer action) {
        SERVER_ACK.set(null);
        client.getSingleplayerServer().execute(() -> {
            // Integrated local network delivery is asynchronous. Give the real packet handler
            // three server ticks to execute before checking its effect; never call handle().
            SERVER_ACTIONS.add(new Delayed(client.getSingleplayerServer().getTickCount() + 3, label, action));
        });
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean("stretcher.staff_smoke") || finished) return;
        while (SERVER_ACTIONS.peek() != null && SERVER_ACTIONS.peek().tick <= event.getServer().getTickCount()) {
            Delayed delayed = SERVER_ACTIONS.remove();
            try {
                ServerPlayer player = event.getServer().getPlayerList().getPlayer(playerId);
                check(player != null, "real server player exists"); delayed.action.accept(player);
                SERVER_ACK.set(delayed.label);
            } catch (Throwable error) { SERVER_FAILURE.set(error); LogUtils.getLogger().error("STAFF COMPAT SERVER FAILED {}", delayed.label, error); }
        }
    }

    private static boolean ack(String label) { return label.equals(SERVER_ACK.get()); }
    private static ItemStack item(String id) { return id.equals("minecraft:air") ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id))); }
    private static String id(ItemStack stack) { return stack.isEmpty() ? "minecraft:air" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(); }
    private static Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }
    private static Object invokeStatic(String name, String method, Class<?>[] types, Object... args) throws Exception {
        return Class.forName(name).getMethod(method, types).invoke(null, args);
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean booleanComponent(ItemStack stack, String owner, String name) throws Exception {
        Object deferred = Class.forName(owner).getField(name).get(null);
        Object component = deferred.getClass().getMethod("get").invoke(deferred);
        return (Boolean) stack.getOrDefault((net.minecraft.core.component.DataComponentType) component, false);
    }
    private static synchronized void record(String name, String status, String detail) {
        JsonObject row = new JsonObject(); row.addProperty("name", name); row.addProperty("status", status); row.addProperty("detail", detail); RESULTS.add(row);
        LogUtils.getLogger().info("STAFF COMPAT {} {} {}", status, name, detail);
    }
    private static void finish(Minecraft client, String status, Throwable failure) {
        if (finished) return; finished = true;
        if (failure != null) LogUtils.getLogger().error("STAFF COMPAT CHECKS FAILED", failure);
        try {
            JsonObject result = new JsonObject(); result.addProperty("status", status); result.addProperty("fixtureTicks", ticks);
            result.addProperty("integratedServer", true); result.addProperty("productionBytesModified", false);
            result.addProperty("identityDriftObserved", identityDrift); result.add("checks", RESULTS);
            if (contract != null) result.add("contract", contract);
            if (failure != null) result.addProperty("failure", failure.toString());
            if (evidence != null) Files.writeString(evidence, new GsonBuilder().setPrettyPrinting().create().toJson(result) + "\n", java.nio.file.StandardOpenOption.CREATE_NEW);
        } catch (Throwable outputFailure) { LogUtils.getLogger().error("STAFF COMPAT EVIDENCE WRITE FAILED", outputFailure); }
        LogUtils.getLogger().info("STAFF COMPAT CHECKS COMPLETED status={} checks={}", status, RESULTS.size());
        client.stop();
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    @FunctionalInterface private interface CheckedPlayer { void accept(ServerPlayer player) throws Exception; }
    private record Delayed(int tick, String label, CheckedPlayer action) { }
}
