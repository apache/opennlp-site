<!--
   Licensed to the Apache Software Foundation (ASF) under one
   or more contributor license agreements.  See the NOTICE file
   distributed with this work for additional information
   regarding copyright ownership.  The ASF licenses this file
   to you under the Apache License, Version 2.0 (the
   "License"); you may not use this file except in compliance
   with the License.  You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing,
   software distributed under the License is distributed on an
   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
   KIND, either express or implied.  See the License for the
   specific language governing permissions and limitations
   under the License.  
-->

Welcome to OpenNLP Site Source Code
====================================

[![GitHub license](https://img.shields.io/badge/license-Apache%202-blue.svg)](https://raw.githubusercontent.com/apache/opennlp/main/LICENSE)
[![Build Status](https://github.com/apache/opennlp/workflows/Java%20CI/badge.svg)](https://github.com/apache/opennlp-site/actions)
[![Stack Overflow](https://img.shields.io/badge/stack%20overflow-opennlp-f1eefe.svg)](https://stackoverflow.com/questions/tagged/opennlp)

#### Requirements

- Java 21
- Maven 3.6.3+

#### Build

```bash
mvn clean install
```

The output is rendered to `target/opennlp-site/`. Open `target/opennlp-site/index.html` in a browser to preview.

#### Live dev mode

```bash
mvn compile -Pserve                       # http://localhost:8080/
mvn compile -Pserve -Djbake.port=9000     # custom port
```

Bakes the site once, then serves `target/opennlp-site/` over HTTP and watches `src/main/jbake/` recursively. Any change to a content file, template, asset or `jbake.properties` triggers a re-bake (debounced ~400 ms); reload the browser to see it. Press Ctrl-C to stop.

The contributor fetch (Whimsy + GitHub) runs once at startup and is reused across re-bakes — no re-fetch and no new rate-limit cost while you iterate. Cached HTTP responses live under `target/contrib-cache/`. Restart `mvn compile -Pserve` to refresh the contributor data.

#### Live contributor data

The team page (`team.html`) is populated at build time by the `org.apache.opennlp.website.Site` driver, which fetches:

- the OpenNLP committer/PMC roster from [Whimsy LDAP exports](https://whimsy.apache.org/public/), and
- live contributor + activity data from the GitHub REST API across `apache/opennlp`, `apache/opennlp-site`, `apache/opennlp-addons` and `apache/opennlp-sandbox`.

It then partitions members into **Active Team** (any activity in the last 2 years), **Emeritus** (committer/PMC with no recent activity) and a **Wall of Fame** (everyone else with a GitHub login — committers/PMCs aren't repeated here). Identity merging (login + apache id + email + normalized name) and bot filtering match the logic of the `opennlp-stats` reference tool.

HTTP responses are cached on disk under `target/contrib-cache/` with a 6-hour TTL, so iterative local builds are cheap. The build runs without a GitHub token; anonymous rate limits (60 req/h) may leave a few `/users/{login}` lookups unresolved on a cold cache, which can drop a committer whose GitHub login differs from their Apache id into Emeritus until the cache warms. Re-running the build inside the TTL fills it in.

##### Manual roster overrides

Whimsy doesn't always carry `githubUsername` for committers, and the live `/users/{login}` bridge can hit anonymous rate limits, so `src/main/resources/team-overrides.properties` lets you pin attributes per Apache id. The file is read at build time and merged on top of the Whimsy + GitHub data.

| Key | Meaning |
| --- | --- |
| `<apacheId>.gh` | One or more `;`-separated GitHub logins. The first is used for the card link and avatar; the rest are merged into the same record so their commits/PRs/comments roll up. |
| `<apacheId>.status` | `active` or `emeritus`. Forces the section bucket regardless of what the live activity check says. |
| `<apacheId>.chair` | `true` for the current PMC chair. Renders an extra orange `Chair` badge on the card and adds the chair entry to the legend. |

Example:

```properties
jzemerick.gh     = jzonthemtn
jzemerick.status = active
jzemerick.chair  = true

joern.gh         = kottmann
joern.status     = active
```

##### Skipping the live fetch (offline / restricted CI)

If the build environment can't reach `api.github.com` or `whimsy.apache.org` — corporate proxy, blocked CI runner, demo build — skip the retrieval entirely:

```bash
mvn compile -Pno-fetch          # Maven profile
OPENNLP_SITE_NO_FETCH=1 mvn compile   # env var (any non-empty truthy value)
```

The team page renders with empty Active/Emeritus/Wall-of-Fame sections (each shows a "No contributors to show." placeholder); the rest of the site builds normally.

#### Build Bot

Website is built via ASF BuildBot. You find it [here](https://ci.apache.org/).


