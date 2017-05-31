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

import com.google.gerrit.common.EventListener;
import com.google.gerrit.server.events.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Event listener that automatically describes event that it received
 */
public abstract class SelfDescribingEventListener implements EventListener {

    private static final Logger LOG = LoggerFactory.getLogger(SelfDescribingEventListener.class);

    @Override
    public void onEvent(final Event event) {
        if (canConsume(event)) {
            LOG.info("Event[type={},created={}] has been triggered, consuming ...", event.getType(),
                    event.eventCreatedOn);
            consumeDescribedEvent(event);
            LOG.info("Event[type={},created={}] successfully processed", event.getType(), event.eventCreatedOn);
        }
    }

    /**
     * Consumes event that has been already described
     */
    protected abstract void consumeDescribedEvent(final Event event);

    /**
     * Returns true if listener can consume following type of event
     */
    protected abstract boolean canConsume(final Event event);
}
