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
package org.jboss.jokre.agent;

import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Class used to collect details of methods which need to be updated by the agent.
 */
public class UpdateSet
{
    /**
     * value used as a marker to identify entries in the concurrent hash map index
     */
    private static Object PRESENT = new Object();
    private int renotifications = 0;
    private int indexRaces = 0;
    private int insertionRaces = 0;

    /**
     * index by classname and methodname used to detect entries which have already been
     * notified. note we use a normal hashmap and synchronize explicitly on it because
     * we want the inserting clients threads and the removing agent thread to be able
     * to sleep on this index.
     */
    private HashMap<String, Object> classMethodIndex;
    /**
     * a map keyed by class#method to record when a specific method was first notified
     */
    private ConcurrentHashMap<String, Long> notifiedTimestamps;
    /**
     * a map keyed by class#method to record when a notify for a specific method was noticed by the agent
     */
    private ConcurrentHashMap<String, Long> processedTimestamps;
    /**
     * a map keyed by class#method to record when a the agent first performed transformation of a specific method
     */
    private ConcurrentHashMap<String, List<Long>> transformedTimestamps;
    /**
     * index by classname which allows identification of methods associated with
     */
    private ConcurrentHashMap<String, MethodUpdateSet> classIndex;

    public UpdateSet()
    {
        this(false);
    }

    public UpdateSet(boolean trackTransforms)
    {
        classMethodIndex = new HashMap<String, Object>();
        classIndex = new ConcurrentHashMap<String, MethodUpdateSet>();
        // we timestamp class#method entries  when they are notified, detected by the agent and trasformed
        // the notified timestamps are set when an entry is added to the agent's notified update set
        // the processed timestamps are set when an entry is added to the agent's transform update set
        // and the notified timestamp is copied across from notified to transform update set at the same time
        // the transformed timestamp is updated when the transformed bytecode is generated
        notifiedTimestamps = new ConcurrentHashMap<String, Long>();
        if (trackTransforms) {
            processedTimestamps = new ConcurrentHashMap<String, Long>();
            transformedTimestamps = new ConcurrentHashMap<String, List<Long>>();
        } else {
            processedTimestamps = null;
            transformedTimestamps = null;
        }
    }

    /**
     * add an entry to the update set if it is not already present
     * @param className the name of the class to be updated
     * @param methodName the name of the method of thast class  to be updated
     * @return true if the entry has been added or false if it is already present
     */
    public boolean add(String className, String methodName)
    {
        // the classmethod index provides a quick check allowing us to avoid
        // synchronizing on the full index if we have already seen this entry
        // this avoids the situation where the more extensive processing needed
        // to handle new index entries locks out threads which are merely
        // notifying a known entry.
        String classMethodName = className +  "#" + methodName;
        boolean present;

        synchronized (classMethodIndex) {
            present = classMethodIndex.put(classMethodName, PRESENT) == null;
        }
        if (!present) {
            synchronized (this) {
                // track renotifications for performance checking
                renotifications++;
            }
            return false;
        } else if (processedTimestamps != null) {
            processedTimestamps.put(classMethodName, System.currentTimeMillis());
        } else {
            notifiedTimestamps.put(classMethodName, System.currentTimeMillis());
        }
        // this is a new entry so update the full index

        return fullyIndex(className, methodName);
    }

    /**
     * insert a newly notified entry into a method update set which is indexed in the class
     * index by the owner class name
     * @param className
     * @param methodName
     * @return true if the entry was successfully added and false if it was already present (not
     * sure yet that this should ever return false)
     */
    private boolean fullyIndex(String className, String methodName)
    {
        MethodUpdateSet methodUpdates = classIndex.get(className);
        
        if (methodUpdates == null) {
            // use putIfAbsent to resolve insertion races for the method update set
            MethodUpdateSet newMethodUpdates = new MethodUpdateSet(className);
            methodUpdates = classIndex.putIfAbsent(className, newMethodUpdates);
            // count if we had an indexing race
            if (methodUpdates != null) {
                synchronized (this) {
                    indexRaces++;
                }
            } else {
                methodUpdates = newMethodUpdates;
            }
        }
        
        // we may see a repeat entry because we started then insert just as an old entry was being
        // transferred

        if (!methodUpdates.add(methodName)) {
            synchronized (this) {
                insertionRaces++;
            }
            return false;
        }

        return true;
    }

