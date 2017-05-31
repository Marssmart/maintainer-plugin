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

package io.fd.maintainer.plugin.parser;


public class Maintainer {

    private final String name;
    private final String email;

    public Maintainer(final String name, final String email) {
        this.name = name;
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    @Override
    public String toString() {
        return "Maintainer{" +
                "name='" + name + '\'' +
                ", email='" + email + '\'' +
                '}';
    }

    public static class MaintainerBuilder {
        private String name;
        private String email;

        public MaintainerBuilder setName(final String name) {
            this.name = name;
            return this;
        }

        public MaintainerBuilder setEmail(final String email) {
            this.email = email;
            return this;
        }

        public Maintainer createMaintainer() {
            return new Maintainer(name, email);
        }
    }
}
