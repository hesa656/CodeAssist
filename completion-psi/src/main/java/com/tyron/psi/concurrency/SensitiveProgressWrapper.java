package com.tyron.psi.concurrency;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.kotlin.com.intellij.openapi.progress.util.ProgressWrapper;

import java.lang.reflect.Field;

/**
 * A progress indicator wrapper, which reacts to its own cancellation in addition to the cancellation of its wrappee.
 */
public class SensitiveProgressWrapper extends ProgressWrapper {
    public SensitiveProgressWrapper(@NotNull ProgressIndicator indicator) {
        try {
            Field field = ProgressWrapper.class.getDeclaredField("myOriginal");
            field.setAccessible(true);
            field.set(this, indicator);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}