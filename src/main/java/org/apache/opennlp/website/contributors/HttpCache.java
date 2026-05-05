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
package org.apache.opennlp.website.contributors;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/** TTL-keyed disk cache for HTTP GETs; falls back to stale data on transient errors. */
public final class HttpCache {

    private final Path dir;
    private final HttpClient client;
    private final Duration ttl;
    private final String token;

    public HttpCache(final Path dir, final Duration ttl, final String token) throws IOException {
        this.dir = dir;
        this.ttl = ttl;
        this.token = token;
        Files.createDirectories(dir);
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public String fetch(final String url) throws IOException, InterruptedException {
        final Path file = dir.resolve(hash(url) + ".json");
        if (Files.exists(file)) {
            final long ageMs = System.currentTimeMillis() - Files.getLastModifiedTime(file).toMillis();
            if (ageMs < ttl.toMillis()) {
                return Files.readString(file, StandardCharsets.UTF_8);
            }
        }
        final HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "application/vnd.github+json, application/json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "opennlp-site-build")
                .timeout(Duration.ofSeconds(45));
        if (token != null && !token.isBlank() && url.startsWith("https://api.github.com/")) {
            req.header("Authorization", "Bearer " + token);
        }
        final HttpResponse<String> resp = client.send(req.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            // Fall back to stale cache rather than fail the build
            if (Files.exists(file)) {
                System.err.println("[http-cache] " + url + " -> " + resp.statusCode()
                        + ", serving stale cache");
                return Files.readString(file, StandardCharsets.UTF_8);
            }
            throw new IOException("GET " + url + " -> " + resp.statusCode() + ": " + truncate(resp.body()));
        }
        Files.writeString(file, resp.body(), StandardCharsets.UTF_8);
        return resp.body();
    }

    /** Returns the response body and the parsed Link-header `next` URL (if any). */
    public Page fetchPage(final String url) throws IOException, InterruptedException {
        final Path file = dir.resolve(hash(url) + ".json");
        final Path linkFile = dir.resolve(hash(url) + ".link");
        if (Files.exists(file)) {
            final long ageMs = System.currentTimeMillis() - Files.getLastModifiedTime(file).toMillis();
            if (ageMs < ttl.toMillis()) {
                final String body = Files.readString(file, StandardCharsets.UTF_8);
                final String next = Files.exists(linkFile) ? Files.readString(linkFile).trim() : "";
                return new Page(body, next.isEmpty() ? null : next);
            }
        }
        final HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "opennlp-site-build")
                .timeout(Duration.ofSeconds(45));
        if (token != null && !token.isBlank() && url.startsWith("https://api.github.com/")) {
            req.header("Authorization", "Bearer " + token);
        }
        final HttpResponse<String> resp = client.send(req.build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            if (Files.exists(file)) {
                System.err.println("[http-cache] " + url + " -> " + resp.statusCode()
                        + ", serving stale cache");
                final String body = Files.readString(file, StandardCharsets.UTF_8);
                final String next = Files.exists(linkFile) ? Files.readString(linkFile).trim() : "";
                return new Page(body, next.isEmpty() ? null : next);
            }
            throw new IOException("GET " + url + " -> " + resp.statusCode() + ": " + truncate(resp.body()));
        }
        Files.writeString(file, resp.body(), StandardCharsets.UTF_8);
        final Optional<String> link = resp.headers().firstValue("link");
        final String next = link.map(HttpCache::parseNextLink).orElse(null);
        Files.writeString(linkFile, next == null ? "" : next, StandardCharsets.UTF_8);
        return new Page(resp.body(), next);
    }

    private static String parseNextLink(final String header) {
        for (final String part : header.split(",")) {
            final List<String> bits = List.of(part.split(";"));
            if (bits.size() < 2) continue;
            final String rel = bits.get(1).trim();
            if (rel.equals("rel=\"next\"")) {
                final String url = bits.get(0).trim();
                if (url.startsWith("<") && url.endsWith(">")) return url.substring(1, url.length() - 1);
            }
        }
        return null;
    }

    private static String hash(final String s) {
        try {
            final MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8))).substring(0, 24);
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String truncate(final String body) {
        if (body == null) return "";
        return body.length() > 240 ? body.substring(0, 240) + "..." : body;
    }

    public record Page(String body, String nextUrl) {}
}
