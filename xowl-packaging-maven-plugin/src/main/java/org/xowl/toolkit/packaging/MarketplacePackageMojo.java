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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;
import org.xowl.infra.utils.TextUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.zip.ZipOutputStream;

/**
 * Builds a static marketplace that contains addons for the the xOWL federation platform
 *
 * @author Laurent Wouters
 */
@Mojo(name = "xowl-marketplace-package", defaultPhase = LifecyclePhase.PACKAGE)
public class MarketplacePackageMojo extends PackagingAbstractMojo {
    /**
     * The version of the descriptor model produced by this plugin
     */
    public static final String MODEL_VERSION = "1.0";

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        File targetDirectory = new File(project.getModel().getBuild().getDirectory());
        if (!targetDirectory.exists()) {
            if (!targetDirectory.mkdirs())
                throw new MojoFailureException("Failed to create target directory");
        }

        Collection<MavenProject> addons = new ArrayList<>();
        for (MavenProject candidate : project.getCollectedProjects()) {
            if (candidate.getParent() == project) {
                addons.add(candidate);
            }
        }

        File fileDescriptor = writeDescriptor(addons);
        File[] fileAddons = retrieveAddons(addons);
        File filePackage = buildPackage(fileDescriptor, fileAddons, addons);
        // attach the artifacts
        projectHelper.attachArtifact(
                project,
                "json",
                "xowl-marketplace-descriptor",
                fileDescriptor
        );
        projectHelper.attachArtifact(
                project,
                "zip",
                "xowl-marketplace",
                filePackage
        );
    }

    /**
     * Writes the marketplace descriptor
     *
     * @param addons The addons of this marketplace
     * @return The file for the descriptor
     * @throws MojoFailureException When writing failed
     */
    private File writeDescriptor(Collection<MavenProject> addons) throws MojoFailureException {
        File targetDirectory = new File(project.getModel().getBuild().getDirectory());
        File marketplaceDescriptor = new File(targetDirectory, getArtifactName() + "-xowl-marketplace-descriptor.json");
        getLog().info("Writing descriptor for marketplace: " + marketplaceDescriptor.getName());
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(marketplaceDescriptor), Charset.forName("UTF-8"))) {
            writer.write("{\n");
            writer.write("\t\"modelVersion\": \"" + TextUtils.escapeStringJSON(MODEL_VERSION) + "\",\n");
            writer.write("\t\"addons\": [\n");
            if (!addons.isEmpty()) {
                boolean first = true;
                for (MavenProject module : addons) {
                    if (!first)
                        writer.write(",\n");
                    first = false;
                    writer.write("\t\"" + TextUtils.escapeStringJSON(module.getGroupId() + "." + module.getArtifactId() + "-" + module.getVersion()) + "\"");
                }
                writer.write("\n");
            }
            writer.write("\t]\n");
            writer.write("}\n");
        } catch (IOException exception) {
            throw new MojoFailureException("Failed to write the marketplace description", exception);
        }
        return marketplaceDescriptor;
    }

    /**
     * Retrieves the packages for the addons
     *
     * @param addons The addons of this marketplace
     * @throws MojoFailureException When the resolution failed
     */
    private File[] retrieveAddons(Collection<MavenProject> addons) throws MojoFailureException {
        File[] result = new File[project.getDependencies().size() * 2];
        int i = 0;
        for (MavenProject addon : addons) {
            result[i++] = resolveArtifact(addon.getGroupId(), addon.getArtifactId(), addon.getVersion(), "xowl-addon", "zip");
            result[i++] = resolveArtifact(addon.getGroupId(), addon.getArtifactId(), addon.getVersion(), "xowl-addon-descriptor", "json");
        }
        return result;
    }

    /**
     * Builds the package for the marketplace
     *
     * @param fileDescriptor The file for the descriptor
     * @param fileAddons     The files for the addons
     * @param addons         The addons of this marketplace
     * @return The file for the package
     * @throws MojoFailureException When the packaging failed
     */
    private File buildPackage(File fileDescriptor, File[] fileAddons, Collection<MavenProject> addons) throws MojoFailureException {
        File targetDirectory = new File(project.getModel().getBuild().getDirectory());
        File marketplacePackage = new File(targetDirectory, getArtifactName() + "-xowl-marketplace.zip");
        getLog().info("Writing package for marketplace: " + marketplacePackage.getName());
        try (FileOutputStream fileStream = new FileOutputStream(marketplacePackage)) {
            try (ZipOutputStream stream = new ZipOutputStream(fileStream)) {
                stream.setLevel(9);
                zipAddFile(
                        stream,
                        fileDescriptor,
                        "marketplace.json");
                int i = 0;
                for (MavenProject addon : addons) {
                    zipAddFile(
                            stream,
                            fileAddons[i++],
                            addon.getGroupId() + "." + addon.getArtifactId() + "-" + addon.getVersion() + ".zip");
                    zipAddFile(
                            stream,
                            fileAddons[i++],
                            addon.getGroupId() + "." + addon.getArtifactId() + "-" + addon.getVersion() + ".descriptor");
                }
            }
            return marketplacePackage;
        } catch (IOException exception) {
            throw new MojoFailureException("Failed to write the addon package", exception);
        }
    }
}
