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

import static java.lang.String.format;
import static java.util.Objects.nonNull;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.fd.maintainer.plugin.parser.ComponentInfo;
import io.fd.maintainer.plugin.parser.MaintainerMismatchException;
import io.fd.maintainer.plugin.parser.MaintainersParser;
import io.fd.maintainer.plugin.service.dto.PluginBranchSpecificSettings;
import io.fd.maintainer.plugin.util.ClosestMatch;
import io.fd.maintainer.plugin.util.PatchListProcessing;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class MaintainersProvider implements ClosestMatch, PatchListProcessing {

    private static final Logger LOG = LoggerFactory.getLogger(MaintainersProvider.class);
    final MaintainersParser maintainersParser;
    @Inject
    private GitRepositoryManager manager;
    @Inject
    private SettingsProvider settingsProvider;
    @Inject
    private SchemaFactory<ReviewDb> schemaFactory;

    public MaintainersProvider() {
        maintainersParser = new MaintainersParser();
    }

    @Nonnull
    public List<ComponentInfo> getMaintainersInfo(@Nonnull final String branchName, final int changeNumber) {

        // get configuration for branch of change
        final PluginBranchSpecificSettings settings = settingsProvider.getBranchSpecificSettings(branchName);

        try (final ReviewDb reviewDb = schemaFactory.open()) {
            final Change change = reviewDb.changes().get(new Change.Id(changeNumber));
            final String fullFileRef = settings.fullFileRef();

            try (final Repository repository = manager.openRepository(change.getProject())) {

                final Ref ref = Optional.ofNullable(repository.findRef(fullFileRef))
                        .orElseThrow(() -> new IllegalStateException(
                                format("Unable to get ref %s", fullFileRef)));

                final RevCommit revCommit = new RevWalk(repository).parseCommit(ref.getObjectId());

                final String maintainersFileContent =
                        findMostRecentMaintainersChangeContent(settings.getLocalFilePath(), repository,
                                new RevWalk(repository), revCommit);

                if (nonNull(maintainersFileContent)) {
                    return maintainersParser.parseMaintainers(maintainersFileContent);
                } else {
                    throw new IllegalStateException(
                            format("Unable to find file %s in branch %s", settings.getLocalFilePath(),
                                    fullFileRef));
                }
            } catch (IOException | MaintainerMismatchException e) {
                throw new IllegalStateException(e);
            }

        } catch (OrmException e) {
            throw new IllegalStateException(e);
        }
    }

    // skips head commit
    private String findMostRecentMaintainersChangeContent(
            final String maintainersFileName,
            final Repository repository,
            final RevWalk revWalk,
            final RevCommit headCommit) {
        LOG.info("Starting search at {}", headCommit);

        final RevCommit parent = getRevCommit(revWalk, headCommit.getParent(0).getId());
        LOG.info("Finding most recent maintainers file in {}", parent);


        final String parentIdName = parent.getId().getName();
        LOG.info("Parent id name {}", parentIdName);

        try (TreeWalk treeWalk = new TreeWalk(repository)) {
            treeWalk.addTree(parent.getTree());
            treeWalk.setRecursive(true);
            treeWalk.setFilter(PathFilter.create(maintainersFileName));
            LOG.info("Attempting to find {}", maintainersFileName);

            if (treeWalk.next()) {
                LOG.info("Maintainers file found in commit {}", parent.getId());
                ObjectId objectId = treeWalk.getObjectId(0);
                ObjectLoader loader = repository.open(objectId);

                // and then one can the loader to read the file
                final ByteArrayOutputStream out = new ByteArrayOutputStream();
                loader.copyTo(out);
                revWalk.dispose();
                return new String(out.toByteArray());
            }

            LOG.info("Maintainers file not found in commit {}, going deep", parent.getId());
            if (parent.getParents() == null) {
                throw new IllegalStateException(format("Root of branch reached with commit %s", parent));
            }
            return findMostRecentMaintainersChangeContent(maintainersFileName, repository, revWalk, parent);
        } catch (IOException e) {
            throw new IllegalStateException(format("Unable to detect maintainers file in %s", parent.getId()));
        }
    }

    private RevCommit getRevCommit(final RevWalk revWalk, final ObjectId id) {
        try {
            return revWalk.parseCommit(id);
        } catch (IOException e) {
            throw new IllegalStateException(format("Unable to parse commit %s", id));
        }
    }
}
