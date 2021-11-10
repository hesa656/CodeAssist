package com.tyron.psi.completion;

import com.tyron.psi.completion.impl.CompletionInitializationContextImpl;
import com.tyron.psi.editor.Caret;
import com.tyron.psi.editor.Editor;
import com.tyron.psi.util.PsiUtilBase;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.com.intellij.openapi.Disposable;
import org.jetbrains.kotlin.com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.kotlin.com.intellij.openapi.editor.Document;
import org.jetbrains.kotlin.com.intellij.openapi.editor.impl.DocumentImpl;
import org.jetbrains.kotlin.com.intellij.openapi.fileEditor.FileDocumentManager;
import org.jetbrains.kotlin.com.intellij.openapi.project.DumbAware;
import org.jetbrains.kotlin.com.intellij.openapi.project.Project;
import org.jetbrains.kotlin.com.intellij.openapi.util.Key;
import org.jetbrains.kotlin.com.intellij.openapi.util.Pair;
import org.jetbrains.kotlin.com.intellij.openapi.util.Ref;
import org.jetbrains.kotlin.com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.kotlin.com.intellij.psi.PsiDocumentManager;
import org.jetbrains.kotlin.com.intellij.psi.PsiFile;
import org.jetbrains.kotlin.com.intellij.psi.impl.PsiFileEx;
import org.jetbrains.kotlin.com.intellij.reference.SoftReference;
import org.jetbrains.kotlin.com.intellij.util.Consumer;

import java.lang.reflect.Field;
import java.util.Objects;
import java.util.function.Supplier;

public class CompletionInitializationUtil {
    private static final Logger LOG = Logger.getInstance(CompletionInitializationUtil.class);

    public static CompletionInitializationContextImpl createCompletionInitializationContext(@NotNull Project project,
                                                                                            @NotNull Editor editor,
                                                                                            @NotNull Caret caret, int invocationCount, CompletionType completionType) {


        PsiDocumentManager.getInstance(project).commitAllDocuments();
        // CompletionAssertions.checkEditorValid(editor);

        final PsiFile psiFile = PsiUtilBase.getPsiFileInEditor(editor, project);
        assert psiFile != null : "no PSI file: " + FileDocumentManager.getInstance().getFile(editor.getDocument());
        psiFile.putUserData(PsiFileEx.BATCH_REFERENCE_PROCESSING, Boolean.TRUE);
        //CompletionAssertions.assertCommitSuccessful(editor, psiFile);

        return runContributorsBeforeCompletion(editor, psiFile, invocationCount, caret, completionType);
    }

    public static CompletionInitializationContextImpl runContributorsBeforeCompletion(Editor editor,
                                                                                      PsiFile psiFile,
                                                                                      int invocationCount,
                                                                                      @NotNull Caret caret,
                                                                                      CompletionType completionType) {
        final Ref<CompletionContributor> current = Ref.create(null);
        CompletionInitializationContextImpl context =
                new CompletionInitializationContextImpl(editor, caret, psiFile.getLanguage(), psiFile, completionType, invocationCount) {
                    CompletionContributor dummyIdentifierChanger;

                    @Override
                    public void setDummyIdentifier(@NotNull String dummyIdentifier) {
                        super.setDummyIdentifier(dummyIdentifier);

                        if (dummyIdentifierChanger != null) {
                            LOG.error("Changing the dummy identifier twice, already changed by " + dummyIdentifierChanger);
                        }
                        dummyIdentifierChanger = current.get();
                    }
                };
        Project project = psiFile.getProject();
        // DumbAware.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> {
        for (final CompletionContributor contributor : CompletionContributor.forLanguageHonorDumbness(context.getPositionLanguage(), project)) {
            current.set(contributor);
            contributor.beforeCompletion(context);
            //  CompletionAssertions.checkEditorValid(editor);
            assert !PsiDocumentManager.getInstance(project).isUncommited(editor.getDocument()) : "Contributor " +
                    contributor +
                    " left the document uncommitted";
        }
        // });
        return context;
    }

