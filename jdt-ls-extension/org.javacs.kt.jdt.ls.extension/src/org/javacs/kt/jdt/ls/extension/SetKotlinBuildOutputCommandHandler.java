package org.javacs.kt.jdt.ls.extension;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.ls.core.internal.IDelegateCommandHandler;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.managers.GradleBuildSupport;
import org.eclipse.jdt.ls.core.internal.managers.IBuildSupport;
import org.eclipse.jdt.ls.core.internal.managers.MavenBuildSupport;

/**
 * Delegate command handler that adds a Kotlin build output to the classpath.
 * This also registers a directory watcher to update the classpath as new compiled files are added and removed.
 */
@SuppressWarnings("restriction")
public class SetKotlinBuildOutputCommandHandler implements IDelegateCommandHandler {

	/**
	 * Goes through all the projects and updates their classpath with the argument received in this command.
	 */
	@Override
	public Object executeCommand(String commandId, List<Object> arguments, IProgressMonitor monitor) throws Exception {
		if (!arguments.isEmpty()) {
			String outputLocation = arguments.get(0).toString();
			Path path = Paths.get(outputLocation);

			for (IProject project : ProjectUtils.getAllProjects()) {
				IBuildSupport buildSupport = null;
				
				if (ProjectUtils.isMavenProject(project)) {
					buildSupport = new MavenBuildSupport();
				} else if (ProjectUtils.isGradleProject(project)) {
					buildSupport = new GradleBuildSupport();
				}

				if (buildSupport != null) {
					KotlinImporterUtils.setKlsClasspathEntry(project, path, monitor, buildSupport);
					KotlinImporterUtils.registerKlsWatcher(path, subMonitor -> {
						try {
							for (IProject underlyingProject : ProjectUtils.getAllProjects()) {
								if (ProjectUtils.isMavenProject(project)) {
									KotlinImporterUtils.setKlsClasspathEntry(underlyingProject, path, subMonitor, new MavenBuildSupport());
								} else if (ProjectUtils.isGradleProject(project)) {
									KotlinImporterUtils.setKlsClasspathEntry(underlyingProject, path, subMonitor, new GradleBuildSupport());
								}
							}
		
							return Status.OK_STATUS;
						} catch (CoreException ex) {
							JavaLanguageServerPlugin.logException(ex);
							return Status.error("An error occurred while updating the kotlin classpath entry");
						}
					});
				}
			}
		}

		return null;
	}
}
