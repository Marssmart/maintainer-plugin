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

import static io.fd.maintainer.plugin.service.ComponentReviewInfo.ComponentReviewInfoState.COMPONENT_FOUND;
import static io.fd.maintainer.plugin.service.ComponentReviewInfo.ComponentReviewInfoState.COMPONENT_NOT_FOUND;
import static java.util.Objects.isNull;

import io.fd.maintainer.plugin.parser.Maintainer;
import java.util.Set;

public class ComponentReviewInfo {

    private final String affectedFile;
    private final ComponentReviewInfoState state;
    private final String componentName;
    private final Set<Maintainer> componentMaintainers;

    private ComponentReviewInfo(final String affectedFile, final String componentName,
                                final Set<Maintainer> componentMaintainers) {
        this.affectedFile = affectedFile;
        this.state = isNull(componentName)
                ? COMPONENT_NOT_FOUND
                : COMPONENT_FOUND;
        this.componentName = componentName;
        this.componentMaintainers = componentMaintainers;
    }

    public String getAffectedFile() {
        return affectedFile;
    }

    public String getComponentName() {
        return componentName;
    }

    public Set<Maintainer> getComponentMaintainers() {
        return componentMaintainers;
    }

    public ComponentReviewInfoState getState() {
        return state;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final ComponentReviewInfo that = (ComponentReviewInfo) o;

        if (affectedFile != null
                ? !affectedFile.equals(that.affectedFile)
                : that.affectedFile != null) {
            return false;
        }
        if (state != that.state) {
            return false;
        }
        if (componentName != null
                ? !componentName.equals(that.componentName)
                : that.componentName != null) {
            return false;
        }
        return componentMaintainers != null
                ? componentMaintainers.equals(that.componentMaintainers)
                : that.componentMaintainers == null;
    }

    @Override
    public int hashCode() {
        int result = affectedFile != null
                ? affectedFile.hashCode()
                : 0;
        result = 31 * result + (state != null
                ? state.hashCode()
                : 0);
        result = 31 * result + (componentName != null
                ? componentName.hashCode()
                : 0);
        result = 31 * result + (componentMaintainers != null
                ? componentMaintainers.hashCode()
                : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ComponentReviewInfo{" +
                "affectedFile='" + affectedFile + '\'' +
                ", state=" + state +
                ", componentName='" + componentName + '\'' +
                ", componentMaintainers=" + componentMaintainers +
                '}';
    }

    public enum ComponentReviewInfoState {
        COMPONENT_FOUND,
        COMPONENT_NOT_FOUND;
    }

    public static class ComponentReviewInfoBuilder {
        private String affectedFile;
        private String componentName;
        private Set<Maintainer> componentMaintainers;

        public ComponentReviewInfoBuilder setAffectedFile(final String affectedFile) {
            this.affectedFile = affectedFile;
            return this;
        }

        public ComponentReviewInfoBuilder setComponentName(final String componentName) {
            this.componentName = componentName;
            return this;
        }

        public ComponentReviewInfoBuilder setComponentMaintainers(
                final Set<Maintainer> componentMaintainers) {
            this.componentMaintainers = componentMaintainers;
            return this;
        }

        public ComponentReviewInfo createComponentReviewInfo() {
            return new ComponentReviewInfo(affectedFile, componentName, componentMaintainers);
        }
    }
}
