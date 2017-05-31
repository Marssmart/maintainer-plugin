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

package io.fd.maintainer.plugin.service;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.server.patch.PatchList;
import io.fd.maintainer.plugin.parser.ComponentPath;
import io.fd.maintainer.plugin.util.MaintainersIndex;
import io.fd.maintainer.plugin.util.PatchListProcessing;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.parboiled.common.Tuple2;

public class PatchsetReviewInfo implements PatchListProcessing {

    private final ReviewState reviewState;
    private final Set<String> missingComponentReview;

    public PatchsetReviewInfo(@Nonnull final MaintainersIndex index,
                              @Nonnull final PatchList patchList,
                              @Nonnull final Set<Account> currentVerificationAuthors) {
        final Set<String> componentsForPatchlist = getRelevantPatchListEntries(patchList)
                .stream()
                .map(patchListEntry -> {
                    final Tuple2<Set<ComponentPath>, Set<ComponentPath>> componentTuple =
                            index.getComponentPathsForEntry(patchListEntry);
                    if (getRelevantChangeName(patchListEntry).equals(patchListEntry.getOldName())) {
                        return componentTuple.a;
                    } else {
                        return componentTuple.b;
                    }
                })
                .flatMap(Collection::stream)
                .map(index::getComponentForPath)
                .filter(index::isReviewComponent)
                .collect(Collectors.toSet());
        final Set<String> componentsCurrentlyReviewed = currentVerificationAuthors.stream()
                .map(account -> index.getComponentsForMaintainer(account.getFullName()))
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());

        if (componentsCurrentlyReviewed.containsAll(componentsForPatchlist)) {
            reviewState = ReviewState.ALL_COMPONENTS_REVIEWED;
            missingComponentReview = Collections.emptySet();
        } else {
            reviewState = ReviewState.MISSING_COMPONENT_REVIEW;
            missingComponentReview = componentsForPatchlist.stream()
                    .filter(component -> !componentsCurrentlyReviewed.contains(component))
                    .collect(Collectors.toSet());
        }
    }

    public ReviewState getReviewState() {
        return reviewState;
    }

    public Set<String> getMissingComponentReview() {
        return missingComponentReview;
    }

    public enum ReviewState {
        ALL_COMPONENTS_REVIEWED,
        MISSING_COMPONENT_REVIEW;
    }
}
