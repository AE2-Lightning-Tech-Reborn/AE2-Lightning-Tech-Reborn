package diagnostic;
import java.util.*;
import org.spongepowered.asm.service.*;
public final class HarnessProperties implements IGlobalPropertyService {
    private record Key(String name) implements IPropertyKey {}
    private final Map<IPropertyKey,Object> values = new HashMap<>();
    public IPropertyKey resolveKey(String name) { return new Key(name); }
    @SuppressWarnings("unchecked") public <T> T getProperty(IPropertyKey key) { return (T) values.get(key); }
    public void setProperty(IPropertyKey key, Object value) { values.put(key, value); }
    public <T> T getProperty(IPropertyKey key, T fallback) { T v = getProperty(key); return v == null ? fallback : v; }
    public String getPropertyString(IPropertyKey key, String fallback) { Object v = values.get(key); return v == null ? fallback : v.toString(); }
}
