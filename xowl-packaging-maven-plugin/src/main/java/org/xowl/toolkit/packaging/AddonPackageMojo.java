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

import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.xowl.infra.utils.Base64;
import org.xowl.infra.utils.Files;
import org.xowl.infra.utils.TextUtils;

import java.io.*;
import java.nio.charset.Charset;
import java.util.zip.ZipOutputStream;

/**
 * Builds the xOWL addon package that can be deployed onto a marketplace so that xOWL federation platforms can use it.
 *
 * @author Laurent Wouters
 */
@Mojo(name = "xowl-addon-package", defaultPhase = LifecyclePhase.PACKAGE)
public class AddonPackageMojo extends PackagingAbstractMojo {
    /**
     * The version of the descriptor model produced by this plugin
     */
    public static final String MODEL_VERSION = "1.0";

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
     * The tags for the addon
     */
    @Parameter
    protected String[] tags;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        File targetDirectory = new File(project.getModel().getBuild().getDirectory());
        if (!targetDirectory.exists()) {
            if (!targetDirectory.mkdirs())
                throw new MojoFailureException("Failed to create target directory");
        }

        File fileDescriptor = writeDescriptor();
        File[] fileBundles = retrieveBundles();
        File filePackage = buildPackage(fileDescriptor, fileBundles);

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
                "xowl-addon",
                "",
                artifactHandler
        );
        mainArtifact.setFile(filePackage);
        project.setArtifact(mainArtifact);
        projectHelper.attachArtifact(
                project,
                "json",
                "",
                fileDescriptor
        );
    }

    /**
     * Writes the addon descriptor
     *
     * @return The file for the descriptor
     * @throws MojoFailureException When writing failed
     */
    private File writeDescriptor() throws MojoFailureException {
        String iconName = "";
        String iconContent = "";
        if (icon != null && !icon.isEmpty()) {
            File directory = new File(project.getModel().getBuild().getDirectory());
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

        File targetDirectory = new File(project.getModel().getBuild().getDirectory());
        File addonDescriptor = new File(targetDirectory, getArtifactName() + ".json");
        getLog().info("Writing descriptor for addon: " + addonDescriptor.getName());
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(addonDescriptor), Charset.forName("UTF-8"))) {
            writer.write("{\n");
            writer.write("\t\"modelVersion\": \"" + TextUtils.escapeStringJSON(MODEL_VERSION) + "\",\n");
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
                writer.write("\t\t\"fullText\": \"" + TextUtils.escapeStringJSON(project.getModel().getLicenses().get(0).getUrl()) + "\"\n");
            }
            writer.write("\t},\n");
            writer.write("\t\"pricing\": \"" + (pricing == null ? "" : TextUtils.escapeStringJSON(pricing)) + "\",\n");
            writer.write("\t\"bundles\": [\n");
            boolean first = true;
            for (Dependency dependency : project.getModel().getDependencies()) {
                if (!first)
                    writer.write(",\n");
                first = false;
                writer.write("\t\t{\n");
                writer.write("\t\t\t\"groupId\": \"" + TextUtils.escapeStringJSON(dependency.getGroupId()) + "\",\n");
                writer.write("\t\t\t\"artifactId\": \"" + TextUtils.escapeStringJSON(dependency.getArtifactId()) + "\",\n");
                writer.write("\t\t\t\"version\": \"" + TextUtils.escapeStringJSON(dependency.getVersion()) + "\"\n");
                writer.write("\t\t}");
            }
            writer.write("\n\t],\n");
            writer.write("\t\"tags\": [\n");
            if (tags != null) {
                for (int i = 0; i != tags.length; i++) {
                    if (i != 0)
                        writer.write(",\n");
                    writer.write("\t\t\"" + TextUtils.escapeStringJSON(tags[i]) + "\"");
                }
                writer.write("\n");
            }
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
        File[] result = new File[project.getDependencies().size()];
        int i = 0;
        for (Dependency dependency : project.getModel().getDependencies()) {
            result[i++] = resolveArtifact(dependency);
        }
        return result;
    }

    /**
     * Builds the package for the addon
     *
     * @param fileDescriptor The file for the descriptor
     * @param fileBundles    The files for the bundles
     * @return The file for the package
     * @throws MojoFailureException When the packaging failed
     */
    private File buildPackage(File fileDescriptor, File[] fileBundles) throws MojoFailureException {
        File targetDirectory = new File(project.getModel().getBuild().getDirectory());
        File addonPackage = new File(targetDirectory, getArtifactName() + ".zip");
        getLog().info("Writing package for addon: " + addonPackage.getName());
        try (FileOutputStream fileStream = new FileOutputStream(addonPackage)) {
            try (ZipOutputStream stream = new ZipOutputStream(fileStream)) {
                stream.setLevel(9);
                zipAddFile(
                        stream,
                        fileDescriptor,
                        "descriptor.json");
                int i = 0;
                for (Dependency dependency : project.getModel().getDependencies()) {
                    zipAddFile(
                            stream,
                            fileBundles[i],
                            dependency.getGroupId() + "." + dependency.getArtifactId() + "-" + dependency.getVersion() + ".jar");
                }
            }
            return addonPackage;
        } catch (IOException exception) {
            throw new MojoFailureException("Failed to write the addon package", exception);
        }
    }
}
