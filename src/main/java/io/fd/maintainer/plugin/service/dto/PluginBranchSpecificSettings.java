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

package io.fd.maintainer.plugin.service.dto;

import com.google.gerrit.reviewdb.client.RefNames;

public class PluginBranchSpecificSettings {

    private static final String HEAD_PART = "/" + RefNames.HEAD;

    private final String pluginUserName;
    private final String branch;
    private final String fileRef;
    private final String localFilePath;
    private final boolean allowMaintainersSubmit;
    private final boolean autoAddReviewers;
    private final boolean autoSubmit;

    private PluginBranchSpecificSettings(final String pluginUserName,
                                         final String branch,
                                         final String fileRef,
                                         final String localFilePath,
                                         final boolean allowMaintainersSubmit,
                                         final boolean autoAddReviewers,
                                         final boolean autoSubmit) {
        this.pluginUserName = pluginUserName;
        this.branch = branch;
        this.fileRef = fileRef;
        this.localFilePath = localFilePath;
        this.allowMaintainersSubmit = allowMaintainersSubmit;
        this.autoAddReviewers = autoAddReviewers;
        this.autoSubmit = autoSubmit;
    }

    public String getFileRef() {
        return fileRef;
    }

    public String getLocalFilePath() {
        return localFilePath;
    }

    public boolean isAllowMaintainersSubmit() {
        return allowMaintainersSubmit;
    }

    public boolean isAutoAddReviewers() {
        return autoAddReviewers;
    }

    public boolean isAutoSubmit() {
        return autoSubmit;
    }

    public String fullFileRef() {
        return branch.concat(fileRef);
    }

    public String getPluginUserName() {
        return pluginUserName;
    }

    @Override
    public String toString() {
        return "PluginBranchSpecificSettings{" +
                "branch='" + branch + '\'' +
                ", fileRef='" + fileRef + '\'' +
                ", localFilePath='" + localFilePath + '\'' +
                ", allowMaintainersSubmit=" + allowMaintainersSubmit +
                ", autoAddReviewers=" + autoAddReviewers +
                '}';
    }

    public static class PluginSettingsBuilder {
        private String pluginUserName;
        private String branch;
        private String fileRef;
        private String localFilePath;
        private boolean allowMaintainersSubmit;
        private boolean autoAddReviewers;
        private boolean autoSubmit;

        private static String reduceWildcard(String input) {
            return input.contains("*")
                    ? input.substring(0, input.indexOf("*"))
                    : input;
        }

        private static String addEndSlash(String input) {
            return input.endsWith("/")
                    ? input
                    : input.concat("/");
        }

        public PluginSettingsBuilder setPluginUserName(final String pluginUserName) {
            this.pluginUserName = pluginUserName;
            return this;
        }

        public PluginSettingsBuilder setFileRef(final String fileRef) {
            // TODO - remove this replace if configuration will be changed
            this.fileRef = fileRef.replace(HEAD_PART, "");
            return this;
        }

        public PluginSettingsBuilder setLocalFilePath(final String localFilePath) {
            this.localFilePath = localFilePath;
            return this;
        }

        public PluginSettingsBuilder setAllowMaintainersSubmit(final boolean allowMaintainersSubmit) {
            this.allowMaintainersSubmit = allowMaintainersSubmit;
            return this;
        }

        public PluginSettingsBuilder setAutoAddReviewers(final boolean autoAddReviewers) {
            this.autoAddReviewers = autoAddReviewers;
            return this;
        }

        public PluginSettingsBuilder setBranch(final String branch) {
            this.branch = addEndSlash(reduceWildcard(branch));
            return this;
        }

        public PluginSettingsBuilder setAutoSubmit(final boolean autoSubmit) {
            this.autoSubmit = autoSubmit;
            return this;
        }

        public PluginBranchSpecificSettings createPluginSettings() {
            return new PluginBranchSpecificSettings(pluginUserName, branch, fileRef, localFilePath,
                    allowMaintainersSubmit, autoAddReviewers, autoSubmit);
        }
    }
}