    /**
     * transfer entries from this set into a target set where they are not present and also record
     * newly added entries in a difference set. the target set may not be concurrently modified while
     * this operation is in progress but this set may be modified concurrently.
     * @param  target the set to which entries from this set may be added
     * @return the difference set containing all entries added to the target set
     */
    public UpdateSet transfer(UpdateSet target)
    {
        UpdateSet diff = new UpdateSet();
        // iterate over all entries in the table
        Enumeration<MethodUpdateSet> methodUpdateSets = classIndex.elements();

        while(methodUpdateSets.hasMoreElements()) {
            // retrieve the class and method names for this entry, resetting the method name set to empty
            MethodUpdateSet methodUpdateSet = methodUpdateSets.nextElement();
            String className = methodUpdateSet.getClassName();
            List<String> methodNames = methodUpdateSet.reset();
            if (methodNames != null) {

                // copy the entries to the target set and, where appropriate, the difference set

                for (String methodName : methodNames) {
                    // first remove tne entry so that we can sleep when the index is empty.
                    // we will eventually stop being renotified because the bytecode transform
                    // will bypass each call to the notifying method

                    // TODO hmm, that last stateent is maybe not certain e.g. if for some reason we cannto
                    // // transform a specific class. if that happens then we may need to do the delete from
                    // a shadow index and retain the main index list to avoid renotifications

                    String classMethodName = className + "#" + methodName;
                    synchronized (classMethodIndex) {
                        classMethodIndex.remove(classMethodName);
                    }

                    if (target.add(className, methodName)) {
                        // propagate the notified  timestamp
                        Long notifiedTimestamp = notifiedTimestamps.get(classMethodName);
                        target.notifiedTimestamps.put(classMethodName, notifiedTimestamp);
                        // add tis to the diff set so we retransform the class
                        diff.add(className, methodName);
                    }
                }
            }
        }

        // the difference set now contains entries for all classes which we need to retransform

        return diff;
    }

    public List<String> classNames()
    {
        if (classMethodIndex.isEmpty()) {
            return null;
        }
        List<String> classNames = new ArrayList<String>();

        Enumeration<String> keys = classIndex.keys();
        while (keys.hasMoreElements()) {
            classNames.add(keys.nextElement());
        }

        return classNames;
    }
    
    // TODO -- these two methods probably ought to be wrapped up internally rather than being exposed to clients
    /**
     * this is called after a successful notification of a new entry to ensure that the Jokre agent wakes up and
     * processes the entry. note that the lock is not taken until notification which means we may get false
     * wakeups but that means inserting threads don't see a big delay which is what we want.
     */
    public void wakeup()
    {
        synchronized (classMethodIndex) {
            classMethodIndex.notifyAll();
        }
    }

    /**
     * this is called by the Jokre agent thread afer it has finished tarnsferring notifications from the staging
     * update set to the installed update set. it causes the Jokre agent to wait until new updates are available.
     */
    public void waitForUpdates()
    {
        synchronized (classMethodIndex) {
            while (classMethodIndex.isEmpty()) {
                try {
                    classMethodIndex.wait();
                } catch (InterruptedException ie) {
                    // ignore
                }
            }
        }
    }

    public List<String> listMethods(String className) {
        MethodUpdateSet updateSet = classIndex.get(className);
        if (updateSet != null) {
            return updateSet.collect();
        } else {
            return null;
        }
    }

    public void transformed(String className, List<String> methodNames)
    {
        // no need for null check as this is only ever called on the update set
        for (String methodname : methodNames) {
            String classMethodName = className + "#" + methodname;
            // no need for lock as this only ever happens single-threaded
            List<Long> timestamps = transformedTimestamps.get(classMethodName);
            if (timestamps == null) {
                timestamps = new ArrayList<Long>();
                transformedTimestamps.put(classMethodName, timestamps);
            }
            synchronized (timestamps) {
                timestamps.add(System.currentTimeMillis());
            }
        }
    }

