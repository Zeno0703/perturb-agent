package org.instrumentation;

import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.pool.TypePool;
import org.probe.PerturbationGate;
import org.probe.ProbeCatalog;

import static net.bytebuddy.matcher.ElementMatchers.any;

public class VariablePerturbationStrategy implements PerturbationStrategy {

    @Override
    public DynamicType.Builder<?> apply(DynamicType.Builder<?> builder) {
        return builder.visit(
                new AsmVisitorWrapper.ForDeclaredMethods()
                        .method(any(), new VariableAssignmentPerturber())
                        .writerFlags(net.bytebuddy.jar.asm.ClassWriter.COMPUTE_FRAMES)
        );
    }

    public static int resolveAndApply(int value, String methodName, int varIndex) {
        String locationKey = methodName + ":var:" + varIndex;
        int probeId = ProbeCatalog.idForLocation(locationKey);

        if (probeId != -1) {
            ProbeCatalog.describe(probeId, "Modified integer local variable at index " + varIndex + " in " + methodName);
            return PerturbationGate.apply(value, probeId);
        }
        return value;
    }

    public static class VariableAssignmentPerturber implements AsmVisitorWrapper.ForDeclaredMethods.MethodVisitorWrapper {
        @Override
        public MethodVisitor wrap(TypeDescription instrumentedType, MethodDescription instrumentedMethod,
                                  MethodVisitor methodVisitor, Implementation.Context implementationContext,
                                  TypePool typePool, int writerFlags, int readerFlags) {
            return new VariablePerturbationVisitor(Opcodes.ASM9, methodVisitor, instrumentedMethod.toString());
        }
    }

    public static class VariablePerturbationVisitor extends MethodVisitor {
        private final String methodName;

        public VariablePerturbationVisitor(int api, MethodVisitor methodVisitor, String methodName) {
            super(api, methodVisitor);
            this.methodName = methodName;
        }

        @Override
        public void visitVarInsn(int opcode, int varIndex) {
            if (opcode == Opcodes.ISTORE) {
                super.visitLdcInsn(methodName);
                super.visitLdcInsn(varIndex);
                super.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "org/instrumentation/VariablePerturbationStrategy",
                        "resolveAndApply",
                        "(ILjava/lang/String;I)I",
                        false
                );
            }
            super.visitVarInsn(opcode, varIndex);
        }

        @Override
        public void visitIincInsn(int varIndex, int increment) {
            super.visitVarInsn(Opcodes.ILOAD, varIndex);
            super.visitIntInsn(Opcodes.SIPUSH, increment);
            super.visitInsn(Opcodes.IADD);
            super.visitLdcInsn(methodName);
            super.visitLdcInsn(varIndex);
            super.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "org/instrumentation/VariablePerturbationStrategy",
                    "resolveAndApply",
                    "(ILjava/lang/String;I)I",
                    false
            );
            super.visitVarInsn(Opcodes.ISTORE, varIndex);
        }
    }
}