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

import java.time.Instant;

public final class Stats {
    private int contributions;
    private Instant lastActivity;

    public void touch(final Instant ts) {
        contributions++;
        if (ts != null && (lastActivity == null || ts.isAfter(lastActivity))) {
            lastActivity = ts;
        }
    }

    public void add(final Stats other) {
        contributions += other.contributions;
        if (other.lastActivity != null
                && (lastActivity == null || other.lastActivity.isAfter(lastActivity))) {
            lastActivity = other.lastActivity;
        }
    }

    public int contributions() {
        return contributions;
    }

    public Instant lastActivity() {
        return lastActivity;
    }
}
