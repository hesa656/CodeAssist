package com.tyron.builder.compiler.incremental.resource;

import androidx.annotation.VisibleForTesting;

import com.tyron.builder.BuildModule;
import com.tyron.builder.compiler.BuildType;
import com.tyron.builder.compiler.Task;
import com.tyron.builder.exception.CompilationFailedException;
import com.tyron.builder.log.ILogger;
import com.tyron.builder.project.api.AndroidProject;
import com.tyron.common.util.BinaryExecutor;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class IncrementalAapt2Task extends Task<AndroidProject> {

    private static final String TAG = "IncrementalAAPT2";

    private final boolean mGenerateProtoFormat;

    public IncrementalAapt2Task(AndroidProject project, ILogger logger, boolean generateProtoFormat) {
        super(project, logger);
        mGenerateProtoFormat = generateProtoFormat;
    }

    @Override
    public String getName() {
        return TAG;
    }

    @Override
    public void prepare(BuildType type) throws IOException {

    }

    public void run() throws IOException, CompilationFailedException {
        Map<String, List<File>> filesToCompile = getFiles();
        List<File> librariesToCompile = getLibraries();

        compileProject(filesToCompile);
        compileLibraries(librariesToCompile);

        link();

        updateJavaFiles();
    }

    private void updateJavaFiles() {
        File genFolder = new File(getProject().getBuildDirectory(), "gen");
        if (genFolder.exists()) {
            FileUtils.iterateFiles(genFolder,
                    FileFilterUtils.suffixFileFilter(".java"),
                    TrueFileFilter.INSTANCE
            ).forEachRemaining(getProject()::addJavaFile);
        }
    }

    private void compileProject(Map<String, List<File>> files)
            throws IOException, CompilationFailedException {
        List<String> args = new ArrayList<>();
        args.add(getBinary().getAbsolutePath());
        args.add("compile");

        for (String resourceType : files.keySet()) {
            List<File> filesToCompile = files.get(resourceType);
            if (filesToCompile != null && !filesToCompile.isEmpty()) {
                for (File fileToCompile : filesToCompile) {
                    args.add(fileToCompile.getAbsolutePath());
                }
            }
        }
        args.add("-o");

        File outputCompiled = new File(getProject().getBuildDirectory(), "bin/res/compiled");
        if (!outputCompiled.exists() && !outputCompiled.mkdirs()) {
            throw new IOException("Failed to create compiled directory");
        }
        args.add(outputCompiled.getAbsolutePath());

        BinaryExecutor executor = new BinaryExecutor();
        executor.setCommands(args);
        if (!executor.execute().isEmpty()) {
            throw new CompilationFailedException(executor.getLog());
        }

        copyMapToDir(files);
    }

    private void compileLibraries(List<File> libraries)
            throws IOException, CompilationFailedException {
        getLogger().debug("Compiling libraries.");

        File output = new File(getProject().getBuildDirectory(), "bin/res");
        if (!output.exists()) {
            if (!output.mkdirs()) {
                throw new IOException("Failed to create resource output directory");
            }
        }

        for (File file : libraries) {
            File parent = file.getParentFile();
            if (parent == null) {
                throw new IOException("Library folder doesn't exist");
            }
            File[] files = parent.listFiles();
            if (files == null) {
                continue;
            }

            for (File inside : files) {
                if (inside.isDirectory() && inside.getName().equals("res")) {
                    List<String> args = new ArrayList<>();
                    args.add(getBinary().getAbsolutePath());
                    args.add("compile");
                    args.add("--dir");
                    args.add(inside.getAbsolutePath());
                    args.add("-o");
                    args.add(createNewFile(output, parent.getName() + ".zip").getAbsolutePath());

                    BinaryExecutor exec = new BinaryExecutor();
                    exec.setCommands(args);
                    if (!exec.execute().trim().isEmpty()) {
                        throw new CompilationFailedException(exec.getLog());
                    }
                }
            }
        }
    }

    /**
     * Used to generate R.java files for new projects, not used for compilation
     */
    public void generateResourceClasses() throws IOException, CompilationFailedException {
        Map<String, List<File>> filesToCompile = getFiles();
        List<File> librariesToCompile = getLibraries();

        compileProject(filesToCompile);
        compileLibraries(librariesToCompile);

        List<String> args = new ArrayList<>();
        args.add(getBinary().getAbsolutePath());
        args.add("link");
        args.add("-I");
        args.add(getProject().getBootstrapJarFile().getAbsolutePath());  File files = new File(getOutputPath(), "compiled");
        args.add("--allow-reserved-package-id");
        args.add("--no-version-vectors");
        args.add("--no-version-transitions");
        args.add("--auto-add-overlay");
        args.add("--min-sdk-version");
        args.add(String.valueOf(getProject().getMinSdk()));
        args.add("--target-sdk-version");
        args.add(String.valueOf(getProject().getTargetSdk()));
        File[] libraryResources = getOutputPath().listFiles();
        if (libraryResources != null) {
            for (File resource : libraryResources) {
                if (resource.isDirectory()) {
                    continue;
                }
                if (!resource.getName().endsWith(".zip")) {
                    getLogger().warning("Unrecognized file " + resource.getName());
                    continue;
                }

                if (resource.length() == 0) {
                    getLogger().warning("Empty zip file " + resource.getName());
                }

                args.add("-R");
                args.add(resource.getAbsolutePath());
            }
        }

        File[] resources = files.listFiles();
        if (resources == null) {
            throw new CompilationFailedException("No files to compile");
        }
        for (File resource : resources) {
            if (!resource.getName().endsWith(".flat")) {
                getLogger().warning("Unrecognized file " + resource.getName() + " at compiled directory");
                continue;
            }
            args.add("-R");
            args.add(resource.getAbsolutePath());
        }
        args.add("--java");
        File gen = new File(getProject().getBuildDirectory(), "gen");
        if (!gen.exists()) {
            if (!gen.mkdirs()) {
                throw  new CompilationFailedException("Failed to create gen folder");
            }
        }
        args.add(gen.getAbsolutePath());
        args.add("-o");
        args.add(getOutputPath().getParent() + "/generated.apk.res");

        args.add("--output-text-symbols");
        File file = new File(getOutputPath(), "R.txt");
        Files.deleteIfExists(file.toPath());
        if (!file.createNewFile()) {
            throw new IOException("Unable to create R.txt file");
        }
        args.add(file.getAbsolutePath());

        if (getProject().getAssetsDirectory().exists()) {
            args.add("-A");
            args.add(getProject().getAssetsDirectory().getAbsolutePath());
        }
        args.add("--manifest");
        args.add(getProject().getManifestFile().getAbsolutePath());

        BinaryExecutor exec = new BinaryExecutor();
        exec.setCommands(args);
        if (!exec.execute().trim().isEmpty()) {
            throw new CompilationFailedException(exec.getLog());
        }

        updateJavaFiles();
    }

    private void link() throws IOException, CompilationFailedException {
        getLogger().debug("Linking resources");

        List<String> args = new ArrayList<>();
        args.add(getBinary().getAbsolutePath());
        args.add("link");
        args.add("-I");
        args.add(getProject().getBootstrapJarFile().getAbsolutePath());  File files = new File(getOutputPath(), "compiled");
        args.add("--allow-reserved-package-id");
        args.add("--no-version-vectors");
        args.add("--no-version-transitions");
        args.add("--auto-add-overlay");
        args.add("--min-sdk-version");
        args.add(String.valueOf(getProject().getMinSdk()));
        args.add("--target-sdk-version");
        args.add(String.valueOf(getProject().getTargetSdk()));
        args.add("--proguard");
        args.add(createNewFile(new File(getProject().getBuildDirectory(), "bin/res"),
                "generated-rules.txt").getAbsolutePath());

        File[] libraryResources = getOutputPath().listFiles();
        if (libraryResources != null) {
            for (File resource : libraryResources) {
                if (resource.isDirectory()) {
                    continue;
                }
                if (!resource.getName().endsWith(".zip")) {
                    getLogger().warning("Unrecognized file " + resource.getName());
                    continue;
                }

                if (resource.length() == 0) {
                    getLogger().warning("Empty zip file " + resource.getName());
                }

                args.add("-R");
                args.add(resource.getAbsolutePath());
            }
        }

        File[] resources = files.listFiles();
        if (resources != null) {
            for (File resource : resources) {
                if (!resource.getName().endsWith(".flat")) {
                    getLogger().warning("Unrecognized file " + resource.getName() + " at compiled directory");
                    continue;
                }
                args.add("-R");
                args.add(resource.getAbsolutePath());
            }
        }

        args.add("--java");
        File gen = new File(getProject().getBuildDirectory(), "gen");
        if (!gen.exists()) {
            if (!gen.mkdirs()) {
                throw  new CompilationFailedException("Failed to create gen folder");
            }
        }
        args.add(gen.getAbsolutePath());

        args.add("--manifest");
        File mergedManifest = new File(getProject().getBuildDirectory(),
                "bin/AndroidManifest.xml");
        if (!mergedManifest.exists()) {
            throw new IOException("Unable to get merged manifest file");
        }
        args.add(mergedManifest.getAbsolutePath());

        args.add("-o");
        if (mGenerateProtoFormat) {
            args.add(getOutputPath().getParent() + "/proto-format.zip");
            args.add("--proto-format");
        } else {
            args.add(getOutputPath().getParent() + "/generated.apk.res");
        }

        args.add("--output-text-symbols");
        File file = new File(getOutputPath(), "R.txt");
        Files.deleteIfExists(file.toPath());
        if (!file.createNewFile()) {
            throw new IOException("Unable to create R.txt file");
        }
        args.add(file.getAbsolutePath());

        if (getProject().getAssetsDirectory().exists()) {
            args.add("-A");
            args.add(getProject().getAssetsDirectory().getAbsolutePath());
        }

        BinaryExecutor exec = new BinaryExecutor();
        exec.setCommands(args);
        if (!exec.execute().trim().isEmpty()) {
            throw new CompilationFailedException(exec.getLog());
        }
    }
    /**
     * Utility function to get all the files that needs to be recompiled
     * @return resource files to compile
     */
    public Map<String, List<File>> getFiles() throws IOException {
        Map<String, List<ResourceFile>> newFiles = findFiles(getProject()
                .getAndroidResourcesDirectory());
        Map<String, List<ResourceFile>> oldFiles = findFiles(getOutputDirectory());
        Map<String, List<File>> filesToCompile = new HashMap<>();

        for (String resourceType : newFiles.keySet()) {

            // if the cache doesn't contain the new files then its considered new
            if (!oldFiles.containsKey(resourceType)) {
                List<ResourceFile> files = newFiles.get(resourceType);
                if (files != null) {
                    addToMapList(filesToCompile, resourceType, files);
                }
                continue;
            }

            // both contain the resource type, compare the contents
            if (oldFiles.containsKey(resourceType)) {
                List<ResourceFile> newFilesResource = newFiles.get(resourceType);
                List<ResourceFile> oldFilesResource = oldFiles.get(resourceType);

                if (newFilesResource == null) {
                    newFilesResource = Collections.emptyList();
                }
                if (oldFilesResource == null) {
                    oldFilesResource = Collections.emptyList();
                }

                addToMapList(filesToCompile, resourceType,
                        getModifiedFiles(newFilesResource, oldFilesResource));
            }
        }

        for (String resourceType : oldFiles.keySet()) {
            // if the new files doesn't contain the old file then its deleted
            if (!newFiles.containsKey(resourceType)) {
                List<ResourceFile> files = oldFiles.get(resourceType);
                if (files != null) {
                    for (File file : files) {
                        if (!file.delete()) {
                            throw new IOException("Failed to delete file " + file);
                        }
                    }
                }
            }
        }

        return filesToCompile;
    }

    /**
     * Utility method to add a list of files to a map, if it doesn't exist, it creates a new one
     * @param map The map to add to
     * @param key Key to add the value
     * @param values The list of files to add
     */
    private void addToMapList(Map<String, List<File>> map, String key, List<ResourceFile> values) {
        List<File> mapValues = map.get(key);
        if (mapValues == null) {
            mapValues = new ArrayList<>();
        }

        mapValues.addAll(values);
        map.put(key, mapValues);
    }

    private void copyMapToDir(Map<String, List<File>> map) throws IOException {
        File output = new File(getProject().getBuildDirectory(), "intermediate/resources");
        if (!output.exists()) {
            if (!output.mkdirs()) {
                throw new IOException("Failed to create intermediate directory");
            }
        }

        for (String resourceType : map.keySet()) {
            File outputDir = new File(output, resourceType);
            if (!outputDir.exists()) {
               if (!outputDir.mkdir()) {
                   throw new IOException("Failed to create output directory for " + outputDir);
               }
            }

            List<File> files = map.get(resourceType);
            if (files != null) {
                for (File file : files) {
                    FileUtils.copyFileToDirectory(file, outputDir, true);
                }
            }
        }
    }

    /**
     * Utility method to compare to list of files
     */
    private List<ResourceFile> getModifiedFiles(
            List<ResourceFile> newFiles,
            List<ResourceFile> oldFiles)
            throws IOException {
        List<ResourceFile> resourceFiles = new ArrayList<>();

        for (ResourceFile newFile : newFiles) {
            if (!oldFiles.contains(newFile)) {
                resourceFiles.add(newFile);
            } else {
                File oldFile = oldFiles.get(oldFiles.indexOf(newFile));
                if (contentModified(newFile, oldFile)) {
                    resourceFiles.add(newFile);
                    if (!oldFile.delete()) {
                        throw new IOException("Failed to delete file " + oldFile.getName());
                    }
                }
                oldFiles.remove(oldFile);
            }
        }

        for (ResourceFile removedFile : oldFiles) {
            if (!removedFile.delete()) {
                throw new IOException("Failed to delete old file " + removedFile);
            }
        }

        return resourceFiles;
    }

    private boolean contentModified(File newFile, File oldFile) {
        if (!oldFile.exists() || !newFile.exists()) {
            return true;
        }

        if (newFile.length() != oldFile.length()) {
            return true;
        }

        return newFile.lastModified() > oldFile.lastModified();
    }

    /**
     * Returns a map of resource type, and the files for a given resource directory
     * @param file res directory
     * @return Map of resource type and the files corresponding to it
     */
    private Map<String, List<ResourceFile>> findFiles(File file) {
        File[] children = file.listFiles();
        if (children == null) {
            return Collections.emptyMap();
        }

        Map<String, List<ResourceFile>> map = new HashMap<>();
        for (File child : children) {
            if (!file.isDirectory()) {
                continue;
            }

            String resourceType = child.getName();
            File[] resourceFiles = child.listFiles();
            List<File> files;
            if (resourceFiles == null) {
                files = Collections.emptyList();
            } else {
                files = Arrays.asList(resourceFiles);
            }


            map.put(resourceType, files.stream()
                    .map(ResourceFile::fromFile).collect(Collectors.toList()));
        }

        return map;
    }

    /**
     * Returns the list of resource directories of libraries that needs to be compiled
     * It determines whether the library should be compiled by checking the build/bin/res folder,
     * if it contains a zip file with its name, then its most likely the same library
     */
    private List<File> getLibraries()  throws IOException {
        File resDir = new File(getProject().getBuildDirectory(), "bin/res");
        if (!resDir.exists()) {
            if (!resDir.mkdirs()) {
                throw new IOException("Failed to create resource directory");
            }
        }

        List<File> libraries = new ArrayList<>();

        for (File library : getProject().getLibraries()) {
            File parent = library.getParentFile();
            if (parent != null) {

                if (!new File(parent, "res").exists()) {
                    // we don't need to check it if it has no resource directory
                    continue;
                }

                File check = new File(resDir, parent.getName() + ".zip");
                if (!check.exists()) {
                    libraries.add(library);
                }
            }
        }

        return libraries;
    }

    private File createNewFile(File parent, String name) throws IOException {
        File createdFile = new File(parent, name);
        if (!parent.exists()) {
            if (!parent.mkdirs()) {
                throw new IOException("Unable to create directories");
            }
        }
        if (!createdFile.exists() && !createdFile.createNewFile()) {
            throw new IOException("Unable to create file " + name);
        }
        return createdFile;
    }

    private File getOutputDirectory() throws IOException {
        File intermediateDirectory = new File(getProject().getBuildDirectory(), "intermediate");

        if (!intermediateDirectory.exists()) {
            if (!intermediateDirectory.mkdirs()) {
                throw new IOException("Failed to create intermediate directory");
            }
        }

        File resourceDirectory = new File(intermediateDirectory, "resources");
        if (!resourceDirectory.exists()) {
            if (!resourceDirectory.mkdirs()) {
                throw new IOException("Failed to create resource directory");
            }
        }
        return resourceDirectory;
    }

    private File getOutputPath() throws IOException {
        File file = new File(getProject().getBuildDirectory(), "bin/res");
        if (!file.exists()) {
            if (!file.mkdirs()) {
                throw new IOException("Failed to get resource directory");
            }
        }
        return file;
    }

    private static File sAapt2Binary;

    @VisibleForTesting
    public static void setAapt2Binary(File file) {
        sAapt2Binary = file;
    }

    private static File getBinary() throws IOException {
        if (sAapt2Binary != null) {
            return sAapt2Binary;
        }

        File check = new File(
                BuildModule.getContext().getApplicationInfo().nativeLibraryDir,
                "libaapt2.so"
        );
        if (check.exists()) {
            sAapt2Binary = check;
            return check;
        }

        throw new IOException("AAPT2 Binary not found");
    }
}
