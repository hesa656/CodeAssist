package com.tyron.psi.completion;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.com.intellij.openapi.editor.Document;
import org.jetbrains.kotlin.com.intellij.openapi.editor.impl.DocumentImpl;
import org.jetbrains.kotlin.com.intellij.openapi.progress.EmptyProgressIndicator;
import org.jetbrains.kotlin.com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.kotlin.com.intellij.openapi.progress.ProgressIndicatorProvider;
import org.jetbrains.kotlin.com.intellij.openapi.progress.ProgressManager;
import org.jetbrains.kotlin.com.intellij.openapi.project.Project;
import org.jetbrains.kotlin.com.intellij.openapi.util.TextRange;
import org.jetbrains.kotlin.com.intellij.pom.PomManager;
import org.jetbrains.kotlin.com.intellij.pom.PomModel;
import org.jetbrains.kotlin.com.intellij.pom.PomModelAspect;
import org.jetbrains.kotlin.com.intellij.pom.PomTransaction;
import org.jetbrains.kotlin.com.intellij.pom.event.PomModelEvent;
import org.jetbrains.kotlin.com.intellij.pom.impl.PomTransactionBase;
import org.jetbrains.kotlin.com.intellij.psi.PsiElement;
import org.jetbrains.kotlin.com.intellij.psi.PsiFile;
import org.jetbrains.kotlin.com.intellij.psi.impl.ChangedPsiRangeUtil;
import org.jetbrains.kotlin.com.intellij.psi.impl.DiffLog;
import org.jetbrains.kotlin.com.intellij.psi.impl.source.tree.FileElement;
import org.jetbrains.kotlin.com.intellij.psi.text.BlockSupport;
import org.jetbrains.kotlin.com.intellij.util.IncorrectOperationException;

import java.util.function.Supplier;

public class OffsetsInFile {

    private final PsiFile file;
    private final OffsetMap offsets;

    public OffsetsInFile(PsiFile file) {
        this(file, new OffsetMap(file.getViewProvider().getDocument()));
    }

    public OffsetsInFile(PsiFile file, OffsetMap offsets) {
        this.file = file;
        this.offsets = offsets;
    }

    public PsiFile getFile() {
        return file;
    }

    public OffsetMap getOffsets() {
        return offsets;
    }

    public Supplier<OffsetsInFile> replaceInCopy(PsiFile hostCopy, int startOffset, int endOffset, String replacement) {
        CharSequence originalText = offsets.getDocument().getImmutableCharSequence();
        Document tempDocument = new DocumentImpl(originalText, originalText.toString().contains("\r") ||  replacement.contains("\r"), true);
        OffsetMap tempMap = offsets.copyOffsets(tempDocument);
        tempDocument.replaceString(startOffset, endOffset, replacement);

        Document copyDocument = hostCopy.getViewProvider().getDocument();
    }

    private Runnable reparseFile(@NotNull Project myProject, @NotNull PsiFile file, @NotNull FileElement treeElement, @NotNull CharSequence newText) {
        TextRange changedPsiRange = ChangedPsiRangeUtil.getChangedPsiRange(file, treeElement, newText);
        if (changedPsiRange == null) return null;

        ProgressIndicator indicator = EmptyProgressIndicator.notNullize(ProgressIndicatorProvider.getGlobalProgressIndicator());
        DiffLog log = BlockSupport.getInstance(myProject).reparseRange(file, treeElement, changedPsiRange, newText, indicator, treeElement.getText());

        return () -> {

        };
    }

}
