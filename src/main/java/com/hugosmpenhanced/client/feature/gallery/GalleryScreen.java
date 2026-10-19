package com.hugosmpenhanced.client.feature.gallery;

import com.hugosmpenhanced.client.util.Desktop;
import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import com.hugosmpenhanced.client.HugoSmpEnhancedClient;
import com.hugosmpenhanced.client.gui.HugoStyle;
import com.hugosmpenhanced.client.gui.StyledScreen;
import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * Browses the screenshots folder, newest first: a paged grid of thumbnails, and a single view with
 * buttons to copy, delete or show the file. Images are read and shrunk on a background thread and
 * uploaded as textures on the render thread; every texture is released when the screen closes.
 */
public class GalleryScreen extends Screen implements StyledScreen {
	private static final int MARGIN = 10;
	private static final int GAP = 6;
	private static final int TOP = 20;
	private static final int BAR = 30;
	private static final int THUMB_TARGET = 110;
	private static final int TEXT_DIM = 0xFF888888;
	private static final SimpleDateFormat DATE = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY);

	private static int counter;

	/** A loaded (or loading) image; {@code id} is null until the texture is ready. */
	private static final class Picture {
		Identifier id;
		int width;
		int height;
		boolean failed;
	}

	private final Screen parent;
	private final Path folder;
	private List<Path> files = new ArrayList<>();
	private final Map<Path, Picture> thumbs = new HashMap<>();
	private Picture full;
	private Path fullPath;
	private boolean closed;

	private int page;
	private int viewing = -1;
	private boolean deleteArmed;
	private String status;
	private long statusUntil;

	private int columns;
	private int rows;
	private int thumbWidth;
	private int thumbHeight;
	private int gridLeft;

	public GalleryScreen(Screen parent) {
		super(Component.literal("Screenshots"));
		this.parent = parent;
		this.folder = net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath().resolve(Screenshot.SCREENSHOT_DIR);
		this.scan();
	}

	private void scan() {
		if (!Files.isDirectory(this.folder)) {
			this.files = new ArrayList<>();
			return;
		}
		try (Stream<Path> stream = Files.list(this.folder)) {
			this.files = new ArrayList<>(stream
				.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
				.sorted(Comparator.comparingLong(GalleryScreen::modified).reversed())
				.toList());
		} catch (IOException e) {
			HugoSmpEnhancedClient.LOGGER.warn("Could not list screenshots", e);
			this.files = new ArrayList<>();
		}
	}

	private static long modified(Path path) {
		try {
			return Files.getLastModifiedTime(path).toMillis();
		} catch (IOException e) {
			return 0;
		}
	}

	private int perPage() {
		return this.columns * this.rows;
	}

	private int pages() {
		return Math.max(1, (this.files.size() + this.perPage() - 1) / this.perPage());
	}

	@Override
	protected void init() {
		int available = this.width - 2 * MARGIN;
		this.columns = Math.max(2, Math.min(6, (available + GAP) / (THUMB_TARGET + GAP)));
		this.thumbWidth = (available - (this.columns - 1) * GAP) / this.columns;
		this.thumbHeight = this.thumbWidth * 9 / 16;
		this.rows = Math.max(1, (this.height - TOP - BAR + GAP) / (this.thumbHeight + GAP));
		this.gridLeft = (this.width - (this.columns * this.thumbWidth + (this.columns - 1) * GAP)) / 2;
		this.page = Math.max(0, Math.min(this.page, this.pages() - 1));

		int y = this.height - 26;
		if (this.viewing < 0) {
			this.initGrid(y);
		} else {
			this.initViewer(y);
		}
	}

	private void initGrid(int y) {
		int center = this.width / 2;
		Button previous = this.addRenderableWidget(Button.builder(Component.literal("◀"), b -> this.turnPage(-1))
			.bounds(center - 150, y, 20, 20).build());
		Button next = this.addRenderableWidget(Button.builder(Component.literal("▶"), b -> this.turnPage(1))
			.bounds(center - 60, y, 20, 20).build());
		previous.active = this.page > 0;
		next.active = this.page < this.pages() - 1;
		this.addRenderableWidget(Button.builder(Component.literal("Ordner öffnen"), b -> this.openFolder(null))
			.bounds(center - 34, y, 90, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Fertig"), b -> this.onClose())
			.bounds(center + 60, y, 90, 20).build());

		int first = this.page * this.perPage();
		for (int i = first; i < Math.min(this.files.size(), first + this.perPage()); i++) {
			this.thumb(this.files.get(i));
		}
	}

	private void initViewer(int y) {
		int center = this.width / 2;
		Button previous = this.addRenderableWidget(Button.builder(Component.literal("◀"), b -> this.view(this.viewing - 1))
			.bounds(center - 182, y, 20, 20).build());
		Button next = this.addRenderableWidget(Button.builder(Component.literal("▶"), b -> this.view(this.viewing + 1))
			.bounds(center - 158, y, 20, 20).build());
		previous.active = this.viewing > 0;
		next.active = this.viewing < this.files.size() - 1;
		this.addRenderableWidget(Button.builder(Component.literal("Kopieren"), b -> this.copy())
			.bounds(center - 132, y, 70, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal(this.deleteArmed ? "Wirklich?" : "Löschen"), b -> this.delete())
			.bounds(center - 58, y, 70, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Im Ordner"), b -> this.openFolder(this.files.get(this.viewing)))
			.bounds(center + 16, y, 70, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal("Zurück"), b -> this.onClose())
			.bounds(center + 90, y, 92, 20).build());

		Path path = this.files.get(this.viewing);
		if (!path.equals(this.fullPath)) {
			this.releaseFull();
			this.fullPath = path;
			int maxWidth = Math.max(640, this.minecraft.getWindow().getWidth());
			this.full = this.load(path, maxWidth);
		}
	}

	private Picture thumb(Path path) {
		Picture picture = this.thumbs.get(path);
		if (picture == null) {
			int maxWidth = Math.min(480, (int) (this.thumbWidth * this.minecraft.getWindow().getGuiScale()));
			picture = this.load(path, maxWidth);
			this.thumbs.put(path, picture);
		}
		return picture;
	}

	private Picture load(Path path, int maxWidth) {
		Picture picture = new Picture();
		CompletableFuture.supplyAsync(() -> read(path, maxWidth), Util.backgroundExecutor())
			.whenCompleteAsync((image, error) -> {
				if (error != null || image == null) {
					picture.failed = true;
					if (error != null) {
						HugoSmpEnhancedClient.LOGGER.warn("Could not load screenshot {}", path, error);
					}
					return;
				}
				if (this.closed || (picture != this.full && !this.thumbs.containsValue(picture))) {
					image.close();
					return;
				}
				Identifier id = Identifier.fromNamespaceAndPath(HugoSmpEnhancedClient.MOD_ID, "gallery/" + counter++);
				this.minecraft.getTextureManager().register(id, new DynamicTexture(() -> "hugosmpenhanced gallery", image));
				picture.width = image.getWidth();
				picture.height = image.getHeight();
				picture.id = id;
			}, this.minecraft);
		return picture;
	}

	private static NativeImage read(Path path, int maxWidth) {
		try (InputStream in = Files.newInputStream(path)) {
			NativeImage image = NativeImage.read(in);
			if (image.getWidth() <= maxWidth) {
				return image;
			}
			int height = Math.max(1, image.getHeight() * maxWidth / image.getWidth());
			NativeImage small = new NativeImage(maxWidth, height, false);
			image.resizeSubRectTo(0, 0, image.getWidth(), image.getHeight(), small);
			image.close();
			return small;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void release(Picture picture) {
		if (picture != null && picture.id != null) {
			this.minecraft.getTextureManager().release(picture.id);
			picture.id = null;
		}
	}

	private void releaseFull() {
		this.release(this.full);
		this.full = null;
		this.fullPath = null;
	}

	private void turnPage(int direction) {
		this.page = Math.max(0, Math.min(this.pages() - 1, this.page + direction));
		this.rebuildWidgets();
	}

	private void view(int index) {
		if (index < 0 || index >= this.files.size()) {
			return;
		}
		this.viewing = index;
		this.deleteArmed = false;
		this.rebuildWidgets();
	}

	private void copy() {
		Path path = this.files.get(this.viewing);
		this.showStatus("Kopiere …");
		ImageClipboard.copy(path).thenAcceptAsync(ok ->
			this.showStatus(ok ? "In die Zwischenablage kopiert" : "Kopieren hat nicht geklappt"), this.minecraft);
	}

	private void delete() {
		if (!this.deleteArmed) {
			this.deleteArmed = true;
			this.rebuildWidgets();
			return;
		}
		Path path = this.files.get(this.viewing);
		try {
			Files.deleteIfExists(path);
		} catch (IOException e) {
			HugoSmpEnhancedClient.LOGGER.warn("Could not delete screenshot {}", path, e);
			this.showStatus("Löschen hat nicht geklappt");
			return;
		}
		this.release(this.thumbs.remove(path));
		this.releaseFull();
		this.files.remove(path);
		this.deleteArmed = false;
		this.showStatus("Gelöscht");
		if (this.files.isEmpty()) {
			this.viewing = -1;
		} else {
			this.viewing = Math.min(this.viewing, this.files.size() - 1);
		}
		this.rebuildWidgets();
	}

	private void openFolder(Path file) {
		try {
			Files.createDirectories(this.folder);
		} catch (IOException ignored) {
		}
		if (file != null && Util.getPlatform() == Util.OS.WINDOWS) {
			try {
				new ProcessBuilder("explorer.exe", "/select," + file.toAbsolutePath()).start();
				return;
			} catch (IOException ignored) {
			}
		}
		Desktop.openPath(this.folder);
	}

	private void showStatus(String text) {
		this.status = text;
		this.statusUntil = Util.getMillis() + 3000;
	}

	private int[] cell(int indexOnPage) {
		int column = indexOnPage % this.columns;
		int row = indexOnPage / this.columns;
		return new int[] {this.gridLeft + column * (this.thumbWidth + GAP), TOP + row * (this.thumbHeight + GAP)};
	}

	private int fileAt(double mouseX, double mouseY) {
		int first = this.page * this.perPage();
		for (int i = 0; i < this.perPage() && first + i < this.files.size(); i++) {
			int[] at = this.cell(i);
			if (mouseX >= at[0] && mouseX < at[0] + this.thumbWidth && mouseY >= at[1] && mouseY < at[1] + this.thumbHeight) {
				return first + i;
			}
		}
		return -1;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (this.viewing < 0 && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			int index = this.fileAt(event.x(), event.y());
			if (index >= 0) {
				this.view(index);
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY != 0) {
			if (this.viewing < 0) {
				this.turnPage(scrollY < 0 ? 1 : -1);
			} else {
				this.view(this.viewing + (scrollY < 0 ? 1 : -1));
			}
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int direction = event.key() == InputConstants.KEY_LEFT ? -1 : event.key() == InputConstants.KEY_RIGHT ? 1 : 0;
		if (direction != 0) {
			if (this.viewing < 0) {
				this.turnPage(direction);
			} else {
				this.view(this.viewing + direction);
			}
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		if (this.viewing >= 0) {
			this.viewing = -1;
			this.deleteArmed = false;
			this.page = this.perPage() > 0 ? Math.min(this.page, this.pages() - 1) : 0;
			this.rebuildWidgets();
			return;
		}
		this.minecraft.setScreenAndShow(this.parent);
	}

	@Override
	public void removed() {
		this.closed = true;
		this.thumbs.values().forEach(this::release);
		this.thumbs.clear();
		this.releaseFull();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		if (this.viewing < 0) {
			this.renderGrid(graphics, mouseX, mouseY);
		} else {
			this.renderViewer(graphics);
		}
		if (this.status != null && Util.getMillis() < this.statusUntil) {
			graphics.centeredText(this.font, this.status, this.width / 2, this.height - 38, 0xFFFFFFFF);
		}
	}

	private void renderGrid(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.centeredText(this.font, "— SCREENSHOTS (" + this.files.size() + ") —", this.width / 2, 6, 0xFFAAAAAA);
		graphics.centeredText(this.font, "Seite " + (this.page + 1) + "/" + this.pages(), this.width / 2 - 95, this.height - 20, 0xFFE0E0E0);
		if (this.files.isEmpty()) {
			graphics.centeredText(this.font, "Noch keine Screenshots. Mit F2 machst du einen.", this.width / 2, this.height / 2 - 4, TEXT_DIM);
			return;
		}
		int hovered = this.fileAt(mouseX, mouseY);
		int first = this.page * this.perPage();
		for (int i = 0; i < this.perPage() && first + i < this.files.size(); i++) {
			Path path = this.files.get(first + i);
			int[] at = this.cell(i);
			this.drawFitted(graphics, this.thumb(path), at[0], at[1], this.thumbWidth, this.thumbHeight);
			HugoStyle.drawOutline(graphics, at[0], at[1], this.thumbWidth, this.thumbHeight,
				hovered == first + i ? 0xFFFFFFFF : 0xFF555555);
		}
		if (hovered >= 0) {
			Path path = this.files.get(hovered);
			List<Component> lines = List.of(Component.literal(path.getFileName().toString()),
				Component.literal(DATE.format(new Date(modified(path)))).withStyle(net.minecraft.ChatFormatting.GRAY));
			graphics.setTooltipForNextFrame(this.font, lines, java.util.Optional.empty(), mouseX, mouseY);
		}
	}

	private void renderViewer(GuiGraphicsExtractor graphics) {
		Path path = this.files.get(this.viewing);
		String title = path.getFileName() + "  ·  " + DATE.format(new Date(modified(path))) + "  ·  " + (this.viewing + 1) + "/" + this.files.size();
		graphics.centeredText(this.font, title, this.width / 2, 6, 0xFFAAAAAA);
		this.drawFitted(graphics, this.full, MARGIN, TOP, this.width - 2 * MARGIN, this.height - TOP - BAR - 14);
	}

	/** Draws the picture as large as fits in the box, centred, or a placeholder while it loads. */
	private void drawFitted(GuiGraphicsExtractor graphics, Picture picture, int x, int y, int width, int height) {
		if (picture == null || picture.id == null) {
			graphics.fill(x, y, x + width, y + height, 0x80101010);
			String text = picture != null && picture.failed ? "Fehler beim Laden" : "Lädt …";
			graphics.centeredText(this.font, text, x + width / 2, y + height / 2 - 4, TEXT_DIM);
			return;
		}
		float scale = Math.min(width / (float) picture.width, height / (float) picture.height);
		int drawWidth = Math.max(1, Math.round(picture.width * scale));
		int drawHeight = Math.max(1, Math.round(picture.height * scale));
		int drawX = x + (width - drawWidth) / 2;
		int drawY = y + (height - drawHeight) / 2;
		graphics.blit(RenderPipelines.GUI_TEXTURED, picture.id, drawX, drawY, 0, 0, drawWidth, drawHeight,
			picture.width, picture.height, picture.width, picture.height);
	}
}
