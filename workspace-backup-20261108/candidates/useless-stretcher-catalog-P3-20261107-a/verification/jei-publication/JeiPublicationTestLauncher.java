import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import java.io.PrintWriter;

/** Standalone Java/JUnit only: no ModLauncher, client, server or world. */
public final class JeiPublicationTestLauncher {
    public static void main(String[] args) throws Exception {
        printClassOrigin("CONTROLLER", com.sorrowmist.useless.stretcher.client.CatalogJeiPublication.class);
        printClassOrigin("OWNER", com.sorrowmist.useless.stretcher.content.catalog.OwnerRecipePublication.class);
        com.sorrowmist.useless.stretcher.VanillaTestRegistries.initialize();
        var request = LauncherDiscoveryRequestBuilder.request().selectors(DiscoverySelectors.selectClass(
                "com.sorrowmist.useless.stretcher.client.CatalogJeiPublicationTest")).build();
        var listener = new SummaryGeneratingListener();
        LauncherFactory.create().execute(request, listener);
        var summary = listener.getSummary();
        summary.printTo(new PrintWriter(System.out, true));
        summary.printFailuresTo(new PrintWriter(System.out, true));
        summary.getFailures().forEach(f -> f.getException().printStackTrace(System.out));
        if (summary.getTestsFoundCount() == 0 || summary.getTestsFailedCount() != 0
                || summary.getContainersFailedCount() != 0) System.exit(1);
    }

    private static void printClassOrigin(String label, Class<?> type) throws Exception {
        System.out.println(label + "_ORIGIN=" + type.getProtectionDomain().getCodeSource().getLocation());
        try (var input = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(input.readAllBytes());
            System.out.println(label + "_CLASS_SHA256=" + java.util.HexFormat.of().withUpperCase().formatHex(hash));
        }
    }
}
