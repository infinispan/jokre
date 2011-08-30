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

import java.io.File;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

/**
 * Agent main program for Jokre bytecode optimizer.
 */
public class Main
{
    /**
     * prefix used to specify boot jar argument for agent
     */
    private static final String BOOT_PREFIX = "boot:";

    /**
     * prefix used to specify system jar argument for agent
     */
    private static final String SYS_PREFIX = "sys:";

    /**
     * list of paths to extra bootstrap jars supplied on command line
     */
    private static List<String> bootJarPaths = new ArrayList<String>();

    /**
     * list of paths to extra system jars supplied on command line
     */
    private static List<String> sysJarPaths = new ArrayList<String>();

    /**
     * flag used to void repeated agent loads
     */

    private static boolean firstTime = true;

    public static void premain(String args, Instrumentation inst)
            throws Exception
    {
        // guard against the agent being loaded twice
        synchronized (Main.class) {
            if (firstTime) {
                firstTime = false;
            } else {
                throw new Exception("org.jboss.jokre.agent.Main : attempting to load Jokre agent more than once");
            }
        }

        if (args != null) {
            // args are supplied separated by ',' characters
            String[] argsArray = args.split(",");
            // we accept extra jar files to be added to the boot/sys classpaths
            // script files to be scanned for rules
            // listener flag which implies use of a retransformer
            for (String arg : argsArray) {
                if (arg.startsWith(BOOT_PREFIX)) {
                    bootJarPaths.add(arg.substring(BOOT_PREFIX.length(), arg.length()));
                } else if (arg.startsWith(SYS_PREFIX)) {
                    sysJarPaths.add(arg.substring(SYS_PREFIX.length(), arg.length()));
                } else {
                    System.err.println("org.jboss.jokre.agent.Main:\n" +
                            "  illegal agent argument : " + arg + "\n" +
                            "  valid arguments are boot:<path-to-jar> or sys:<path-to-jar>");
                }
            }
        }

        // add any boot jars to the boot class path

        for (String bootJarPath : bootJarPaths) {
            try {
                JarFile jarfile = new JarFile(new File(bootJarPath));
                inst.appendToBootstrapClassLoaderSearch(jarfile);
            } catch (IOException ioe) {
                System.err.println("org.jboss.jokre.agent.Main: unable to open boot jar file : " + bootJarPath);
                throw ioe;
            }
        }

        // add any sys jars to the system class path

        for (String sysJarPath : sysJarPaths) {
            try {
                JarFile jarfile = new JarFile(new File(sysJarPath));
                inst.appendToSystemClassLoaderSearch(jarfile);
            } catch (IOException ioe) {
                System.err.println("org.jboss.jokre.agent.Main: unable to open system jar file : " + sysJarPath);
                throw ioe;
            }
        }
        // install a transformer to do call site transformations

        boolean isRetransform = inst.isRetransformClassesSupported();

        if (!isRetransform) {
            throw new Exception("org.jboss.jokre.agent.Main : JVM does not support retransformation");
        }

        ClassFileTransformer transformer;
        ClassLoader loader = ClassLoader.getSystemClassLoader();
        Class transformerClazz;

        // create the Jokre agent via reflection in case we want to put the Jokre lib into the bootstrap classpath

        //transformer = new Jokre(inst);
        transformerClazz = loader.loadClass("org.jboss.jokre.agent.Jokre");
        Constructor constructor = transformerClazz.getConstructor(Instrumentation.class);
        transformer = (ClassFileTransformer)constructor.newInstance(new Object[] { inst });

        inst.addTransformer(transformer, true);
    }

    public static void agentmain(String args, Instrumentation inst) throws Exception
    {
        premain(args, inst);
    }
}
