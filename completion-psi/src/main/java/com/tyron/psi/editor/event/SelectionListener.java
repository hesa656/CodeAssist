package com.tyron.psi.editor.event;

import com.tyron.psi.editor.SelectionModel;

import java.util.EventListener;

/**
 * Allows to receive information about selection changes in an editor.
 *
 * @see SelectionModel#addSelectionListener(SelectionListener)
 * @see EditorEventMulticaster#addSelectionListener(SelectionListener)
 */
public interface SelectionListener extends EventListener {
    /**
     * Called when the selected area in an editor is changed.
     *
     * @param e the event containing information about the change.
     */
    void selectionChanged(SelectionEvent e);
}