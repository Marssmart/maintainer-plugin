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

import static com.google.gerrit.reviewdb.client.Patch.COMMIT_MSG;
import static java.lang.String.format;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.server.data.ApprovalAttribute;
import com.google.gerrit.server.events.CommentAddedEvent;
import com.google.gerrit.server.patch.PatchList;
import com.google.gerrit.server.patch.PatchListCache;
import com.google.gerrit.server.patch.PatchListEntry;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;

/**
 * Common logic for patchlist processing
 */
public interface PatchListProcessing {

    String CODE_REVIEW_LABEL = "Code-Review";
    int PATCHSET_VERIFIED = 1;

    static boolean isCodeReview(final PatchSetApproval approval) {
        return CODE_REVIEW_LABEL.equals(approval.getLabel());
    }

    static boolean isVerifyPatchset(final PatchSetApproval approval) {
        return PATCHSET_VERIFIED == (int) approval.getValue();
    }

    static boolean isCodeReview(final ApprovalAttribute approvalAttribute) {
        return CODE_REVIEW_LABEL.equals(approvalAttribute.type);
    }

    static boolean isVerifyPatchset(final ApprovalAttribute approvalAttribute) {
        return PATCHSET_VERIFIED == Integer.valueOf(approvalAttribute.value);
    }

    /**
     * Gets relevant patch list entries for processing
     */
    default List<PatchListEntry> getRelevantPatchListEntries(@Nonnull final PatchList patchList) {
        return patchList.getPatches().stream()
                // filters out commit msg
                .filter(entry -> !COMMIT_MSG.equals(entry.getNewName()))
                .collect(Collectors.toList());
    }

    /**
     * By design of plugin, matching of files per component should be done by old name.
     * Only in case of create of new file ,new name is used
     */
    default String getRelevantChangeName(@Nonnull final PatchListEntry entry) {
        return entry.getOldName() == null
                ? entry.getNewName()
                : entry.getOldName();
    }

    /**
     * Attempts to find patchset changes in cache
     */
    default PatchList getPatchList(@Nonnull final PatchListCache patchListCache,
                                   @Nonnull final Change change,
                                   @Nonnull final PatchSet mostCurrentPatchSet) {
        try {
            return patchListCache.get(change, mostCurrentPatchSet);
        } catch (PatchListNotAvailableException e) {
            throw new IllegalStateException(
                    format("Unable to get patchlist for patchset %s", mostCurrentPatchSet.getId()), e);
        }
    }

    /**
     * Filters out only approvals that are labeled Code-Review+1
     */
    default Optional<ApprovalAttribute> getPatchListVerifications(final CommentAddedEvent commentAddedEvent) {
        return Arrays.stream(commentAddedEvent.approvals.get())
                .filter(PatchListProcessing::isCodeReview)
                .filter(PatchListProcessing::isVerifyPatchset)
                .findFirst();
    }

    default List<PatchSetApproval> getPatchListCurrentVerifications(final List<PatchSetApproval> patchSetApprovals,
                                                                    final PatchSet.Id currentPatchsetId) {
        return patchSetApprovals.stream()
                .filter(approval -> approval.getPatchSetId().equals(currentPatchsetId))
                .filter(PatchListProcessing::isCodeReview)
                .filter(PatchListProcessing::isVerifyPatchset)
                .collect(Collectors.toList());
    }
}
