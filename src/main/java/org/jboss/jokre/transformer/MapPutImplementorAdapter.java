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

import org.objectweb.asm.ClassAdapter;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import static org.jboss.jokre.transformer.MapAdapterConstants.*;

/**
 * Adapter used to transform implementors of Map.put into a potentially more efficient implementation
 */
public class MapPutImplementorAdapter extends ClassAdapter
{
    private ClassLoader loader;
    private String className;
    private boolean transformed;
    String[] exceptions;
    String[] asyncExceptions;
    String signature;
    String asyncSignature;

    public MapPutImplementorAdapter(ClassVisitor cv, ClassLoader loader, String className)
    {
        super(cv);
        this.loader = loader;
        this.className =  className;
        this.transformed = false;
        this.exceptions = null;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces)
    {
        // add the extra interface which we want this class to implement

        final int length = interfaces.length;
        String[] newInterfaces = new String[length +1];
        for (int i = 0; i < length; i++) {
            newInterfaces[i]= interfaces[i];
        }
        newInterfaces[length] = MapAdapterConstants.CLASS_NON_RETURN_MAP;

        super.visit(version, access, name, signature, superName, newInterfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions)
    {
        // rename put method to be put$originalSlowPath and make it private

        if (name.equals(SET_METHOD_NAME) && desc.equals(SET_METHOD_DESC))
        {
            this.asyncExceptions = exceptions;
            this.asyncSignature = signature;
        } else if (name.equals(PUT_METHOD_NAME) && desc.equals(PUT_METHOD_DESC))
        {
            this.exceptions = exceptions;
            this.signature = signature;

            // rename this as a private method so we can reuse the implementation
            name = PUT_METHOD_ORIGINAL_SLOW_PATH_NAME;
            access |= Opcodes.ACC_PRIVATE;
            access &= ~Opcodes.ACC_PUBLIC;
        }
        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);

        return mv;
    }

    public void visitEnd()
    {
        int access = Opcodes.ACC_PUBLIC;

        // generate rewwritten put method which is instrumented and calls the original slowpath
        MethodVisitor mv = super.visitMethod(access, PUT_METHOD_NAME, PUT_METHOD_DESC, signature, exceptions);
        mv.visitCode();
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, CLASS_JOKRE, NOTIFY_MAP_PUT_METHOD_NAME, NOTIFY_MAP_PUT_METHOD_DESC);
        mv.visitInsn(Opcodes.POP);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, className, PUT_METHOD_ORIGINAL_SLOW_PATH_NAME, PUT_METHOD_DESC);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(3, 3);
        mv.visitEnd();

        // generate put$fastPath which calls putAsync

        mv = super.visitMethod(access, PUT_METHOD_FAST_PATH_NAME, SET_METHOD_DESC, asyncSignature, exceptions);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, className, SET_METHOD_NAME, SET_METHOD_DESC);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(3, 3);
        mv.visitEnd();

        // generate put$alternativeSlowPath which is not instrumented and calls the original slowpath
        mv = super.visitMethod(access, PUT_METHOD_ALTERNATIVE_SLOW_PATH_NAME, PUT_METHOD_DESC, signature, exceptions);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitVarInsn(Opcodes.ALOAD, 2);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, className, PUT_METHOD_ORIGINAL_SLOW_PATH_NAME, PUT_METHOD_DESC);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(3, 3);
        mv.visitEnd();
    }
}