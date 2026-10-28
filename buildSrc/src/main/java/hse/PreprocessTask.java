package hse;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFiles;
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

	@InputFiles
	@PathSensitive(PathSensitivity.NONE)
	public abstract ConfigurableFileCollection getSwaps();

	/** 0 for 1.21.11, 1 for 26.1, 2 for 26.2, 3 for 26.3. */
	@Input
	public abstract Property<Integer> getLevel();

	@OutputDirectory
	public abstract DirectoryProperty getTarget();

	@TaskAction
	public void run() throws IOException {
		Path source = getSource().get().getAsFile().toPath();
		Path target = getTarget().get().getAsFile().toPath();
		List<Preprocessor.Swap> swaps = new ArrayList<>();
		for (File file : getSwaps().getFiles()) {
			swaps.addAll(Preprocessor.readSwaps(file.toPath()));
		}
		new Preprocessor(getLevel().get(), swaps).run(source, target);
	}
}
