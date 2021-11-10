package com.tyron.psi.concurrency;

import org.jetbrains.kotlin.com.intellij.openapi.progress.StandardProgressIndicator;
import org.jetbrains.kotlin.com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase;

public class ProgressIndicatorBase extends AbstractProgressIndicatorExBase implements StandardProgressIndicator {
    public ProgressIndicatorBase() {
        super();
    }

    public ProgressIndicatorBase(boolean reusable) {
        super(reusable);
    }

    public ProgressIndicatorBase(boolean reusable, boolean allowSystemActivity) {
        super(reusable);
        if (!allowSystemActivity) dontStartActivity();
    }

    @Override
    public final void cancel() {
        super.cancel();
    }

    @Override
    public final boolean isCanceled() {
        return super.isCanceled();
    }
}
