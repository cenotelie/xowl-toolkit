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

package org.xowl.toolkit.maven.addonbuilder;

import org.apache.maven.model.Model;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.impl.ArtifactResolver;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.xowl.infra.utils.Base64;
import org.xowl.infra.utils.Files;
import org.xowl.infra.utils.TextUtils;

import javax.inject.Inject;
import java.io.*;
import java.nio.charset.Charset;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds the xOWL addon package that can be deployed onto a marketplace so that xOWL federation platforms can use it.
 *
 * @author Laurent Wouters
 */
@Execute(goal = "addon-package", phase = LifecyclePhase.PACKAGE)
@Mojo(name = "addon-package", defaultPhase = LifecyclePhase.PACKAGE)
public class AddonPackageMojo extends AbstractMojo {
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
     * The model for the current project
     */
    @Parameter(readonly = true, defaultValue = "${project.model}")
    protected Model model;

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
    protected String icon;

    /**
     * The description of the pricing policy for the addon
     */
    @Parameter
    protected String pricing;

    /**
     * The additional bundles for the addon
     */
    @Parameter
    protected Bundle[] bundles;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        File descriptor = writeDescritor();
        File[] files = retrieveBundles();
        buildPackage(descriptor, new File(model.getBuild().getDirectory(), model.getArtifactId() + "-" + model.getVersion() + ".jar"), files);
    }

    /**
     * Writes the addon descriptor
     *
     * @return The file for the descriptor
     * @throws MojoFailureException When writing failed
     */
    private File writeDescritor() throws MojoFailureException {
        String iconName = "";
        String iconContent = "";
        if (icon != null && !icon.isEmpty()) {
            File directory = new File(model.getBuild().getDirectory());
            File fileIcon = new File(directory.getParentFile(), icon);
            try (InputStream stream = new FileInputStream(fileIcon)) {
                byte[] bytes = Files.load(stream);
                iconContent = Base64.encodeBase64(bytes);
                iconName = fileIcon.getName();
            } catch (IOException exception) {
                throw new MojoFailureException("Failed to read the specified icon (" + icon + ")", exception);
            }
        } else {
            getLog().warn("No icon has been specified");
        }

        File targetDirectory = new File(model.getBuild().getDirectory());
        File addonDescriptor = new File(targetDirectory, model.getGroupId() + "." + model.getArtifactId() + "-" + model.getVersion() + "-addon.descriptor");
        getLog().info("Writing descriptor for addon: " + addonDescriptor.getName());
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(addonDescriptor), Charset.forName("UTF-8"))) {
            writer.write("{\n");
            writer.write("\t\"identifier\": \"" + TextUtils.escapeStringJSON(model.getGroupId() + "." + model.getArtifactId()) + "\",\n");
            writer.write("\t\"name\": \"" + TextUtils.escapeStringJSON(model.getName()) + "\",\n");
            writer.write("\t\"description\": \"" + TextUtils.escapeStringJSON(model.getDescription()) + "\",\n");
            writer.write("\t\"version\": {\n");
            writer.write("\t\t\"number\": \"" + TextUtils.escapeStringJSON(model.getVersion()) + "\",\n");
            writer.write("\t\t\"scmTag\": \"" + (versionScmTag == null ? "" : TextUtils.escapeStringJSON(versionScmTag)) + "\",\n");
            writer.write("\t\t\"buildUser\": \"\",\n");
            writer.write("\t\t\"buildTag\": \"" + (versionBuildTag == null ? "" : TextUtils.escapeStringJSON(versionBuildTag)) + "\",\n");
            writer.write("\t\t\"buildTimestamp\": \"" + (versionBuildTimestamp == null ? "" : TextUtils.escapeStringJSON(versionBuildTimestamp)) + "\"\n");
            writer.write("\t},\n");
            writer.write("\t\"copyright\": \"Copyright (c) " + TextUtils.escapeStringJSON(model.getOrganization().getName()) + "\",\n");
            writer.write("\t\"iconName\": \"" + iconName + "\",\n");
            writer.write("\t\"iconContent\": \"" + iconContent + "\",\n");
            writer.write("\t\"vendor\": \"" + TextUtils.escapeStringJSON(model.getOrganization().getName()) + "\",\n");
            writer.write("\t\"vendorLink\": \"" + TextUtils.escapeStringJSON(model.getOrganization().getUrl()) + "\",\n");
            writer.write("\t\"link\": \"" + TextUtils.escapeStringJSON(model.getUrl()) + "\",\n");
            writer.write("\t\"license\": {\n");
            if (!model.getLicenses().isEmpty()) {
                writer.write("\t\t\"name\": \"" + TextUtils.escapeStringJSON(model.getLicenses().get(0).getName()) + "\",\n");
                writer.write("\t\t\"fullText\": \"" + TextUtils.escapeStringJSON(model.getLicenses().get(0).getUrl()) + "\"\n");
            }
            writer.write("\t},\n");
            writer.write("\t\"pricing\": \"" + (pricing == null ? "" : TextUtils.escapeStringJSON(pricing)) + "\",\n");
            writer.write("\t\"bundles\": [\n");
            if (bundles != null) {
                for (int i = 0; i != bundles.length; i++) {
                    writer.write("\t\t{\n");
                    writer.write("\t\t\t\"groupId\": \"" + TextUtils.escapeStringJSON(bundles[i].groupId) + "\",\n");
                    writer.write("\t\t\t\"artifactId\": \"" + TextUtils.escapeStringJSON(bundles[i].artifactId) + "\",\n");
                    writer.write("\t\t\t\"version\": \"" + TextUtils.escapeStringJSON(bundles[i].version) + "\"\n");
                    writer.write("\t\t},\n");
                }
            }
            writer.write("\t\t{\n");
            writer.write("\t\t\t\"groupId\": \"" + TextUtils.escapeStringJSON(model.getGroupId()) + "\",\n");
            writer.write("\t\t\t\"artifactId\": \"" + TextUtils.escapeStringJSON(model.getArtifactId()) + "\",\n");
            writer.write("\t\t\t\"version\": \"" + TextUtils.escapeStringJSON(model.getVersion()) + "\"\n");
            writer.write("\t\t}\n");
            writer.write("\t]\n");
            writer.write("}\n");
        } catch (IOException exception) {
            throw new MojoFailureException("Failed to write the addon description", exception);
        }
        return addonDescriptor;
    }

    /**
     * Retrieves the bundles for the addon
     *
     * @throws MojoFailureException When the resolution failed
     */
    private File[] retrieveBundles() throws MojoFailureException {
        if (bundles != null) {
            File[] result = new File[bundles.length];
            for (int i = 0; i != bundles.length; i++) {
                result[i] = retrieveBundle(bundles[i]);
            }
            return result;
        }
        return null;
    }

    /**
     * Retrieves a bundle for the addon
     *
     * @param bundle The bundle to retrieve
     * @return The file for the bundle
     * @throws MojoFailureException When the resolution failed
     */
    private File retrieveBundle(Bundle bundle) throws MojoFailureException {
        getLog().info("Resolving artifact: " + bundle.groupId + "." + bundle.artifactId + "-" + bundle.version);
        Artifact artifact = new DefaultArtifact(
                bundle.groupId,
                bundle.artifactId,
                "jar",
                bundle.version
        );
        try {
            ArtifactResult result = artifactResolver.resolveArtifact(repositorySystemSession, new ArtifactRequest(artifact, null, null));
            if (!result.isResolved()) {
                throw new MojoFailureException("Failed to resolve artifact " + bundle.groupId + "." + bundle.artifactId + "-" + bundle.version);
            }
            return result.getArtifact().getFile();
        } catch (ArtifactResolutionException exception) {
            throw new MojoFailureException("Failed to resolve artifact " + bundle.groupId + "." + bundle.artifactId + "-" + bundle.version, exception);
        }
    }

    /**
     * Builds the package for the addon
     *
     * @param fileDescriptor The file for the descriptor
     * @param fileMain       The file for the main bundle
     * @param fileBundles    The files for the other bundles
     * @throws MojoFailureException When the packaging failed
     */
    private void buildPackage(File fileDescriptor, File fileMain, File[] fileBundles) throws MojoFailureException {
        File targetDirectory = new File(model.getBuild().getDirectory());
        File addonPackage = new File(targetDirectory, model.getGroupId() + "." + model.getArtifactId() + "-" + model.getVersion() + "-addon.zip");
        getLog().info("Writing package for addon: " + addonPackage.getName());
        try (FileOutputStream fileStream = new FileOutputStream(addonPackage)) {
            try (ZipOutputStream stream = new ZipOutputStream(fileStream)) {
                buildPackageAddFile(
                        stream,
                        fileDescriptor,
                        "descriptor.json");
                buildPackageAddFile(
                        stream,
                        fileMain,
                        model.getGroupId() + "." + model.getArtifactId() + "-" + model.getVersion() + ".jar");
                if (bundles != null) {
                    for (int i = 0; i != bundles.length; i++) {
                        buildPackageAddFile(
                                stream,
                                fileBundles[i],
                                bundles[i].groupId + "." + bundles[i].artifactId + "-" + bundles[i].version + ".jar");
                    }
                }
            }
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
        stream.putNextEntry(new ZipEntry(entryName));
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
