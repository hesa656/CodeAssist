package com.tyron.psi.editor;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.com.intellij.openapi.editor.Document;
import org.jetbrains.kotlin.com.intellij.openapi.project.Project;
import org.jetbrains.kotlin.com.intellij.openapi.util.UserDataHolder;

public interface Editor extends UserDataHolder {
    /**
     * Returns the document edited or viewed in the editor.
     *
     * @return the document instance.
     */
    @NotNull
    Document getDocument();

    CaretModel getCaretModel();

    /**
     * Returns the selection model for the editor, which can be used to select ranges of text in
     * the document and retrieve information about the selection.
     *
     * @return the selection model instance.
     */
    @NotNull SelectionModel getSelectionModel();

    Project getProject();

    /**
     * Returns the value indicating whether the editor operates in viewer mode, with
     * all modification actions disabled.
     *
     * @return {@code true} if the editor works as a viewer, {@code false} otherwise.
     */
    boolean isViewer();
}
