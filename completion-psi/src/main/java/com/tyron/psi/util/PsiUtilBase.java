package com.tyron.psi.util;

import com.tyron.psi.editor.Caret;
import com.tyron.psi.editor.Editor;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.com.intellij.lang.Language;
import org.jetbrains.kotlin.com.intellij.openapi.project.Project;
import org.jetbrains.kotlin.com.intellij.psi.PsiDocumentManager;
import org.jetbrains.kotlin.com.intellij.psi.PsiFile;
import org.jetbrains.kotlin.com.intellij.psi.PsiFileWithOneLanguage;
import org.jetbrains.kotlin.com.intellij.psi.util.PsiUtilCore;

public class PsiUtilBase {

    @Nullable
    public static PsiFile getPsiFileInEditor(@NotNull Editor editor, @NotNull Project project) {
        final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        if (file == null) {
            return null;
        }

        return file;
    }

    @Nullable
    public static PsiFile getPsiFileInEditor(@NotNull Caret caret, @NotNull Project project) {
        Editor editor = caret.getEditor();
        final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
        if (file == null) return null;

        PsiUtilCore.ensureValid(file);

        if (file instanceof PsiFileWithOneLanguage) {
            return file;
        }

        return file;
    }

}
