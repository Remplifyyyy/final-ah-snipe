package com.remplify.ahsniper;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Small popup opened by the "add held item" key: shows the item and asks for a max price. */
public class AddSnipeScreen extends Screen {
	private final ItemStack stack;
	private final Screen parent;
	private EditBox priceBox;
	private EditBox loreBox;
	private String error = "";
	private boolean perItem;

	public AddSnipeScreen(ItemStack stack) {
		this(stack, null);
	}

	public AddSnipeScreen(ItemStack stack, Screen parent) {
		super(Component.literal("Add to AH Sniper"));
		this.stack = stack.copy();
		this.parent = parent;
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(parent);
	}

	@Override
	protected void init() {
		int cx = this.width / 2;
		int cy = this.height / 2;

		priceBox = new EditBox(this.font, cx - 100, cy - 18, 200, 20, Component.literal("Max price"));
		priceBox.setMaxLength(32);
		priceBox.setHint(Component.literal("Max price, e.g. 50000 or 1.5m"));
		addRenderableWidget(priceBox);

		loreBox = new EditBox(this.font, cx - 100, cy + 8, 200, 20, Component.literal("Lore filter"));
		loreBox.setMaxLength(64);
		loreBox.setHint(Component.literal("Optional: lore must contain..."));
		addRenderableWidget(loreBox);

		if (stack.getCount() > 1 || stack.getMaxStackSize() > 1) {
			addRenderableWidget(Button.builder(Component.literal("Price is for: whole listing"), b -> {
				perItem = !perItem;
				b.setMessage(Component.literal("Price is for: " + (perItem ? "each item" : "whole listing")));
			}).bounds(cx - 100, cy + 34, 200, 20).build());
		}

		addRenderableWidget(Button.builder(Component.literal("Add"), b -> save())
				.bounds(cx - 100, cy + 60, 98, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
				.bounds(cx + 2, cy + 60, 98, 20).build());

		setInitialFocus(priceBox);
	}

	private void save() {
		long price = SnipeEntry.parsePrice(priceBox.getValue());
		if (price <= 0) {
			error = "Enter a price, e.g. 25000, 250k or 1.5m";
			return;
		}
		SnipeEntry entry = new SnipeEntry(stack, price);
		entry.perItem = perItem;
		String lore = loreBox.getValue().strip();
		if (!lore.isEmpty()) entry.loreFilter = lore;
		SniperConfig.get().entries.add(entry);
		SniperConfig.save();
		AhSniperClient.msg("§aAdded §f" + entry.describe());
		onClose();
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		super.render(g, mouseX, mouseY, delta);
		int cx = this.width / 2;
		int cy = this.height / 2;
		g.drawCenteredString(this.font, this.title, cx, cy - 66, 0xFFFFFFFF);
		g.renderItem(stack, cx - 8, cy - 54);
		g.drawCenteredString(this.font, Component.literal(SnipeEntry.displayName(stack)), cx, cy - 34, 0xFF55FFFF);
		if (!error.isEmpty()) g.drawCenteredString(this.font, Component.literal(error), cx, cy + 86, 0xFFFF5555);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
