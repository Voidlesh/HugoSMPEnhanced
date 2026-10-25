package hse;

import java.io.IOException;
import java.nio.file.Path;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

/** Writes the sources of one Minecraft line from the shared tree, see {@link Preprocessor}. */
@CacheableTask
public abstract class PreprocessTask extends DefaultTask {
	@InputDirectory
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract DirectoryProperty getSource();

	@InputFile
	@PathSensitive(PathSensitivity.NONE)
	public abstract RegularFileProperty getSwaps();

	@Input
	public abstract Property<Boolean> getModern();

	@OutputDirectory
	public abstract DirectoryProperty getTarget();

	@TaskAction
	public void run() throws IOException {
		Path source = getSource().get().getAsFile().toPath();
		Path target = getTarget().get().getAsFile().toPath();
		new Preprocessor(getModern().get(), Preprocessor.readSwaps(getSwaps().get().getAsFile().toPath())).run(source, target);
	}
}
