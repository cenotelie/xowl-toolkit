/*******************************************************************************
 * Copyright (c) 2016 Association Cénotélie (cenotelie.fr)
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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.impl.ArtifactResolver;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.xowl.infra.utils.Files;
import org.xowl.infra.utils.TextUtils;

import javax.inject.Inject;
import java.io.*;
import java.nio.charset.Charset;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds a static marketplace that contains addons for the the xOWL federation platform
 *
 * @author Laurent Wouters
 */
@Execute(goal = "marketplace-package", phase = LifecyclePhase.PACKAGE)
@Mojo(name = "marketplace-package", defaultPhase = LifecyclePhase.PACKAGE)
public class MarketplacePackageMojo extends AbstractMojo {
    /**
     * The version of the descriptor model produced by this plugin
     */
    public static final String MODEL_VERSION = "1.0";

    /*
     * The various required components
     */

    /**
     * The current artifact resolve
     */
    @Inject
    protected ArtifactResolver artifactResolver;

    /**
     * The current repository/network configuration of Maven.
     */
    @Parameter(defaultValue = "${repositorySystemSession}")
    protected RepositorySystemSession repositorySystemSession;

    /**
     * Maven project helper
     */
    @Component
    protected MavenProjectHelper projectHelper;

    /**
     * The current Maven project
     */
    @Parameter(readonly = true, defaultValue = "${project}", required = true)
    protected MavenProject project;

    /*
     * The parameters for this Mojo
     */

    /**
     * The categories for this marketplace
     */
    @Parameter
    protected Category[] categories;

