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
import io.fd.maintainer.plugin.util.CommonTasks;
import io.fd.maintainer.plugin.util.WarningGenerator;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class WarningPusher implements CommonTasks {

    private static final Logger LOG = LoggerFactory.getLogger(WarningPusher.class);

    @Inject
    private ChangesCollection changesCollection;

    @Inject
    private Revisions revisions;

    @Inject
    private Provider<PostReview> reviewProvider;

    private static String formatComments(final Set<WarningGenerator.ComponentChangeWarning> comments) {
        return "Following entries are now no longer part of their components. Maintainers file update is recommended."
                + LINE_SEPARATOR + LINE_SEPARATOR
                + comments.stream()
                .map(warning -> format("File %s renamed to %s - invalid components %s", warning.getOldName(),
                        warning.getNewName(), warning.getInvalidComponents()
                                .stream().map(componentWithPath -> format("Component %s[path=%s]",
                                        componentWithPath.getComponentTitle(), componentWithPath.getPath().getPath()))
                                .collect(Collectors.toSet())))
                .collect(Collectors.joining(LINE_SEPARATOR));
    }

    public void sendWarnings(@Nonnull final Set<ComponentChangeWarning> comments,
                             @Nonnull final Change change,
                             @Nonnull final PatchSet patchSet,
                             @Nonnull final String onBehalfOf) throws OrmException {
        if (comments.isEmpty()) {
            LOG.warn("No warnings");
            return;
        }

        try {
            ChangeResource changeResource = changesCollection.parse(change.getId());
            final RevisionResource revisionResource = revisions.parse(changeResource, IdString.fromUrl("current"));

            ReviewInput review = ReviewInput.dislike()
                    .message(formatComments(comments));// review -1
            review.onBehalfOf = onBehalfOf;

            reviewProvider.get().apply(revisionResource, review);
        } catch (IOException | RestApiException | UpdateException e) {
            throw new IllegalStateException(
                    format("Unable to add warning comments for change %s / patchset %s", change.getId(),
                            patchSet.getId()), e);
        }
    }
}
