package org.javacs.kt.jdt.ls.extension;

import java.nio.file.Paths;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.managers.MavenBuildSupport;
import org.eclipse.jdt.ls.core.internal.managers.MavenProjectImporter;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.project.IMavenProjectFacade;

/**
 * Custom importer for kotlin maven projects.
 * This relies on the JDT LS MavenProjectImporter to actually import the base project.
 * It then updates the classpath to include the kotlin compiled classes.
 */
@SuppressWarnings("restriction")
public class KotlinMavenImporter extends MavenProjectImporter  {

	/**
	 * Checks if this is a maven kotlin project.
	 * For now, we rely on MavenProjectImporter.applies to check if this is a maven project.
	 * On top of that, we check if we have any kotlin files in the workspace.
	 * If we do, we assume this is a kotlin maven project.
	 * TODO: We should make this more robust in the future.
	 */
	@Override
	public boolean applies(IProgressMonitor monitor) throws CoreException {
		return super.applies(monitor) && KotlinImporterUtils.anyKotlinFiles(rootFolder.toPath());
	}

	@Override
	public void reset() {
		super.reset();
	}

	/**
	 * This imports the project. It relies on the MavenProjectImporter.importToWorkspace to do most of the work.
	 * Once the base maven project is imported, we add the kls folder to the classpath as a library.
	 * The kls folder is the folder used by the kotlin language server to output classfiles.
	 */
	@Override
	public void importToWorkspace(IProgressMonitor monitor) throws CoreException {
		super.importToWorkspace(monitor);

		for (IMavenProjectFacade mavenProject : MavenPlugin.getMavenProjectRegistry().getProjects()) {
			IProject project = mavenProject.getProject();
			java.nio.file.Path path = Paths.get(project.getLocation().toOSString(), "kls");
			KotlinImporterUtils.setKlsClasspathEntry(project, path, monitor, new MavenBuildSupport());
			KotlinImporterUtils.registerKlsWatcher(path, subMonitor -> {
				try {
					for (IMavenProjectFacade underliyingMavenProject : MavenPlugin.getMavenProjectRegistry().getProjects()) {
						KotlinImporterUtils.setKlsClasspathEntry(underliyingMavenProject.getProject(), path, subMonitor, new MavenBuildSupport());
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