    /**
     * The addons for this marketplace
     */
    @Parameter
    protected Addon[] addons;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        File targetDirectory = new File(project.getModel().getBuild().getDirectory());
        if (!targetDirectory.exists()) {
            if (!targetDirectory.mkdirs())
                throw new MojoFailureException("Failed to create target directory");
        }
        File fileDescriptor = writeDescriptor();
        File[] fileAddons = retrieveAddons();
        buildPackage(fileDescriptor, fileAddons);
    }

    /**
     * Gets the prefix name of artifacts
     *
     * @return The prefix name
     */
    private String getArtifactName() {
        return project.getModel().getArtifactId() + "-" + project.getModel().getVersion();
    }

    /**
     * Writes the marketplace descriptor
     *
     * @return The file for the descriptor
     * @throws MojoFailureException When writing failed
     */
    private File writeDescriptor() throws MojoFailureException {
        File targetDirectory = new File(project.getModel().getBuild().getDirectory());
        File marketplaceDescriptor = new File(targetDirectory, getArtifactName() + "-marketplace-descriptor.json");
        getLog().info("Writing descriptor for marketplace: " + marketplaceDescriptor.getName());
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(marketplaceDescriptor), Charset.forName("UTF-8"))) {
            writer.write("{\n");
            writer.write("\t\"modelVersion\": \"" + TextUtils.escapeStringJSON(MODEL_VERSION) + "\",\n");
            writer.write("\t\"categories\": [\n");
            if (categories != null) {
                for (int i = 0; i != categories.length; i++) {
                    writer.write("\t\t{\n");
                    writer.write("\t\t\t\"identifier\": \"" + TextUtils.escapeStringJSON(categories[i].identifier) + "\",\n");
                    writer.write("\t\t\t\"name\": \"" + TextUtils.escapeStringJSON(categories[i].name) + "\"\n");
                    if (i == categories.length - 1)
                        writer.write("\t\t}\n");
                    else
                        writer.write("\t\t},\n");
                }
            }
            writer.write("\t],\n");
            writer.write("\t\"addons\": [\n");
            if (addons != null) {
                for (int i = 0; i != addons.length; i++) {
                    writer.write("\t\t{\n");
                    writer.write("\t\t\t\"identifier\": \"" + TextUtils.escapeStringJSON(addons[i].groupId + "." + addons[i].artifactId + "-" + addons[i].version) + "\",\n");
                    writer.write("\t\t\t\"categories\": [\n");
                    if (addons[i].categories != null) {
                        for (int j = 0; j != addons[i].categories.length; j++) {
                            writer.write("\t\t\t\t\"" + TextUtils.escapeStringJSON(addons[i].categories[j]) + "\"");
                            if (j != addons[i].categories.length - 1)
                                writer.write(",");
                            writer.write("\n");
                        }
                    }
                    writer.write("\t\t\t]\n");
                    if (i == categories.length - 1)
                        writer.write("\t\t}\n");
                    else
                        writer.write("\t\t},\n");
                }
            }
            writer.write("\t]\n");
            writer.write("}\n");
            // attach the descriptor
            projectHelper.attachArtifact(
                    project,
                    "json",
                    "marketplace-descriptor",
                    marketplaceDescriptor
            );
        } catch (IOException exception) {
            throw new MojoFailureException("Failed to write the marketplace description", exception);
        }
        return marketplaceDescriptor;
    }

    /**
     * Retrieves the packages for the addons
     *
     * @throws MojoFailureException When the resolution failed
     */
    private File[] retrieveAddons() throws MojoFailureException {
        if (addons != null) {
            File[] result = new File[addons.length * 2];
            int j = 0;
            for (int i = 0; i != addons.length; i++) {
                result[j++] = retrieveAddonArtifact(addons[i], "addon-descriptor", "json");
                result[j++] = retrieveAddonArtifact(addons[i], "addon-package", "zip");
            }
            return result;
        }
        return null;
    }

    /**
     * Retrieves an artifact for an addon
     *
     * @param addon      The relevant addon
     * @param classifier The classifier of the artifact to retrieve
     * @param extension  The extension of the artifact to retrieve
     * @return The file for the bundle
     * @throws MojoFailureException When the resolution failed
     */
    private File retrieveAddonArtifact(Addon addon, String classifier, String extension) throws MojoFailureException {
        String name = addon.groupId + "." + addon.artifactId + "-" + addon.version + "-" + classifier + "." + extension;
        getLog().info("Resolving artifact: " + name);
        Artifact artifact = new DefaultArtifact(
                addon.groupId,
                addon.artifactId,
                classifier,
                extension,
                addon.version
        );
        try {
            ArtifactResult result = artifactResolver.resolveArtifact(repositorySystemSession, new ArtifactRequest(artifact, null, null));
            if (!result.isResolved()) {
                throw new MojoFailureException("Failed to resolve artifact " + name);
            }
            return result.getArtifact().getFile();
        } catch (ArtifactResolutionException exception) {
            throw new MojoFailureException("Failed to resolve artifact " + name, exception);
        }
    }

    /**
     * Builds the package for the marketplace
     *
     * @param fileDescriptor The file for the descriptor
     * @param fileAddons     The files for the addons
     * @throws MojoFailureException When the packaging failed
     */
    private void buildPackage(File fileDescriptor, File[] fileAddons) throws MojoFailureException {
        File targetDirectory = new File(project.getModel().getBuild().getDirectory());
        File marketplacePackage = new File(targetDirectory, getArtifactName() + "-marketplace-package.zip");
        getLog().info("Writing package for marketplace: " + marketplacePackage.getName());
        try (FileOutputStream fileStream = new FileOutputStream(marketplacePackage)) {
            try (ZipOutputStream stream = new ZipOutputStream(fileStream)) {
                stream.setLevel(9);
                buildPackageAddFile(
                        stream,
                        fileDescriptor,
                        "marketplace.json");
                if (addons != null) {
                    int j = 0;
                    for (int i = 0; i != addons.length; i++) {
                        buildPackageAddFile(
                                stream,
                                fileAddons[j++],
                                addons[i].groupId + "." + addons[i].artifactId + "-" + addons[i].version + ".descriptor");
                        buildPackageAddFile(
                                stream,
                                fileAddons[j++],
                                addons[i].groupId + "." + addons[i].artifactId + "-" + addons[i].version + ".zip");
                    }
                }
            }
            // attach the package
            projectHelper.attachArtifact(
                    project,
                    "zip",
                    "addon-package",
                    marketplacePackage
            );
        } catch (IOException exception) {
            throw new MojoFailureException("Failed to write the addon package", exception);
        }
    }

    /**
     * Adds a file to the package
     *
     * @param stream    The stream to the package
     * @param file      The file to add
     * @param entryName The name of the zip entry
     * @throws IOException          When an IO operation failed
     * @throws MojoFailureException When the packaging failed
     */
    private void buildPackageAddFile(ZipOutputStream stream, File file, String entryName) throws IOException, MojoFailureException {
        getLog().info("Adding package entry " + entryName + " for file " + file.getAbsolutePath());
        ZipEntry entry = new ZipEntry(entryName);
        entry.setMethod(ZipEntry.DEFLATED);
        stream.putNextEntry(entry);
        byte[] bytes;
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            bytes = Files.load(fileInputStream);
        } catch (FileNotFoundException exception) {
            throw new MojoFailureException("Cannot read file " + file.getAbsolutePath());
        }
        stream.write(bytes, 0, bytes.length);
        stream.closeEntry();
    }
}
