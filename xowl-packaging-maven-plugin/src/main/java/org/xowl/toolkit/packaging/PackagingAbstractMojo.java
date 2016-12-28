package org.xowl.toolkit.packaging;

import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
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

import javax.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Represents an abstract Mojo that defines some useful components as parameters
 */
public abstract class PackagingAbstractMojo extends AbstractMojo {
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
     * Maven manager for artifact handlers
     */
    @Component
    protected ArtifactHandlerManager artifactHandlerManager;

    /**
     * The current Maven project
     */
    @Parameter(readonly = true, defaultValue = "${project}", required = true)
    protected MavenProject project;


    /**
     * Gets the prefix name of artifacts
     *
     * @return The prefix name
     */
    protected String getArtifactName() {
        return project.getModel().getArtifactId() + "-" + project.getModel().getVersion();
    }

    /**
     * Resolves an artifact
     *
     * @param dependency The artifact specification to resolve
     * @return The file for the bundle
     * @throws MojoFailureException When the resolution failed
     */
    protected File resolveArtifact(Dependency dependency) throws MojoFailureException {
        String type = dependency.getType();
        if (type == null)
            type = "jar";
        String extension = type;
        ArtifactHandler handler = artifactHandlerManager.getArtifactHandler(type);
        if (handler != null) {
            extension = handler.getExtension();
            if (extension == null)
                extension = type;
        }
        String classifier = dependency.getClassifier();
        if (classifier == null)
            classifier = "";

        String name = dependency.getGroupId() + "." + dependency.getArtifactId() + "-" + dependency.getVersion();
        if (!classifier.isEmpty())
            name += "-" + classifier;
        name += "." + extension;

        return resolveArtifact(
                dependency.getGroupId(),
                dependency.getArtifactId(),
                dependency.getVersion(),
                classifier,
                extension);
    }

    /**
     * Resolves an artifact
     *
     * @param groupId    The groupId of the artifact
     * @param artifactId The artifactId of the artifact
     * @param version    The version of the artifact
     * @param classifier The classifier of the artifact
     * @param extension  The extension of the artifact
     * @return The file for the bundle
     * @throws MojoFailureException When the resolution failed
     */
    protected File resolveArtifact(String groupId, String artifactId, String version, String classifier, String extension) throws MojoFailureException {
        String name = groupId + "." + artifactId + "-" + version;
        if (!classifier.isEmpty())
            name += "-" + classifier;
        name += "." + extension;

        getLog().info("Resolving artifact: " + name);
        Artifact artifact = new DefaultArtifact(
                groupId,
                artifactId,
                classifier,
                extension,
                version);
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
     * Adds a file to the zip package
     *
     * @param stream    The stream to the zip package
     * @param file      The file to add
     * @param entryName The name of the zip entry
     * @throws IOException          When an IO operation failed
     * @throws MojoFailureException When the packaging failed
     */
    protected void zipAddFile(ZipOutputStream stream, File file, String entryName) throws IOException, MojoFailureException {
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
