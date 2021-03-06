# Introduction

Jokre** is an add-on to Infinispan which solves a specific performance problem which arises when using legacy, library or other 3rd-party code which cannot be rewritten to use the Infinispan APIs. It implements a bytecode-to-bytecode optimization transformation which translates calls to the generic Map API so that they call the more
efficient Infinispan implementation. Although it only addresses this specific problem it is a proof of concept for a more general application of the same technique. Details of the transformation and of applications which will benefit from it are provided below.

** n.b. you can pronounce Jokre however you want. The name has no actual significance beyond, perhaps, a small in-joke -- as Thomas Pynchon once said "Those who know, know".

# Using Jokre

Jokre is supplied as a java agent jar which requires loading on the java command line. You can use it in any VM which supports the java agent API defined in the sun tools extension (i.e. most serious JVMs) by adding the following option to the java command line

    -javaagent:/path/to/jokre.jar=boot:/path/to/jokre.jar

where /path/to/jokre.jar identifies the implementation jar. No other configuration is required.

# The Map Transformation

The specific case addressed by Jokre is where a call to method Map.put(), does not use its return value. Most map implementations have to perform updates synchronously in order to atomically retrieve the current map entry and modify it with a new entry. When the return value is not actually used then this provides an opportunity to remove the caller synchronization or, at least, to decouple it by returning a Future which the client can call later to ensure the operation has completed.

This opportunity is particularly significant in the case where the Map is implemented as a distributed cache e.g. as with the Infinispan project. A put operation on one node may be significantly delayed if it needs to wait for the update to be performed on a remote and the previous value returned. By queueing the put for remote processing and then allowing the caller to continue operation, network latency can be avoided. A Map implementor can provide an alternative to put which does not return the old value and, indeed, Infinispan provides such an API. However, this is of no help when its cache instances are passed (as a Map) to legacy, library or JVM runtime code which calls the generic put().

Dynamic bytecode transformation can be used to modify Map client code so that Map.put() calls are replaced with more efficient calls in the case where the method target implements an optimized variant. The bytecode-to-bytecode transformation has to use a runtime type test to distinguish cases where this alternative path is an option and, if
not, fall back on the original put call.

This transformation could potentially be applied to any Map.put() call site at load time. However, applying it in all cases would require scanning every class during loading to identify potential call sites. A more efficient strategy is to modify the put method of an implementation class which offers a fast alternative, instrumenting it so it identifies its caller and applies the transform on demand when put() is called. This approach ensures that the overhead of detecting
and transforming the call site is only incurred for calls which actually refer to an implementor instance. It can be further optimized to avoid repeated instrumentation checks by rerouting all calls, either to a fast alternative or to an alternative slow path which omits the instrumentation code.

# The Infinispan Class Transformation

The Map implementation class used in Jokre is the Infinispan cache and the method which initiates the transform is CacheSupport.put(), its implementation of Map.put(). CacheSupport provides an alternative method set() which takes the same arguments as put but is void and executes the put non-synchronously.

Jokre transforms the Infinispan class at load time, so that it monitors client calls to put, notifying Jokre so it can transform the call site. Jokre renames the original method to put$originalSlowPath. It then generates a new implementation for cacheSupport.put() which notifies Jokre that a call has been made and then calls put$originalSlowPath() to do a normal put. Jokre uses a background thread to handle notifications, inspecting the caller method bytecode to locate calls to Map.put(). A call site which does not consume its result is transformed by Jokre so that it calls through to set.

An auxiliary interface NonReturnMap is used in the transformed code.

    package org.jboss.jokre;
    
    public interface NonReturnMap<K, V>
    {
       public V put$alternativeSlowPath(K key, V value);
       public void put$fastPath(K key, V value);
    }

Jokre adds this interface to CacheSupport's 'implements' list. It also generates implementations for its two methods. put$fastPath just calls set. put$alternativeSlowPath just calls put$originalSlowPath.

# The Client Call Site Transformation

A client call site is rerouted to one of these two methods whenever the target instance implements NonReturnMap. The transformation has to be applied in both cases

  - where the return value from CacheSupport.put() is used and
  - where it is ignored.

The decision as to which transformation to perform is made according to whether the call to Map.put() is followed by a pop (or return) or by some other bytecode operation. The first case is potentially redirected to a call to put$fastPath and the second case is potentially redirected to a call to put$alternativeSlowPath.

