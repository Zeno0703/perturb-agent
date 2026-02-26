package org.instrumentation;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import org.probe.PerturbationGate;
import org.probe.ProbeCatalog;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isNative;

public class ArgumentPerturbationStrategy implements PerturbationStrategy {

    @Override
    public DynamicType.Builder<?> apply(DynamicType.Builder<?> builder) {
        return builder.visit(
                Advice.to(ArgumentAdvice.class).on(
                        isMethod()
                                .and(not(isConstructor()))
                                .and(not(isAbstract()))
                                .and(not(isNative()))
                )
        );
    }

    public static class ArgumentAdvice {
        @Advice.OnMethodEnter
        public static void enter(
                @Advice.AllArguments(readOnly = false, typing = Assigner.Typing.DYNAMIC) Object[] args,
                @Advice.Origin String locationKey
        ) {
            if (args != null && args.length > 0) {
                Object[] modifiedArgs = new Object[args.length];
                System.arraycopy(args, 0, modifiedArgs, 0, args.length);
                boolean writeBack = false;

                for (int i = 0; i < modifiedArgs.length; i++) {
                    if (modifiedArgs[i] instanceof Integer) {
                        String argKey = locationKey + ":arg:" + i;
                        int probeId = ProbeCatalog.idForLocation(argKey);
                        if (probeId != -1) {
                            ProbeCatalog.describe(probeId, "Modified Integer argument at index " + i + " in " + locationKey);
                            Integer num = (Integer) modifiedArgs[i];
                            modifiedArgs[i] = PerturbationGate.apply(num.intValue(), probeId);
                            writeBack = true;
                        }
                    } else if (modifiedArgs[i] instanceof Boolean) {
                        String argKey = locationKey + ":arg:" + i;
                        int probeId = ProbeCatalog.idForLocation(argKey);
                        if (probeId != -1) {
                            ProbeCatalog.describe(probeId, "Modified boolean argument at index " + i + " in " + locationKey);
                            modifiedArgs[i] = PerturbationGate.apply(((Boolean) modifiedArgs[i]).booleanValue(), probeId);
                            writeBack = true;
                        }
                    } else if (modifiedArgs[i] != null) {
                        String argKey = locationKey + ":arg:" + i;
                        int probeId = ProbeCatalog.idForLocation(argKey);
                        if (probeId != -1) {
                            ProbeCatalog.describe(probeId, "Modified Object argument at index " + i + " in " + locationKey);
                            modifiedArgs[i] = PerturbationGate.apply(modifiedArgs[i], probeId);
                            writeBack = true;
                        }
                    }
                }

                if (writeBack) {
                    args = modifiedArgs;
                }
            }
        }
    }
}