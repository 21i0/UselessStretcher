package offline;
import com.google.gson.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Real Sponge injection preparation/application against original class bytes, no game launch. */
public final class SpongeMergeMain {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) throw new IllegalArgumentException("config-resource new-result.json target...");
        Path result = Path.of(args[1]);
        if (Files.exists(result)) throw new IllegalArgumentException("Refusing to overwrite result");
        MixinBootstrap.init();
        MixinEnvironment.getDefaultEnvironment().setSide(MixinEnvironment.Side.CLIENT);
        for (String config : args[0].split(",")) Mixins.addConfiguration(config);
        Method phase = MixinEnvironment.class.getDeclaredMethod("gotoPhase", MixinEnvironment.Phase.class);
        phase.setAccessible(true); phase.invoke(null, MixinEnvironment.Phase.DEFAULT);
        Constructor<?> constructor = Class.forName("org.spongepowered.asm.mixin.transformer.MixinTransformer").getDeclaredConstructor();
        constructor.setAccessible(true);
        IMixinTransformer transformer = (IMixinTransformer) constructor.newInstance();
        if (Boolean.getBoolean("offline.mixinextras")) {
            Class.forName("com.llamalad7.mixinextras.MixinExtrasBootstrap").getMethod("init").invoke(null);
        }
        JsonArray rows = new JsonArray(); int passed = 0, failed = 0;
        for (int i = 2; i < args.length; i++) {
            String target = args[i]; JsonObject row = new JsonObject(); row.addProperty("target", target);
            try (InputStream input = SpongeMergeMain.class.getClassLoader().getResourceAsStream(target.replace('.', '/') + ".class")) {
                if (input == null) throw new ClassNotFoundException(target);
                byte[] original = input.readAllBytes(); row.addProperty("inputSha256", sha(original));
                byte[] transformed = transformer.transformClassBytes(target, target, original);
                if (Arrays.equals(original, transformed)) throw new AssertionError("No Sponge transform applied");
                ClassNode node = new ClassNode(); new ClassReader(transformed).accept(node, 0);
                ClassNode before = new ClassNode(); new ClassReader(original).accept(before, 0);
                row.add("ingredientSupplierRedirectEvidence", ingredientEvidence(before, node));
                List<String> handlers = node.methods.stream().filter(m -> m.name.contains("stretcher") || m.name.contains("uselessStretcher"))
                        .map(m -> m.name + m.desc).toList();
                if (handlers.isEmpty()) throw new AssertionError("No merged stretcher handler methods");
                JsonArray merged = new JsonArray(); handlers.forEach(merged::add); row.add("mergedHandlers", merged);
                row.addProperty("outputSha256",sha(transformed));
                Path bytes = result.toAbsolutePath().getParent().resolve("transformed").resolve(target.replace('.', '/') + ".class");
                Files.createDirectories(bytes.getParent()); Files.write(bytes,transformed);
                row.addProperty("transformedClass",bytes.toString()); row.addProperty("status","PASS"); passed++;
            } catch (Throwable error) {
                row.addProperty("status","FAIL"); row.addProperty("failure",error.toString());
                StringWriter trace = new StringWriter(); error.printStackTrace(new PrintWriter(trace)); row.addProperty("trace",trace.toString());
                error.printStackTrace(System.out); failed++;
            }
            rows.add(row);
        }
        JsonObject report = new JsonObject(); report.addProperty("status",failed == 0 ? "PASS" : "FAIL");
        report.addProperty("gameLaunched",false); report.addProperty("modLauncherUsed",false);
        report.addProperty("transformer",transformer.getClass().getName()); report.addProperty("mixinVersion",MixinEnvironment.getCurrentEnvironment().getVersion());
        report.addProperty("spongeCodeSource",MixinBootstrap.class.getProtectionDomain().getCodeSource().getLocation().toString());
        report.addProperty("checksFound",args.length-2); report.addProperty("checksPassed",passed); report.addProperty("checksFailed",failed);
        JsonArray applied = new JsonArray(); SpongeOfflineService.APPLIED.forEach(applied::add); report.add("auditApplied",applied); report.add("targets",rows);
        Files.createDirectories(result.toAbsolutePath().getParent()); Files.writeString(result,new GsonBuilder().setPrettyPrinting().create().toJson(report));
        System.out.println("REAL SPONGE MERGE: " + passed + " passed, " + failed + " failed");
        if (failed != 0) System.exit(1);
    }
    private static String sha(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static JsonArray ingredientEvidence(ClassNode before, ClassNode after) {
        JsonArray result = new JsonArray();
        if (!before.name.equals("mezz/jei/library/util/IngredientSupplierHelper")) return result;
        for (MethodNode original : before.methods) {
            if (!original.name.equals("getIngredientSupplier")) continue;
            MethodNode merged = after.methods.stream().filter(m -> m.name.equals(original.name) && m.desc.equals(original.desc)).findFirst().orElseThrow();
            JsonObject row = new JsonObject(); row.addProperty("method", original.name + original.desc);
            int nativeBefore = count(original,"mezz/jei/api/recipe/category/IRecipeCategory","setRecipe");
            int nativeAfter = count(merged,"mezz/jei/api/recipe/category/IRecipeCategory","setRecipe");
            int redirects = 0, coveredRedirects = 0;
            for (AbstractInsnNode instruction : merged.instructions) {
                if (instruction instanceof MethodInsnNode call && call.owner.equals(after.name) && call.name.contains("stretcher$timeCategory")) {
                    redirects++;
                    int position = merged.instructions.indexOf(instruction);
                    if (merged.tryCatchBlocks.stream().anyMatch(t -> merged.instructions.indexOf(t.start) <= position && position < merged.instructions.indexOf(t.end))) coveredRedirects++;
                }
            }
            row.addProperty("nativeSetRecipeBefore",nativeBefore); row.addProperty("nativeSetRecipeAfter",nativeAfter);
            row.addProperty("redirectCallsAfter",redirects); row.addProperty("redirectCallsInsideNativeTry",coveredRedirects);
            List<String> oldCatches = original.tryCatchBlocks.stream().map(t -> String.valueOf(t.type)).toList();
            List<String> newCatches = merged.tryCatchBlocks.stream().map(t -> String.valueOf(t.type)).toList();
            JsonArray catches = new JsonArray(); newCatches.forEach(catches::add); row.add("catchTypes",catches);
            if (!oldCatches.equals(newCatches)) throw new AssertionError("Native catch types changed in " + original.desc);
            if (nativeBefore > 0 && (nativeAfter != 0 || redirects != nativeBefore || coveredRedirects != redirects))
                throw new AssertionError("Redirect/catch evidence mismatch in " + original.desc);
            result.add(row);
        }
        return result;
    }
    private static int count(MethodNode method, String owner, String name) {
        int result = 0;
        for (AbstractInsnNode instruction : method.instructions) if (instruction instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) result++;
        return result;
    }
}
