package org.instrumentation;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.DynamicType;
import org.probe.PerturbationGate;
import org.probe.ProbeCatalog;

import static net.bytebuddy.matcher.ElementMatchers.returns;

public class ReturnPerturbationStrategy implements PerturbationStrategy {

    @Override
    public DynamicType.Builder<?> apply(DynamicType.Builder<?> builder) {
        return builder
                .visit(Advice.to(IntegerAdvice.class).on(returns(int.class)))
                .visit(Advice.to(BooleanAdvice.class).on(returns(boolean.class)));
    }

    public static int resolveProbe(String locationKey, String type) {
        int probeId = ProbeCatalog.idForLocation(locationKey);
        if (probeId != -1) {
            ProbeCatalog.describe(probeId, type + " return modified at " + locationKey);
        }
        return probeId;
    }

    public static class IntegerAdvice {
        @Advice.OnMethodExit
        public static void exit(@Advice.Return(readOnly = false) int returnValue,
                                @Advice.Origin String locationKey) {

            int probeId = resolveProbe(locationKey, "int");
            if (probeId != -1) {
                returnValue = PerturbationGate.apply(returnValue, probeId);
            }
        }
    }

    public static class BooleanAdvice {
        @Advice.OnMethodExit
        public static void exit(@Advice.Return(readOnly = false) boolean returnValue,
                                @Advice.Origin String locationKey) {

            int probeId = resolveProbe(locationKey, "boolean");
            if (probeId != -1) {
                returnValue = PerturbationGate.apply(returnValue, probeId);
            }
        }
    }
}