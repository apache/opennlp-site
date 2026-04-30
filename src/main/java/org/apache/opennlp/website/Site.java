/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.opennlp.website;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.opennlp.website.contributors.Contributor;
import org.apache.opennlp.website.contributors.Contributors;
import org.jbake.app.Oven;
import org.jbake.app.configuration.JBakeConfiguration;
import org.jbake.app.configuration.JBakeConfigurationFactory;

import java.io.File;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** Build driver: fetches live contributor data, stages the JBake tree, then bakes. */
public final class Site {

    private static final DateTimeFormatter STAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'", Locale.ROOT).withZone(ZoneOffset.UTC);

    public static void main(final String[] args) throws Exception {
        if (args.length < 4) {
            throw new IllegalArgumentException(
                    "usage: Site <source> <dest> <staged> <cache> [--serve] [--port=N]");
        }
        final Path source = Path.of(args[0]);
        final Path dest = Path.of(args[1]);
        final Path staged = Path.of(args[2]);
        final Path cacheDir = Path.of(args[3]);

        boolean serve = false;
        int port = 8080;
        for (int i = 4; i < args.length; i++) {
            final String a = args[i];
            if (a.equals("--serve")) serve = true;
            else if (a.startsWith("--port=")) port = Integer.parseInt(a.substring("--port=".length()));
        }

        System.out.println("[site] fetching contributor data...");
        final Contributors.Sections sections = Contributors.load(cacheDir);
        System.out.printf("[site] active=%d emeritus=%d wall-of-fame=%d%n",
                sections.active().size(), sections.emeritus().size(), sections.wallOfFame().size());

        bake(source, dest, staged, sections);

        if (serve) {
            startServer(dest, port);
            watchAndRebake(source, dest, staged, sections);
        }
    }

    private static void bake(
            final Path source, final Path dest, final Path staged, final Contributors.Sections sections)
            throws Exception {
        System.out.println("[site] staging JBake source tree to " + staged);
        rsync(source, staged);

        final Path teamFile = staged.resolve("content/team.ad");
        if (Files.exists(teamFile)) {
            final String stamp = STAMP_FORMAT.format(Instant.now());
            final String original = Files.readString(teamFile, StandardCharsets.UTF_8);
            final String body = original
                    .replace("<!-- ACTIVE -->", grid(sections.active()))
                    .replace("<!-- EMERITUS -->", grid(sections.emeritus()))
                    .replace("<!-- WALL_OF_FAME -->", grid(sections.wallOfFame()))
                    .replace("<!-- LAST_UPDATED -->", lastUpdatedBlock(stamp));
            Files.writeString(teamFile, body, StandardCharsets.UTF_8);
        } else {
            System.err.println("[site] WARN: " + teamFile + " not found; skipping contributor injection");
        }

        Files.createDirectories(dest);
        System.out.println("[site] baking JBake site to " + dest);
        final JBakeConfiguration config = new JBakeConfigurationFactory()
                .createDefaultJbakeConfiguration(staged.toFile(), dest.toFile(), false);
        new Oven(config).bake();
        System.out.println("[site] done.");
    }

    /* ---------------- Live dev mode (--serve) ---------------- */

