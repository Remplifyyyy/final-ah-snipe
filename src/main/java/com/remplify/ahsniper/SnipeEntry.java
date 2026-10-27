package com.remplify.ahsniper;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One item on the snipe list. Matched by item type + display name, plus (strict mode) the item's
 * own lore lines and hidden custom data, so renamed vanilla items can't pass as custom items.
 * An optional lore filter narrows it further (e.g. "Sharpness V" for a specific book).
 */
public class SnipeEntry {
	public String itemId;
	public String name;
	public long maxPrice;
	public String loreFilter;
	public boolean enabled = true;

	/**
	 * Strict matching (default). Captured from the item you held when adding it:
	 *  - requiredLore: the item's own description/enchant lines ("This is Raid TNT...", "Unbroken Chain")
	 *  - requiredKeys: the server plugin's hidden custom-data keys on the item
	 * A vanilla item renamed in an anvil has neither, so it can't match a custom item.
	 * Listings may have MORE lines/keys than this (e.g. a sword with extra enchants), never fewer.
	 */
	public boolean strict = true;
	/** true: maxPrice is per item (a stack of 64 may cost up to 64x). false: maxPrice is for the whole listing. */
	public boolean perItem = false;
	public List<String> requiredLore = new ArrayList<>();
	public List<String> requiredKeys = new ArrayList<>();

	public SnipeEntry() {}

	public SnipeEntry(ItemStack stack, long maxPrice) {
		this.itemId = itemId(stack);
		this.name = displayName(stack);
		this.maxPrice = maxPrice;
		this.requiredLore = descriptionLore(stack);
		this.requiredKeys = customDataKeys(stack);
	}

	/** Built from the Economy tab, using the fingerprint of the listings seen there. */
	public SnipeEntry(String itemId, String name, long maxPrice, boolean perItem, List<String> lore, List<String> keys) {
		this.itemId = itemId;
		this.name = name;
		this.maxPrice = maxPrice;
		this.perItem = perItem;
		this.requiredLore = lore == null ? new ArrayList<>() : new ArrayList<>(lore);
		this.requiredKeys = keys == null ? new ArrayList<>() : new ArrayList<>(keys);
	}

	/** Highest total price this entry will pay for a listing of {@code count} items. */
	public long limitFor(int count) {
		return perItem ? maxPrice * Math.max(1, count) : maxPrice;
	}

	/** Item type + name only; used on the confirm screen after the listing already passed matches(). */
	public boolean matchesBasic(ItemStack stack) {
		return !stack.isEmpty() && itemId.equals(itemId(stack)) && name.equalsIgnoreCase(displayName(stack));
	}

	public boolean matches(ItemStack stack) {
		if (stack.isEmpty()) return false;
		if (!itemId.equals(itemId(stack))) return false;
		if (!name.equalsIgnoreCase(displayName(stack))) return false;
		if (strict) {
			if (requiredLore != null && !requiredLore.isEmpty()) {
				List<String> have = new ArrayList<>();
				for (String l : loreLines(stack)) have.add(l.strip().toLowerCase(Locale.ROOT));
				for (String need : requiredLore) {
					if (!have.contains(need.strip().toLowerCase(Locale.ROOT))) return false;
				}
			}
			if (requiredKeys != null && !requiredKeys.isEmpty()) {
				Set<String> keys = new HashSet<>(customDataKeys(stack));
				// If the AH menu strips hidden data from its display copies, rely on the lore check
				// instead, but only when there is lore to check (a renamed vanilla item has none).
				boolean listingHasData = !keys.isEmpty();
				boolean loreChecked = requiredLore != null && !requiredLore.isEmpty();
				if ((listingHasData || !loreChecked) && !keys.containsAll(requiredKeys)) return false;
			}
		}
		if (loreFilter != null && !loreFilter.isBlank()) {
			String f = loreFilter.toLowerCase(Locale.ROOT);
			for (String line : loreLines(stack)) {
				if (line.toLowerCase(Locale.ROOT).contains(f)) return true;
			}
			return false;
		}
		return true;
	}

	public String describe() {
		String s = name + " §7(" + itemId.replace("minecraft:", "") + ")§f ≤ §e" + formatPrice(maxPrice) + (perItem ? " each" : "");
		if (loreFilter != null && !loreFilter.isBlank()) s += " §7[lore: " + loreFilter + "]";
		if (!strict) s += " §6(loose: name only)";
		if (!enabled) s += " §c(paused)";
		return s;
	}

