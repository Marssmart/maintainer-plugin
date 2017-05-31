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

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final Maintainer that = (Maintainer) o;

        if (name != null
                ? !name.equals(that.name)
                : that.name != null) {
            return false;
        }
        return email != null
                ? email.equals(that.email)
                : that.email == null;
    }

    @Override
    public int hashCode() {
        int result = name != null
                ? name.hashCode()
                : 0;
        result = 31 * result + (email != null
                ? email.hashCode()
                : 0);
        return result;
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
