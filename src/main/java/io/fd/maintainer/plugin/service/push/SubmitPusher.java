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

import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.ChangesCollection;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.change.Revisions;
import com.google.gerrit.server.change.Submit;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import javax.annotation.Nonnull;

@Singleton
public class SubmitPusher {

    @Inject
    private Submit submitApi;

    @Inject
    private ChangesCollection changesCollection;

    @Inject
    private Revisions revisions;

    public void submitPatch(@Nonnull final Change change,
                            @Nonnull final String onBehalfOf) {
        SubmitInput request = new SubmitInput();
        request.onBehalfOf = onBehalfOf;

        try {
            ChangeResource changeResource = changesCollection.parse(change.getId());
            final RevisionResource revisionResource = revisions.parse(changeResource, IdString.fromUrl("current"));
            submitApi.apply(revisionResource, request);
        } catch (OrmException | RestApiException | IOException e) {
            throw new IllegalStateException(format("Unable to submit change %s", change.getId()));
        }
    }
}
