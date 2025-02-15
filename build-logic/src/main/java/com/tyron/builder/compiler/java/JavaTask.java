package com.tyron.builder.compiler.java;


import android.annotation.SuppressLint;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.tyron.builder.BuildModule;
import com.tyron.builder.compiler.BuildType;
import com.tyron.builder.compiler.Task;
import com.tyron.builder.exception.CompilationFailedException;
import com.tyron.builder.log.ILogger;
import com.tyron.builder.model.DiagnosticWrapper;
import com.tyron.builder.model.SourceFileObject;
import com.tyron.builder.parser.FileManager;
import com.tyron.builder.project.api.JavaProject;

import org.openjdk.javax.tools.Diagnostic;
import org.openjdk.javax.tools.DiagnosticListener;
import org.openjdk.javax.tools.JavaFileObject;
import org.openjdk.javax.tools.StandardJavaFileManager;
import org.openjdk.javax.tools.StandardLocation;
import org.openjdk.source.util.JavacTask;
import org.openjdk.tools.javac.api.JavacTool;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class JavaTask extends Task<JavaProject> {

    private List<File> mCompiledFiles;

    public JavaTask(JavaProject project, ILogger logger) {
        super(project, logger);
    }

    @Override
    public String getName() {
        return "Java Compiler";
    }

    @Override
    public void prepare(BuildType type) throws IOException {

    }

    @Override
    public void run() throws IOException, CompilationFailedException {
        compile();
    }

    private final List<Diagnostic<? extends JavaFileObject>> diagnostics = new ArrayList<>();

    @SuppressLint("NewApi")
    public void compile() throws CompilationFailedException {

        long startTime = System.currentTimeMillis();
        getLogger().debug("Compiling java files.");

        File outputDir = new File(getProject().getBuildDirectory(), "bin/classes");
        if (outputDir.exists()) {
            FileManager.deleteDir(outputDir);
        }
        if(!outputDir.mkdirs()) {
            throw new CompilationFailedException("Cannot create output directory");
        }

        getProject().clear();
        List<File> javaFiles = new ArrayList<>(getProject().getJavaFiles().values());
        javaFiles.addAll(getJavaFiles(new File(getProject().getBuildDirectory(), "gen")));
        mCompiledFiles = new ArrayList<>();

        DiagnosticListener<JavaFileObject> diagnosticCollector = diagnostic -> {
            switch (diagnostic.getKind()) {
                case ERROR:
                    getLogger().error(new DiagnosticWrapper(diagnostic));
                    break;
                case WARNING:
                    getLogger().warning(new DiagnosticWrapper(diagnostic));
            }
        };

        JavacTool tool = JavacTool.create();

        StandardJavaFileManager standardJavaFileManager = tool.getStandardFileManager(
                diagnosticCollector,
                Locale.getDefault(),
                Charset.defaultCharset()
        );
        try {
            standardJavaFileManager.setLocation(StandardLocation.CLASS_OUTPUT, Collections.singletonList(outputDir));
            standardJavaFileManager.setLocation(StandardLocation.PLATFORM_CLASS_PATH, Arrays.asList(
                    BuildModule.getAndroidJar(),
                    BuildModule.getLambdaStubs()
            ));
            standardJavaFileManager.setLocation(StandardLocation.CLASS_PATH, getProject().getLibraries());
            standardJavaFileManager.setLocation(StandardLocation.SOURCE_PATH, javaFiles);
        } catch (IOException e) {
            throw new CompilationFailedException(e);
        }

        List<JavaFileObject> javaFileObjects = new ArrayList<>();
        for (File file : javaFiles) {
            mCompiledFiles.add(file);
            javaFileObjects.add(new SourceFileObject(file.toPath()));
        }

        JavacTask task = tool.getTask(
                null,
                standardJavaFileManager,
                diagnosticCollector,
                null,
                null,
                javaFileObjects
        );

        if (!task.call()) {
            throw new CompilationFailedException("Compilation failed. Check diagnostics for more information.");
        }

        Log.d("JavaCompiler", "Compilation took: " + (System.currentTimeMillis() - startTime) + " ms");
    }

    /**
     * Returns a list of files that are processed by the compiler
     */
    @VisibleForTesting
    public List<File> getCompiledFiles() {
        return mCompiledFiles;
    }
    public static Set<File> getJavaFiles(File dir) {
        Set<File> javaFiles = new HashSet<>();

        File[] files = dir.listFiles();
        if (files == null) {
            return Collections.emptySet();
        }

        for (File file : files) {
            if (file.isDirectory()) {
                javaFiles.addAll(getJavaFiles(file));
            } else {
                if (file.getName().endsWith(".java")) {
                    javaFiles.add(file);
                }
            }
        }

        return javaFiles;
    }

    public List<Diagnostic<? extends JavaFileObject>> getDiagnostics() {
        return diagnostics;
    }
}
