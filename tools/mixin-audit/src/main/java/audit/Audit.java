package audit;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Set;
import java.util.TreeSet;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

public class Audit implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		String version = FabricLoader.getInstance().getModContainer("minecraft").get().getMetadata().getVersion().getFriendlyString();
		String[] parts = version.split("[^0-9]+");
		int minor = parts.length > 1 && !parts[1].isEmpty() ? Integer.parseInt(parts[1]) : 3;
		String config = "hugosmpenhanced.mc26_" + Math.max(1, Math.min(minor, 3)) + ".mixins.json";
		System.out.println("HSE-AUDIT minecraft " + version + " config " + config);
		int ok = 0;
		int bad = 0;
		Set<String> seen = new TreeSet<>();
		try (InputStream in = loader.getResourceAsStream(config)) {
			JsonObject json = JsonParser.parseReader(new InputStreamReader(in)).getAsJsonObject();
			String pkg = json.get("package").getAsString();
			Set<String> mixins = new TreeSet<>();
			for (String key : new String[] {"mixins", "client", "server"}) {
				if (json.has(key)) {
					for (JsonElement e : (JsonArray) json.get(key)) {
						mixins.add(pkg + "." + e.getAsString());
					}
				}
			}
			for (String mixin : mixins) {
				Set<String> targets = new TreeSet<>();
				try (InputStream cls = loader.getResourceAsStream(mixin.replace('.', '/') + ".class")) {
					new ClassReader(cls).accept(new ClassVisitor(Opcodes.ASM9) {
						@Override
						public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
							if (!descriptor.equals("Lorg/spongepowered/asm/mixin/Mixin;")) {
								return null;
							}
							return new AnnotationVisitor(Opcodes.ASM9) {
								@Override
								public AnnotationVisitor visitArray(String name) {
									return new AnnotationVisitor(Opcodes.ASM9) {
										@Override
										public void visit(String n, Object value) {
											if (value instanceof org.objectweb.asm.Type t) {
												targets.add(t.getClassName());
											} else if (value instanceof String s) {
												targets.add(s.replace('/', '.'));
											}
										}
									};
								}
							};
						}
					}, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
				}
				for (String target : targets) {
					if (!seen.add(target)) {
						continue;
					}
					try {
						Class.forName(target, false, loader);
						ok++;
					} catch (Throwable t) {
						bad++;
						Throwable root = t;
						StringBuilder chain = new StringBuilder();
						while (root != null) {
							chain.append(" | ").append(root.getClass().getSimpleName()).append(": ").append(String.valueOf(root.getMessage()).replace('\n', ' '));
							root = root.getCause() == root ? null : root.getCause();
						}
						System.out.println("HSE-FAIL " + mixin + " -> " + target + chain);
					}
				}
			}
		} catch (Throwable t) {
			System.out.println("HSE-AUDIT-ERROR " + t);
			t.printStackTrace(System.out);
			bad++;
		}
		System.out.println("HSE-AUDIT targets ok=" + ok + " failed=" + bad);
		Runtime.getRuntime().halt(bad == 0 ? 0 : 3);
	}
}
