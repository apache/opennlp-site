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

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Merges identities across multiple sources (ASF roster, GitHub commits, GitHub events).
 * Ported from opennlp-stats/aggregate.py: walks login -> apache-id -> email -> normalized-name
 * keys to find an existing record, otherwise creates one.
 */
public final class IdentityIndex {

    private static final Set<String> BOT_LOGINS = Set.of(
            "dependabot[bot]", "github-actions[bot]", "actions-user", "buildbot",
            "renovate[bot]", "codecov-commenter", "copilot", "copilot-swe-agent[bot]");

    private final List<Contributor> records = new ArrayList<>();
    private final Map<String, Contributor> byLogin = new HashMap<>();
    private final Map<String, Contributor> byApacheId = new HashMap<>();
    private final Map<String, Contributor> byEmail = new HashMap<>();
    private final Map<String, Contributor> byName = new HashMap<>();

    /** Returns true if some seeded record exists under this apache id (read-only). */
    public boolean hasApacheId(final String apacheId) {
        return apacheId != null && byApacheId.containsKey(apacheId.toLowerCase(Locale.ROOT));
    }

    /**
     * Registers an additional GitHub login as an alias of an existing contributor.
     * Used for people with multiple GH accounts: future events keyed by the alias
     * resolve to the same record without overwriting the primary login.
     */
    public void linkLoginAlias(final Contributor c, final String login) {
        if (c == null || login == null) return;
        final String trimmed = login.trim();
        if (trimmed.isEmpty() || isBot(trimmed)) return;
        byLogin.put(trimmed.toLowerCase(Locale.ROOT), c);
    }

    public List<Contributor> all() {
        // de-dupe by reference, preserving first-seen order
        final Set<Contributor> seen = new LinkedHashSet<>(records);
        return new ArrayList<>(seen);
    }

    /** Looks up an existing record by any matching key, otherwise creates one. Merges as needed. */
    public Contributor findOrCreate(
            final String loginIn, final String apacheId, final String email, final String name) {
        final String sanitized = sanitizeLogin(loginIn);
        if (sanitized != null && isBot(sanitized)) {
            return null;
        }
        // Recover login from <login>@users.noreply.github.com
        final String login = sanitized != null ? sanitized
                : (email != null ? loginFromNoreply(email) : null);

        final List<Contributor> candidates = new ArrayList<>();
        if (login != null) addIfPresent(candidates, byLogin.get(login.toLowerCase(Locale.ROOT)));
        if (apacheId != null) addIfPresent(candidates, byApacheId.get(apacheId.toLowerCase(Locale.ROOT)));
        if (email != null) addIfPresent(candidates, byEmail.get(email.toLowerCase(Locale.ROOT)));
        if (name != null) addIfPresent(candidates, byName.get(normalizeName(name)));

        final Contributor c;
        if (candidates.isEmpty()) {
            c = new Contributor();
            records.add(c);
        } else {
            c = candidates.get(0);
            for (int i = 1; i < candidates.size(); i++) {
                merge(c, candidates.get(i));
            }
        }
        if (login != null && c.ghLogin() == null) c.setGhLogin(login);
        if (apacheId != null && c.apacheId() == null) c.setApacheId(apacheId);
        if (name != null && (c.name() == null || c.name().isBlank())) c.setName(name);
        if (email != null) c.emails().add(email.toLowerCase(Locale.ROOT));
        if (name != null) c.aliases().add(name);
        link(c);
        return c;
    }

    private void merge(final Contributor into, final Contributor other) {
        if (into == other) return;
        if (into.ghLogin() == null) into.setGhLogin(other.ghLogin());
        if (into.apacheId() == null) into.setApacheId(other.apacheId());
        if (into.name() == null || into.name().isBlank()) into.setName(other.name());
        if (into.avatarUrl() == null) into.setAvatarUrl(other.avatarUrl());
        if (other.isPmc()) into.setPmc(true);
        if (other.isCommitter()) into.setCommitter(true);
        if (other.isChair()) into.setChair(true);
        if (into.forcedStatus() == null) into.setForcedStatus(other.forcedStatus());
        into.stats().add(other.stats());
        into.emails().addAll(other.emails());
        into.aliases().addAll(other.aliases());

        records.remove(other);
        for (final Map<String, Contributor> table :
                Arrays.<Map<String, Contributor>>asList(byLogin, byApacheId, byEmail, byName)) {
            for (final Map.Entry<String, Contributor> e : new HashMap<>(table).entrySet()) {
                if (e.getValue() == other) table.put(e.getKey(), into);
            }
        }
    }

    private void link(final Contributor c) {
        if (c.ghLogin() != null) byLogin.put(c.ghLogin().toLowerCase(Locale.ROOT), c);
        if (c.apacheId() != null) byApacheId.put(c.apacheId().toLowerCase(Locale.ROOT), c);
        if (c.name() != null) {
            final String n = normalizeName(c.name());
            if (!n.isBlank()) byName.put(n, c);
        }
        for (final String alias : c.aliases()) {
            final String n = normalizeName(alias);
            if (!n.isBlank()) byName.putIfAbsent(n, c);
        }
        for (final String email : c.emails()) {
            byEmail.put(email, c);
        }
    }

    private static void addIfPresent(final List<Contributor> list, final Contributor c) {
        if (c != null && !list.contains(c)) list.add(c);
    }

    private static String sanitizeLogin(final String login) {
        if (login == null) return null;
        final String trimmed = login.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static boolean isBot(final String login) {
        if (login == null) return false;
        final String lower = login.toLowerCase(Locale.ROOT);
        return lower.endsWith("[bot]") || BOT_LOGINS.contains(lower);
    }

    private static String loginFromNoreply(final String email) {
        if (email == null) return null;
        final String lower = email.toLowerCase(Locale.ROOT);
        if (!lower.endsWith("@users.noreply.github.com")) return null;
        final String local = lower.substring(0, lower.indexOf('@'));
        final int plus = local.indexOf('+');
        return plus >= 0 ? local.substring(plus + 1) : local;
    }

    private static String normalizeName(final String name) {
        if (name == null) return "";
        final String stripped = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return stripped.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }
}
