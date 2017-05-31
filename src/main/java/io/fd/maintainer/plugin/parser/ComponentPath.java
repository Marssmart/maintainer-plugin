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

import static io.fd.maintainer.plugin.parser.ComponentPath.MatchLevel.FULL;
import static io.fd.maintainer.plugin.parser.ComponentPath.MatchLevel.NONE;
import static io.fd.maintainer.plugin.parser.ComponentPath.MatchLevel.PARTIAL;
import static io.fd.maintainer.plugin.parser.ComponentPath.MatchLevel.WILDCARD_ONLY;
import static io.fd.maintainer.plugin.parser.ComponentPath.MatchLevel.WILDCARD_WITH_EXTENSION;

import java.util.Comparator;

public class ComponentPath {

    private final String path;

    public ComponentPath(final String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    public MatchLevel matchAgainst(final String path) {
        // initial match level is NONE unless full match
        if (path == null) {
            return NONE;
        }

        MatchLevel matchLevel = this.path.equals(path)
                ? FULL
                : NONE;
        // trims down wildcard

        if (matchLevel == NONE) {
            final int indexOfWildcard = this.path.indexOf("*");

            if (-1 != indexOfWildcard) {
                final String wildcardLess = this.path.replace(this.path.substring(indexOfWildcard), "");
                if (path.contains(wildcardLess)) {
                    matchLevel = this.path.equals(wildcardLess)
                            // if path does not have wildcard, its partial match
                            ? PARTIAL
                            // if it has, by previous match its proven that it matches wildcard
                            : WILDCARD_ONLY;

                    final int extensionStart = this.path.lastIndexOf(".");
                    final String componentExtension = extension(extensionStart, this.path);

                    if (!componentExtension.isEmpty()) {
                        // meaning that index of last dot was found,therefore extension is present
                        if (-1 != extensionStart && extension(path).equals(componentExtension)) {
                            matchLevel = WILDCARD_WITH_EXTENSION;
                        } else {
                            // matches wildcard but not the extension
                            matchLevel = NONE;
                        }
                    }
                }
            } else {
                // not a wildcard path ,therefore attempts match it as direct child
                final String[] componentPathParts = this.path.split("/");
                final String[] matchedPathParts = path.split("/");

                matchLevel = matchedPathParts.length - componentPathParts.length > 1
                        ?
                        NONE
                        : matchPathsAsDirectChild(componentPathParts, matchedPathParts);
            }
        }
        return matchLevel;
    }

    private MatchLevel matchPathsAsDirectChild(final String[] componentPath, final String[] matchedPath) {
        for (int i = 0; i < componentPath.length; i++) {
            if (componentPath[i].equals(matchedPath[i])) {
                // dir equals,continue
                continue;
            }
            // as soon as dir is not equal, return NONE
            return NONE;
        }
        // everything has been matched, therefore partial
        return PARTIAL;
    }

    private String extension(final int extensionStart, final String path) {
        if (-1 == extensionStart) {
            return "";
        }
        return path.substring(extensionStart + 1);
    }

    private String extension(final String path) {
        return extension(path.lastIndexOf("."), path);
    }

    @Override
    public String toString() {
        return "ComponentPath{" +
                "path='" + path + '\'' +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final ComponentPath that = (ComponentPath) o;

        return path != null
                ? path.equals(that.path)
                : that.path == null;
    }

    @Override
    public int hashCode() {
        return path != null
                ? path.hashCode()
                : 0;
    }

    public enum MatchLevel {
        FULL(4), // full equality match
        WILDCARD_WITH_EXTENSION(3),// matches wildcarded path with extension for ex.: foo/bar/*.mk
        PARTIAL(2),// matches part of the path
        WILDCARD_ONLY(1),// matches wildcarded path for ex.: foo/bar/*
        NONE(0); // no match

        public static final Comparator<MatchLevel> MAX = Comparator.comparingInt(MatchLevel::getValue);

        private final int value;

        private MatchLevel(final int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

    }
}
