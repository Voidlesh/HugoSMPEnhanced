package com.hugosmpenhanced.client.feature.chatfilter;

import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * A recognizable chat message category. {@code visible} reflects the current config
 * toggle for this category (not whether the rule itself is "active").
 */
public record ChatFilterRule(Pattern pattern, Supplier<Boolean> visible) {
	public boolean isVisible() {
		return visible.get();
	}
}
