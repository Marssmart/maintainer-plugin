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

package io.fd.maintainer.plugin;

import com.google.gerrit.common.EventListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.inject.AbstractModule;
import io.fd.maintainer.plugin.events.OnCommittersToBeAddedListener;
import io.fd.maintainer.plugin.events.OnPatchsetVerifiedListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MaintainerPluginModule extends AbstractModule {

    private static final Logger LOG = LoggerFactory.getLogger(MaintainerPluginModule.class);

    @Override
    protected void configure() {
        LOG.info("Configuring ComponentInfo plugin module");
        DynamicSet.bind(binder(), EventListener.class).to(OnCommittersToBeAddedListener.class);
        DynamicSet.bind(binder(), EventListener.class).to(OnPatchsetVerifiedListener.class);
    }
}
