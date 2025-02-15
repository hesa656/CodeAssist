package com.tyron.code.ui.file.action.file;

import androidx.appcompat.app.AlertDialog;

import com.tyron.ProjectManager;
import com.tyron.builder.project.api.JavaProject;
import com.tyron.builder.project.api.Project;
import com.tyron.code.R;
import com.tyron.code.ui.file.action.ActionContext;
import com.tyron.code.ui.file.action.FileAction;
import com.tyron.common.util.StringSearch;

import java.io.File;

import kotlin.io.FileWalkDirection;
import kotlin.io.FilesKt;

public class DeleteFileAction extends FileAction {
    @Override
    public boolean isApplicable(File file) {
        return true;
    }

    @Override
    public void addMenu(ActionContext context) {
        context.getMenu().add("Delete")
                .setOnMenuItemClickListener(menuItem -> {
                    new AlertDialog.Builder(context.getFragment().requireContext())
                            .setMessage(String.format(context.getFragment().getString(R.string.dialog_confirm_delete),
                                    context.getCurrentNode().getValue().getFile().getName()))
                            .setPositiveButton(context.getFragment().getString(R.string.dialog_delete), (d, which) -> {
                                deleteFiles(context);
                                context.getTreeView().deleteNode(context.getCurrentNode());
                                context.getTreeView().refreshTreeView();
                            })
                            .show();
                    return true;
                });
    }

    private void deleteFiles(ActionContext context) {
        FilesKt.walk(context.getCurrentNode().getContent().getFile(), FileWalkDirection.TOP_DOWN).iterator().forEachRemaining(file -> {
            if (file.getName().endsWith(".java")) { // todo: add .kt and .xml checks
                context.getFragment().getMainViewModel().removeFile(file);

                Project project = ProjectManager.getInstance().getCurrentProject();
                if (project instanceof JavaProject) {
                    String packageName = StringSearch.packageName(file);
                    if (packageName != null) {
                        packageName += "." + file.getName()
                                .substring(0, file.getName().lastIndexOf("."));
                    }
                    ((JavaProject) project).removeJavaFile(packageName);
                }
            }
        });

        FilesKt.deleteRecursively(context.getCurrentNode().getContent().getFile());
    }
}
