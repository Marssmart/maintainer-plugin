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

import static java.lang.String.format;

import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.ChangesCollection;
import com.google.gerrit.server.change.PostReview;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.change.Revisions;
import com.google.gerrit.server.update.UpdateException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ApprovalPusher {

    private static final Logger LOG = LoggerFactory.getLogger(ApprovalPusher.class);

    @Inject
    private ChangesCollection changes;

    @Inject
    private Revisions revisions;

    @Inject
    private Provider<PostReview> reviewProvider;

    public void approvePatchset(@Nonnull final Change change,
                                @Nonnull final PatchSet patchSet,
                                @Nonnull final String onBehalfOf) {
        try {
            ChangeResource changeResource = changes.parse(change.getId());
            final RevisionResource revisionResource = revisions.parse(changeResource, IdString.fromUrl("current"));

            final PostReview post = reviewProvider.get();

            ReviewInput review =
                    ReviewInput.approve()
                            .message(format(" All relevant component maintainers verified patchset %s",
                                    patchSet.getPatchSetId()));// review +2
            review.onBehalfOf = onBehalfOf;

            post.apply(revisionResource, review);

        } catch (OrmException | IOException | RestApiException | UpdateException e) {
            LOG.error("Unable to approve patchset {}", patchSet.getId(),
                    e);
            return;
        }
        LOG.info("Patchset {} successfully approved", patchSet.getId());
    }
}
