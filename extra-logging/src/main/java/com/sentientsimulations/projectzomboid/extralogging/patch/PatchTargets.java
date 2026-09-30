package com.sentientsimulations.projectzomboid.extralogging.patch;

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.pool.TypePool;

/**
 * Weave-time guard so a renamed target method fails the transform instead of silently no-opping.
 */
final class PatchTargets {

    private PatchTargets() {}

    static void require(
            TypePool typePool,
            String className,
            ElementMatcher<? super MethodDescription> method,
            String what) {
        if (typePool.describe(className).resolve().getDeclaredMethods().filter(method).isEmpty()) {
            throw new IllegalStateException(
                    className
                            + ": "
                            + what
                            + " is gone. Re-verify against the current game source.");
        }
    }
}
