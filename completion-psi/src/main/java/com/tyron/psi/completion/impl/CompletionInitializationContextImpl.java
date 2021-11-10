package com.tyron.psi.completion.impl;

import com.tyron.psi.completion.CompletionInitializationContext;
import com.tyron.psi.completion.CompletionType;
import com.tyron.psi.editor.Caret;
import com.tyron.psi.editor.Editor;

import org.jetbrains.kotlin.com.intellij.lang.Language;
import org.jetbrains.kotlin.com.intellij.psi.PsiFile;

public class CompletionInitializationContextImpl extends CompletionInitializationContext {
    public CompletionInitializationContextImpl(Editor editor, Caret caret, Language language, PsiFile file, CompletionType completionType, int invocationCount) {
        super(editor, caret, language, file, completionType, invocationCount);
    }
}
