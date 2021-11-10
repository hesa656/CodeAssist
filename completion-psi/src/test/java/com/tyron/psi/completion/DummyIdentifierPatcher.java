package com.tyron.psi.completion;


import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.com.intellij.openapi.editor.Document;
import org.jetbrains.kotlin.com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.kotlin.com.intellij.psi.PsiFile;

/**
 * @author peter
 */
public class DummyIdentifierPatcher extends FileCopyPatcher {
    private final String myDummyIdentifier;

    public DummyIdentifierPatcher(final String dummyIdentifier) {
        myDummyIdentifier = dummyIdentifier;
    }

    @Override
    public void patchFileCopy(@NotNull final PsiFile fileCopy, @NotNull final Document document, @NotNull final OffsetMap map) {
        if (StringUtil.isEmpty(myDummyIdentifier)) return;
        int startOffset = map.getOffset(CompletionInitializationContext.START_OFFSET);
        int endOffset = map.getOffset(CompletionInitializationContext.SELECTION_END_OFFSET);
        document.replaceString(startOffset, endOffset, myDummyIdentifier);
    }

    @Override
    public String toString() {
        return "Insert \"" + myDummyIdentifier + "\"";
    }
}