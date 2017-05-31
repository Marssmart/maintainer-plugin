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

package io.fd.maintainer.plugin.service.push;

import static io.fd.maintainer.plugin.service.ComponentReviewInfo.ComponentReviewInfoState.COMPONENT_FOUND;
import static java.util.stream.Collectors.toMap;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.change.ChangesCollection;
import com.google.gerrit.server.change.PostReview;
import com.google.gerrit.server.change.PostReviewers;
import com.google.gerrit.server.change.Revisions;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import io.fd.maintainer.plugin.parser.Maintainer;
import io.fd.maintainer.plugin.service.ComponentReviewInfo;
import io.fd.maintainer.plugin.util.CommonTasks;
import io.fd.maintainer.plugin.util.MaintainersIndex;
import io.fd.maintainer.plugin.util.PatchListProcessing;
import io.fd.maintainer.plugin.util.WarningGenerator;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ReviewerPusher implements WarningGenerator, PatchListProcessing, CommonTasks {

    private static final Logger LOG = LoggerFactory.getLogger(ReviewerPusher.class);

    @Inject
    private ChangesCollection changesCollection;

    @Inject
    private Provider<PostReviewers> reviewersProvider;

    @Inject
    private Provider<PostReview> reviewProvider;

    @Inject
    private PatchListCache patchListCache;

    @Inject
    private Revisions revisions;

    @Inject
    private SchemaFactory<ReviewDb> schemaFactory;


    public void addRelevantReviewers(@Nonnull final MaintainersIndex maintainersIndex,
                                     @Nonnull final Change change,
                                     @Nonnull final PatchSet mostCurrentPatchSet,
                                     @Nonnull final String onBehalfOf) throws OrmException {

        final Set<ComponentReviewInfo> reviewInfoSet =
                getRelevantPatchListEntries(getPatchList(patchListCache, change, mostCurrentPatchSet))
                        .stream()
                        .map(this::getRelevantChangeName)
                        .map(maintainersIndex::getReviewInfoForPath)
                        .collect(Collectors.toSet());

        final ReviewDb reviewDb = schemaFactory.open();
        final Map<String, Account.Id> accountIndex = reviewDb.accounts().all().toList().stream()
                .collect(toMap(Account::getFullName, Account::getId));

        final Set<Account.Id> reviewersToBeAdded = reviewInfoSet.stream()
                .filter(reviewInfo -> reviewInfo.getState() == COMPONENT_FOUND)
                .map(ComponentReviewInfo::getComponentMaintainers)
                .flatMap(Collection::stream)
                .map(Maintainer::getName)
                .map(accountIndex::get)
                .collect(Collectors.toSet());

        LOG.info("Adding reviewers for change {}", change.getId());
        addReviewers(reviewersProvider.get(), reviewersToBeAdded, changesCollection, change);
        sendReviewersInfo(reviewInfoSet, change, changesCollection, revisions, reviewProvider.get(), onBehalfOf);
    }
}
