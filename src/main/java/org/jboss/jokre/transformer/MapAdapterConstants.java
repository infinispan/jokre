/*
* JBoss, Home of Professional Open Source
* Copyright 2011, Red Hat and individual contributors
* by the @authors tag.
*
* This is free software; you can redistribute it and/or modify it
* under the terms of the GNU Lesser General Public License as
* published by the Free Software Foundation; either version 2.1 of
* the License, or (at your option) any later version.
*
* This software is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public
* License along with this software; if not, write to the Free
* Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
* 02110-1301 USA, or see the FSF site: http://www.fsf.org.
*
* @authors Andrew Dinn
*/
package org.jboss.jokre.transformer;

/**
 * Constants used by adapter classes
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
