package org.instrumentation;

import net.bytebuddy.dynamic.DynamicType;

public interface PerturbationStrategy {
    DynamicType.Builder<?> apply(DynamicType.Builder<?> builder);
}