package com.hugosmpenhanced.client.feature.streamer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.hugosmpenhanced.client.config.ModConfigManager;
import com.hugosmpenhanced.client.formats.HugoFormats;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

/**
 * Recognises incoming payments ("KaiDerBauer hat dir $20.000 gezahlt.") and private messages
 * ("[Steve -> mir] join") in system chat lines. Only whole lines that start with the payer or a
 * plain server prefix count, so a player can never fake a bid by typing the text in public chat
 * (their line always starts with their own name and a separator). When the server words it
 * differently, one real message is enough to learn the wording.
 */
public final class ChatPatterns {
	private static final String NAME = "(?<name>[A-Za-z0-9_]{3,16})";
	private static final String AMOUNT = "(?<amount>" + Money.AMOUNT_REGEX + ")";
	/** At most one short tag like "[HugoSMP]" or "[$]" in front. */
	private static final String PREFIX = "^(?:\\[[^\\]]{1,24}]\\s*|[»>✉$]\\s*)?";
	private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

	private static final List<Pattern> PAYMENTS = List.of(
		Pattern.compile(PREFIX + NAME + " hat dir " + AMOUNT + " (?:gezahlt|bezahlt|gesendet|geschickt|überwiesen|gegeben|gespendet)[.!]?$", FLAGS),
		Pattern.compile(PREFIX + "du hast " + AMOUNT + " von " + NAME + " (?:erhalten|bekommen)[.!]?$", FLAGS),
		Pattern.compile(PREFIX + "du hast von " + NAME + " " + AMOUNT + " (?:erhalten|bekommen)[.!]?$", FLAGS),
		Pattern.compile(PREFIX + AMOUNT + " (?:wurden?|wurde dir) von " + NAME + " (?:erhalten|überwiesen|gezahlt|gesendet)[.!]?$", FLAGS),
		Pattern.compile(PREFIX + "(?:zahlung|überweisung) (?:von|erhalten von) " + NAME + "\\s*[:»-]\\s*\\+?" + AMOUNT + "[.!]?$", FLAGS),
		Pattern.compile(PREFIX + "\\+?" + AMOUNT + " von " + NAME + "(?: erhalten)?[.!]?$", FLAGS),
		Pattern.compile(PREFIX + AMOUNT + " (?:has been|have been|was) (?:received|sent to you) from " + NAME + "[.!]?$", FLAGS),
		Pattern.compile(PREFIX + "you (?:have )?received " + AMOUNT + " from " + NAME + "[.!]?$", FLAGS),
		Pattern.compile(PREFIX + NAME + " (?:has )?(?:paid|sent|given) you " + AMOUNT + "[.!]?$", FLAGS));

	private static final List<Pattern> WHISPERS = List.of(
		Pattern.compile("^\\[\\s*" + NAME + "\\s*(?:->|→|»|>>|➡|➜)\\s*(?:mir|me|dir|you|ich)\\s*]\\s*[:»>]?\\s*(?<text>.+)$", FLAGS),
		Pattern.compile("^\\(\\s*" + NAME + "\\s*(?:->|→|»|>>)\\s*(?:mir|me|dir|you|ich)\\s*\\)\\s*[:»>]?\\s*(?<text>.+)$", FLAGS),
		Pattern.compile(PREFIX + NAME + "\\s*(?:->|→|»|>>|➡|➜)\\s*(?:mir|me|dir|you|ich)\\s*[:»>|]\\s*(?<text>.+)$", FLAGS),
		Pattern.compile(PREFIX + "(?:von|from) " + NAME + "\\s*[:»>]\\s*(?<text>.+)$", FLAGS),
		Pattern.compile(PREFIX + NAME + " (?:flüstert dir zu|flüstert dir|flüstert|whispers to you|whispers)\\s*[:»]?\\s*(?<text>.+)$", FLAGS));

	public record Payment(String name, long amount, String line) {
	}

	public record Whisper(String name, String text, String line) {
	}

	public enum Learning {
		NONE, PAYMENT, WHISPER
	}

	private static Learning learning = Learning.NONE;
	private static long learningUntil;
	private static Payment lastPayment;
	private static long lastPaymentAt;
	private static Whisper lastWhisper;
	private static long lastWhisperAt;
	private static String learnedPaymentSource;
	private static Pattern learnedPayment;
	private static String learnedWhisperSource;
	private static Pattern learnedWhisper;

	private ChatPatterns() {
	}

	// ------------------------------------------------------------------ parsing

