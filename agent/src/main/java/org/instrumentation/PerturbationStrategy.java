package org.instrumentation;

import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import java.util.Map;

public interface PerturbationStrategy {
    // Centralized ASM Constants
    String GATE_CLASS = "org/probe/PerturbationGate";
    String GATE_METHOD = "apply";
    String DESC_INT = "(II)I";
    String DESC_BOOL = "(ZI)Z";
    String DESC_OBJ = "(Ljava/lang/Object;I)Ljava/lang/Object;";

    String GATE_METHOD_CHECK = "checkAndTrackObject";
    String DESC_CHECK_OBJ = "(Ljava/lang/Object;I)Z";

    DynamicType.Builder<?> apply(DynamicType.Builder<?> builder,
                                 TypeDescription typeDesc,
                                 ClassLoader classLoader,
                                 Map<String, AsmMethodAnalyser.MethodLineInfo> lineInfoMap);
}