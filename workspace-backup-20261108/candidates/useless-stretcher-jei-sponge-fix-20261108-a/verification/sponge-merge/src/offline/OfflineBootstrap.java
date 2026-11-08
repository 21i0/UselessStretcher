package offline;
import org.spongepowered.asm.service.IMixinServiceBootstrap;
public final class OfflineBootstrap implements IMixinServiceBootstrap {
    public String getName() { return "OfflineBytecodeOnly"; }
    public String getServiceClassName() { return SpongeOfflineService.class.getName(); }
    public void bootstrap() { }
}
