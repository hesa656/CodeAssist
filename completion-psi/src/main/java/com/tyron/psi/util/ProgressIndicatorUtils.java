package com.tyron.psi.util;

import com.tyron.psi.concurrency.ProgressIndicatorBase;
import com.tyron.psi.concurrency.SensitiveProgressWrapper;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.com.intellij.openapi.progress.ProcessCanceledException;
import org.jetbrains.kotlin.com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.kotlin.com.intellij.openapi.progress.ProgressIndicatorProvider;
import org.jetbrains.kotlin.com.intellij.openapi.progress.ProgressManager;
import org.jetbrains.kotlin.com.intellij.openapi.util.Computable;
import org.jetbrains.kotlin.com.intellij.util.concurrency.AppExecutorUtil;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProgressIndicatorUtils {

    @Nullable
    public static <T> T withTimeout(long timeoutMs, @NotNull Computable<T> computation) {
        ProgressManager.checkCanceled();
        ProgressIndicator outer = ProgressIndicatorProvider.getGlobalProgressIndicator();
        ProgressIndicator inner = outer != null ? new SensitiveProgressWrapper(outer) : new ProgressIndicatorBase(false, false);
        AtomicBoolean canceledByTimeout = new AtomicBoolean();
        ScheduledFuture<?> cancelProgress = AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> {
            canceledByTimeout.set(true);
            inner.cancel();
        }, timeoutMs, TimeUnit.MILLISECONDS);
        try {
            ProgressManager.getInstance().runProcess(computation, inner);
        } catch (ProcessCanceledException e) {
            if (canceledByTimeout.get()) {
                return null;
            }
            throw e; // canceled not by timeout
        } finally {
            cancelProgress.cancel(false);
        }

        return null;
    }
}
