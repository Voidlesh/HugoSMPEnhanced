package com.hugosmpenhanced.loader;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Both mixin configs use this plugin. It lets a config apply only on its own Minecraft line and hands every call to
 * the mod's own plugin of that line, which is only loaded then (its classes are built for a different Java version).
 */
public class GatePlugin implements IMixinConfigPlugin {
	private IMixinConfigPlugin delegate;

	@Override
	public void onLoad(String mixinPackage) {
		boolean forModernLine = mixinPackage.startsWith(Line.NEW_PACKAGE);
		if (forModernLine != Line.modern()) {
			return;
		}
		try {
			this.delegate = (IMixinConfigPlugin) Class.forName(mixinPackage + ".HugoMixinPlugin")
					.getDeclaredConstructor().newInstance();
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("HugoSMP Enhanced mixin plugin missing", e);
		}
		this.delegate.onLoad(mixinPackage);
	}

	@Override
	public String getRefMapperConfig() {
		return this.delegate == null ? null : this.delegate.getRefMapperConfig();
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		return this.delegate != null && this.delegate.shouldApplyMixin(targetClassName, mixinClassName);
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
		if (this.delegate != null) {
			this.delegate.acceptTargets(myTargets, otherTargets);
		}
	}

	@Override
	public List<String> getMixins() {
		return this.delegate == null ? null : this.delegate.getMixins();
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
		if (this.delegate != null) {
			this.delegate.preApply(targetClassName, targetClass, mixinClassName, mixinInfo);
		}
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
		if (this.delegate != null) {
			this.delegate.postApply(targetClassName, targetClass, mixinClassName, mixinInfo);
		}
	}
}
