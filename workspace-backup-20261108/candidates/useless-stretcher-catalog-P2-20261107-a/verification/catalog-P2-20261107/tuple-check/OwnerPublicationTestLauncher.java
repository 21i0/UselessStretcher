import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
public final class OwnerPublicationTestLauncher {
 public static void main(String[] args) {
  var listener = new SummaryGeneratingListener(); var launcher = LauncherFactory.create();
  launcher.registerTestExecutionListeners(listener);
  launcher.execute(LauncherDiscoveryRequestBuilder.request().selectors(DiscoverySelectors.selectClass("com.sorrowmist.useless.stretcher.content.catalog.OwnerRecipePublicationTest")).build());
  var summary=listener.getSummary(); summary.printTo(new java.io.PrintWriter(System.out,true)); summary.printFailuresTo(new java.io.PrintWriter(System.out,true));
  if(summary.getTestsFoundCount()!=10||summary.getTotalFailureCount()!=0)System.exit(1);
 }
}