	public static Payment parsePayment(String raw) {
		String line = clean(raw);
		if (line.isEmpty() || line.length() > 160) {
			return null;
		}
		Pattern learned = learnedPayment();
		if (learned != null) {
			Payment payment = match(learned, line);
			if (payment != null) {
				return payment;
			}
		}
		for (Pattern pattern : HugoFormats.patterns("pay.received")) {
			Payment payment = match(pattern, line);
			if (payment != null) {
				return payment;
			}
		}
		for (Pattern pattern : PAYMENTS) {
			Payment payment = match(pattern, line);
			if (payment != null) {
				return payment;
			}
		}
		return null;
	}

	private static Payment match(Pattern pattern, String line) {
		Matcher matcher = pattern.matcher(line);
		if (!matcher.matches()) {
			return null;
		}
		String name = HugoFormats.group(matcher, "name");
		long amount = Money.parse(HugoFormats.group(matcher, "amount"));
		if (name == null) {
			return null;
		}
		if (amount <= 0 || isSelf(name)) {
			return null;
		}
		return new Payment(name, amount, line);
	}

	public static Whisper parseWhisper(String raw) {
		String line = clean(raw);
		if (line.isEmpty() || line.length() > 200) {
			return null;
		}
		Pattern learned = learnedWhisper();
		List<Pattern> patterns = new ArrayList<>();
		if (learned != null) {
			patterns.add(learned);
		}
		patterns.addAll(WHISPERS);
		for (Pattern pattern : patterns) {
			Matcher matcher = pattern.matcher(line);
			if (matcher.matches() && !isSelf(matcher.group("name"))) {
				return new Whisper(matcher.group("name"), matcher.group("text").strip(), line);
			}
		}
		return null;
	}

	/** Plain text without colour codes and odd spaces. */
	static String clean(String raw) {
		if (raw == null) {
			return "";
		}
		return raw.replaceAll("§.", "").replace(' ', ' ').replaceAll("\\s+", " ").strip();
	}

	private static boolean isSelf(String name) {
		Minecraft client = Minecraft.getInstance();
		return client.player != null && client.player.getGameProfile().name().equalsIgnoreCase(name);
	}

	// ------------------------------------------------------------------ seen messages (for "Testen")

	static void seenPayment(Payment payment) {
		lastPayment = payment;
		lastPaymentAt = System.currentTimeMillis();
	}

	static void seenWhisper(Whisper whisper) {
		lastWhisper = whisper;
		lastWhisperAt = System.currentTimeMillis();
	}

	public static Payment lastPayment() {
		return lastPayment;
	}

	public static long lastPaymentAt() {
		return lastPaymentAt;
	}

	public static Whisper lastWhisper() {
		return lastWhisper;
	}

	public static long lastWhisperAt() {
		return lastWhisperAt;
	}

	// ------------------------------------------------------------------ learning

	public static void startLearning(Learning kind) {
		learning = kind;
		learningUntil = System.currentTimeMillis() + 180_000;
	}

	public static void stopLearning() {
		learning = Learning.NONE;
	}

	public static Learning learning() {
		if (learning != Learning.NONE && System.currentTimeMillis() > learningUntil) {
			learning = Learning.NONE;
		}
		return learning;
	}

	public static boolean hasLearned(Learning kind) {
		String pattern = kind == Learning.PAYMENT ? ModConfigManager.CONFIG.streamerPaymentPattern : ModConfigManager.CONFIG.streamerWhisperPattern;
		return pattern != null && !pattern.isBlank();
	}

	public static void forget(Learning kind) {
		if (kind == Learning.PAYMENT) {
			ModConfigManager.CONFIG.streamerPaymentPattern = "";
		} else {
			ModConfigManager.CONFIG.streamerWhisperPattern = "";
		}
		ModConfigManager.save();
	}

	/**
	 * Tries to learn from one system line while learning is on. Returns a short description of what
	 * was learned, or null when the line did not look like the expected message.
	 */
	static String tryLearn(String raw) {
		Learning kind = learning();
		if (kind == Learning.NONE) {
			return null;
		}
		String line = clean(raw);
		String name = onlinePlayerIn(line);
		if (name == null) {
			return null;
		}
		String regex = kind == Learning.PAYMENT ? paymentTemplate(line, name) : whisperTemplate(line, name);
		if (regex == null) {
			return null;
		}
		if (kind == Learning.PAYMENT) {
			ModConfigManager.CONFIG.streamerPaymentPattern = regex;
		} else {
			ModConfigManager.CONFIG.streamerWhisperPattern = regex;
		}
		ModConfigManager.save();
		learning = Learning.NONE;
		return line;
	}

