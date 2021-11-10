package com.tyron.psi.completion;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.com.intellij.openapi.editor.Document;
import org.jetbrains.kotlin.com.intellij.psi.PsiFile;
import org.jetbrains.kotlin.com.intellij.psi.PsiReference;

/**
 * @author peter
 */
public abstract class FileCopyPatcher {

    /**
     * On completion, a file copy is created and this method is invoked on corresponding document. This is usually
     * done to ensure that there is some non-whitespace text at caret position, for example, to find reference at
     * that offset and ask for its {@link PsiReference#getVariants()}. In
     * {@link CompletionContributor} it will also be easier to determine which
     * variants to suggest at current position.
     *
     * Default implementation is {@link DummyIdentifierPatcher} which
     * inserts {@link CompletionInitializationContext#DUMMY_IDENTIFIER}
     * to the document replacing editor selection (see {@link CompletionInitializationContext#START_OFFSET} and
     * {@link CompletionInitializationContext#SELECTION_END_OFFSET}).
     *
     * @param fileCopy
     * @param document
     * @param map {@link CompletionInitializationContext#START_OFFSET} should be valid after return
     */
    public abstract void patchFileCopy(@NotNull final PsiFile fileCopy, @NotNull Document document, @NotNull OffsetMap map);

}
