package com.hugosmpenhanced.client.feature.calculator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds a calculation at the end of the chat input ("verkaufe für 64*3,5" → "64*3,5") and works it
 * out. Understands + - * / (also x, × and :), ^, %, brackets, German and English decimals
 * ("3,5", "3.5", "1.000" as thousands) and the suffixes k (thousand), m (million) and st (stack of
 * 64). Input without an operator (or a stack suffix) is never treated as a calculation.
 */
public final class ChatCalculator {
	/** Where a calculation starts in the input, and its result. */
	public record Result(int start, double value) {
		/** The result as typed into chat: plain digits, a dot as decimal point, at most two decimals. */
		public String plain() {
			BigDecimal rounded = BigDecimal.valueOf(this.value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
			return rounded.scale() < 0 ? rounded.setScale(0).toPlainString() : rounded.toPlainString();
		}

		/** The result for reading: German thousands dots and decimal comma. */
		public String pretty() {
			return GERMAN.format(this.value);
		}

		/**
		 * "3 Stacks + 32" for whole results from one stack up to a few hundred, with the number of
		 * full shulker boxes (27 stacks each) once that is at least one; otherwise null.
		 */
		public String stacks() {
			if (this.value < 64 || this.value != Math.rint(this.value) || this.value > 100_000) {
				return null;
			}
			long whole = (long) this.value;
			long stacks = whole / 64;
			long rest = whole % 64;
			String text = stacks + (stacks == 1 ? " Stack" : " Stacks") + (rest > 0 ? " + " + rest : "");
			long shulkers = whole / (64 * 27);
			return shulkers > 0 ? text + " (" + GERMAN.format(whole / (64.0 * 27)) + " Shulker)" : text;
		}
	}

