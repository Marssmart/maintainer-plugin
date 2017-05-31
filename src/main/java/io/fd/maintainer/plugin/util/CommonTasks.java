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


import static io.fd.maintainer.plugin.service.ComponentReviewInfo.ComponentReviewInfoState.COMPONENT_FOUND;
import static io.fd.maintainer.plugin.service.ComponentReviewInfo.ComponentReviewInfoState.COMPONENT_NOT_FOUND;
import static java.lang.String.format;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toMap;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.ChangesCollection;
import com.google.gerrit.server.change.PostReview;
import com.google.gerrit.server.change.PostReviewers;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.change.Revisions;
import com.google.gerrit.server.patch.PatchListEntry;
import com.google.gerrit.server.update.UpdateException;
import com.google.gwtorm.server.OrmException;
import io.fd.maintainer.plugin.parser.ComponentPath;
import io.fd.maintainer.plugin.parser.Maintainer;
import io.fd.maintainer.plugin.service.ComponentReviewInfo;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.parboiled.common.Tuple2;

/**
 * Task to build complex index that is used to detect path components,etc...
 */
public interface CommonTasks extends WarningGenerator, PatchListProcessing {

    String LINE_SEPARATOR = System.lineSeparator();

    static String formatReviewerInfo(final Set<ComponentReviewInfo> reviewInfoSet) {

        final Multimap<String, String> componentToAffectedFileIndex = LinkedListMultimap.create();
        final Set<ComponentReviewInfo> componentBoundReviewInfoSet = reviewInfoSet.stream()
                .filter(reviewInfo -> reviewInfo.getState() == COMPONENT_FOUND).collect(Collectors.toSet());
        componentBoundReviewInfoSet
                .forEach(reviewInfo -> componentToAffectedFileIndex
                        .put(reviewInfo.getComponentName(), reviewInfo.getAffectedFile()));

        final Map<String, Set<Maintainer>> componentToMaintainers = new HashMap<>();
        componentBoundReviewInfoSet.forEach(reviewInfo -> {
            if (!componentToMaintainers.containsKey(reviewInfo.getComponentName())) {
                componentToMaintainers.put(reviewInfo.getComponentName(), reviewInfo.getComponentMaintainers());
            }
        });

        final List<ComponentReviewInfo> componentNotFoundReviewInfos = reviewInfoSet.stream()
                .filter(reviewInfo -> reviewInfo.getState() == COMPONENT_NOT_FOUND)
                .collect(Collectors.toList());

        final String messageComponentsFound = componentToAffectedFileIndex.keySet()
                .stream()
                .map(key -> format(
                        "Component %s%s%s" +
                                "Maintainers :%s%s%s" +
                                "Affected files :%s%s%s",
                        key, LINE_SEPARATOR, LINE_SEPARATOR,
                        LINE_SEPARATOR, formatMaintainers(componentToMaintainers.get(key)), LINE_SEPARATOR,
                        LINE_SEPARATOR, formatFiles(componentToAffectedFileIndex.get(key)), LINE_SEPARATOR))
                .collect(Collectors.joining(LINE_SEPARATOR));

        final String messageComponentsNotFound =
                format("No component found for following files%s%s", LINE_SEPARATOR,
                        formatFilesWithNoComponent(componentNotFoundReviewInfos));

        if (nonNull(messageComponentsNotFound)) {
            return messageComponentsFound.concat(LINE_SEPARATOR).concat(messageComponentsNotFound);
        } else {
            return messageComponentsFound;
        }
    }

    static String formatFilesWithNoComponent(final List<ComponentReviewInfo> componentNotFoundReviewInfos) {
        return componentNotFoundReviewInfos.stream()
                .map(ComponentReviewInfo::getAffectedFile)
                .map(" "::concat)
                .collect(Collectors.joining(LINE_SEPARATOR));
    }

    static String formatMaintainers(final Set<Maintainer> maintainers) {
        return maintainers.stream()
                .map(maintainer -> format(" %s<%s>", maintainer.getName(), maintainer.getEmail()))
                .collect(Collectors.joining(LINE_SEPARATOR));
    }

    static String formatFiles(final Collection<String> files) {
        return files.stream()
                .map(file -> format(" Path: %s", file))
                .collect(Collectors.joining(LINE_SEPARATOR));
    }

    default Map<PatchListEntry, Tuple2<Set<ComponentPath>, Set<ComponentPath>>> renamedEntriesToComponentIndex(
            final @Nonnull MaintainersIndex maintainersIndex, final List<PatchListEntry> patches) {
        return patches.stream()
                // only renames
                .filter(entry -> entry.getChangeType() == Patch.ChangeType.RENAMED)
                .collect(toMap(entry -> entry, maintainersIndex::getComponentPathsForEntry));
    }

    default void sendReviewersInfo(@Nonnull final Set<ComponentReviewInfo> reviewInfoSet,
                                   @Nonnull final Change change,
                                   @Nonnull final ChangesCollection changesCollection,
                                   @Nonnull final Revisions revisions,
                                   @Nonnull final PostReview reviewApi,
                                   @Nonnull final String onBehalfOf) throws OrmException {
        try {
            ChangeResource changeResource = changesCollection.parse(change.getId());
            final RevisionResource revisionResource = revisions.parse(changeResource, IdString.fromUrl("current"));
            ReviewInput review = ReviewInput.noScore().message(formatReviewerInfo(reviewInfoSet));
            review.onBehalfOf = onBehalfOf;

            reviewApi.apply(revisionResource, review);
        } catch (IOException | RestApiException | UpdateException e) {
            throw new IllegalStateException(
                    format("Unable to add reviewers info for patchset %s", change.currentPatchSetId()));
        }
    }

    default void addReviewers(final PostReviewers reviewersApi,
                              final Set<Account.Id> reviewers,
                              final ChangesCollection changes,
                              final Change change) {
        try {
            ChangeResource changeResource = changes.parse(change.getId());
            for (Account.Id accountId : reviewers) {
                AddReviewerInput input = new AddReviewerInput();
                input.reviewer = accountId.toString();
                reviewersApi.apply(changeResource, input);
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Couldn't add reviewers to the change", ex);
        }
    }
}
