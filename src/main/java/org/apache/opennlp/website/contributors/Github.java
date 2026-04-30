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

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class Github {

    public static final List<String> REPOS = List.of(
            "apache/opennlp",
            "apache/opennlp-site",
            "apache/opennlp-addons",
            "apache/opennlp-sandbox");

    private final HttpCache cache;

    public Github(final HttpCache cache) {
        this.cache = cache;
    }

    public record ContributorRow(String login, String avatarUrl, int commits) {}

    public record EventRow(String login, Instant timestamp) {}

    public List<ContributorRow> contributors(final String repoFull) throws Exception {
        final List<ContributorRow> out = new ArrayList<>();
        paginate("https://api.github.com/repos/" + repoFull + "/contributors?per_page=100",
                arr -> {
                    for (int i = 0; i < arr.length(); i++) {
                        final JSONObject c = arr.getJSONObject(i);
                        final String login = c.optString("login", null);
                        final String avatar = c.optString("avatar_url", null);
                        final int commits = c.optInt("contributions", 0);
                        if (login == null || login.isBlank()) continue;
                        if (IdentityIndex.isBot(login)) continue;
                        out.add(new ContributorRow(login, avatar, commits));
                    }
                });
        return out;
    }

    /** PR-opened events since cutoff (issues + PRs combined endpoint, filtered to PRs). */
    public List<EventRow> prsOpenedSince(final String repoFull, final Instant since) throws Exception {
        final List<EventRow> out = new ArrayList<>();
        final String url = "https://api.github.com/repos/" + repoFull
                + "/issues?state=all&per_page=100&since=" + since.toString();
        paginate(url, arr -> {
            for (int i = 0; i < arr.length(); i++) {
                final JSONObject item = arr.getJSONObject(i);
                if (!item.has("pull_request")) continue;
                final JSONObject user = item.optJSONObject("user");
                final String login = user != null ? user.optString("login", null) : null;
                final Instant ts = parseTs(item.optString("created_at", null));
                if (login == null || ts == null) continue;
                out.add(new EventRow(login, ts));
            }
        });
        return out;
    }

    public List<EventRow> issueCommentsSince(final String repoFull, final Instant since) throws Exception {
        return commentsSince(repoFull, "/issues/comments", since);
    }

    public List<EventRow> reviewCommentsSince(final String repoFull, final Instant since) throws Exception {
        return commentsSince(repoFull, "/pulls/comments", since);
    }

    private List<EventRow> commentsSince(final String repoFull, final String path, final Instant since)
            throws Exception {
        final List<EventRow> out = new ArrayList<>();
        final String url = "https://api.github.com/repos/" + repoFull
                + path + "?per_page=100&since=" + since.toString();
        paginate(url, arr -> {
            for (int i = 0; i < arr.length(); i++) {
                final JSONObject item = arr.getJSONObject(i);
                final JSONObject user = item.optJSONObject("user");
                final String login = user != null ? user.optString("login", null) : null;
                final Instant ts = parseTs(item.optString("created_at", null));
                if (login == null || ts == null) continue;
                out.add(new EventRow(login, ts));
            }
        });
        return out;
    }

    /** Returns the display name (`name` field) from /users/{login}, or null on 404. */
    public String userName(final String login) {
        if (login == null || login.isBlank() || IdentityIndex.isBot(login)) return null;
        try {
            final String body = cache.fetch("https://api.github.com/users/" + login);
            final JSONObject obj = new JSONObject(body);
            final String name = obj.optString("name", null);
            return (name == null || name.isBlank()) ? null : name;
        } catch (final Exception e) {
            return null;
        }
    }

    public List<EventRow> commitsSince(final String repoFull, final Instant since) throws Exception {
        final List<EventRow> out = new ArrayList<>();
        final String url = "https://api.github.com/repos/" + repoFull
                + "/commits?per_page=100&since=" + since.toString();
        paginate(url, arr -> {
            for (int i = 0; i < arr.length(); i++) {
                final JSONObject item = arr.getJSONObject(i);
                final JSONObject author = item.optJSONObject("author");
                final String login = author != null ? author.optString("login", null) : null;
                final JSONObject commit = item.optJSONObject("commit");
                final JSONObject commitAuthor = commit != null ? commit.optJSONObject("author") : null;
                final Instant ts = commitAuthor != null ? parseTs(commitAuthor.optString("date", null)) : null;
                if (login == null || ts == null) continue;
                out.add(new EventRow(login, ts));
            }
        });
        return out;
    }

    private void paginate(final String startUrl, final Consumer<JSONArray> consumer) throws Exception {
        String url = startUrl;
        while (url != null) {
            final HttpCache.Page page = cache.fetchPage(url);
            final JSONArray arr = new JSONArray(page.body());
            consumer.accept(arr);
            url = page.nextUrl();
            if (arr.isEmpty()) break;
        }
    }

    private static Instant parseTs(final String s) {
        try {
            return s == null ? null : Instant.parse(s);
        } catch (final Exception e) {
            return null;
        }
    }
}