	private static final DecimalFormat GERMAN = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.GERMANY));
	/** The longest tail of the input made only of characters a calculation can contain. */
	private static final Pattern TAIL = Pattern.compile("[0-9.,+\\-*/x×:^%() kKmMsStT]+$");
	private static final Pattern GERMAN_THOUSANDS = Pattern.compile("\\d{1,3}(\\.\\d{3})+");

	private ChatCalculator() {
	}

	/** The calculation at the end of the input and its value, or null if there is none. */
	public static Result find(String input) {
		Matcher tail = TAIL.matcher(input);
		if (!tail.find()) {
			return null;
		}
		// Try the longest candidate first, then shorter ones, so "für 2+2" skips the word but
		// "(1+2)*3" keeps its bracket.
		for (int start = tail.start(); start < input.length(); start++) {
			if (start > 0 && !Character.isWhitespace(input.charAt(start - 1)) && start != tail.start()) {
				continue;
			}
			String candidate = input.substring(start).strip();
			if (candidate.isEmpty()) {
				break;
			}
			Double value = evaluate(candidate);
			if (value != null) {
				int offset = input.indexOf(candidate, start);
				return new Result(offset, value);
			}
		}
		return null;
	}

	/** The value of a calculation with at least one operator, or null if it is not one. */
	static Double evaluate(String expression) {
		Parser parser = new Parser(expression);
		try {
			double value = parser.parseExpression();
			parser.skipSpaces();
			if (parser.pos != expression.length() || parser.operators == 0
				|| Double.isNaN(value) || Double.isInfinite(value)) {
				return null;
			}
			return value;
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** Recursive-descent parser: expression = term (+|- term)*, term = power (*|/|% power)*. */
	private static final class Parser {
		private final String text;
		private int pos;
		private int operators;

		private Parser(String text) {
			this.text = text;
		}

		private void skipSpaces() {
			while (this.pos < this.text.length() && this.text.charAt(this.pos) == ' ') {
				this.pos++;
			}
		}

		private boolean eat(char... options) {
			this.skipSpaces();
			if (this.pos >= this.text.length()) {
				return false;
			}
			char c = this.text.charAt(this.pos);
			for (char option : options) {
				if (c == option) {
					this.pos++;
					return true;
				}
			}
			return false;
		}

		private double parseExpression() {
			double value = this.parseTerm();
			while (true) {
				if (this.eat('+')) {
					this.operators++;
					value += this.parseTerm();
				} else if (!this.negativeNumberAhead() && this.eat('-')) {
					this.operators++;
					value -= this.parseTerm();
				} else {
					return value;
				}
			}
		}

		/**
		 * "64 -200" is two numbers (coordinates in "/tp 100 64 -200"), not a subtraction: a minus
		 * with a space before it and a digit right after it starts a negative number.
		 */
		private boolean negativeNumberAhead() {
			this.skipSpaces();
			int at = this.pos;
			return at > 0 && at + 1 < this.text.length() && this.text.charAt(at - 1) == ' '
				&& this.text.charAt(at) == '-' && Character.isDigit(this.text.charAt(at + 1));
		}

		private double parseTerm() {
			double value = this.parsePower();
			while (true) {
				if (this.eat('*', 'x', '×')) {
					this.operators++;
					value *= this.parsePower();
				} else if (this.eat('/', ':')) {
					this.operators++;
					value /= this.parsePower();
				} else if (this.eat('%')) {
					// "200 % 15" is modulo only between numbers; a trailing "%" means percent.
					this.skipSpaces();
					if (this.pos < this.text.length() && (Character.isDigit(this.text.charAt(this.pos)) || this.text.charAt(this.pos) == '(')) {
						this.operators++;
						value %= this.parsePower();
					} else {
						value /= 100;
					}
				} else {
					return value;
				}
			}
		}

		private double parsePower() {
			double base = this.parseUnary();
			if (this.eat('^')) {
				this.operators++;
				return Math.pow(base, this.parsePower());
			}
			return base;
		}

		private double parseUnary() {
			if (this.eat('-')) {
				return -this.parseUnary();
			}
			if (this.eat('+')) {
				return this.parseUnary();
			}
			if (this.eat('(')) {
				double value = this.parseExpression();
				if (!this.eat(')')) {
					throw new IllegalArgumentException("missing )");
				}
				return value * this.suffix();
			}
			return this.parseNumber();
		}

		private double parseNumber() {
			this.skipSpaces();
			int start = this.pos;
			while (this.pos < this.text.length()
				&& (Character.isDigit(this.text.charAt(this.pos)) || this.text.charAt(this.pos) == '.' || this.text.charAt(this.pos) == ',')) {
				this.pos++;
			}
			String raw = this.text.substring(start, this.pos);
			if (raw.isEmpty() || !Character.isDigit(raw.charAt(0))) {
				throw new IllegalArgumentException("number expected");
			}
			return number(raw) * this.suffix();
		}

		private double suffix() {
			String rest = this.text.substring(this.pos).toLowerCase(Locale.ROOT);
			if (rest.startsWith("st")) {
				// "2st" alone is already worth showing: 128.
				this.pos += 2;
				this.operators++;
				return 64;
			}
			if (rest.startsWith("k")) {
				this.pos++;
				return 1_000;
			}
			if (rest.startsWith("m")) {
				this.pos++;
				return 1_000_000;
			}
			return 1;
		}

		private static double number(String raw) {
			String normalized;
			if (GERMAN_THOUSANDS.matcher(raw).matches()) {
				normalized = raw.replace(".", "");
			} else if (raw.indexOf(',') >= 0 && raw.indexOf('.') >= 0) {
				// "1.234,5" (German) or "1,234.5" (English): the last one is the decimal mark.
				boolean commaDecimal = raw.lastIndexOf(',') > raw.lastIndexOf('.');
				normalized = commaDecimal ? raw.replace(".", "").replace(',', '.') : raw.replace(",", "");
			} else {
				normalized = raw.replace(',', '.');
			}
			try {
				return Double.parseDouble(normalized);
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException(raw);
			}
		}
	}
}
