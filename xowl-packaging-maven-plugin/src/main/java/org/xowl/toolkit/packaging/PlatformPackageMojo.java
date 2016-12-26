package org.xowl.toolkit.packaging;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Builds a distribution of the xOWL federation platform
 *
 * @author Laurent Wouters
 */
@Mojo(name = "xowl-platform-package", defaultPhase = LifecyclePhase.PACKAGE)
public class PlatformPackageMojo extends PackagingAbstractMojo {
    /**
     * The group identifier for the Felix distribution
     */
    private static final String FELIX_DISTRIB_GROUP_ID = "org.apache.felix";
    /**
     * The artifact identifier for the Felix distribution
     */
    private static final String FELIX_DISTRIB_ARTIFACT_ID = "org.apache.felix.main.distribution";

    /**
     * Whether to build a base platform
     */
    @Parameter
    protected boolean isBase;

    /**
     * The resources to deploy at the root of the distribution
     */
    @Parameter
    protected File[] resources;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (isBase)
            executeBase();
        else
            executeCustomization();
    }

    /**
     * Builds the package for the base platform
     *
     * @throws MojoExecutionException if an unexpected problem occurs.
     *                                Throwing this exception causes a "BUILD ERROR" message to be displayed.
     * @throws MojoFailureException   if an expected problem (such as a compilation failure) occurs.
     *                                Throwing this exception causes a "BUILD FAILURE" message to be displayed.
     */
    private void executeBase() throws MojoExecutionException, MojoFailureException {
        File targetDirectory = new File(project.getModel().getBuild().getDirectory());
        if (!targetDirectory.exists()) {
            if (!targetDirectory.mkdirs())
                throw new MojoFailureException("Failed to create target directory");
        }

        // get the dependencies
        File[] fileDependencies = new File[project.getModel().getDependencies().size()];
        File fileFelix = null;
        int i = 0;
        for (Dependency dependency : project.getModel().getDependencies()) {
            File file = resolveArtifact(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion(), dependency.getClassifier(), dependency.getType());
            fileDependencies[i++] = file;
            if (dependency.getGroupId().equals(FELIX_DISTRIB_GROUP_ID) && dependency.getArtifactId().equals(FELIX_DISTRIB_ARTIFACT_ID))
                fileFelix = file;
        }
        if (fileFelix == null) {
            throw new MojoFailureException("No Felix distribution specified in dependencies");
        }

        File targetDistribution = new File(targetDirectory, "distribution");
        if (!targetDistribution.exists()) {
            if (!targetDistribution.mkdirs())
                throw new MojoFailureException("Failed to create target directory");
        }

        // extract the felix distribution
        extractTarGz(fileFelix, targetDistribution);
        File[] content = targetDistribution.listFiles();
        if (content == null || content.length == 0)
            throw new MojoFailureException("Failed to extract " + fileFelix.getAbsolutePath());
        File targetDistributionFelix1 = content[0];
        File targetDistributionFelix2 = new File(targetDistribution, "felix");
        try {
            Files.move(targetDistributionFelix1.toPath(), targetDistributionFelix2.toPath());
        } catch (IOException exception) {
            getLog().error(exception);
            throw new MojoFailureException("Failed to move " + targetDistributionFelix1.getAbsolutePath() + " to " + targetDistributionFelix2.getAbsolutePath(), exception);
        }

        // deploy the bundles
        File directoryBundles = new File(targetDistributionFelix2, "bundle");
        i = 0;
        for (Dependency dependency : project.getModel().getDependencies()) {
            File bundleFileSource = fileDependencies[i++];
            if (bundleFileSource == fileFelix)
                continue;
            try {
                File bundleFileTarget = dependency.getGroupId().equals(FELIX_DISTRIB_GROUP_ID) ?
                        new File(directoryBundles, dependency.getArtifactId() + "-" + dependency.getVersion() + ".jar") :
                        new File(directoryBundles, dependency.getGroupId() + "." + dependency.getArtifactId() + "-" + dependency.getVersion() + ".jar");
                Files.copy(bundleFileSource.toPath(), bundleFileTarget.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException exception) {
                getLog().error(exception);
                throw new MojoFailureException("Failed to copy " + targetDistributionFelix1.getAbsolutePath() + " to " + targetDistributionFelix2.getAbsolutePath(), exception);
            }
        }

        // deploy the resources
        if (resources != null) {
            for (i = 0; i != resources.length; i++) {
                File origin = resources[i];
                File target = new File(targetDistribution, origin.getName());
                copyResource(origin, target);
            }
        }

        // build the distribution package
        File filePackage = new File(targetDirectory, getArtifactName() + "-xowl-platform.tar.gz");
        packageTarGz(targetDistribution, filePackage, project.getModel().getArtifactId());
        org.xowl.infra.utils.Files.deleteFolder(targetDistribution);

        DefaultArtifactHandler artifactHandler = new DefaultArtifactHandler("xowl-addon");
        artifactHandler.setAddedToClasspath(false);
        artifactHandler.setExtension("zip");
        artifactHandler.setLanguage("java");
        artifactHandler.setIncludesDependencies(false);
        DefaultArtifact mainArtifact = new DefaultArtifact(
                project.getModel().getGroupId(),
                project.getModel().getArtifactId(),
                project.getModel().getVersion(),
                "compile",
                "xowl-platform",
                "xowl-platform",
                artifactHandler
        );
        mainArtifact.setFile(filePackage);
        project.setArtifact(mainArtifact);
    }

    /**
     * Copy a resource
     *
     * @param origin The origin file
     * @param target The target file
     * @throws MojoFailureException if an expected problem (such as a compilation failure) occurs.
     *                              Throwing this exception causes a "BUILD FAILURE" message to be displayed.
     */
    private void copyResource(File origin, File target) throws MojoFailureException {
        if (origin.isDirectory()) {
            if (!target.mkdirs())
                throw new MojoFailureException("Failed to copy " + origin.getAbsolutePath() + " to " + target.getAbsolutePath());
            File[] children = origin.listFiles();
            if (children == null)
                return;
            for (int i = 0; i != children.length; i++) {
                copyResource(children[i], new File(target, children[i].getName()));
            }
        } else {
            try {
                Files.copy(origin.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException exception) {
                getLog().error(exception);
                throw new MojoFailureException("Failed to copy " + origin.getAbsolutePath() + " to " + target.getAbsolutePath(), exception);
            }
        }
    }

    /**
     * Creates an tar.gz archive from a directory
     *
     * @param input    The input directory
     * @param output   The ouput tar.gz file
     * @param rootName The name of the root folder in the package
     * @throws MojoFailureException if an expected problem (such as a compilation failure) occurs.
     *                              Throwing this exception causes a "BUILD FAILURE" message to be displayed.
     */
    private void packageTarGz(File input, File output, String rootName) throws MojoFailureException {
        try (TarArchiveOutputStream outputStream = new TarArchiveOutputStream(new GZIPOutputStream(new FileOutputStream(output)))) {
            outputStream.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            packageTarGzDirectory(outputStream, input, rootName);
        } catch (IOException exception) {
            getLog().error(exception);
            throw new MojoFailureException("Failed to package " + output.getAbsolutePath(), exception);
        }
    }

    /**
     * Adds a directory to the tar archive
     *
     * @param outputStream The stream to write to
     * @param directory    The directory to put into the archive
     * @param path         The current path in the archive
     * @throws IOException When an IO error occurs
     */
    private void packageTarGzDirectory(TarArchiveOutputStream outputStream, File directory, String path) throws IOException {
        TarArchiveEntry entry = new TarArchiveEntry(directory, path);
        outputStream.putArchiveEntry(entry);
        outputStream.closeArchiveEntry();
        File[] files = directory.listFiles();
        if (files == null)
            return;
        for (File child : files) {
            if (child.isDirectory())
                packageTarGzDirectory(outputStream, child, path + "/" + child.getName());
            else {
                entry = new TarArchiveEntry(child, path + "/" + child.getName());
                outputStream.putArchiveEntry(entry);
                try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(child))) {
                    IOUtils.copy(bis, outputStream);
                }
                outputStream.closeArchiveEntry();
            }
        }
    }

    /**
     * Extracts a tar.gz file
     *
     * @param input  The input tar.gz file
     * @param output The output directory
     * @throws MojoFailureException if an expected problem (such as a compilation failure) occurs.
     *                              Throwing this exception causes a "BUILD FAILURE" message to be displayed.
     */
    private void extractTarGz(File input, File output) throws MojoFailureException {
        byte[] buffer = new byte[8192];
        try (TarArchiveInputStream inputStream = new TarArchiveInputStream(new GZIPInputStream(new FileInputStream(input)))) {
            while (true) {
                TarArchiveEntry entry = inputStream.getNextTarEntry();
                if (entry == null)
                    break;
                if (entry.isDirectory()) {
                    File directory = new File(output, entry.getName());
                    if (!directory.mkdirs())
                        throw new MojoFailureException("Failed to extract " + input.getAbsolutePath());
                } else {
                    File target = new File(output, entry.getName());
                    File directory = target.getParentFile();
                    if (!directory.exists() && !directory.mkdirs())
                        throw new MojoFailureException("Failed to extract " + input.getAbsolutePath());
                    try (FileOutputStream fileOutputStream = new FileOutputStream(target)) {
                        int read = 0;
                        while (read >= 0) {
                            read = inputStream.read(buffer, 0, buffer.length);
                            if (read > 0)
                                fileOutputStream.write(buffer, 0, read);
                        }
                    }
                }
            }
        } catch (IOException exception) {
            getLog().error(exception);
            throw new MojoFailureException("Failed to extract " + input.getAbsolutePath(), exception);
        }
    }

    /**
     * Builds the package of a custom platform
     *
     * @throws MojoExecutionException if an unexpected problem occurs.
     *                                Throwing this exception causes a "BUILD ERROR" message to be displayed.
     * @throws MojoFailureException   if an expected problem (such as a compilation failure) occurs.
     *                                Throwing this exception causes a "BUILD FAILURE" message to be displayed.
     */
    private void executeCustomization() throws MojoExecutionException, MojoFailureException {

    }
}