	/** A player from the tab list (not you) whose name stands as a whole word in the line. */
	private static String onlinePlayerIn(String line) {
		Minecraft client = Minecraft.getInstance();
		if (client.getConnection() == null) {
			return null;
		}
		String best = null;
		for (PlayerInfo info : client.getConnection().getOnlinePlayers()) {
			String name = info.getProfile().name();
			if (isSelf(name) || name.length() < 3) {
				continue;
			}
			if (Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(name) + "(?![A-Za-z0-9_])").matcher(line).find()
				&& (best == null || name.length() > best.length())) {
				best = name;
			}
		}
		return best;
	}

	static String paymentTemplate(String line, String name) {
		Matcher nameAt = wordMatcher(line, name);
		if (!nameAt.find()) {
			return null;
		}
		Matcher amount = Pattern.compile(Money.AMOUNT_REGEX, FLAGS).matcher(line);
		int amountStart = -1;
		int amountEnd = -1;
		while (amount.find()) {
			boolean overlapsName = amount.start() < nameAt.end() && amount.end() > nameAt.start();
			if (!overlapsName && amount.group().matches(".*\\d.*") && Money.parse(amount.group()) > 0) {
				amountStart = amount.start();
				amountEnd = amount.end();
				break;
			}
		}
		if (amountStart < 0) {
			return null;
		}
		// Trailing spaces belong to the literal text around the amount.
		while (amountEnd > amountStart && line.charAt(amountEnd - 1) == ' ') {
			amountEnd--;
		}
		while (amountStart < amountEnd && line.charAt(amountStart) == ' ') {
			amountStart++;
		}
		StringBuilder regex = new StringBuilder("^");
		if (nameAt.start() < amountStart) {
			regex.append(literal(line.substring(0, nameAt.start()))).append(NAME)
				.append(literal(line.substring(nameAt.end(), amountStart))).append(AMOUNT)
				.append(literal(line.substring(amountEnd)));
		} else {
			regex.append(literal(line.substring(0, amountStart))).append(AMOUNT)
				.append(literal(line.substring(amountEnd, nameAt.start()))).append(NAME)
				.append(literal(line.substring(nameAt.end())));
		}
		return regex.append("$").toString();
	}

	static String whisperTemplate(String line, String name) {
		Matcher nameAt = wordMatcher(line, name);
		if (!nameAt.find()) {
			return null;
		}
		String word = ModConfigManager.CONFIG.streamerIngameJoinWord.strip();
		if (word.isEmpty()) {
			return null;
		}
		int textAt = line.toLowerCase(Locale.ROOT).lastIndexOf(word.toLowerCase(Locale.ROOT));
		if (textAt < nameAt.end()) {
			return null;
		}
		return "^" + literal(line.substring(0, nameAt.start())) + NAME + literal(line.substring(nameAt.end(), textAt)) + "(?<text>.+)$";
	}

	private static Matcher wordMatcher(String line, String name) {
		return Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(name) + "(?![A-Za-z0-9_])").matcher(line);
	}

	/** Literal text, but any other number in it (a balance, a time) may differ next time. */
	private static String literal(String text) {
		StringBuilder out = new StringBuilder();
		Matcher digits = Pattern.compile("\\d[\\d.,]*").matcher(text);
		int last = 0;
		while (digits.find()) {
			out.append(quote(text.substring(last, digits.start()))).append("[\\d.,]+");
			last = digits.end();
		}
		return out.append(quote(text.substring(last))).toString();
	}

	private static String quote(String text) {
		return text.isEmpty() ? "" : Pattern.quote(text);
	}

	private static Pattern learnedPayment() {
		String source = ModConfigManager.CONFIG.streamerPaymentPattern;
		if (source == null || source.isBlank()) {
			return null;
		}
		if (!source.equals(learnedPaymentSource)) {
			learnedPaymentSource = source;
			learnedPayment = compile(source);
		}
		return learnedPayment;
	}

	private static Pattern learnedWhisper() {
		String source = ModConfigManager.CONFIG.streamerWhisperPattern;
		if (source == null || source.isBlank()) {
			return null;
		}
		if (!source.equals(learnedWhisperSource)) {
			learnedWhisperSource = source;
			learnedWhisper = compile(source);
		}
		return learnedWhisper;
	}

	private static Pattern compile(String source) {
		try {
			return Pattern.compile(source, FLAGS);
		} catch (PatternSyntaxException e) {
			return null;
		}
	}
}
