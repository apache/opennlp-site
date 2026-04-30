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

import java.util.HashSet;
import java.util.Set;

public final class Contributor {
    private String name;
    private String ghLogin;
    private String apacheId;
    private String avatarUrl;
    private boolean pmc;
    private boolean committer;
    private boolean chair;
    private Roster.ForcedStatus forcedStatus;
    private final Stats stats = new Stats();
    private final Set<String> emails = new HashSet<>();
    private final Set<String> aliases = new HashSet<>();

    public String name() { return name; }
    public String ghLogin() { return ghLogin; }
    public String apacheId() { return apacheId; }
    public String avatarUrl() { return avatarUrl; }
    public boolean isPmc() { return pmc; }
    public boolean isCommitter() { return committer; }
    public boolean isChair() { return chair; }
    public Roster.ForcedStatus forcedStatus() { return forcedStatus; }
    public Stats stats() { return stats; }
    public Set<String> emails() { return emails; }
    public Set<String> aliases() { return aliases; }

    public void setName(final String name) { this.name = name; }
    public void setGhLogin(final String ghLogin) { this.ghLogin = ghLogin; }
    public void setApacheId(final String apacheId) { this.apacheId = apacheId; }
    public void setAvatarUrl(final String avatarUrl) { this.avatarUrl = avatarUrl; }
    public void setPmc(final boolean pmc) { this.pmc = pmc; }
    public void setCommitter(final boolean committer) { this.committer = committer; }
    public void setChair(final boolean chair) { this.chair = chair; }
    public void setForcedStatus(final Roster.ForcedStatus forcedStatus) { this.forcedStatus = forcedStatus; }

    public String displayName() {
        if (name != null && !name.isBlank()) return name;
        if (ghLogin != null && !ghLogin.isBlank()) return ghLogin;
        if (apacheId != null && !apacheId.isBlank()) return apacheId;
        return "(unknown)";
    }

    /** Sort key: "lastname firstname" — used for stable, deterministic ordering. */
    public String sortKey() {
        final String n = displayName().trim();
        if (n.isEmpty()) return "";
        final int lastSpace = n.lastIndexOf(' ');
        if (lastSpace < 0) return n.toLowerCase(java.util.Locale.ROOT);
        final String last = n.substring(lastSpace + 1);
        final String first = n.substring(0, lastSpace);
        return (last + " " + first).toLowerCase(java.util.Locale.ROOT);
    }

    public String profileUrl() {
        return ghLogin != null && !ghLogin.isBlank()
                ? "https://github.com/" + ghLogin
                : null;
    }

    public String roleFlags() {
        if (pmc) return "C-P";
        if (committer) return "C";
        return "";
    }
}
