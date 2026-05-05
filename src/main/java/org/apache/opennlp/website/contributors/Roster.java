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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/**
 * Pulls the OpenNLP PMC + committer roster from Whimsy LDAP exports.
 * `public_ldap_projects.json` lists `members` (committers) and `owners` (PMC) per project;
 * `public_ldap_people.json` maps each apache id to a real name and (sometimes) a github login.
 */
public final class Roster {

    private static final String PROJECTS_URL = "https://whimsy.apache.org/public/public_ldap_projects.json";
    private static final String PEOPLE_URL = "https://whimsy.apache.org/public/public_ldap_people.json";
    private static final String PROJECT_KEY = "opennlp";

    /** `null` when the bucket should be derived from live activity. */
    public enum ForcedStatus { ACTIVE, EMERITUS }

    public static final class Member {
        public final String name;
        public final String apacheId;
        /** Ordered list of GitHub logins; index 0 is the primary used for display. Empty if unknown. */
        public final List<String> ghLogins;
        public final boolean pmc;
        public final boolean committer;
        public final boolean chair;
        public final ForcedStatus forcedStatus;

        Member(final String name, final String apacheId, final List<String> ghLogins,
               final boolean pmc, final boolean committer, final boolean chair,
               final ForcedStatus forcedStatus) {
            this.name = name;
            this.apacheId = apacheId;
            this.ghLogins = ghLogins;
            this.pmc = pmc;
            this.committer = committer;
            this.chair = chair;
            this.forcedStatus = forcedStatus;
        }

        public String primaryGhLogin() {
            return ghLogins.isEmpty() ? null : ghLogins.get(0);
        }
    }

    public static List<Member> fetch(final HttpCache cache) {
        try {
            final JSONObject projects = new JSONObject(cache.fetch(PROJECTS_URL));
            final JSONObject project = projects.getJSONObject("projects").optJSONObject(PROJECT_KEY);
            if (project == null) {
                throw new IllegalStateException("project '" + PROJECT_KEY + "' not in " + PROJECTS_URL);
            }
            final Set<String> committerIds = jsonArrayToSet(project, "members");
            final Set<String> pmcIds = jsonArrayToSet(project, "owners");
            final Set<String> all = new TreeSet<>();
            all.addAll(committerIds);
            all.addAll(pmcIds);

            final JSONObject people = new JSONObject(cache.fetch(PEOPLE_URL)).optJSONObject("people");
            final Properties overrides = loadOverrides();

            final List<Member> members = new ArrayList<>();
            for (final String id : all) {
                final JSONObject info = people != null ? people.optJSONObject(id) : null;
                final String name = info != null ? info.optString("name", id) : id;
                // Override > Whimsy githubUsername > github.com link in Whimsy urls.
                final List<String> ghLogins;
                final String overrideGh = overrides.getProperty(id + ".gh");
                if (overrideGh != null && !overrideGh.isBlank()) {
                    ghLogins = parseLoginList(overrideGh);
                } else {
                    final String declaredGh = info != null ? info.optString("githubUsername", null) : null;
                    final String single = (declaredGh != null && !declaredGh.isBlank())
                            ? declaredGh : githubLoginFromUrls(info);
                    ghLogins = single == null ? List.of() : List.of(single);
                }
                final ForcedStatus forced = parseStatus(overrides.getProperty(id + ".status"));
                final boolean chair = Boolean.parseBoolean(
                        overrides.getProperty(id + ".chair", "false").trim());
                members.add(new Member(name, id, ghLogins, pmcIds.contains(id), true, chair, forced));
            }
            return members;
        } catch (final Exception e) {
            System.err.println("[roster] failed to fetch Whimsy roster: " + e.getMessage()
                    + " — Active/Emeritus sections will be empty");
            return List.of();
        }
    }

    private static List<String> parseLoginList(final String value) {
        final List<String> out = new ArrayList<>();
        for (final String raw : value.split(";")) {
            final String trimmed = raw.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return out;
    }

    private static ForcedStatus parseStatus(final String value) {
        if (value == null) return null;
        final String lower = value.trim().toLowerCase(java.util.Locale.ROOT);
        if (lower.isEmpty()) return null;
        if (lower.equals("active")) return ForcedStatus.ACTIVE;
        if (lower.equals("emeritus")) return ForcedStatus.EMERITUS;
        System.err.println("[roster] team-overrides: unknown status '" + value
                + "' (expected active|emeritus); ignoring");
        return null;
    }

    /** Loads the apache-id -> override map shipped on the classpath. */
    private static Properties loadOverrides() {
        final Properties props = new Properties();
        try (InputStream in = Roster.class.getResourceAsStream("/team-overrides.properties")) {
            if (in != null) props.load(in);
        } catch (final Exception e) {
            System.err.println("[roster] failed to load team-overrides.properties: " + e.getMessage());
        }
        return props;
    }

    /** Some Whimsy people entries list a GitHub profile URL but no `githubUsername`. */
    private static String githubLoginFromUrls(final JSONObject info) {
        if (info == null || !info.has("urls")) return null;
        final JSONArray urls = info.optJSONArray("urls");
        if (urls == null) return null;
        for (int i = 0; i < urls.length(); i++) {
            final String url = urls.optString(i, "");
            final String host = "https://github.com/";
            final int idx = url.indexOf(host);
            if (idx < 0) continue;
            final String tail = url.substring(idx + host.length());
            // strip trailing path/query/fragment
            int cut = tail.length();
            for (int c = 0; c < tail.length(); c++) {
                final char ch = tail.charAt(c);
                if (ch == '/' || ch == '?' || ch == '#') { cut = c; break; }
            }
            final String login = tail.substring(0, cut);
            if (!login.isBlank()) return login;
        }
        return null;
    }

    private static Set<String> jsonArrayToSet(final JSONObject obj, final String key) {
        final Set<String> out = new HashSet<>();
        if (!obj.has(key) || obj.isNull(key)) return out;
        final JSONArray arr = obj.getJSONArray(key);
        for (int i = 0; i < arr.length(); i++) out.add(arr.getString(i));
        return out;
    }

    private Roster() {}
}
