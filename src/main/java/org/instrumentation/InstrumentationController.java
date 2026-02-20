package org.instrumentation;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.dynamic.DynamicType;

import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.List;

public final class InstrumentationController {
    public static void install(Instrumentation inst) {
        List<PerturbationStrategy> strategies = List.of(
                new ReturnPerturbationStrategy()
        );

        new AgentBuilder.Default()
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .ignore(net.bytebuddy.matcher.ElementMatchers.nameStartsWith("net.bytebuddy.")
                        .or(net.bytebuddy.matcher.ElementMatchers.nameStartsWith("org.junit."))
                        .or(net.bytebuddy.matcher.ElementMatchers.nameStartsWith("org.apache.maven."))
                        .or(net.bytebuddy.matcher.ElementMatchers.nameStartsWith("java."))
                        .or(net.bytebuddy.matcher.ElementMatchers.nameStartsWith("javax."))
                        .or(net.bytebuddy.matcher.ElementMatchers.nameStartsWith("jdk."))
                        .or(net.bytebuddy.matcher.ElementMatchers.nameStartsWith("sun.")))
                .type((typeDesc, classLoader, module, classBeingRedefined, pd) ->
                        isLikelyProjectClass(pd)
                                && !typeDesc.isInterface()
                                && !typeDesc.isAnnotation()
                                && !typeDesc.isEnum()
                )
                .transform((builderInstance, type, loader, module, domain) -> {
                    DynamicType.Builder<?> modifiedBuilder = builderInstance;
                    for (PerturbationStrategy strategy : strategies) {
                        modifiedBuilder = strategy.apply(modifiedBuilder);
                    }
                    return modifiedBuilder;
                })
                .installOn(inst);
    }

    private InstrumentationController() {
        throw new UnsupportedOperationException("Utility class");
    }

    private static boolean isLikelyProjectClass(ProtectionDomain pd) {
        if (pd == null || pd.getCodeSource() == null || pd.getCodeSource().getLocation() == null) {
            return false;
        }
        String loc = pd.getCodeSource().getLocation().toString();
        return loc.contains("/target/classes") || loc.contains("/target/test-classes")
                || loc.contains("\\target\\classes") || loc.contains("\\target\\test-classes");
    }
}