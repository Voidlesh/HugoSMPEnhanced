package com.hugosmpenhanced.client.feature.sell;

/** One item the sell assistant may sell: how many to always keep and from which amount on to sell. */
public class SellRule {
	public String item = "";
	/** Never sell below this many, counted over the whole inventory including the hotbar. */
	public int keep;
	/** Only sell when at least this many can be sold at once. */
	public int minAmount;
	public boolean enabled = true;

	public SellRule() {
	}

	public SellRule(String item) {
		this.item = item;
	}
}
