package offline;
import org.spongepowered.asm.service.*;
import java.util.*;
public final class OfflineProperties implements IGlobalPropertyService {
    private record Key(String name) implements IPropertyKey { }
    private final Map<IPropertyKey,Object> values = new HashMap<>();
    public IPropertyKey resolveKey(String name) { return new Key(name); }
    @SuppressWarnings("unchecked") public <T> T getProperty(IPropertyKey key) { return (T) values.get(key); }
    public void setProperty(IPropertyKey key, Object value) { values.put(key,value); }
    public <T> T getProperty(IPropertyKey key, T fallback) { T value = getProperty(key); return value == null ? fallback : value; }
    public String getPropertyString(IPropertyKey key, String fallback) { Object value = values.get(key); return value == null ? fallback : value.toString(); }
}