    private static void startServer(final Path dest, final int port) throws Exception {
        final HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new StaticHandler(dest));
        server.setExecutor(null);
        server.start();
        System.out.printf("[site] serving %s on http://localhost:%d/  (Ctrl-C to stop)%n", dest, port);
    }

    private static void watchAndRebake(
            final Path source, final Path dest, final Path staged, final Contributors.Sections sections)
            throws Exception {
        try (WatchService ws = source.getFileSystem().newWatchService()) {
            registerRecursive(source, ws);
            long lastBakeMs = 0;
            while (true) {
                final WatchKey key = ws.poll(500, TimeUnit.MILLISECONDS);
                if (key == null) continue;
                boolean relevant = false;
                for (final WatchEvent<?> ev : key.pollEvents()) {
                    if (ev.kind() == StandardWatchEventKinds.OVERFLOW) continue;
                    relevant = true;
                    if (ev.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                        final Object ctx = ev.context();
                        final Path watchedDir = (Path) key.watchable();
                        final Path child = ctx instanceof Path p ? watchedDir.resolve(p) : null;
                        if (child != null && Files.isDirectory(child)) {
                            registerRecursive(child, ws);
                        }
                    }
                }
                key.reset();
                if (!relevant) continue;
                final long now = System.currentTimeMillis();
                if (now - lastBakeMs < 400) continue; // debounce burst writes
                lastBakeMs = now;
                System.out.println("[site] change detected, re-baking...");
                try {
                    bake(source, dest, staged, sections);
                } catch (final Exception e) {
                    System.err.println("[site] re-bake failed: " + e.getMessage());
                }
            }
        }
    }

    private static void registerRecursive(final Path root, final WatchService ws) throws Exception {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs)
                    throws java.io.IOException {
                dir.register(ws,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static final class StaticHandler implements HttpHandler {
        private static final Map<String, String> MIME = Map.ofEntries(
                Map.entry(".html", "text/html; charset=utf-8"),
                Map.entry(".css", "text/css; charset=utf-8"),
                Map.entry(".js", "application/javascript; charset=utf-8"),
                Map.entry(".json", "application/json; charset=utf-8"),
                Map.entry(".xml", "application/xml; charset=utf-8"),
                Map.entry(".svg", "image/svg+xml"),
                Map.entry(".png", "image/png"),
                Map.entry(".jpg", "image/jpeg"),
                Map.entry(".jpeg", "image/jpeg"),
                Map.entry(".gif", "image/gif"),
                Map.entry(".ico", "image/x-icon"),
                Map.entry(".woff", "font/woff"),
                Map.entry(".woff2", "font/woff2"),
                Map.entry(".ttf", "font/ttf"),
                Map.entry(".pdf", "application/pdf"));

        private final Path root;

        StaticHandler(final Path root) {
            this.root = root;
        }

        @Override
        public void handle(final HttpExchange ex) throws java.io.IOException {
            final String requested = ex.getRequestURI().getPath();
            final String path = requested.endsWith("/") ? requested + "index.html" : requested;
            // strip leading "/" and resolve safely under root
            final Path resolved = root.resolve(path.substring(1)).normalize();
            if (!resolved.startsWith(root) || !Files.exists(resolved) || Files.isDirectory(resolved)) {
                final byte[] body = ("404 - " + path).getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(404, body.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(body);
                }
                return;
            }
            final String name = resolved.getFileName().toString();
            final int dot = name.lastIndexOf('.');
            final String ext = dot >= 0 ? name.substring(dot).toLowerCase(Locale.ROOT) : "";
            final String mime = MIME.getOrDefault(ext, "application/octet-stream");
            final byte[] body = Files.readAllBytes(resolved);
            ex.getResponseHeaders().set("Content-Type", mime);
            ex.getResponseHeaders().set("Cache-Control", "no-store");
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(body);
            }
        }
    }

    private static String grid(final List<Contributor> people) {
        final StringBuilder sb = new StringBuilder();
        sb.append("++++\n");
        sb.append("<div class=\"contributor-grid\">\n");
        if (people.isEmpty()) {
            sb.append("  <p class=\"contributor-empty\">No contributors to show.</p>\n");
        } else {
            final String[] palette = {
                    "#3a1c71", "#6a3093", "#1f4068", "#2c5364", "#283c86", "#0f2027",
                    "#5614b0", "#11324d", "#16222a", "#373b44", "#1d2671", "#3b1f5b"};
            for (final Contributor c : people) {
                final String name = c.displayName();
                final String url = c.profileUrl();
                final String tag = openTag(url);
                final String close = url == null ? "</span>" : "</a>";
                final String initials = initials(name);
                final String color = palette[Math.floorMod(name.hashCode(), palette.length)];
                final String roleBadge = roleBadge(c);
                sb.append("  ").append(tag)
                        .append("<span class=\"contributor-badge\" style=\"background:").append(color)
                        .append(";\" aria-hidden=\"true\">").append(escape(initials)).append("</span>")
                        .append("<span class=\"contributor-name\">").append(escape(name)).append("</span>")
                        .append(roleBadge)
                        .append(close).append("\n");
            }
        }
        sb.append("</div>\n");
        sb.append("++++\n");
        return sb.toString();
    }

    private static String lastUpdatedBlock(final String stamp) {
        return "++++\n"
                + "<div class=\"team-meta\">\n"
                + "  <p class=\"team-last-updated\">Last updated: <time datetime=\""
                + escape(Instant.now().toString()) + "\">"
                + escape(stamp) + "</time></p>\n"
                + "  <p class=\"contributor-legend\">"
                + "<span class=\"contributor-role\">C</span> indicates a committer, "
                + "<span class=\"contributor-role\">C-P</span> a PMC member, and "
                + "<span class=\"contributor-role contributor-role-chair\">Chair</span> the current PMC chair."
                + "</p>\n"
                + "</div>\n"
                + "++++\n";
    }

    private static String openTag(final String url) {
        if (url == null) return "<span class=\"contributor-card\">";
        return "<a class=\"contributor-card\" href=\"" + escape(url)
                + "\" rel=\"noopener\" target=\"_blank\">";
    }

    private static String roleBadge(final Contributor c) {
        final StringBuilder sb = new StringBuilder();
        final String flags = c.roleFlags();
        if (!flags.isEmpty()) {
            sb.append("<span class=\"contributor-role\">").append(flags).append("</span>");
        }
        if (c.isChair()) {
            sb.append("<span class=\"contributor-role contributor-role-chair\">Chair</span>");
        }
        return sb.toString();
    }

    private static String initials(final String name) {
        final String[] parts = name.trim().split("\\s+");
        if (parts.length >= 2) {
            return ("" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0))
                    .toUpperCase(Locale.ROOT);
        }
        if (name.length() >= 2) {
            return name.substring(0, 2).toUpperCase(Locale.ROOT);
        }
        return name.toUpperCase(Locale.ROOT);
    }

    private static String escape(final String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static void rsync(final Path from, final Path to) throws Exception {
        if (Files.exists(to)) {
            try (Stream<Path> walk = Files.walk(to)) {
                walk.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        }
        Files.createDirectories(to);
        try (Stream<Path> walk = Files.walk(from)) {
            walk.forEach(src -> {
                try {
                    final Path rel = from.relativize(src);
                    final Path target = to.resolve(rel);
                    if (Files.isDirectory(src)) {
                        Files.createDirectories(target);
                    } else {
                        Files.createDirectories(target.getParent());
                        Files.copy(src, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (final Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private Site() {}
}
