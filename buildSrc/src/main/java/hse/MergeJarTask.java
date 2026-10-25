package hse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import groovy.json.JsonBuilder;
import groovy.json.JsonSlurper;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

/**
 * Builds the one jar for Minecraft 1.21.11 and 26.x out of the jar of each line.
 *
 * <p>The 1.21.11 jar (remapped, Java 21) is the base. All classes of the 26.x jar (unobfuscated, Java 25) move to
 * {@code com.hugosmpenhanced.mc26}, so both copies can sit side by side. Two mixin configs (one per copy) and the
 * entrypoints use the gate classes of {@code com.hugosmpenhanced.loader}, which pick the copy of the running Minecraft
 * and never load the other one. The manifest of the 1.21.11 jar stays: without its mapping namespace Fabric does not
 * find the mixin targets.
 */
public abstract class MergeJarTask extends DefaultTask {
	static final String OLD = "com/hugosmpenhanced/";
	static final String NEW = "com/hugosmpenhanced/mc26/";
	static final String MIXINS = "hugosmpenhanced.client.mixins.json";
	static final String MIXINS_26 = "hugosmpenhanced.mc26.mixins.json";

	@InputFile
	public abstract RegularFileProperty getLegacyJar();

	@InputFile
	public abstract RegularFileProperty getModernJar();

	@OutputFile
	public abstract RegularFileProperty getOutput();

	@TaskAction
	@SuppressWarnings("unchecked")
	public void run() throws IOException {
		Remapper remapper = new Remapper() {
			@Override
			public String map(String name) {
				return name.startsWith(OLD) ? NEW + name.substring(OLD.length()) : name;
			}
		};
		Set<String> written = new HashSet<>();
		Map<String, byte[]> extra = new LinkedHashMap<>();
		try (JarFile legacy = new JarFile(getLegacyJar().get().getAsFile());
				JarFile modern = new JarFile(getModernJar().get().getAsFile());
				OutputStream file = Files.newOutputStream(getOutput().get().getAsFile().toPath())) {
			Manifest manifest = legacy.getManifest();
			try (JarOutputStream out = new JarOutputStream(file, manifest)) {
				for (Enumeration<JarEntry> e = legacy.entries(); e.hasMoreElements();) {
					JarEntry entry = e.nextElement();
					String name = entry.getName();
					if (entry.isDirectory() || name.equals("META-INF/MANIFEST.MF")) {
						continue;
					}
					byte[] data = read(legacy, entry);
					if (name.equals("fabric.mod.json")) {
						data = modJson(data);
					} else if (name.equals(MIXINS)) {
						data = replace(data, "\"plugin\": \"com.hugosmpenhanced.client.mixin.HugoMixinPlugin\"",
								"\"plugin\": \"com.hugosmpenhanced.loader.GatePlugin\"");
					}
					put(out, written, name, data);
				}
				for (Enumeration<JarEntry> e = modern.entries(); e.hasMoreElements();) {
					JarEntry entry = e.nextElement();
					String name = entry.getName();
					if (entry.isDirectory() || name.startsWith("META-INF/") || name.equals("fabric.mod.json")) {
						continue;
					}
					byte[] data = read(modern, entry);
					if (name.endsWith(".class")) {
						ClassWriter writer = new ClassWriter(0);
						new ClassReader(data).accept(new ClassRemapper(writer, remapper), 0);
						put(out, written, remapper.map(name.substring(0, name.length() - 6)) + ".class", writer.toByteArray());
					} else if (name.equals(MIXINS)) {
						String text = new String(data, StandardCharsets.UTF_8);
						text = text.replace("\"package\": \"com.hugosmpenhanced.client.mixin\"",
								"\"package\": \"com.hugosmpenhanced.mc26.client.mixin\"");
						text = text.replace("\"plugin\": \"com.hugosmpenhanced.client.mixin.HugoMixinPlugin\"",
								"\"plugin\": \"com.hugosmpenhanced.loader.GatePlugin\"");
						if (!text.contains("mc26.client.mixin") || !text.contains("loader.GatePlugin")) {
							throw new IllegalStateException(MIXINS + " of the 26.x jar has an unexpected package or plugin line");
						}
						put(out, written, MIXINS_26, text.getBytes(StandardCharsets.UTF_8));
					} else if (!written.contains(name)) {
						put(out, written, name, data);
					}
				}
			}
		}
	}

	/** One entrypoint and one mixin config per copy, the Minecraft range of both lines. */
	@SuppressWarnings("unchecked")
	private byte[] modJson(byte[] data) {
		Map<String, Object> json = (Map<String, Object>) new JsonSlurper().parse(data);
		Map<String, Object> entrypoints = (Map<String, Object>) json.get("entrypoints");
		entrypoints.put("client", List.of("com.hugosmpenhanced.loader.Entry"));
		entrypoints.put("modmenu", List.of("com.hugosmpenhanced.loader.ModMenuEntry"));
		json.put("mixins", List.of(
				Map.of("config", MIXINS, "environment", "client"),
				Map.of("config", MIXINS_26, "environment", "client")));
		Map<String, Object> depends = (Map<String, Object>) json.get("depends");
		depends.put("minecraft", List.of("1.21.11", ">=26.3"));
		depends.put("java", ">=21");
		return new JsonBuilder(json).toPrettyString().getBytes(StandardCharsets.UTF_8);
	}

	private static byte[] replace(byte[] data, String from, String to) {
		String text = new String(data, StandardCharsets.UTF_8);
		if (!text.contains(from)) {
			throw new IllegalStateException("mixin config does not contain: " + from);
		}
		return text.replace(from, to).getBytes(StandardCharsets.UTF_8);
	}

	private static byte[] read(JarFile jar, JarEntry entry) throws IOException {
		try (InputStream in = jar.getInputStream(entry)) {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			in.transferTo(out);
			return out.toByteArray();
		}
	}

	private static void put(JarOutputStream out, Set<String> written, String name, byte[] data) throws IOException {
		if (!written.add(name)) {
			throw new IllegalStateException("both jars contain " + name);
		}
		out.putNextEntry(new JarEntry(name));
		out.write(data);
		out.closeEntry();
	}
}
