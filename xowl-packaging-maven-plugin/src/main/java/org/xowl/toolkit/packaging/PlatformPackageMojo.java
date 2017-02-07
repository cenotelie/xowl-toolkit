/*******************************************************************************
 * Copyright (c) 2017 Association Cénotélie (cenotelie.fr)
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General
 * Public License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/

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
import org.xowl.infra.utils.Base64;
import org.xowl.infra.utils.TextUtils;

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
     * File mode for executable files in a tar package
     */
    private static final int EXECUTABLE_MODE = 0100755;


    /**
     * The SCM tag (changeset / commit id) for the addon
     */
    @Parameter
    protected String versionScmTag;

    /**
     * The build tag for the addon
     */
    @Parameter
    protected String versionBuildTag;

    /**
     * The build timestamp for the addon
     */
    @Parameter
    protected String versionBuildTimestamp;

    /**
     * The path to the icon for the addon
     */
    @Parameter
    protected File icon;

    /**
     * The full text for the license
     */
    @Parameter
    protected File licenseFullText;

    /**
     * The resources to deploy at the root of the distribution
     */
    @Parameter
    protected File[] resources;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        File targetDirectory = new File(project.getModel().getBuild().getDirectory());
        if (!targetDirectory.exists()) {
            if (!targetDirectory.mkdirs())
                throw new MojoFailureException("Failed to create target directory");
        }

        File[] fileDependencies = resolveDependencies();
        // look for the base distribution
        File fileBaseFelix = null;
        File fileBasePlatform = null;
        File toExclude = null;
        int i = 0;
        for (Dependency dependency : project.getModel().getDependencies()) {
            if (dependency.getGroupId().equals(FELIX_DISTRIB_GROUP_ID) && dependency.getArtifactId().equals(FELIX_DISTRIB_ARTIFACT_ID)) {
                fileBaseFelix = fileDependencies[i];
                toExclude = fileBaseFelix;
                break;
            }
            if ("xowl-platform".equals(dependency.getType())) {
                fileBasePlatform = fileDependencies[i];
                toExclude = fileBasePlatform;
                break;
            }
            i++;
        }
        File targetDistribution;
        if (fileBaseFelix != null)
            targetDistribution = extractBaseFelix(targetDirectory, fileBaseFelix);
        else if (fileBasePlatform != null)
            targetDistribution = extractBaseXOWL(targetDirectory, fileBasePlatform);
        else
            throw new MojoFailureException("No specified base distribution (Felix or xOWL platform)");

        deployBundles(targetDistribution, fileDependencies, toExclude);
        deployResources(targetDistribution);
        File fileDescriptor = writeDescriptor(targetDistribution);
        packageDistribution(targetDistribution);

        projectHelper.attachArtifact(
                project,
                "json",
                "",
                fileDescriptor
        );
    }

    /**
     * Extracts the base distribution in the case of the core Felix distribution
     *
     * @param targetDirectory The current target directory
     * @param fileBaseFelix   The file for the felix distribution
     * @return The directory of the target distribution
     * @throws MojoFailureException if an expected problem (such as a compilation failure) occurs.
     *                              Throwing this exception causes a "BUILD FAILURE" message to be displayed.
     */
    private File extractBaseFelix(File targetDirectory, File fileBaseFelix) throws MojoFailureException {
        File targetDistribution = new File(targetDirectory, "distribution");
        if (!targetDistribution.exists()) {
            if (!targetDistribution.mkdirs())
                throw new MojoFailureException("Failed to create target directory");
        }

        extractTarGz(fileBaseFelix, targetDistribution);
        File[] content = targetDistribution.listFiles();
        if (content == null || content.length == 0)
            throw new MojoFailureException("Failed to extract " + fileBaseFelix.getAbsolutePath());
        File targetDistributionFelix1 = content[0];
        File targetDistributionFelix2 = new File(targetDistribution, "felix");
        try {
            Files.move(targetDistributionFelix1.toPath(), targetDistributionFelix2.toPath());
        } catch (IOException exception) {
            getLog().error(exception);
            throw new MojoFailureException("Failed to move " + targetDistributionFelix1.getAbsolutePath() + " to " + targetDistributionFelix2.getAbsolutePath(), exception);
        }
        return targetDistribution;
    }

    /**
     * Extracts the base distribution in the case of a previous xOWL platform distribution
     *
     * @param targetDirectory  The current target directory
     * @param fileBasePlatform The file for the xOWL platform distribution
     * @return The directory of the target distribution
     * @throws MojoFailureException if an expected problem (such as a compilation failure) occurs.
     *                              Throwing this exception causes a "BUILD FAILURE" message to be displayed.
     */
    private File extractBaseXOWL(File targetDirectory, File fileBasePlatform) throws MojoFailureException {
        File temp = new File(targetDirectory, "temp");
        if (!temp.exists()) {
            if (!temp.mkdirs())
                throw new MojoFailureException("Failed to create target directory");
        }
        extractTarGz(fileBasePlatform, temp);
        File[] content = temp.listFiles();
        if (content == null || content.length == 0)
            throw new MojoFailureException("Failed to extract " + fileBasePlatform.getAbsolutePath());
        File targetDistribution1 = content[0];
        File targetDistribution2 = new File(targetDirectory, "distribution");
        try {
            Files.move(targetDistribution1.toPath(), targetDistribution2.toPath());
        } catch (IOException exception) {
            getLog().error(exception);
            throw new MojoFailureException("Failed to move " + targetDistribution1.getAbsolutePath() + " to " + targetDistribution2.getAbsolutePath(), exception);
        }
        org.xowl.infra.utils.Files.deleteFolder(temp);
        return targetDistribution2;
    }

    /**
     * Resolves the dependencies for the distribution
     *
     * @return The files for the resolved dependency
     * @throws MojoFailureException if an expected problem (such as a compilation failure) occurs.
     *                              Throwing this exception causes a "BUILD FAILURE" message to be displayed.
     */
    private File[] resolveDependencies() throws MojoFailureException {
        File[] fileDependencies = new File[project.getModel().getDependencies().size()];
        int i = 0;
        for (Dependency dependency : project.getModel().getDependencies()) {
            File file = resolveArtifact(dependency);
            fileDependencies[i++] = file;
        }
        return fileDependencies;
    }

    /**
     * Deploys the dependency bundles into the distribution to build
     *
     * @param targetDistribution The directory of the distribution to build
     * @param fileDependencies   The file of the resolved dependencies
     * @param excludedDependency The file of the excluded dependency (base platform)
     * @throws MojoFailureException if an expected problem (such as a compilation failure) occurs.
     *                              Throwing this exception causes a "BUILD FAILURE" message to be displayed.
     */
    private void deployBundles(File targetDistribution, File[] fileDependencies, File excludedDependency) throws MojoFailureException {
        getLog().info("Deploying new bundles");
        File directoryFelix = new File(targetDistribution, "felix");
        File directoryBundles = new File(directoryFelix, "bundle");
        int i = 0;
        for (Dependency dependency : project.getModel().getDependencies()) {
            File bundleFileSource = fileDependencies[i++];
            if (bundleFileSource == excludedDependency)
                continue;
            File bundleFileTarget = dependency.getGroupId().equals(FELIX_DISTRIB_GROUP_ID) ?
                    new File(directoryBundles, dependency.getArtifactId() + "-" + dependency.getVersion() + ".jar") :
                    new File(directoryBundles, dependency.getGroupId() + "." + dependency.getArtifactId() + "-" + dependency.getVersion() + ".jar");
            try {
                Files.copy(bundleFileSource.toPath(), bundleFileTarget.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException exception) {
                getLog().error(exception);
                throw new MojoFailureException("Failed to copy " + bundleFileSource.getAbsolutePath() + " to " + bundleFileTarget.getAbsolutePath(), exception);
            }
        }
    }

    /**
     * Deploys configured resources into the distribution to build
     *
     * @param targetDistribution The directory of the distribution to build
     * @throws MojoFailureException if an expected problem (such as a compilation failure) occurs.
     *                              Throwing this exception causes a "BUILD FAILURE" message to be displayed.
     */
    private void deployResources(File targetDistribution) throws MojoFailureException {
        getLog().info("Deploying new resources");
        if (resources != null) {
            for (int i = 0; i != resources.length; i++) {
                File origin = resources[i];
                File target = new File(targetDistribution, origin.getName());
                copyResource(origin, target);
            }
        }
    }

    /**
     * Copy a resource for the distribution.
     * If the resource is a directory, ir is recursively copied.
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
     * Writes the descriptor for the distribution
     *
     * @param targetDistribution The directory of the distribution to build
     * @return The file for the descriptor
     * @throws MojoFailureException if an expected problem (such as a compilation failure) occurs.
     *                              Throwing this exception causes a "BUILD FAILURE" message to be displayed.
     */
    private File writeDescriptor(File targetDistribution) throws MojoFailureException {
        String iconName = "";
        String iconContent = "";
        if (icon != null) {
            try (InputStream stream = new FileInputStream(icon)) {
                byte[] bytes = org.xowl.infra.utils.Files.load(stream);
                iconContent = Base64.encodeBase64(bytes);
                iconName = icon.getName();
            } catch (IOException exception) {
                throw new MojoFailureException("Failed to read the specified icon (" + icon.getAbsolutePath() + ")", exception);
            }
        } else {
            getLog().warn("No icon has been specified");
        }
        String licenseText = project.getModel().getLicenses().get(0).getUrl();
        if (licenseFullText != null) {
            try (InputStream stream = new FileInputStream(licenseFullText)) {
                licenseText = org.xowl.infra.utils.Files.read(stream, org.xowl.infra.utils.Files.CHARSET);
            } catch (IOException exception) {
                throw new MojoFailureException("Failed to read the specified license (" + licenseFullText.getAbsolutePath() + ")", exception);
            }
        }


        getLog().info("Writing manifest");
        File targetDirectory = new File(project.getModel().getBuild().getDirectory());
        File fileDescriptor = new File(targetDirectory, getArtifactName() + ".json");
        try (FileOutputStream stream = new FileOutputStream(fileDescriptor)) {
            OutputStreamWriter writer = new OutputStreamWriter(stream, org.xowl.infra.utils.Files.CHARSET);
            writer.write("{\n");
            writer.write("\t\"identifier\": \"" + TextUtils.escapeStringJSON(project.getModel().getGroupId() + "." + project.getModel().getArtifactId() + "-" + project.getModel().getVersion()) + "\",\n");
            writer.write("\t\"name\": \"" + TextUtils.escapeStringJSON(project.getModel().getName()) + "\",\n");
            writer.write("\t\"description\": \"" + TextUtils.escapeStringJSON(project.getModel().getDescription()) + "\",\n");
            writer.write("\t\"version\": {\n");
            writer.write("\t\t\"number\": \"" + TextUtils.escapeStringJSON(project.getModel().getVersion()) + "\",\n");
            writer.write("\t\t\"scmTag\": \"" + (versionScmTag == null ? "" : TextUtils.escapeStringJSON(versionScmTag)) + "\",\n");
            writer.write("\t\t\"buildUser\": \"" + TextUtils.escapeStringJSON(System.getProperty("user.name")) + "\",\n");
            writer.write("\t\t\"buildTag\": \"" + (versionBuildTag == null ? "" : TextUtils.escapeStringJSON(versionBuildTag)) + "\",\n");
            writer.write("\t\t\"buildTimestamp\": \"" + (versionBuildTimestamp == null ? "" : TextUtils.escapeStringJSON(versionBuildTimestamp)) + "\"\n");
            writer.write("\t},\n");
            writer.write("\t\"copyright\": \"Copyright (c) " + TextUtils.escapeStringJSON(project.getModel().getOrganization().getName()) + "\",\n");
            writer.write("\t\"iconName\": \"" + iconName + "\",\n");
            writer.write("\t\"iconContent\": \"" + iconContent + "\",\n");
            writer.write("\t\"vendor\": \"" + TextUtils.escapeStringJSON(project.getModel().getOrganization().getName()) + "\",\n");
            writer.write("\t\"vendorLink\": \"" + TextUtils.escapeStringJSON(project.getModel().getOrganization().getUrl()) + "\",\n");
            writer.write("\t\"link\": \"" + TextUtils.escapeStringJSON(project.getModel().getUrl()) + "\",\n");
            writer.write("\t\"license\": {\n");
            if (!project.getModel().getLicenses().isEmpty()) {
                writer.write("\t\t\"name\": \"" + TextUtils.escapeStringJSON(project.getModel().getLicenses().get(0).getName()) + "\",\n");
                writer.write("\t\t\"fullText\": \"" + TextUtils.escapeStringJSON(licenseText) + "\"\n");
            }
            writer.write("\t}\n");
            writer.write("}\n");
            writer.flush();
            writer.close();
        } catch (IOException exception) {
            getLog().error(exception);
            throw new MojoFailureException("Failed to write descriptor " + fileDescriptor.getAbsolutePath(), exception);
        }

        File fileDescriptor2 = new File(targetDistribution, "descriptor.json");
        try {
            Files.copy(fileDescriptor.toPath(), fileDescriptor2.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            getLog().error(exception);
            throw new MojoFailureException("Failed to copy " + fileDescriptor.getAbsolutePath() + " to " + fileDescriptor2.getAbsolutePath(), exception);
        }
        return fileDescriptor;
    }

    /**
     * Packages the resulting distribution and builds the corresponding Maven artifact
     *
     * @param targetDistribution The directory of the distribution to build
     * @throws MojoFailureException if an expected problem (such as a compilation failure) occurs.
     *                              Throwing this exception causes a "BUILD FAILURE" message to be displayed.
     */
    private void packageDistribution(File targetDistribution) throws MojoFailureException {
        getLog().info("Packaging ...");
        File filePackage = new File(new File(project.getModel().getBuild().getDirectory()), getArtifactName() + ".tar.gz");
        packageTarGz(targetDistribution, filePackage, project.getModel().getArtifactId());
        org.xowl.infra.utils.Files.deleteFolder(targetDistribution);

        DefaultArtifactHandler artifactHandler = new DefaultArtifactHandler("xowl-platform");
        artifactHandler.setAddedToClasspath(false);
        artifactHandler.setExtension("tar.gz");
        artifactHandler.setLanguage("java");
        artifactHandler.setIncludesDependencies(false);
        DefaultArtifact mainArtifact = new DefaultArtifact(
                project.getModel().getGroupId(),
                project.getModel().getArtifactId(),
                project.getModel().getVersion(),
                "compile",
                "xowl-platform",
                "",
                artifactHandler
        );
        mainArtifact.setFile(filePackage);
        project.setArtifact(mainArtifact);
    }

    /**
     * Creates an tar.gz archive from a directory
     *
     * @param input    The input directory
     * @param output   The output tar.gz file
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
     * Adds a directory to a tar archive
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
                if (child.canExecute()) {
                    // set as executable
                    entry.setMode(EXECUTABLE_MODE);
                }
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
                    if (entry.getMode() == EXECUTABLE_MODE) {
                        if (!target.setExecutable(true, false))
                            throw new MojoFailureException("Failed to set executable bit on " + target.getAbsolutePath());
                    }
                }
            }
        } catch (IOException exception) {
            getLog().error(exception);
            throw new MojoFailureException("Failed to extract " + input.getAbsolutePath(), exception);
        }
    }
}
