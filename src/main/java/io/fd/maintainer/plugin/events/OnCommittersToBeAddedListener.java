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

package io.fd.maintainer.plugin.events;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.change.ChangesCollection;
import com.google.gerrit.server.change.PostReview;
import com.google.gerrit.server.change.PostReviewers;
import com.google.gerrit.server.change.Revisions;
import com.google.gerrit.server.data.ChangeAttribute;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.PatchSetCreatedEvent;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListEntry;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;
import io.fd.maintainer.plugin.parser.ComponentPath;
import io.fd.maintainer.plugin.service.MaintainersProvider;
import io.fd.maintainer.plugin.service.SettingsProvider;
import io.fd.maintainer.plugin.service.dto.PluginBranchSpecificSettings;
import io.fd.maintainer.plugin.service.push.ReviewerPusher;
import io.fd.maintainer.plugin.service.push.WarningPusher;
import io.fd.maintainer.plugin.util.CommonTasks;
import io.fd.maintainer.plugin.util.MaintainersIndex;
import io.fd.maintainer.plugin.util.WarningGenerator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.parboiled.common.Tuple2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Filters out events that should cause assignment of committers to patch
 */
public class OnCommittersToBeAddedListener extends SelfDescribingEventListener implements CommonTasks {

    private static final Logger LOG = LoggerFactory.getLogger(OnCommittersToBeAddedListener.class);

    @Inject
    private SchemaFactory<ReviewDb> schemaFactory;

    @Inject
    private PatchListCache patchListCache;

    @Inject
    private MaintainersProvider maintainersProvider;

    @Inject
    private ChangesCollection changes;

    @Inject
    private Revisions revisions;

    @Inject
    private Provider<PostReviewers> reviewersProvider;

    @Inject
    private Provider<PostReview> reviewProvider;

    @Inject
    private SettingsProvider settingsProvider;

    @Inject
    private ReviewerPusher reviewerPusher;

    @Inject
    private WarningPusher warningPusher;

    OnCommittersToBeAddedListener() {

    }

    @Override
    protected void consumeDescribedEvent(final Event event) {
        final PatchSetCreatedEvent patchSetCreatedEvent = PatchSetCreatedEvent.class.cast(event);

        final ChangeAttribute changeAttributes = patchSetCreatedEvent.change.get();
        final PluginBranchSpecificSettings settings =
                settingsProvider.getBranchSpecificSettings(changeAttributes.branch);

        if (!settings.isAutoAddReviewers()) {
            LOG.warn("Auto add reviewers option turned off");
            return;
        }

        try (final ReviewDb reviewDb = schemaFactory.open()) {
            final Change.Id changeId = new Change.Id(changeAttributes.number);
            final Change change = reviewDb.changes().get(changeId);

            final PatchSet mostCurrentPatchSet = reviewDb.patchSets().get(change.currentPatchSetId());

            LOG.info("Processing change {} | patchset {}", change.getId(), mostCurrentPatchSet.getId());
            final MaintainersIndex index =
                    new MaintainersIndex(
                            maintainersProvider.getMaintainersInfo(changeAttributes.branch, changeAttributes.number));

            reviewerPusher.addRelevantReviewers(index, change, mostCurrentPatchSet, settings.getPluginUserName());
            LOG.info("Reviewers for change {} successfully added", change.getId());

            final PatchList patchList = getPatchList(patchListCache, change, mostCurrentPatchSet);
            final List<PatchListEntry> patches = getRelevantPatchListEntries(patchList);

            final Map<PatchListEntry, Tuple2<Set<ComponentPath>, Set<ComponentPath>>> renamedEntryToComponentsIndex =
                    renamedEntriesToComponentIndex(index, patches);

            final Set<WarningGenerator.ComponentChangeWarning> warnings =
                    generateComponentChangeWarnings(index, renamedEntryToComponentsIndex);
            warningPusher.sendWarnings(warnings, change, mostCurrentPatchSet, settings.getPluginUserName());
            LOG.info("Warnings for change {} successfully added", change.getId());
        } catch (OrmException e) {
            throw new IllegalStateException("Unable to open review DB", e);
        }
        LOG.info("Change {} successfully processed", patchSetCreatedEvent.changeKey);
    }

    @Override
    protected boolean canConsume(final Event event) {
        return event instanceof PatchSetCreatedEvent;
    }
}
