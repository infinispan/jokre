package org.jboss.jokre.transformer;

/**
 * Constants usedby adapter classes
 */
public class MapAdapterConstants
{
    /**
     * the names of the Map implementors whose  put calls we transform
     */
    public final static String CLASS_CACHE_SUPPORT = "org/infinispan/CacheSupport";
    public final static String CLASS_ABSTRACT_DELEGATING_CACHE = "org/infinispan/AbstractDelegatingCache";

    public final static String PUT_METHOD_NAME = "put";
    public final static String SET_METHOD_NAME = "set";
    public final static String PUT_METHOD_FAST_PATH_NAME = "put$fastPath";
    public final static String PUT_METHOD_ORIGINAL_SLOW_PATH_NAME = "put$originalSlowPath";
    public final static String PUT_METHOD_ALTERNATIVE_SLOW_PATH_NAME = "put$alternativeSlowPath";
    public final static String PUT_METHOD_DESC = "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;";
    public final static String SET_METHOD_DESC = "(Ljava/lang/Object;Ljava/lang/Object;)V";

    public final static String CLASS_MAP = "java/util/Map";
    public final static String CLASS_CONCURRENT_MAP = "java/util/concurrent/ConcurrentMap";
    public final static String CLASS_CACHE = "org/infinispan/Cache";
    public final static String CLASS_ADVANCED_CACHE = "org/infinispan/AdvancedCache";

    public final static String CLASS_NON_RETURN_MAP = "org/jboss/jokre/NonReturnMap";
    public final static String CLASS_NON_RETURN_MAP_EXTERNAL = CLASS_NON_RETURN_MAP.replace('/','.');

    public final static String NOTIFY_MAP_PUT_METHOD_NAME = "notifyMapPut";
    public final static String NOTIFY_MAP_PUT_METHOD_DESC = "()Z";
    public final static String CLASS_JOKRE = "org/jboss/jokre/agent/Jokre";
}