Both the transforms inject a type test (instanceof) on the call target to determine whether the target instance is a NonReturnMap or just a normal map.  If the test returns true they cast and call the relevant interface method to do the put. The false branch just does a normal put. The equivalent source transform for the fast path case would be from something like

    map.put(key, value);

to

    if (map instanceof NonReturnMap) {
       ((NonReturnNap)map).put$fastPath(key, value);
    } else {
       map.put(key, value);
    }

and for the slow path case it would be from something like

    oldValue = map.put(key, value);

to

    if (map instanceof NonReturnMap) {
       value = ((NonReturnNap)map).put$alternativeSlowPath(key, value);
    } else {
       value = map.put(key, value);
    }

In the fast path case the original bytecode will look like this:

(note that the comment lines preceding an instruction identify the stack layout before and after the following instruction has executed)

      // [... map, key, value] ==> [..., retval]
      invokeinterface #5,  3; //InterfaceMethod java/util/Map.put:(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
      // [..., retval] ==> [...]
      pop
      . . .

and the transformed version will look like this

      // [... map, key, value] ==> [... key, value, map, key, value]
      dup2_x1
      // [... key, value, map, key, value] ==> [... key, value, map]
      pop2
      // [... key, value, map] ==> [... key, value, map, map]
      dup
      // [... key, value, map, map] ==> [... key, value, map, bool]
      instanceof #3 //class org/jboss/jokre/NonReturnMap
      // [... key, value, map, bool] ==> [... key, value, map]
      iffalse L1
      // [... key, value, map ] ==> [... key, value, map]
      checkcast #3 //class org/jboss/jokre/NonReturnMap
      // [... key, value, map ] ==> [... map, key, value, map]
      dup_x2
      // [... map, key, value, map ] ==> [... map, key, value]
      pop
      // [... map, key, value] ==> [...]
      invokeinterface #3,  3; //InterfaceMethod org/jboss/jokre/NonReturnMap.put$fastPath:(Ljava/lang/Object;Ljava/lang/Object;)V
      goto L2
    L1:
      // [... key, value, map ] ==> [... map, key, value, map]
      dup_x2
      // [... map, key, value, map ] ==> [... map, key, value]
      pop
      // [... map, key, value] ==> [..., retval]
      invokeinterface #5,  3; //InterfaceMethod java/util/Map.put:(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
      // [..., retval] ==> [...]
      pop
    L2:
      . . .

In the slow path case the original bytecode will look like this

    invokeinterface	#3,  3; //InterfaceMethod java/util/Map.put:(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
    <not-pop>
    . . .

and the transformed version will look like this

      // [... map, key, value] ==> [... key, value, map, key, value]
      dup2_x1
      // [... key, value, map, key, value] ==> [... key, value, map]
      pop2
      // [... key, value, map] ==> [... key, value, map, map]
      dup
      // [... key, value, map, map] ==> [... key, value, map, bool]
      instanceof #3 //class org/jboss/jokre/NonReturnMap
      // [... key, value, map, bool] ==> [... key, value, map]
      iffalse L1
      // [... key, value, map ] ==> [... key, value, map]
      checkcast #3 //class org/jboss/jokre/NonReturnMap
      // [... key, value, map ] ==> [... map, key, value, map]
      dup_x2
      // [... map, key, value, map ] ==> [... map, key, value]
      pop
      // [... map, key, value] ==> [..., retval]
      invokeinterface #3,  3; //InterfaceMethod org/jboss/jokre/NonReturnMap.put$alternativeSlowPath:(Ljava/lang/Object;Ljava/lang/Object;);Ljava/lang/Object;
      goto L2
    L1:
      // [... key, value, map ] ==> [... map, key, value, map]
      dup_x2
      // [... map, key, value, map ] ==> [... map, key, value]
      pop
      // [... map, key, value] ==> [..., retval]
      invokeinterface #5,  3; //InterfaceMethod java/util/Map.put:(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;
      // [..., retval] ==> [...]
    L2:
      <notpop>
      . . .

# Generalizing The Technique

Clearly, the same technique can be extended to apply to other top-level implementors of the Map API or even to other legacy APIs which could benefit from employing a void fast path alternative. In fact Jokre doens't just transform CacheSupport. It also applies the same transformation to the Infinispan class AbstractDelegatingCache.
All that is needed for the current Jokre transformation to be applied is for the Map implementor to provide a fast path method called set (clearly this has to be implemented without calling the Map put implementation). The Infinispan code needs no modification.
You can run Infinispan distributed without Jokre and get slow puts or run with Jokre and do fast puts.
