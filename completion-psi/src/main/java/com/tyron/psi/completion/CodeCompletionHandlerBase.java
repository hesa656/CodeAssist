package com.tyron.psi.completion;

import com.tyron.psi.completion.impl.CompletionInitializationContextImpl;
import com.tyron.psi.editor.Caret;
import com.tyron.psi.editor.Editor;
import com.tyron.psi.util.ProgressIndicatorUtils;
import com.tyron.psi.util.PsiUtilBase;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.com.intellij.lang.FileASTNode;
import org.jetbrains.kotlin.com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.kotlin.com.intellij.openapi.application.WriteAction;
import org.jetbrains.kotlin.com.intellij.openapi.command.CommandProcessor;
import org.jetbrains.kotlin.com.intellij.openapi.diagnostic.Attachment;
import org.jetbrains.kotlin.com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.kotlin.com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments;
import org.jetbrains.kotlin.com.intellij.openapi.editor.Document;
import org.jetbrains.kotlin.com.intellij.openapi.fileEditor.FileDocumentManager;
import org.jetbrains.kotlin.com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.kotlin.com.intellij.openapi.progress.ProgressIndicatorProvider;
import org.jetbrains.kotlin.com.intellij.openapi.project.Project;
import org.jetbrains.kotlin.com.intellij.openapi.util.Computable;
import org.jetbrains.kotlin.com.intellij.openapi.util.Ref;
import org.jetbrains.kotlin.com.intellij.psi.PsiDocumentManager;
import org.jetbrains.kotlin.com.intellij.psi.PsiFile;
import org.jetbrains.kotlin.com.intellij.psi.impl.DebugUtil;
import org.jetbrains.kotlin.com.intellij.psi.impl.PsiFileEx;

public class CodeCompletionHandlerBase {

    private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.completion.CodeCompletionHandlerBase");
    private final CompletionType myCompletionType;
    final boolean invokedExplicitly;
    final boolean synchronous;
    final boolean autopopup;

    public CodeCompletionHandlerBase(final CompletionType completionType) {
        this(completionType, true, false, true);
    }

    public CodeCompletionHandlerBase(CompletionType completionType, boolean invokedExplicitly, boolean autopopup, boolean synchronous) {
        myCompletionType = completionType;
        this.invokedExplicitly = invokedExplicitly;
        this.autopopup = autopopup;
        this.synchronous = synchronous;

        if (invokedExplicitly) {
            assert synchronous;
        }
        if (autopopup) {
            assert !invokedExplicitly;
        }
    }

    public final void invokeCompletion(final Project project, final Editor editor) {
        invokeCompletion(project, editor, 1);
    }

    public final void invokeCompletion(@NotNull final Project project, @NotNull final Editor editor, int time) {
        invokeCompletion(project, editor, time, false);
    }

    public final void invokeCompletion(@NotNull Project project, @NotNull Editor editor, int time, boolean hasModifiers) {
       // clearCaretMarkers(editor);
        invokeCompletion(project, editor, time, hasModifiers, editor.getCaretModel().getPrimaryCaret());
    }

    private void invokeCompletion(@NotNull Project project, @NotNull Editor editor, int time, boolean hasModifiers, @NotNull Caret caret) {
        checkNoWriteAccess();

        CompletionType completionType = CompletionType.BASIC;
        int invocationCount = 0;
        
        long startingTime = System.currentTimeMillis();
        Runnable initCmd = () -> {
//            WriteAction.run(() -> EditorUtil.fillVirtualSpaceUntilCaret(editor));
            CompletionInitializationContextImpl context = withTimeout(calcSyncTimeOut(startingTime), () ->
                    CompletionInitializationUtil.createCompletionInitializationContext(project, editor, caret, invocationCount, completionType));

            boolean hasValidContext = context != null;
            if (!hasValidContext) {
                final PsiFile psiFile = PsiUtilBase.getPsiFileInEditor(caret, project);
                context = new CompletionInitializationContextImpl(editor, caret, psiFile.getLanguage(), psiFile, completionType, invocationCount);
            }

            doComplete(context, hasModifiers, hasValidContext, startingTime);
        };
    }

    private void doComplete(CompletionInitializationContextImpl initContext, boolean hasModifiers, boolean hasValidContext, long startingTime) {
        final Editor editor = initContext.getEditor();
        if (synchronous && hasValidContext) {
            withTimeout(calcSyncTimeOut(startingTime), () -> {
                PsiDocumentManager.getInstance(initContext.getProject()).commitAllDocuments();
                return CompletionInitializationUtil.insertDummyIdentifier(initContext, indicator).get();
            });
        }
    }


    private static void assertCommitSuccessful(Editor editor, PsiFile psiFile) {
        Document document = editor.getDocument();
        int docLength = document.getTextLength();
        int psiLength = psiFile.getTextLength();
        if (docLength == psiLength) {
            return;
        }

        String message = "unsuccessful commit: (injected=" + false +")";
        message += "\nfile=" + psiFile.getName();
        message += "\nfile class=" + psiFile.getClass();
        message += "\nlanguage=" + psiFile.getLanguage();
        message += "\ndoc.length=" + docLength;
        message += "\npsiFile.length=" + psiLength;
        String fileText = psiFile.getText();
        if (fileText != null) {
            message += "\npsiFile.text.length=" + fileText.length();
        }
        FileASTNode node = psiFile.getNode();
        if (node != null) {
            message += "\nnode.length=" + node.getTextLength();
            String nodeText = node.getText();
            message += "\nnode.text.length=" + nodeText.length();
        }

        LOG.error(new RuntimeExceptionWithAttachments("Commit unsuccessful", message,
                new Attachment(psiFile.getViewProvider().getVirtualFile().getPath(), fileText),
                createAstAttachment(psiFile, psiFile),
                new Attachment("docText.txt", document.getText())));
    }

    private static void checkNoWriteAccess() {
        if (!ApplicationManager.getApplication().isUnitTestMode()) {
            if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
                throw new AssertionError("Completion should not be invoked inside write action");
            }
        }
    }

    private static Attachment createAstAttachment(PsiFile fileCopy, final PsiFile originalFile) {
        return new Attachment(originalFile.getViewProvider().getVirtualFile().getPath() + " syntactic tree.txt", DebugUtil.psiToString(fileCopy, false));
    }

    private static Attachment createFileTextAttachment(PsiFile fileCopy, final PsiFile originalFile) {
        return new Attachment(originalFile.getViewProvider().getVirtualFile().getPath(), fileCopy.getText());
    }

    @Nullable
    private <T> T withTimeout(long maxDurationMillis, @NotNull Computable<T> task) {
        if (false) {
            return task.compute();
        }

        return ProgressIndicatorUtils.withTimeout(maxDurationMillis, task);
    }

    private static int ourAutoInsertItemTimeout = 2000;

    private static int calcSyncTimeOut(long startTime) {
        return (int)Math.max(300, ourAutoInsertItemTimeout - (System.currentTimeMillis() - startTime));
    }
}
