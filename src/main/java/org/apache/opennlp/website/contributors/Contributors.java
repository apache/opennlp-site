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

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Contributors {

    public static final int ACTIVE_WINDOW_YEARS = 2;

    public record Sections(List<Contributor> active, List<Contributor> emeritus, List<Contributor> wallOfFame) {}

    /** No-network fallback used by --no-fetch / OPENNLP_SITE_NO_FETCH. */
    public static Sections empty() {
        return new Sections(List.of(), List.of(), List.of());
    }

    public static Sections load(final Path cacheDir) throws Exception {
        final String token = System.getenv("GITHUB_TOKEN");
        final HttpCache cache = new HttpCache(cacheDir, Duration.ofHours(6), token);
        final Instant cutoff = LocalDate.now(ZoneOffset.UTC)
                .minusYears(ACTIVE_WINDOW_YEARS)
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC);

        final IdentityIndex idx = new IdentityIndex();

        // 1) Seed from the ASF roster so PMC + committers appear even if inactive on GitHub.
        final List<Roster.Member> roster = Roster.fetch(cache);
        for (final Roster.Member m : roster) {
            final Contributor c = idx.findOrCreate(m.primaryGhLogin(), m.apacheId, null, m.name);
            if (c == null) continue;
            if (m.pmc) c.setPmc(true);
            if (m.committer) c.setCommitter(true);
            if (m.chair) c.setChair(true);
            if (m.forcedStatus != null) c.setForcedStatus(m.forcedStatus);
            // Members with multiple GH accounts: link the secondary logins as aliases
            // so future events on those logins merge into this record.
            for (int i = 1; i < m.ghLogins.size(); i++) {
                idx.linkLoginAlias(c, m.ghLogins.get(i));
            }
        }

        // 2) Pull live data per repo, but defer the merge until after we resolve display
        //    names: Whimsy rarely carries githubUsername for ASF committers, so we have to
        //    bridge login <-> roster-name via /users/{login}.name.
        final Github gh = new Github(cache);
        final Set<String> logins = new LinkedHashSet<>();
        record Touch(String login, Instant ts) {}
        record AvatarCommits(String login, String avatar, int commits) {}
        final List<AvatarCommits> contribRows = new ArrayList<>();
        final List<Touch> touches = new ArrayList<>();

        for (final String repo : Github.REPOS) {
            ingest(() -> gh.contributors(repo), row -> {
                logins.add(row.login());
                contribRows.add(new AvatarCommits(row.login(), row.avatarUrl(), row.commits()));
            });
            ingest(() -> gh.prsOpenedSince(repo, cutoff), e -> {
                logins.add(e.login());
                touches.add(new Touch(e.login(), e.timestamp()));
            });
            ingest(() -> gh.issueCommentsSince(repo, cutoff), e -> {
                logins.add(e.login());
                touches.add(new Touch(e.login(), e.timestamp()));
            });
            ingest(() -> gh.reviewCommentsSince(repo, cutoff), e -> {
                logins.add(e.login());
                touches.add(new Touch(e.login(), e.timestamp()));
            });
            ingest(() -> gh.commitsSince(repo, cutoff), e -> {
                logins.add(e.login());
                touches.add(new Touch(e.login(), e.timestamp()));
            });
        }

        // 3) Resolve display name per login (cached), then merge into the index by login + name.
        final Map<String, String> loginToName = new HashMap<>();
        for (final String login : logins) {
            final String name = gh.userName(login);
            if (name != null) loginToName.put(login, name);
        }

        // Pass apacheId=login only when the index already has a roster entry for that id,
        // so the GH event merges into that committer record (very common for ASF
        // committers whose GH login matches their apache_id — rzo1, mawiesne, joern, ...).
        for (final AvatarCommits row : contribRows) {
            final String apId = idx.hasApacheId(row.login()) ? row.login() : null;
            final Contributor c = idx.findOrCreate(row.login(), apId, null, loginToName.get(row.login()));
            if (c == null) continue;
            if (c.avatarUrl() == null) c.setAvatarUrl(row.avatar());
            for (int i = 0; i < row.commits(); i++) c.stats().touch(null);
        }
        for (final Touch t : touches) {
            final String apId = idx.hasApacheId(t.login()) ? t.login() : null;
            final Contributor c = idx.findOrCreate(t.login(), apId, null, loginToName.get(t.login()));
            if (c == null) continue;
            c.stats().touch(t.ts());
        }

        final List<Contributor> all = idx.all();

        final List<Contributor> active = new ArrayList<>();
        final List<Contributor> emeritus = new ArrayList<>();
        for (final Contributor c : all) {
            if (!c.isCommitter() && !c.isPmc()) continue;
            final Roster.ForcedStatus forced = c.forcedStatus();
            if (forced == Roster.ForcedStatus.ACTIVE) {
                active.add(c);
                continue;
            }
            if (forced == Roster.ForcedStatus.EMERITUS) {
                emeritus.add(c);
                continue;
            }
            final Instant last = c.stats().lastActivity();
            if (last != null && !last.isBefore(cutoff)) {
                active.add(c);
            } else {
                emeritus.add(c);
            }
        }
        // Deterministic order across all sections: "Lastname, Firstname".
        final Comparator<Contributor> byLastName = Comparator.comparing(Contributor::sortKey);
        active.sort(byLastName);
        emeritus.sort(byLastName);

        final List<Contributor> wallOfFame = new ArrayList<>();
        for (final Contributor c : all) {
            // Committers + PMC members are already shown in Active or Emeritus; skip them here.
            if (c.isCommitter() || c.isPmc()) continue;
            if (c.ghLogin() == null && c.apacheId() == null) continue;
            wallOfFame.add(c);
        }
        wallOfFame.sort(byLastName);

        return new Sections(active, emeritus, wallOfFame);
    }

    @FunctionalInterface
    private interface Fetcher<T> { List<T> get() throws Exception; }

    @FunctionalInterface
    private interface RowSink<T> { void accept(T row); }

    private static <T> void ingest(final Fetcher<T> fetcher, final RowSink<T> sink) {
        try {
            for (final T row : fetcher.get()) sink.accept(row);
        } catch (final Exception e) {
            System.err.println("[contributors] fetch failed: " + e.getMessage()
                    + " — continuing with partial data");
        }
    }

    private Contributors() {}
}
