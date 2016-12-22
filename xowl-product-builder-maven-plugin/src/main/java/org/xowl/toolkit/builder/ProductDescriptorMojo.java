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

package org.xowl.toolkit.builder;

import org.apache.maven.model.Model;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.xowl.infra.utils.TextUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;

/**
 * The Mojo for the xOWL product builder
 *
 * @author Laurent Wouters
 */
@Mojo(name = "descriptor")
public class ProductDescriptorMojo extends AbstractMojo {
    /**
     * The model for the current project
     */
    @Parameter(readonly = true, defaultValue = "${project.model}")
    private Model model;

    /**
     * The additional bundles for the product
     */
    @Parameter
    private Bundle[] bundles;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        File targetDirectory = new File(model.getBuild().getDirectory());
        File productFile = new File(targetDirectory, model.getArtifactId() + "-" + model.getVersion() + ".product");
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(productFile), Charset.forName("UTF-8"))) {
            writer.write("{\n");
            writer.write("\t\"identifier\": \"" + TextUtils.escapeStringJSON(model.getGroupId() + "." + model.getArtifactId()) + "\",\n");
            writer.write("\t\"name\": \"" + TextUtils.escapeStringJSON(model.getName()) + "\",\n");
            writer.write("\t\"description\": \"" + TextUtils.escapeStringJSON(model.getDescription()) + "\",\n");
            writer.write("\t\"version\": \"" + TextUtils.escapeStringJSON(model.getVersion()) + "\",\n");
            writer.write("\t\"copyright\": \"Copyright (c) " + TextUtils.escapeStringJSON(model.getOrganization().getName()) + "\",\n");
            writer.write("\t\"vendor\": \"" + TextUtils.escapeStringJSON(model.getOrganization().getName()) + "\",\n");
            writer.write("\t\"vendorLink\": \"" + TextUtils.escapeStringJSON(model.getOrganization().getUrl()) + "\",\n");
            writer.write("\t\"link\": \"" + TextUtils.escapeStringJSON(model.getUrl()) + "\",\n");
            writer.write("\t\"license\": {\n");
            if (!model.getLicenses().isEmpty()) {
                writer.write("\t\t\"name\": \"" + TextUtils.escapeStringJSON(model.getLicenses().get(0).getName()) + "\",\n");
                writer.write("\t\t\"fullText\": \"" + TextUtils.escapeStringJSON(model.getLicenses().get(0).getUrl()) + "\"\n");
            }
            writer.write("\t},\n");
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
            throw new MojoFailureException("Failed to write the product description", exception);
        }
    }
}
