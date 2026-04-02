package org.instrumentation;

import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import java.util.Map;

public interface PerturbationStrategy {
    DynamicType.Builder<?> apply(DynamicType.Builder<?> builder,
                                 TypeDescription typeDesc,
                                 ClassLoader classLoader,
                                 Map<String, AsmMethodAnalyser.MethodLineInfo> lineInfoMap);
}