    public void stats()
    {
        Set<String> classMethodKeys = classMethodIndex.keySet();
        Set<String> classKeys = classIndex.keySet();
        System.out.println("class count:     " + classKeys.size());
        dumpNames(System.out, classKeys);
        System.out.println("entry count:     " + classMethodKeys.size());
        if (transformedTimestamps == null) {
            dumpNames(System.out, classMethodKeys);
        } else {
            dumpTimestamps(System.out, classMethodKeys);
        }
        System.out.println("renotifications: " + renotifications);
        System.out.println("indexRaces:      " + indexRaces);
        System.out.println("insertionRaces:  " + insertionRaces);
    }

    private void dumpNames(PrintStream out, Set<String> classes)
    {
        StringBuilder builder = new StringBuilder();
        Iterator<String> iterator = classes.iterator();
        builder.append("[");
        String prefix = "";
        while (iterator.hasNext()) {
            String next = iterator.next();
            builder.append(prefix);
            builder.append(next);
            prefix = ", ";
        }
        builder.append("]");
        out.println(builder.toString());
    }

    private void dumpTimestamps(PrintStream out, Set<String> classMethodNames)
    {
        StringBuilder builder = new StringBuilder();
        Iterator<String> iterator = classMethodNames.iterator();
        builder.append("  [");
        String prefix = "\n    ";
        while (iterator.hasNext()) {
            String classMethodName = iterator.next();
            Long notified = notifiedTimestamps.get(classMethodName);
            Long processed = processedTimestamps.get(classMethodName);
            List<Long> transformed = transformedTimestamps.get(classMethodName);
            builder.append(prefix);
            builder.append(classMethodName);
            // ensure we did not catch this in mid update
            if (notified == null) {
                builder.append("transferring . . .");
            } else  if (processed == null) {
                builder.append(" notify ");
                builder.append(System.currentTimeMillis() - notified);
                builder.append( "ms");
            } else  if (transformed == null) {
                builder.append(" notify ");
                builder.append(processed - notified);
                builder.append( "ms process ") ;
                builder.append(System.currentTimeMillis() - processed);
                builder.append( "ms");
            } else {
                builder.append(" notified ");
                builder.append(processed - notified);
                builder.append( "ms process") ;
                synchronized(transformed) {
                    for (int i = 0; i < transformed.size(); i++) {
                        builder.append( " ");
                        builder.append(transformed.get(i) - processed);
                        builder.append( "ms transform");
                    }
                }
            }
        }
        builder.append("\n  ]");
        out.println(builder.toString());
    }

    public class MethodUpdateSet
    {
        public MethodUpdateSet(String className)
        {
            this.className = className;
            this.methods = new ConcurrentHashMap<String, Object>();
        }

        public boolean add(String methodName)
        {
            return (methods.putIfAbsent(methodName, PRESENT) == null);
        }

        public String getClassName()
        {
            return className;
        }
        
        public List<String> reset()
        {
            Enumeration<String> enumeration =  methods.keys();
            if (enumeration.hasMoreElements()) {
                ArrayList<String> methodNames = new ArrayList<String>();
                while (enumeration.hasMoreElements()) {
                    String methodName = enumeration.nextElement();
                    methodNames.add(methodName);
                    methods.remove(methodName);
                }
                return methodNames;
            } else {
                return null;
            }
        }

        public List<String> collect()
        {
            Enumeration<String> enumeration =  methods.keys();
            if (enumeration.hasMoreElements()) {
                ArrayList<String> methodNames = new ArrayList<String>();
                while (enumeration.hasMoreElements()) {
                    String methodName = enumeration.nextElement();
                    methodNames.add(methodName);
                }
                return methodNames;
            } else {
                return null;
            }
        }

        private String className;
        private ConcurrentHashMap<String, Object> methods;
    }
}
