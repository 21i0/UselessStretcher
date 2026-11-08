import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import java.io.PrintWriter;

public final class SlicedTestLauncher {
    public static void main(String[] args) throws Exception {
        com.sorrowmist.useless.stretcher.VanillaTestRegistries.initialize();
        var request = LauncherDiscoveryRequestBuilder.request().selectors(DiscoverySelectors.selectClass(
                "com.sorrowmist.useless.stretcher.content.catalog.SlicedVatConversionTest")).build();
        var listener = new SummaryGeneratingListener();
        LauncherFactory.create().execute(request, listener);
        var summary = listener.getSummary();
        summary.printTo(new PrintWriter(System.out, true));
        summary.printFailuresTo(new PrintWriter(System.out, true));
        summary.getFailures().forEach(f -> f.getException().printStackTrace(System.out));
        if (summary.getTestsFoundCount() == 0 || summary.getTestsFailedCount() != 0
                || summary.getContainersFailedCount() != 0) System.exit(1);
    }
}
