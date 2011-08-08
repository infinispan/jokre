package org.jboss.jokre;

/**
 * interface implemented by Map classes which want to optimise calls which do not use the return value
 */
public interface NonReturnMap<K, V>
{
    // methods generated by the Jokre transformer
    public V put$alternativeSlowPath(K key, V value);
    public void put$fastPath(K key, V value);
}