	// ---- helpers shared with the sniper ----

	/**
	 * The item's own lore worth requiring: non-blank lines without numbers or "label: value" pairs,
	 * so per-item lines like "Created At: 25/10/2026" or "5% extra damage" aren't required.
	 */
	public static List<String> descriptionLore(ItemStack stack) {
		List<String> out = new ArrayList<>();
		for (String line : loreLines(stack)) {
			String t = line.strip();
			if (t.isEmpty()) continue;
			String lower = t.toLowerCase(Locale.ROOT);
			if (lower.startsWith("seller:") || lower.startsWith("price:")) break; // AH-added part
			if (t.matches(".*[0-9].*") || t.contains(":")) continue;
			out.add(t);
		}
		return out;
	}

	/** Paths of the keys in the item's hidden custom data (e.g. "PublicBukkitValues.lifesteal:item"). */
	public static List<String> customDataKeys(ItemStack stack) {
		List<String> out = new ArrayList<>();
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data != null && !data.isEmpty()) collectKeys(data.copyTag(), "", out, 0);
		return out;
	}

	private static void collectKeys(CompoundTag tag, String prefix, List<String> out, int depth) {
		for (String key : tag.keySet()) {
			String path = prefix.isEmpty() ? key : prefix + "." + key;
			out.add(path);
			Tag child = tag.get(key);
			if (depth < 2 && child instanceof CompoundTag sub) collectKeys(sub, path, out, depth + 1);
		}
	}

	public static String itemId(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	public static String displayName(ItemStack stack) {
		return stack.getHoverName().getString().strip();
	}

	public static List<String> loreLines(ItemStack stack) {
		List<String> out = new ArrayList<>();
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore != null) {
			for (Component c : lore.lines()) out.add(c.getString());
		}
		return out;
	}

	private static final Pattern NUMBER = Pattern.compile("([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([kKmMbB])?(?![a-zA-Z])");

	/** Parses "100,000,000 Coins", "4,500", "1.5m", "250k". Returns -1 if there's no number. */
	public static long parsePrice(String text) {
		if (text == null) return -1;
		Matcher m = NUMBER.matcher(text);
		if (!m.find()) return -1;
		try {
			double v = Double.parseDouble(m.group(1).replace(",", ""));
			String suffix = m.group(2);
			if (suffix != null) {
				switch (Character.toLowerCase(suffix.charAt(0))) {
					case 'k' -> v *= 1_000d;
					case 'm' -> v *= 1_000_000d;
					case 'b' -> v *= 1_000_000_000d;
					default -> {}
				}
			}
			return Math.round(v);
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	private static final Pattern BID_WORD = Pattern.compile("\\bbid(s|ding|der)?\\b", Pattern.CASE_INSENSITIVE);

	/**
	 * True only for Buy-It-Now listings: the AH part of the lore (from "Seller:" down) must say
	 * "Click to buy" and must not mention a bid anywhere ("Current bid", "Click to bid", "Starting bid"...).
	 * Bid/auction listings are ignored by the sniper and kept out of the price data.
	 */
	public static boolean isBuyNowListing(ItemStack stack) {
		boolean inAhPart = false;
		boolean clickToBuy = false;
		for (String line : loreLines(stack)) {
			String t = line.strip().toLowerCase(Locale.ROOT);
			if (t.startsWith("seller:")) inAhPart = true;
			if (!inAhPart) continue;
			if (BID_WORD.matcher(t).find()) return false;
			if (t.contains("click to buy")) clickToBuy = true;
		}
		return inAhPart && clickToBuy;
	}

	/** Finds the lore line that starts with the given label (e.g. "Price:") and returns the text after it. */
	public static String loreValue(ItemStack stack, String label) {
		String l = label.toLowerCase(Locale.ROOT);
		for (String line : loreLines(stack)) {
			String t = line.strip();
			int i = t.toLowerCase(Locale.ROOT).indexOf(l);
			if (i >= 0) return t.substring(i + label.length()).strip();
		}
		return null;
	}

	public static String formatPrice(long p) {
		return String.format(Locale.ROOT, "%,d", p);
	}
}
