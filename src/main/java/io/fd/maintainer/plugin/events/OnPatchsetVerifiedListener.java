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

import static io.fd.maintainer.plugin.service.PatchsetReviewInfo.ReviewState.ALL_COMPONENTS_REVIEWED;
import static java.lang.String.format;

import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.change.ChangesCollection;
import com.google.gerrit.server.data.AccountAttribute;
import com.google.gerrit.server.data.ApprovalAttribute;
import com.google.gerrit.server.events.CommentAddedEvent;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import io.fd.maintainer.plugin.service.MaintainersProvider;
import io.fd.maintainer.plugin.service.PatchsetReviewInfo;
import io.fd.maintainer.plugin.service.SettingsProvider;
import io.fd.maintainer.plugin.service.dto.PluginBranchSpecificSettings;
import io.fd.maintainer.plugin.service.push.ApprovalPusher;
import io.fd.maintainer.plugin.service.push.SubmitPusher;
import io.fd.maintainer.plugin.util.MaintainersIndex;
import io.fd.maintainer.plugin.util.PatchListProcessing;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OnPatchsetVerifiedListener extends SelfDescribingEventListener implements PatchListProcessing {

    private static final Logger LOG = LoggerFactory.getLogger(OnPatchsetVerifiedListener.class);

    @Inject
    private SchemaFactory<ReviewDb> schemaFactory;

    @Inject
    private MaintainersProvider maintainersProvider;

    @Inject
    private PatchListCache patchListCache;

    @Inject
    private ChangesCollection changes;

    @Inject
    private SettingsProvider settingsProvider;

    @Inject
    private ApprovalPusher approvalPusher;

    @Inject
    private SubmitPusher submitPusher;

    private static String formatUser(final AccountAttribute author) {
        return format("%s(%s)<%s>", author.name, author.username, author.email);
    }

    @Override
    protected void consumeDescribedEvent(final Event event) {
        CommentAddedEvent commentAddedEvent = CommentAddedEvent.class.cast(event);

        final PluginBranchSpecificSettings settings =
                settingsProvider.getBranchSpecificSettings(commentAddedEvent.change.get().branch);

        if (!settings.isAllowMaintainersSubmit()) {
            LOG.warn("Maintainers submit is turned off");
            return;
        }

        final Optional<ApprovalAttribute> patchSetVerification = getPatchListVerifications(commentAddedEvent);

        // patchset has been +1
        if (patchSetVerification.isPresent()) {
            LOG.info("User {} just verified change {}", formatUser(commentAddedEvent.author.get()),
                    commentAddedEvent.changeKey.get());

            try (final ReviewDb reviewDb = schemaFactory.open()) {
                final int changeNumber = commentAddedEvent.change.get().number;
                final Change.Id changeId = new Change.Id(changeNumber);
                final Change change = reviewDb.changes().get(changeId);

                final PatchSet currentPatchset = reviewDb.patchSets().get(change.currentPatchSetId());
                final PatchSet.Id currentPatchsetId = currentPatchset.getId();

                final int currentPatchsetNr = currentPatchset.getPatchSetId();
                final int processedPatchsetNr = commentAddedEvent.patchSet.get().number;

                // to filter out reviews on older patchsets
                if (currentPatchsetNr != processedPatchsetNr) {
                    LOG.warn("Event for older patchset {}, most current {}, ignoring", processedPatchsetNr,
                            currentPatchsetNr);
                } else {
                    final List<PatchSetApproval> currentPatchsetVerifications = getPatchListCurrentVerifications(
                            reviewDb.patchSetApprovals().byChange(changeId).toList(),
                            currentPatchsetId);

                    if (currentPatchsetVerifications.isEmpty()) {
                        LOG.warn("No verifications found for patchset {}", currentPatchset.getId());
                    } else {
                        LOG.info("Building maintainers index for patchset {}", currentPatchset.getId());
                        final MaintainersIndex maintainersIndex =
                                new MaintainersIndex(maintainersProvider
                                        .getMaintainersInfo(commentAddedEvent.getBranchNameKey().get(),
                                                changeNumber));

                        LOG.info("Getting current patch list for patchset {}", currentPatchset.getId());
                        final PatchList patchList = getPatchList(patchListCache, change, currentPatchset);

                        LOG.info("Getting current reviewers for patchset {}", currentPatchset.getId());
                        final HashSet<Account> currentVerificators =
                                new HashSet<>(reviewDb.accounts().get(currentPatchsetVerifications
                                        .stream()
                                        .map(PatchSetApproval::getAccountId)
                                        .collect(Collectors.toSet())).toList());

                        LOG.info("Getting patch review info for patchset {}", currentPatchset.getId());
                        // Note that you only need one MAINTAINER per component.
                        // Also note a single reviewer may be a MAINTAINER for multiple components
                        final PatchsetReviewInfo patchsetReviewInfo =
                                new PatchsetReviewInfo(maintainersIndex, patchList, currentVerificators);

                        if (patchsetReviewInfo.getReviewState() == ALL_COMPONENTS_REVIEWED) {
                            LOG.info("All relevant component reviewers verified patchset {}", currentPatchset.getId());
                            approvalPusher.approvePatchset(change, currentPatchset, settings.getPluginUserName());

                            if (settings.isAutoSubmit()) {
                                LOG.info("Submitting change {}", change.getId());
                                submitPusher.submitPatch(change, settings.getPluginUserName());
                            } else {
                                LOG.warn("Auto submit turned off");
                            }
                        } else {
                            LOG.info(
                                    "Patchset {} does not have verifications from following components yet : {}",
                                    currentPatchset.getId(), patchsetReviewInfo.getMissingComponentReview());
                        }
                    }
                }
            } catch (OrmException e) {
                LOG.error("Error accessing review DB", e);
                throw new IllegalStateException(e);
            }
        }
    }

    @Override
    protected boolean canConsume(final Event event) {
        return event instanceof CommentAddedEvent;
    }
}
