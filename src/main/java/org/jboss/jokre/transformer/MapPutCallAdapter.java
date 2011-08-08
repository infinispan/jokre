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

import org.objectweb.asm.*;
import org.objectweb.asm.commons.JSRInlinerAdapter;
import static org.jboss.jokre.transformer.MapAdapterConstants.*;

import java.util.List;

/**
 * Adapter used to transform calls to Map.put into a potentially more efficient implementation
 */
public class MapPutCallAdapter extends ClassAdapter
{
    private ClassLoader loader;
    private String className;
    private List<String> methodNames;
    private boolean transformed;

    public MapPutCallAdapter(ClassVisitor cv, ClassLoader loader, String className, List<String> methodNames)
    {
        super(cv);
        this.loader = loader;
        this.className =  className;
        this.methodNames = methodNames;
        this.transformed = false;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions)
    {
        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);

        if (methodNames.contains(name))
        {
            // TODO -- see if we really need to use a JSR inliner
            MapPutCallMethodAdapter adapter = new MapPutCallMethodAdapter(mv, access, name, desc, signature, exceptions);
            //return adapter;
            MethodVisitor inliner = new JSRInlinerAdapter(adapter, access, name, desc, signature, exceptions);
            return inliner;
            // return new MapPutMethodAdapter(inliner, access, name, desc, signature, exceptions);
        } else {
            //return mv;
            MethodVisitor inliner = new JSRInlinerAdapter(mv, access, name, desc, signature, exceptions);
            return inliner;
        }
    }

    public boolean isTransformed() {
        return transformed;
    }

    /**
     * method adapter which identifies Map.put calls and transforms them
     */
    public class MapPutCallMethodAdapter extends MethodAdapter
    {
        private int access;
        private String name;
        private String desc;
        private String signature;
        private String[] exceptions;

        private boolean isPending;
        private int pendingOpcode;
        private String pendingOwner;
        private boolean methodTransformed;

        public MapPutCallMethodAdapter(MethodVisitor mv, int access, String name, String desc, String signature, String[] exceptions)
        {
            super(mv);
            this.access = access;
            this.name = name;
            this.desc = desc;
            this.signature = signature;
            this.exceptions =  exceptions;
            isPending = false;
            pendingOwner = null;
            pendingOpcode = 0;
            methodTransformed = false;
        }

        /**
         * if a put call was pending from the last instruction then generate it now
         * throwing away however many operands ar appropriate
         *
         * @return true if there was a pending call otherwise false
         */
        public boolean generatePending(boolean throwAway)
        {
            if (!isPending) {
                return false;
            }

            // clear the pending flag before we generate the required instructions

            isPending = false;
            transformed = true;
            methodTransformed = true;

            // generate the requred put call sequence if a put call is pending and
            // then clear the pending flag

            Label l1 = new Label();
            Label l2 = new Label();
            // [... map, key, value] ==> [... key, value, map, key, value]
            super.visitInsn(Opcodes.DUP2_X1);
            // [... key, value, map, key, value] ==> [... key, value, map]
            super.visitInsn(Opcodes.POP2);
            // [... key, value, map] ==> [... key, value, map, map]
            super.visitInsn(Opcodes.DUP);
            // [... key, value, map, map] ==> [... key, value, map, bool]
            super.visitTypeInsn(Opcodes.INSTANCEOF, CLASS_NON_RETURN_MAP);
            // [... key, value, map, bool] ==> [... key, value, map]
            super.visitJumpInsn(Opcodes.IFEQ, l1);
            // [... key, value, map ] ==> [... key, value, map]
            super.visitTypeInsn(Opcodes.CHECKCAST, CLASS_NON_RETURN_MAP);
            // [... key, value, map ] ==> [... map, key, value, map]
            super.visitInsn(Opcodes.DUP_X2);
            // [... map, key, value, map ] ==> [... map, key, value]
            super.visitInsn(Opcodes.POP);
            if (throwAway) {
                // [... map, key, value] ==> [...]
                super.visitMethodInsn(Opcodes.INVOKEINTERFACE, CLASS_NON_RETURN_MAP, PUT_METHOD_FAST_PATH_NAME, SET_METHOD_DESC);
            } else {
                // [... map, key, value] ==> [... retvalue]
                super.visitMethodInsn(Opcodes.INVOKEINTERFACE, CLASS_NON_RETURN_MAP, PUT_METHOD_ALTERNATIVE_SLOW_PATH_NAME, PUT_METHOD_DESC);
            }
            super.visitJumpInsn(Opcodes.GOTO, l2);
            super.visitLabel(l1);
            // [... key, value, map ] ==> [... map, key, value, map]
            super.visitInsn(Opcodes.DUP_X2);
            // [... map, key, value, map ] ==> [... map, key, value]
            super.visitInsn(Opcodes.POP);
            // [... map, key, value] ==> [..., retval]
            super.visitMethodInsn(pendingOpcode, pendingOwner, PUT_METHOD_NAME, PUT_METHOD_DESC);
            // [..., retval] ==> [...]
            if (throwAway) {
                super.visitInsn(Opcodes.POP);
            }
            super.visitLabel(l2);

            // we return true here to indicate that the pop has been taken care of.
            // if the caller was going to generate a pop then this will inhibit it.

            return true;
        }

        @Override
        public void visitInsn(int opcode) {
            if (opcode == Opcodes.POP) {
                if (!generatePending(true)) {
                    super.visitInsn(opcode);
                }
            } else {
                generatePending(false);
                super.visitInsn(opcode);
            }
        }

        @Override
        public void visitIntInsn(int opcode, int operand) {
            generatePending(false);
            super.visitIntInsn(opcode, operand);
        }

        @Override
        public void visitVarInsn(int opcode, int var) {
            generatePending(false);
            super.visitVarInsn(opcode, var);
        }

        @Override
        public void visitTypeInsn(int opcode, String desc) {
            generatePending(false);
            super.visitTypeInsn(opcode, desc);
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
            generatePending(false);
            super.visitFieldInsn(opcode, owner, name, desc);
        }

        @Override
        public void visitJumpInsn(int opcode, Label label) {
            generatePending(false);
            super.visitJumpInsn(opcode, label);
        }

        @Override
        public void visitLdcInsn(Object cst) {
            generatePending(false);
            super.visitLdcInsn(cst);
        }

        @Override
        public void visitIincInsn(int var, int increment) {
            generatePending(false);
            super.visitIincInsn(var, increment);
        }

        @Override
        public void visitTableSwitchInsn(int min, int max, Label dflt, Label[] labels) {
            generatePending(false);
            super.visitTableSwitchInsn(min, max, dflt, labels);
        }

        @Override
        public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
            generatePending(false);
            super.visitLookupSwitchInsn(dflt, keys, labels);
        }

        @Override
        public void visitMultiANewArrayInsn(String desc, int dims) {
            generatePending(false);
            super.visitMultiANewArrayInsn(desc, dims);
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc) {
            generatePending(false);

            // we are interested in cases where the call is to java.util.Map.put() or
            // java.util.concurrent.ConcurrentMap.put(). These are the only two map
            // interfaces the Infinispan caches implement. We also want to replace
            // direct calls to org.infinispan.Cache.put() or to any class which
            // implements org.infinispan.Cache.put()

            if (!name.equals(PUT_METHOD_NAME) || !desc.equals(PUT_METHOD_DESC)) {
                super.visitMethodInsn(opcode, owner, name, desc);
                return;
            }

            switch(opcode) {
                case Opcodes.INVOKEINTERFACE:
                {
                    if (owner.equals(CLASS_MAP) ||
                            owner.equals(CLASS_CONCURRENT_MAP) ||
                            owner.equals(CLASS_CACHE) ||
                            owner.equals(CLASS_ADVANCED_CACHE))
                    {
                        // delay generation of the put call until we see the next instruction
                        isPending  = true;
                        pendingOwner = owner;
                        pendingOpcode = opcode;
                        return;
                    }
                }
                break;
                case Opcodes.INVOKEVIRTUAL:
                {
                    if (isNonReturnMap(owner)) {
                        // delay generation of the put call until we see the next instruction
                        isPending  = true;
                        pendingOwner = owner;
                        pendingOpcode = opcode;
                        return;
                    }
                    break;
                }
            }
            // ok generate the call now
            super.visitMethodInsn(opcode, owner, name, desc);
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            // high water mark is 2 higher when we generate the transformed call
            // if (methodTransformed) {
            //     maxStack += 2;
            // }
            super.visitMaxs(maxStack, maxLocals);
        }

        private boolean isNonReturnMap(String owner)
        {
            //  TODO we use a class load here for now but we may need to avoid this later

            Class<?> ownerClass;
            try {
                ownerClass = loader.loadClass(owner.replace('/', '.'));
            } catch (Exception e) {
                return false;
            }

            return isInfinispanCacheClass(ownerClass);
        }

        private boolean isInfinispanCacheClass(Class<?> candidate)
        {
            // n.b. reflection gives us class names in external format
            if (candidate.getName().equals(MapAdapterConstants.CLASS_NON_RETURN_MAP_EXTERNAL)) {
                return true;
            }
            // check local interfaces
            Class<?> interfaces[] = candidate.getInterfaces();
            for (int i = 0; i < interfaces.length; i++) {
                if (isInfinispanCacheClass(interfaces[i])) {
                    return true;
                }
            }
            Class<?> superclass = candidate.getSuperclass();
            if (superclass != Object.class && superclass != null) {
                return isInfinispanCacheClass(superclass);
            }

            return false;
        }
    }
}