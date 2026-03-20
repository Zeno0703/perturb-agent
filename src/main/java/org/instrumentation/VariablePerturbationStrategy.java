package org.instrumentation;

import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.pool.TypePool;
import org.probe.ProbeCatalog;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static net.bytebuddy.matcher.ElementMatchers.any;

public class VariablePerturbationStrategy implements PerturbationStrategy {

    private static final Map<String, Map<String, Set<Integer>>> BOOLEAN_SLOT_CACHE = new HashMap<>();

    @Override
    public DynamicType.Builder<?> apply(DynamicType.Builder<?> builder) {
        return builder.visit(
                new AsmVisitorWrapper.ForDeclaredMethods()
                        .method(any(), new VariableAssignmentPerturber())
                        .writerFlags(net.bytebuddy.jar.asm.ClassWriter.COMPUTE_FRAMES)
        );
    }

    static Map<String, Set<Integer>> scanBooleanSlots(ClassLoader loader, String classResourcePath) {
        if (BOOLEAN_SLOT_CACHE.containsKey(classResourcePath)) {
            return BOOLEAN_SLOT_CACHE.get(classResourcePath);
        }

        Map<String, Set<Integer>> result = new HashMap<>();
        if (loader == null) loader = ClassLoader.getSystemClassLoader();

        try (InputStream is = loader.getResourceAsStream(classResourcePath)) {
            if (is == null) return result;
            ClassReader cr = new ClassReader(is);
            cr.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    String key = name + descriptor;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitLocalVariable(String varName, String varDesc, String sig, Label start, Label end, int index) {
                            if (varDesc.equals("Z")) {
                                result.computeIfAbsent(key, k -> new HashSet<>()).add(index);
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_FRAMES);
        } catch (IOException ignored) {}

        BOOLEAN_SLOT_CACHE.put(classResourcePath, result);
        return result;
    }

    public static class VariableAssignmentPerturber implements AsmVisitorWrapper.ForDeclaredMethods.MethodVisitorWrapper {
        @Override
        public MethodVisitor wrap(TypeDescription instrumentedType,
                                  MethodDescription instrumentedMethod,
                                  MethodVisitor methodVisitor,
                                  Implementation.Context implementationContext,
                                  TypePool typePool,
                                  int writerFlags,
                                  int readerFlags) {

            if (instrumentedMethod.isSynthetic() || instrumentedMethod.isBridge() || instrumentedMethod.getName().contains("$")) {
                return methodVisitor;
            }

            String classResourcePath = instrumentedType.getName().replace('.', '/') + ".class";
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            Map<String, Set<Integer>> allBooleanSlots = scanBooleanSlots(loader, classResourcePath);

            String asmMethodKey = (instrumentedMethod.isConstructor() ? "<init>" : instrumentedMethod.getInternalName())
                    + instrumentedMethod.getDescriptor();
            Set<Integer> booleanSlots = allBooleanSlots.getOrDefault(asmMethodKey, Collections.emptySet());

            return new VariablePerturbationVisitor(Opcodes.ASM9, methodVisitor, instrumentedMethod.toString(), booleanSlots);
        }
    }

    public static class VariablePerturbationVisitor extends MethodVisitor {
        private final String methodName;
        private final Set<Integer> booleanSlots;
        private final List<PendingProbe> pendingProbes = new ArrayList<>();
        private final Map<Integer, LvtData> lvtEntries = new HashMap<>();
        private int currentLine = -1;

        public VariablePerturbationVisitor(int api, MethodVisitor methodVisitor, String methodName, Set<Integer> booleanSlots) {
            super(api, methodVisitor);
            this.methodName = methodName;
            this.booleanSlots = booleanSlots;
        }

        @Override
        public void visitLineNumber(int line, Label start) {
            currentLine = line;
            super.visitLineNumber(line, start);
        }

        @Override
        public void visitVarInsn(int opcode, int varIndex) {
            if (opcode == Opcodes.ISTORE) {
                boolean isBoolean = booleanSlots.contains(varIndex);

                int probeId = ProbeCatalog.idForLocation(methodName + ":var:" + varIndex);
                ProbeCatalog.setLine(probeId, currentLine);
                if (isBoolean) {
                    pendingProbes.add(new PendingProbe(probeId, varIndex, "boolean", false));

                    super.visitLdcInsn(probeId);
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, "org/probe/PerturbationGate", "apply", "(ZI)Z", false);
                } else {
                    pendingProbes.add(new PendingProbe(probeId, varIndex, "Integer", false));

                    super.visitLdcInsn(probeId);
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, "org/probe/PerturbationGate", "apply", "(II)I", false);
                }
            } else if (opcode == Opcodes.ASTORE) {
                int probeId = ProbeCatalog.idForLocation(methodName + ":objVar:" + varIndex);
                ProbeCatalog.setLine(probeId, currentLine);
                pendingProbes.add(new PendingProbe(probeId, varIndex, "Object", false));

                super.visitInsn(Opcodes.DUP);
                super.visitLdcInsn(probeId);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, "org/probe/PerturbationGate", "checkAndTrackObject", "(Ljava/lang/Object;I)Z", false);
                Label skip = new Label();
                super.visitJumpInsn(Opcodes.IFEQ, skip);
                super.visitInsn(Opcodes.POP);
                super.visitInsn(Opcodes.ACONST_NULL);
                super.visitLabel(skip);
            }
            super.visitVarInsn(opcode, varIndex);
        }

        @Override
        public void visitIincInsn(int varIndex, int increment) {
            int probeId = ProbeCatalog.idForLocation(methodName + ":var:" + varIndex);
            ProbeCatalog.setLine(probeId, currentLine);
            pendingProbes.add(new PendingProbe(probeId, varIndex, "Integer", true));

            super.visitVarInsn(Opcodes.ILOAD, varIndex);
            super.visitIntInsn(Opcodes.SIPUSH, increment);
            super.visitInsn(Opcodes.IADD);
            super.visitLdcInsn(probeId);
            super.visitMethodInsn(Opcodes.INVOKESTATIC, "org/probe/PerturbationGate", "apply", "(II)I", false);
            super.visitVarInsn(Opcodes.ISTORE, varIndex);
        }

        @Override
        public void visitLocalVariable(String name, String descriptor, String signature, Label start, Label end, int index) {
            if (!name.equals("this") && !name.startsWith("this$")) {
                String type = "unknown";
                if (descriptor.equals("Z")) type = "boolean";
                else if (descriptor.equals("I") || descriptor.equals("S") || descriptor.equals("B") || descriptor.equals("C")) type = "Integer";
                else if (descriptor.startsWith("[") || descriptor.startsWith("L")) type = "Object";

                if (!type.equals("unknown")) {
                    lvtEntries.putIfAbsent(index, new LvtData(name, type));
                }
            }
            super.visitLocalVariable(name, descriptor, signature, start, end, index);
        }

        @Override
        public void visitEnd() {
            boolean lvtPresent = !lvtEntries.isEmpty();

            for (PendingProbe p : pendingProbes) {
                if (lvtPresent && lvtEntries.containsKey(p.slot)) {
                    LvtData data = lvtEntries.get(p.slot);
                    ProbeCatalog.describe(p.id, "Modified " + data.type + " local variable '" + data.name + "' in " + methodName);
                } else {
                    ProbeCatalog.describe(p.id, "Modified " + p.fallbackType + " local variable (JVM slot " + p.slot + ") in " + methodName);
                }
            }
            super.visitEnd();
        }

        private record PendingProbe(int id, int slot, String fallbackType, boolean isIinc) {}

        private record LvtData(String name, String type) {}
    }
}