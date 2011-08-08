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

import org.jboss.jokre.transformer.JokreTransformer;
import org.jboss.jokre.transformer.MapAdapterConstants;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.List;

/**
 * Jokre agent transformer used to retransform call sites for Map.put calls so that they call an optimized method instead
 */
public class Jokre implements ClassFileTransformer
{
    // public API

    /**
     * validate that the caller is a put method then add the classname and method name of its caller
     * to the Jokre agent's update set
     * @return true if the caller has not yet been added to the update set or false if it has been added
     */
    public static boolean notifyMapPut()
    {

        Thread currentThread =  Thread.currentThread();
        StackTraceElement[] stackTrace = currentThread.getStackTrace();
        // we should get called from a Map.put implementation and we want to replace its caller
        int length = stackTrace.length;
        if (length < 4) {
            throw new InvalidNotifyException("notifyMapPut must be called below Map.put call site");
        }
        StackTraceElement putCall = stackTrace[2];

        if (putCall.getMethodName() != "put") {
            throw new InvalidNotifyException("notifyMapPut must be called from Map.put implementation");
        }

        StackTraceElement putCaller = stackTrace[3];

        if (putCaller.isNativeMethod()) {
            System.err.println("oops put called via native code!!!");
            // hmm, this would be a call site we cannot modify. this should never happen!
            return false;
        }

        String callerClass = putCaller.getClassName();
        String callerMethod = putCaller.getMethodName();

        if (theJokre == null) {
            System.err.println("Jokre java agent is not installed!!!");
        }

        return theJokre.addToStaging(callerClass, callerMethod);
    }

    /**
     * dump statistics detailing notifications and renotifications into the staging set
     * and the update set
     */
    public static void stats()
    {
        System.out.println("Staging");
        theJokre.staging.stats();
        System.out.println("Updates");
        theJokre.updated.stats();
    }

    // public constructor for use by Jokre Main class
    // n.b. the Jokre Main class is loaded via the system classpath like any java agent
    // it installs the jar containing this class into the bootstrap classpath then constructs
    // an instamce reflectively, thus ensuring it uses the correct version of this class.

    public Jokre(Instrumentation inst)
    {
        synchronized (Jokre.class) {
            if (theJokre != null) {
                throw new RuntimeException("Invalid attempt to create Jokre agent");
            }
            theJokre =  this;
        }
        this.inst = inst;
        checkInfinispan();
        jokreTransformer = new JokreTransformer();
        jokreThread = new JokreThread(this);
        jokreThread.start();
    }

    // protected API for use by background thread

    protected void runJokre()
    {
        while (true) {
            waitForUpdates();
            UpdateSet diffs = staging.transfer(updated);
            List<String> classNames = diffs.classNames();
            if (classNames != null) {
                retransform(classNames);
            }
        }
    }

    // private implemenation

    /**
     * Instrumentation object providing access to the JDK class base
     */
    private Instrumentation inst;

    /**
     * singleton Jokre instance which manages all updates
     */
    private static Jokre theJokre =  null;

    /**
     * a background thread used to process agent notifications
     */
    private JokreThread jokreThread = null;

    /**
     * a transformer which modifies callers which invoke modified put calls
     */
    private JokreTransformer jokreTransformer = null;

    /**
     * the agent uses a staging update set to record updates which have not yet been retransformed
     */
    private UpdateSet staging = new UpdateSet();

    /**
     * the agent uses a second update set to record updates which have been retransformed
     */
    private UpdateSet updated =  new UpdateSet(true);

    /**
     * ensure that no infinispan classes have been loaded into the runtime
     */

    private void checkInfinispan()
    {
        for (Class clazz : inst.getAllLoadedClasses()) {
            if (isMapImplementorClass(clazz.getName())) {
                throw new RuntimeException("Invalid attempt to load Jokre agent after loading infinispan");
            }
        }
    }

    private boolean addToStaging(String callerClass,  String callerMethod)
    {
        boolean result = staging.add(callerClass, callerMethod);

        // m.b. the locking scheme may mean that we rewake the agent after it has just processed that
        // insert but that does no harm and and it ensures that the notify is fast because we don't hold
        // a lock held by the agent for long. for this to work the agent has to be sure the staging set
        // is empty before it sleeps and releases the wakeup lock. that ensures we don;t miss any wakeups.

        if (result) {
            wakeup();
        }

        return result;
    }

    private void wakeup()
    {
        // wakeup any threads waiting for entries to be added to the staging updates set

        staging.wakeup();
    }

    private void waitForUpdates()
    {
        // wait only when  the staging updates set is empty
        staging.waitForUpdates();
    }

    private void retransform(List<String> classNames)
    {
        Class[] classes = inst.getAllLoadedClasses();
        for (Class clazz : classes) {
            String name = clazz.getName();
            if (classNames.contains(name)) {
                try {
                    inst.retransformClasses(clazz);
                } catch (Exception e) {
                    // oops -- what is the consequence of this?
                    // if we get an exception here then the client will keep on renotifying
                    // a call  to the slow path method which will slow down calls via this path
                    // a little. this may not be significant and must be weighed against gains
                    // made elsewhhere. dumping stats on renotifications will show where this
                    // is happening.
                }
            }
        }
    }

    //
    // background thread to process notifications from instrumented API
    //

    private static class JokreThread extends Thread
    {
        private Jokre theJokre;

        public JokreThread(Jokre theJokre)
        {
            this.theJokre = theJokre;
            this.setDaemon(true);
            this.setName("The Jokre");
        }
        public void run()
        {
            theJokre.runJokre();
        }
    }

    //
    // implemenation of interface ClassFileTransformer
    //

    /**
     * transformer entry point
     * @param loader
     * @param className
     * @param classBeingRedefined
     * @param protectionDomain
     * @param classfileBuffer
     * @return
     * @throws IllegalClassFormatException
     */
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException
    {
        byte[] bytes = classfileBuffer;

        if (isMapImplementorClass(className)) {
            // modify this implementation so it supports a void put

            bytes = jokreTransformer.extendMapImplementorAPI(loader, className, classBeingRedefined, protectionDomain, bytes);
        }

        String classNameExternal = className.replace('/', '.');
        List<String> methodNames =  updated.listMethods(classNameExternal);
        if (methodNames != null) {
            bytes = jokreTransformer.transform(loader, className, classBeingRedefined, protectionDomain, bytes, methodNames);
            updated.transformed(classNameExternal, methodNames);
        }

        return bytes;
    }

    private boolean isMapImplementorClass(String className)
    {
        // if appropriate we can extend this test to include other top level
        // Map implementors such as AbstractDelegatingCache
        return (className.equals(MapAdapterConstants.CLASS_CACHE_SUPPORT) ||
                className.equals(MapAdapterConstants.CLASS_ABSTRACT_DELEGATING_CACHE));
    }
}