    private static Supplier<OffsetsInFile> doInsertDummyIdentifier(CompletionInitializationContext initContext,
                                                                   OffsetsInFile topLevelOffsets,
                                                                   Consumer<Supplier<Disposable>> registerDisposable,
                                                                   Boolean noWriteLock) {
        if (initContext.getDummyIdentifier().isEmpty()) {
            return () -> topLevelOffsets;
        }
        Editor editor = initContext.getEditor();
        Editor hostEditor = editor;
        OffsetMap hostMap = topLevelOffsets.getOffsets();

        PsiFile hostCopy = obtainFileCopy(topLevelOffsets.getFile(), noWriteLock);
        Document copyDocument = Objects.requireNonNull(hostCopy.getViewProvider().getDocument());

        String dummyIdentifier = initContext.getDummyIdentifier();
        int startOffset = hostMap.getOffset(CompletionInitializationContext.START_OFFSET);
        int endOffset = hostMap.getOffset(CompletionInitializationContext.SELECTION_END_OFFSET);

        Supplier<OffsetsInFile> apply = topLevelOffsets.replaceInCopy(hostCopy, startOffset, endOffset, dummyIdentifier);
    }

    private static PsiFile obtainFileCopy(PsiFile file, Boolean forbidCaching) {
        final VirtualFile virtualFile = file.getVirtualFile();
        boolean mayCacheCopy = !forbidCaching && file.isPhysical() &&
                // we don't want to cache code fragment copies even if they appear to be physical
                virtualFile != null && virtualFile.isInLocalFileSystem();
        if (mayCacheCopy) {
            final Pair<PsiFile, Document> cached = SoftReference.dereference(file.getUserData(FILE_COPY_KEY));
            if (cached != null && isCopyUpToDate(cached.second, cached.first, file)) {
                PsiFile copy = cached.first;
                //CompletionAssertions.assertCorrectOriginalFile("Cached", file, copy);
                return copy;
            }
        }

        final PsiFile copy = (PsiFile)file.copy();
        if (copy.isPhysical() || copy.getViewProvider().isEventSystemEnabled()) {
            LOG.error("File copy should be non-physical and non-event-system-enabled! Language=" +
                    file.getLanguage() +
                    "; file=" +
                    file +
                    " of " +
                    file.getClass());
        }
       // CompletionAssertions.assertCorrectOriginalFile("New", file, copy);

        if (mayCacheCopy) {
            final Document document = copy.getViewProvider().getDocument();
            assert document != null;
            syncAcceptSlashR(file.getViewProvider().getDocument(), document);
            file.putUserData(FILE_COPY_KEY, new SoftReference<>(Pair.create(copy, document)));
        }
        return copy;
    }

    private static final Key<SoftReference<Pair<PsiFile, Document>>> FILE_COPY_KEY = Key.create("CompletionFileCopy");

    private static boolean isCopyUpToDate(Document document, @NotNull PsiFile copyFile, @NotNull PsiFile originalFile) {
        if (!copyFile.getClass().equals(originalFile.getClass()) ||
                !copyFile.isValid() ||
                !copyFile.getName().equals(originalFile.getName())) {
            return false;
        }
        // the psi file cache might have been cleared by some external activity,
        // in which case PSI-document sync may stop working
        PsiFile current = PsiDocumentManager.getInstance(copyFile.getProject()).getPsiFile(document);
        return current != null && current.getViewProvider().getPsi(copyFile.getLanguage()) == copyFile;
    }

    private static void syncAcceptSlashR(Document originalDocument, Document documentCopy) {
        if (!(originalDocument instanceof DocumentImpl) || !(documentCopy instanceof DocumentImpl)) {
            return;
        }

        ((DocumentImpl)documentCopy).setAcceptSlashR(getAcceptSlashR(((DocumentImpl)originalDocument)));
    }

    private static boolean getAcceptSlashR(DocumentImpl impl) {
        try {
            Field field = DocumentImpl.class.getDeclaredField("myAcceptSlashR");
            field.setAccessible(true);

            Object object = field.get(impl);
            if (object == null) {
                return false;
            }

            return (Boolean) object;
        } catch (Throwable e) {
            return false;
        }
    }

}