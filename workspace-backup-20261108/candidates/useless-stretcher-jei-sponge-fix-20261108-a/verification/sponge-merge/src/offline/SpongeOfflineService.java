package offline;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.platform.container.ContainerHandleVirtual;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.service.*;
import java.io.*;
import java.net.*;
import java.util.*;

/** Bytecode-only host for the unmodified official Sponge transformer. Never defines game targets. */
public final class SpongeOfflineService extends MixinServiceAbstract implements IClassProvider,
        IClassBytecodeProvider, ITransformerProvider, IClassTracker {
    public static final List<String> APPLIED = new ArrayList<>();
    private final IContainerHandle container = new ContainerHandleVirtual("offline-sponge-bytecode-check");
    public String getName() { return "OfflineBytecodeOnly"; }
    public boolean isValid() { return true; }
    public MixinEnvironment.Phase getInitialPhase() { return MixinEnvironment.Phase.PREINIT; }
    public IClassProvider getClassProvider() { return this; }
    public IClassBytecodeProvider getBytecodeProvider() { return this; }
    public ITransformerProvider getTransformerProvider() { return this; }
    public IClassTracker getClassTracker() { return this; }
    public IMixinAuditTrail getAuditTrail() { return new IMixinAuditTrail() {
        public void onApply(String target, String mixin) { APPLIED.add(target + " <- " + mixin); }
        public void onPostProcess(String name) { }
        public void onGenerate(String name, String generator) { }
    }; }
    public Collection<String> getPlatformAgents() { return List.of(); }
    public IContainerHandle getPrimaryContainer() { return container; }
    public InputStream getResourceAsStream(String name) { return getClass().getClassLoader().getResourceAsStream(name); }
    public URL[] getClassPath() { return Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
            .map(value -> { try { return new File(value).toURI().toURL(); } catch (MalformedURLException e) { throw new RuntimeException(e); } }).toArray(URL[]::new); }
    public Class<?> findClass(String name) throws ClassNotFoundException { return findClass(name, false); }
    public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException {
        if (name.startsWith("net.minecraft.") || name.startsWith("mezz.jei.") || name.startsWith("com.sorrowmist.useless."))
            throw new ClassNotFoundException("Target classes must stay bytecode-only: " + name);
        return Class.forName(name, initialize, getClass().getClassLoader());
    }
    public Class<?> findAgentClass(String name, boolean initialize) throws ClassNotFoundException { return findClass(name, initialize); }
    public ClassNode getClassNode(String name) throws ClassNotFoundException, IOException { return getClassNode(name, true, 0); }
    public ClassNode getClassNode(String name, boolean transformers) throws ClassNotFoundException, IOException { return getClassNode(name, transformers, 0); }
    public ClassNode getClassNode(String name, boolean transformers, int flags) throws ClassNotFoundException, IOException {
        String resource = name.replace('.', '/') + ".class";
        try (InputStream input = getResourceAsStream(resource)) {
            if (input == null) throw new ClassNotFoundException(name);
            ClassNode node = new ClassNode(); new ClassReader(input).accept(node, flags); return node;
        }
    }
    public Collection<ITransformer> getTransformers() { return List.of(); }
    public Collection<ITransformer> getDelegatedTransformers() { return List.of(); }
    public void addTransformerExclusion(String name) { }
    public void registerInvalidClass(String name) { }
    public boolean isClassLoaded(String name) { return false; }
    public String getClassRestrictions(String name) { return ""; }
}
