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

import static io.fd.maintainer.plugin.parser.ComponentPath.MatchLevel.MAX;
import static io.fd.maintainer.plugin.parser.ComponentPath.MatchLevel.NONE;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multiset;
import com.google.gerrit.server.patch.PatchListEntry;
import io.fd.maintainer.plugin.parser.ComponentInfo;
import io.fd.maintainer.plugin.parser.ComponentPath;
import io.fd.maintainer.plugin.parser.ComponentPath.MatchLevel;
import io.fd.maintainer.plugin.parser.Maintainer;
import io.fd.maintainer.plugin.service.ComponentReviewInfo;
import io.fd.maintainer.plugin.service.ComponentReviewInfo.ComponentReviewInfoBuilder;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.apache.commons.lang3.StringUtils;
import org.parboiled.common.Tuple2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MaintainersIndex implements ClosestMatch {

    private static final Logger LOG = LoggerFactory.getLogger(MaintainersIndex.class);

    private Map<ComponentPath, Set<Maintainer>> pathToMaintainersIndex;
    private Map<String, String> pathToComponentIndex;
    private Multimap<String, String> maintainerNameToComponentIndex;
    private Map<String, Boolean> reviewComponentIndex;

    public MaintainersIndex(@Nonnull final List<ComponentInfo> maintainers) {
        pathToMaintainersIndex = maintainers.stream()
                .flatMap(maintainersInfo -> maintainersInfo.getPaths().stream()
                        .map(componentPath -> new Tuple2<>(componentPath, maintainersInfo.getMaintainers())))
                .collect(Collectors.toMap(tuple -> tuple.a, tuple -> tuple.b));

        pathToComponentIndex = new HashMap<>();
        maintainers.forEach(maintainersInfo -> maintainersInfo.getPaths()
                .forEach(
                        componentPath ->
                                pathToComponentIndex.put(componentPath.getPath(), maintainersInfo.getTitle())
                ));
        maintainerNameToComponentIndex = LinkedListMultimap.create();
        maintainers.forEach(maintainersInfo -> maintainersInfo.getMaintainers().forEach(maintainer ->
                maintainerNameToComponentIndex.put(maintainer.getName(), maintainersInfo.getTitle())));

        reviewComponentIndex = maintainers.stream()
                .collect(Collectors.toMap(ComponentInfo::getTitle, component -> !component.getMaintainers().isEmpty()));
    }

    private static int getPathLength(final String path) {
        return StringUtils.countMatches(path, "/");
    }

    /**
     * Tells whether component has maintainers configured
     */
    public boolean isReviewComponent(@Nonnull final String component) {
        return reviewComponentIndex.get(component);
    }

    public Set<String> getComponentsForMaintainer(@Nonnull final String name) {
        return new HashSet<>(maintainerNameToComponentIndex.get(name));
    }

    public String getComponentForPath(@Nonnull final ComponentPath path) {
        return pathToComponentIndex.get(path.getPath());
    }

    public Tuple2<Set<ComponentPath>, Set<ComponentPath>> getComponentPathsForEntry(
            @Nonnull final PatchListEntry entry) {
        final LinkedListMultimap<MatchLevel, ComponentPath> byMatchIndexOld = LinkedListMultimap.create();
        final LinkedListMultimap<MatchLevel, ComponentPath> byMatchIndexNew = LinkedListMultimap.create();
        pathToMaintainersIndex.forEach((key, value) -> byMatchIndexOld.put(key.matchAgainst(entry.getOldName()),
                key));

        pathToMaintainersIndex.forEach((key, value) -> byMatchIndexNew.put(key.matchAgainst(entry.getNewName()),
                key));

        final MatchLevel maxMatchLevelOld = maxMatchLevel(byMatchIndexOld.keys());
        final MatchLevel maxMatchLevelNew = maxMatchLevel(byMatchIndexNew.keys());

        final int mostSpecificLengthOld = mostSpecificPathLengthFromComponent(maxMatchLevelOld, byMatchIndexOld);
        final int mostSpecificLengthNew = mostSpecificPathLengthFromComponent(maxMatchLevelOld, byMatchIndexOld);

        final Set<ComponentPath> oldComponents = NONE == maxMatchLevelOld
                ? Collections.emptySet()
                : new HashSet<>(byMatchIndexOld.get(maxMatchLevelOld)
                        .stream()
                        .filter(componentPath -> getPathLength(componentPath.getPath()) == mostSpecificLengthOld)
                        .collect(Collectors.toList()));

        final Set<ComponentPath> newComponents = NONE == maxMatchLevelNew
                ? Collections.emptySet()
                : new HashSet<>(byMatchIndexNew.get(maxMatchLevelNew).stream()
                        .filter(componentPath -> getPathLength(componentPath.getPath()) == mostSpecificLengthNew)
                        .collect(Collectors.toList()));

        return new Tuple2<>(oldComponents, newComponents);
    }

    public ComponentReviewInfo getReviewInfoForPath(final String path) {
        LOG.debug("Getting maintainers for path {}", path);
        final LinkedListMultimap<MatchLevel, Tuple2<ComponentPath, Maintainer>> byMatchIndex =
                LinkedListMultimap.create();

        pathToMaintainersIndex.forEach((key, value) -> value
                .forEach((entry -> byMatchIndex.put(key.matchAgainst(path), new Tuple2<>(key, entry)))));

        final MatchLevel maximumMatchLevel = maxMatchLevel(byMatchIndex.keys());
        LOG.debug("Maximum match level for path {} = {}", path, maximumMatchLevel);

        // out of all that have maximum match level, we need only those that are most basically longest
        // allows to get /foo/bar/* over * or /foo/*
        final int mostSpecificPathLength = mostSpecificPathLengthFromTuple(maximumMatchLevel, byMatchIndex);

        if (NONE == maximumMatchLevel) {
            return new ComponentReviewInfoBuilder()
                    .setAffectedFile(path).createComponentReviewInfo();
        } else {
            return byMatchIndex.get(maximumMatchLevel).stream()
                    .filter(tuple -> getPathLength(tuple.a.getPath()) == mostSpecificPathLength)
                    .peek(maintainer -> LOG
                            .debug("Maintainer found [component={},reviewer={}]", maintainer.a, maintainer.b))
                    .map(tuple -> new ComponentReviewInfoBuilder()
                            .setAffectedFile(path)
                            .setComponentName(getComponentForPath(tuple.a))
                            .setComponentMaintainers(pathToMaintainersIndex.get(tuple.a))
                            .createComponentReviewInfo())
                    .findAny().orElse(new ComponentReviewInfoBuilder()
                            .setAffectedFile(path).createComponentReviewInfo());
        }
    }

    private MatchLevel maxMatchLevel(final Multiset<MatchLevel> keys) {
        return keys.stream().max(MAX).orElse(NONE);
    }

    private int mostSpecificPathLengthFromTuple(final MatchLevel maximumMatchLevel,
                                                final LinkedListMultimap<MatchLevel, Tuple2<ComponentPath, Maintainer>> byMatchIndex) {
        return byMatchIndex.get(maximumMatchLevel)
                .stream()
                .map(tuple -> tuple.a)
                .map(ComponentPath::getPath)
                .map(MaintainersIndex::getPathLength)
                .max(Comparator.comparingInt(integer -> integer))
                .orElse(0);
    }

    private int mostSpecificPathLengthFromComponent(final MatchLevel maximumMatchLevel,
                                                    final LinkedListMultimap<MatchLevel, ComponentPath> byMatchIndex) {
        return byMatchIndex.get(maximumMatchLevel)
                .stream()
                .map(ComponentPath::getPath)
                .map(MaintainersIndex::getPathLength)
                .max(Comparator.comparingInt(integer -> integer))
                .orElse(0);
    }
}
