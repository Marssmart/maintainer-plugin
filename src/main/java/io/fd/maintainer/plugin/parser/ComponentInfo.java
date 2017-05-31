/*
 * Copyright (c) 2017 Cisco and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fd.maintainer.plugin.parser;

import static java.util.Collections.emptySet;

import java.util.Set;

public final class ComponentInfo {

    private final String title;
    private final Set<String> comments;
    private final Set<Maintainer> maintainers;
    private final Set<ComponentPath> paths;

    private ComponentInfo(final String title, final Set<String> contactEmails, final Set<Maintainer> maintainers,
                          final Set<ComponentPath> paths) {
        this.title = title;
        this.comments = contactEmails == null
                ? emptySet()
                : contactEmails;
        this.maintainers = maintainers == null
                ? emptySet()
                : maintainers;
        this.paths = paths == null
                ? emptySet()
                : paths;
    }

    public String getTitle() {
        return title;
    }

    public Set<String> getComments() {
        return comments;
    }

    public Set<Maintainer> getMaintainers() {
        return maintainers;
    }

    public Set<ComponentPath> getPaths() {
        return paths;
    }

    public static class ComponentInfoBuilder {
        private String title;
        private Set<String> contactEmails;
        private Set<Maintainer> maintainers;
        private Set<ComponentPath> paths;

        public ComponentInfoBuilder setTitle(final String title) {
            this.title = title;
            return this;
        }

        public ComponentInfoBuilder setComments(final Set<String> contactEmails) {
            this.contactEmails = contactEmails;
            return this;
        }

        public ComponentInfoBuilder setMaintainers(final Set<Maintainer> maintainers) {
            this.maintainers = maintainers;
            return this;
        }

        public ComponentInfoBuilder setPaths(final Set<ComponentPath> paths) {
            this.paths = paths;
            return this;
        }

        public ComponentInfo createMaintainer() {
            return new ComponentInfo(title, contactEmails, maintainers, paths);
        }
    }
}
