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
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.xowl.infra.utils.TextUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.zip.ZipOutputStream;

/**
 * Builds a static marketplace that contains addons for the the xOWL federation platform
 *
 * @author Laurent Wouters
 */
@Execute(goal = "marketplace-package", phase = LifecyclePhase.PACKAGE)
@Mojo(name = "marketplace-package", defaultPhase = LifecyclePhase.PACKAGE)
public class MarketplacePackageMojo extends PackagingAbstractMojo {
    /**
     * The version of the descriptor model produced by this plugin
     */
    public static final String MODEL_VERSION = "1.0";

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
                result[j++] = resolveArtifact(addons[i].groupId, addons[i].artifactId, addons[i].version, "addon-descriptor", "json");
                result[j++] = resolveArtifact(addons[i].groupId, addons[i].artifactId, addons[i].version, "addon-package", "zip");
            }
            return result;
        }
        return null;
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
                zipAddFile(
                        stream,
                        fileDescriptor,
                        "marketplace.json");
                if (addons != null) {
                    int j = 0;
                    for (int i = 0; i != addons.length; i++) {
                        zipAddFile(
                                stream,
                                fileAddons[j++],
                                addons[i].groupId + "." + addons[i].artifactId + "-" + addons[i].version + ".descriptor");
                        zipAddFile(
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
}
