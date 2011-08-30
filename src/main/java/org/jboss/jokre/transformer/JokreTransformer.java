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

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.ProtectionDomain;
import java.util.Iterator;
import java.util.List;

/**
 * class  which does the actual bytecode transformation to calls to  Map.put() with
 * calls to the NonReturnMap API
 */
public class JokreTransformer
{
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer, List<String> methodNames)
    {
        // traceTransform(className, methodNames);
        ClassReader reader = new ClassReader(classfileBuffer);
        // TODO -- see if we really need to compute and expand frames
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        // ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        MapPutCallAdapter adapter = new MapPutCallAdapter(writer, loader, methodNames);

        try {
            reader.accept(adapter, 0);
            // reader.accept(adapter, 0);
            if (adapter.isTransformed()) {
                byte[] newBytes = writer.toByteArray();
                maybeDumpClass(className, newBytes);
                return newBytes;
            } else {
                System.err.println("JokreTransformer : Failed to transform class " + className);
                return classfileBuffer;
            }
        } catch (Exception e) {
            System.err.println("JokreTransformer.transform : exception " + e);
            e.printStackTrace(System.err);
            return classfileBuffer;
        }
    }

    /**
     * Modify the supplied Map class implementer so it's API can be optimized by the Jokre optimizer.
     * This requires instrumenting the normal slow path put(key, value) method so it notifies Jokre
     * when it is called and then adding two alternative implementations, a fastpath version which
     * is void and an alternative slowpath version which does not perform Jokre notification.
     * @param loader
     * @param className
     * @param classBeingRedefined
     * @param protectionDomain
     * @param classfileBuffer
     */
    public byte[] extendMapImplementorAPI(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                          ProtectionDomain protectionDomain, byte[] classfileBuffer)
    {
        ClassReader reader = new ClassReader(classfileBuffer);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS|ClassWriter.COMPUTE_FRAMES);
        MapPutImplementorAdapter adapter = new MapPutImplementorAdapter(writer, loader, className);

        try {
            reader.accept(adapter, ClassReader.EXPAND_FRAMES);
                byte[] newBytes = writer.toByteArray();
                maybeDumpClass(className, newBytes);
                return newBytes;
        } catch (Exception e) {
            System.err.println("JokreTransformer : Failed to extend MAP API class " + className);
            System.err.println("JokreTransformer.transform : exception " + e);
            e.printStackTrace(System.err);
            return classfileBuffer;
        }
    }

    public static void maybeDumpClass(String fullName, byte[] bytes)
    {
        if (dumpGeneratedClasses) {
            String externalName = fullName.replace('/', '.');
            dumpClass(externalName, bytes);
        }
    }

    public void traceTransform(String className, List<String> methodNames)
    {
        StringBuilder builder = new StringBuilder();
        builder.append("transform : ");
        builder.append(className);
        Iterator<String> iterator = methodNames.iterator();

        builder.append("[");
        String prefix = "";
        while (iterator.hasNext()) {
            String methodName = iterator.next();
            builder.append(prefix);
            builder.append(methodName);
            prefix = ", ";
        }
        builder.append("]");
        System.out.println(builder.toString());
    }
    /* helper methods to dump class files */

    private static void dumpClass(String fullName, byte[] bytes)
    {
        dumpClass(fullName, bytes, false);
    }

    private static void dumpClass(String fullName, byte[] bytes, boolean intermediate)
    {
        // wrap this in a try catch in case the file i/o code generates a runtime exception
        // this may happen e.g. because of a security restriction
        try {
            int dotIdx = fullName.lastIndexOf('.');

            String name = (dotIdx < 0 ? fullName : fullName.substring(dotIdx + 1));
            String prefix = (dotIdx > 0 ? File.separator + fullName.substring(0, dotIdx) : "");
            String dir = dumpGeneratedClassesDir + prefix.replace('.', File.separatorChar);
            if (!ensureDumpDirectory(dir)) {
                System.out.println("JokreTransformer : Cannot dump transformed bytes to directory " + dir + File.separator + prefix);
                return;
            }
            String newname;
            if (intermediate) {
                int counter = 0;
                // add _<n> prefix until we come up with a new name
                newname = dir + File.separator + name + "_" + counter + ".class";
                File file = new File(newname);
                while (file.exists()) {
                    counter++;
                    newname = dir + File.separator + name + "_" + counter + ".class";
                    file = new File(newname);
                }
            } else {
                newname = dir + File.separator + name + ".class";
            }
            System.out.println("JokreTransformer : Saving transformed bytes to " + newname);
            try {
                FileOutputStream fio = new FileOutputStream(newname);
                fio.write(bytes);
                fio.close();
            } catch (IOException ioe) {
                System.out.println("Error saving transformed bytes to" + newname);
                ioe.printStackTrace(System.out);
            }
        } catch (Throwable th) {
            System.out.println("JokreTransformer : Error saving transformed bytes for class " + fullName);
            th.printStackTrace(System.out);
        }
    }

    private static boolean ensureDumpDirectory(String fileName)
    {
        File file = new File(fileName);
        if (file.exists()) {
            return (file.isDirectory() && file.canWrite());
        } else {
            return file.mkdirs();
        }
    }

    /**
     *  switch to control dumping of generated bytecode to .class files
     */
    private static boolean dumpGeneratedClasses = computeDumpGeneratedClasses();

    /**
     *  directory in which to dump generated bytecode .class files (defaults to "."
     */
    private static String dumpGeneratedClassesDir = computeDumpGeneratedClassesDir();

    public static boolean computeDumpGeneratedClasses()
    {
        return System.getProperty(DUMP_GENERATED_CLASSES) != null;
    }

    public static String computeDumpGeneratedClassesDir()
    {
        String userDir = System.getProperty(DUMP_GENERATED_CLASSES_DIR);
        if (userDir != null) {
            File userFile = new File(userDir);
            if (userFile.exists() && userFile.isDirectory() && userFile.canWrite()) {
                return userDir;
            } else {
                return ".";
            }
        } else {
            return ".";
        }
    }

    /**
     * system property set (to any value) in order to switch on dumping of generated bytecode to .class files
     */
    public static final String JOKRE_PACKAGE_PREFIX = "org.jboss.jokre.";

    /**
     * system property set (to any value) in order to switch on dumping of generated bytecode to .class files
     */
    public static final String DUMP_GENERATED_CLASSES = JOKRE_PACKAGE_PREFIX + "dump.generated.classes";

    /**
     * system property set (to any value) in order to switch on dumping of generated bytecode to .class files
     */
    public static final String DUMP_GENERATED_CLASSES_DIR = JOKRE_PACKAGE_PREFIX + "dump.generated.classes.directory";
}
