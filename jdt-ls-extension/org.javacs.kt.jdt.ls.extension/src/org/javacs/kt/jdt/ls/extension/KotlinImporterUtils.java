package org.javacs.kt.jdt.ls.extension;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.core.ClasspathEntry;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.managers.IBuildSupport;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager.CHANGE_TYPE;

@SuppressWarnings("restriction")
public class KotlinImporterUtils {

	/**
	 * Sets the KLS classpath entry.
	 * This is the path where the KLS stores build outputs (i.e., Kotlin compiled code).
	 * The entry has an extra attribute, which is used to remove old paths whenever they change, since this is something that is kept between sessions.
	 */
    public static void setKlsClasspathEntry(IProject project, java.nio.file.Path path, IProgressMonitor monitor, IBuildSupport buildSupport) throws CoreException {
		IJavaProject javaProject = JavaCore.create(project);

		List<IClasspathEntry> entries = new ArrayList<>(Arrays.asList(javaProject.getRawClasspath()));
		IPath klsPath = Path.fromOSString(path.toString());

		entries.removeIf(entry -> entry.getPath().equals(klsPath));
		entries.removeIf(entry -> Stream.of(entry.getExtraAttributes()).anyMatch(attribute -> attribute.getName().equals("kls")));
		IClasspathEntry classpathEntry = JavaCore.newLibraryEntry(
			klsPath,
			null,
			null,
			ClasspathEntry.NO_ACCESS_RULES,
			new IClasspathAttribute[]{
				JavaCore.newClasspathAttribute("kls", "true")
			},
			false
		);
		entries.add(classpathEntry);

		javaProject.setRawClasspath(entries.toArray(new IClasspathEntry[0]), monitor);
		buildSupport.refresh(project, CHANGE_TYPE.CHANGED, monitor);
	}

    /**
	 * Registers a watcher for the kls build output directory.
	 * Any file change leads to an update in the classpath.
     * The classpath update is done using a function sent as parameter, since it's dependent on the build tool used.
	 */
	public static void registerKlsWatcher(java.nio.file.Path path, Function<IProgressMonitor, IStatus> updateClasspathJob) {
		new Thread(() -> {
			try {
				Files.createDirectories(path);
				Map<WatchKey, java.nio.file.Path> keys = new HashMap<>();
				// Create the watcher and watch all directories under the base kls directory.
				WatchService watcher = FileSystems.getDefault().newWatchService();
				keys.putAll(watchDirectory(watcher, path));

				// Wait for file related events.
				// When a file is added or deleted, we update the classpath (we remove and re-add the kls classpath entry)
				while (true) {
					WatchKey key;
					if ((key = watcher.take()) != null) {
						java.nio.file.Path dir = keys.get(key);
						if (dir != null) {
							key.pollEvents().forEach(event -> {
								@SuppressWarnings("unchecked")
								WatchEvent<java.nio.file.Path> ev = (WatchEvent<java.nio.file.Path>) event;

								java.nio.file.Path name = ev.context();
								java.nio.file.Path child = dir.resolve(name);

								// If a new directory was created, we need to watch it as well.
								if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
									if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
										keys.putAll(watchDirectory(watcher, dir));
									}
								}
							});
							Job job = new Job("Updating kotlin classpath entry") {
								@Override
								protected IStatus run(IProgressMonitor monitor) {
									return updateClasspathJob.apply(monitor);
								}
							};
							job.schedule();
							job.join();
							key.reset();
						}
					}
				}
			} catch (IOException | InterruptedException ex) {
				JavaLanguageServerPlugin.logException(ex);
			}
		}).start();
	}

    private static Map<WatchKey, java.nio.file.Path> watchDirectory(WatchService watcher, java.nio.file.Path path) {
		Map<WatchKey, java.nio.file.Path> keys = new HashMap<>();

		try {
			Files.walkFileTree(path, new SimpleFileVisitor<java.nio.file.Path>() {
				@Override
				public FileVisitResult preVisitDirectory(java.nio.file.Path dir, BasicFileAttributes attrs) throws IOException {
					WatchKey key = dir.register(watcher, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
					keys.put(key, dir);
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException ex) {
			JavaLanguageServerPlugin.logException(ex);
		}

		return keys;
	}
}
