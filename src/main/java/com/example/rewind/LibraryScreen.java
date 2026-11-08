package com.example.rewind;

import java.io.IOException;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Stream;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Simple clip list: rename + delete. (Thumbnails/preview/trim come in phase 2.) */
public final class LibraryScreen extends Screen {
    private final Screen parent;
    private List<Path> files = new ArrayList<>();
    private int page, perPage;
    private Path confirmDelete;

    public LibraryScreen(Screen parent) { super(Component.literal("Rewind Clips")); this.parent = parent; }

    private void scan() {
        try (Stream<Path> st = Files.list(RewindMod.ROOT)) {
            files = new ArrayList<>(st.filter(p -> p.toString().endsWith(".avi")).toList());
            files.sort(Comparator.comparingLong((Path p) -> p.toFile().lastModified()).reversed());
        } catch (IOException e) { files = new ArrayList<>(); }
    }

    @Override protected void init() {
        scan();
        perPage = Math.max(3, Math.min(8, (height - 100) / 24));
        page = Math.max(0, Math.min(page, (files.size() - 1) / perPage));
        int x = width / 2;
        for (int i = 0; i < perPage; i++) {
            int idx = page * perPage + i;
            if (idx >= files.size()) break;
            Path p = files.get(idx);
            int y = 40 + i * 24;
            addRenderableWidget(Button.builder(Component.literal("Rename"), b -> minecraft.setScreen(new Rename(this, p)))
                    .bounds(x + 50, y, 55, 20).build());
            boolean sure = p.equals(confirmDelete);
            addRenderableWidget(Button.builder(Component.literal(sure ? "Sure?" : "Delete"), b -> {
                if (sure) { try { Files.deleteIfExists(p); } catch (IOException ignored) {} confirmDelete = null; }
                else confirmDelete = p;
                rebuildWidgets();
            }).bounds(x + 108, y, 45, 20).build());
        }
        int by = height - 28;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; confirmDelete = null; rebuildWidgets(); })
                .bounds(x - 155, by, 30, 20).build()).active = page > 0;
        addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; confirmDelete = null; rebuildWidgets(); })
                .bounds(x - 120, by, 30, 20).build()).active = (page + 1) * perPage < files.size();
        addRenderableWidget(Button.builder(Component.literal("Open folder"), b -> net.minecraft.Util.getPlatform().openPath(RewindMod.ROOT))
                .bounds(x - 80, by, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(x + 25, by, 130, 20).build());
    }

    @Override public void render(GuiGraphics g, int mx, int my, float d) {
        super.render(g, mx, my, d);
        g.drawCenteredString(font, title, width / 2, 15, 0xFFFFFFFF);
        SimpleDateFormat fmt = new SimpleDateFormat("MM-dd HH:mm");
        if (files.isEmpty()) g.drawCenteredString(font, "No clips yet - press F9 in a world", width / 2, 60, 0xFFAAAAAA);
        for (int i = 0; i < perPage; i++) {
            int idx = page * perPage + i;
            if (idx >= files.size()) break;
            Path p = files.get(idx);
            String n = font.plainSubstrByWidth(p.getFileName().toString().replaceFirst("\\.avi$", ""), 150);
            long mb = p.toFile().length() / 1048576L;
            g.drawString(font, n, width / 2 - 155, 41 + i * 24, 0xFFFFFFFF);
            g.drawString(font, fmt.format(new Date(p.toFile().lastModified())) + "  " + mb + " MB", width / 2 - 155, 41 + i * 24 + 10, 0xFF888888);
        }
    }

    @Override public void onClose() { minecraft.setScreen(parent); }

    /** Rename dialog. */
    private static final class Rename extends Screen {
        private final LibraryScreen back; private final Path file; private EditBox box; private String err = "";
        Rename(LibraryScreen back, Path file) { super(Component.literal("Rename clip")); this.back = back; this.file = file; }

        @Override protected void init() {
            box = new EditBox(font, width / 2 - 100, height / 2 - 10, 200, 20, Component.literal("Name"));
            box.setMaxLength(60);
            box.setValue(file.getFileName().toString().replaceFirst("\\.avi$", ""));
            addRenderableWidget(box);
            setInitialFocus(box);
            addRenderableWidget(Button.builder(Component.literal("Save"), b -> save()).bounds(width / 2 - 100, height / 2 + 20, 98, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(width / 2 + 2, height / 2 + 20, 98, 20).build());
        }

        private void save() {
            String n = box.getValue().replaceAll("[\\\\/:*?\"<>|]", "").trim();
            if (n.isEmpty()) { err = "Name can't be empty"; return; }
            Path to = file.resolveSibling(n + ".avi");
            if (!to.equals(file) && Files.exists(to)) { err = "That name already exists"; return; }
            try { if (!to.equals(file)) Files.move(file, to); } catch (IOException e) { err = "Rename failed"; return; }
            onClose();
        }

        @Override public void render(GuiGraphics g, int mx, int my, float d) {
            super.render(g, mx, my, d);
            g.drawCenteredString(font, title, width / 2, height / 2 - 35, 0xFFFFFFFF);
            if (!err.isEmpty()) g.drawCenteredString(font, err, width / 2, height / 2 + 46, 0xFFFF5555);
        }

        @Override public void onClose() { minecraft.setScreen(back); }
    }
}
