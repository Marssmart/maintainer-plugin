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

package io.fd.maintainer.plugin.util;

import com.google.common.collect.Sets;
import com.google.gerrit.server.patch.PatchListEntry;
import io.fd.maintainer.plugin.parser.ComponentPath;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.parboiled.common.Tuple2;

public interface WarningGenerator {

    static Set<ComponentWithPath> getInvalidComponents(
            final MaintainersIndex mappingIndex,
            final Map.Entry<PatchListEntry, Tuple2<Set<ComponentPath>, Set<ComponentPath>>> entry) {
        final Set<ComponentPath> oldComponents = entry.getValue().a;
        final Set<ComponentPath> newComponents = entry.getValue().b;
        final Sets.SetView<ComponentPath> difference = Sets.difference(oldComponents, newComponents);

        return difference.immutableCopy().stream()
                .map(path -> new ComponentWithPath(mappingIndex.getComponentForPath(path), path))
                .collect(Collectors.toSet());
    }

    default Set<ComponentChangeWarning> generateComponentChangeWarnings(
            @Nonnull final MaintainersIndex mappingIndex,
            @Nonnull final Map<PatchListEntry, Tuple2<Set<ComponentPath>, Set<ComponentPath>>> renamesIndex) {
        return renamesIndex.entrySet().stream()
                .map(entry -> {
                    final PatchListEntry key = entry.getKey();
                    return new ComponentChangeWarning(key.getOldName(), key.getNewName(),
                            getInvalidComponents(mappingIndex, entry));
                })
                // if no invalid components, its valid rename/move
                .filter(warning -> !warning.getInvalidComponents().isEmpty())
                .collect(Collectors.toSet());
    }

    class ComponentChangeWarning {
        private final String oldName;
        private final String newName;
        private final Set<ComponentWithPath> invalidComponents;

        ComponentChangeWarning(final String oldName, final String newName,
                               final Set<ComponentWithPath> invalidComponents) {
            this.oldName = oldName;
            this.newName = newName;
            this.invalidComponents = invalidComponents;
        }

        public String getOldName() {
            return oldName;
        }

        public String getNewName() {
            return newName;
        }

        public Set<ComponentWithPath> getInvalidComponents() {
            return invalidComponents;
        }
    }

    class ComponentWithPath {
        private final String componentTitle;
        private final ComponentPath path;

        ComponentWithPath(final String componentTitle,
                          final ComponentPath path) {
            this.componentTitle = componentTitle;
            this.path = path;
        }

        public String getComponentTitle() {
            return componentTitle;
        }

        public ComponentPath getPath() {
            return path;
        }
    }
}
