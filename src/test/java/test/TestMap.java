package test;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 */
public class TestMap<K, V> implements Map<K, V>
{
    HashMap<K, V> delegate;

    public TestMap()
    {
        delegate = new HashMap<K, V>();
    }

    public int size() {
        return delegate.size();
    }

    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    public boolean containsKey(Object key) {
        return delegate.containsKey(key);
    }

    public boolean containsValue(Object value) {
        return delegate.containsValue(value);
    }

    public V get(Object key) {
        return delegate.get(key);
    }

    public V put(K key, V value) {
        //System.out.println("call to TestMap2.put(" + key + ", " + value + ")");
        return delegate.put(key, value);
    }

    public V remove(Object key) {
        return delegate.remove(key);
    }

    public void putAll(Map<? extends K, ? extends V> m) {
        delegate.putAll(m);
    }

    public void clear() {
        delegate.clear();
    }

    public Set<K> keySet() {
        return delegate.keySet();
    }

    public Collection<V> values() {
        return delegate.values();
    }

    public Set<Entry<K,V>> entrySet() {
        return delegate.entrySet();
    }
}
