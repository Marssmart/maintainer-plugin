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


import static com.google.common.collect.ImmutableSet.of;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.junit.Test;

public class MaintainersParserTest {

    private static ComponentInfo vnetBfd() {
        return componentNoComment("VNET Bidirectonal Forwarding Detection (BFD)",
                of(m("Klement Sekera", "ksekera@cisco.com")),
                of(p("src/vnet/bfd/")));
    }

    private static ComponentInfo vlibApiLibraries() {
        return componentNoComment("VLIB API Libraries",
                of(m("Dave Barach", "dave@barachs.net")),
                of(p("src/vlibapi/"), p("src/vlibmemory/"), p("src/vlibsocket/")));
    }

    private static ComponentInfo vlibLibrary() {
        return componentNoComment("VLIB Library",
                of(m("Damjan Marion", "damarion@cisco.com"),
                        m("Dave Barach", "dave@barachs.net")),
                of(p("src/vlib/")));
    }

    private static ComponentInfo infrastractureLibrary() {
        return componentNoComment("Infrastructure Library",
                of(m("Dave Barach", "dave@barachs.net")),
                of(p("src/vppinfra/")));
    }

    private static ComponentInfo dpdkDevelopmentPackaging() {
        return componentNoComment("DPDK Development Packaging",
                of(m("Damjan Marion", "damarion@cisco.com")),
                of(p("dpdk/"), p("dpdk/*")));
    }

    private static ComponentInfo doxygen() {
        return componentNoComment("Doxygen",
                of(m("Chris Luke", "chrisy@flirble.org")),
                of(p("doxygen/")));
    }

    private static ComponentInfo buildSystemInternal() {
        return componentNoComment("Build System Internal",
                of(m("Dave Barach", "dave@barachs.net")),
                of(p("build-root/Makefile"), p("build-data/*")));
    }

    private static ComponentInfo buildSystem() {
        return componentNoComment("Build System",
                of(m("Damjan Marion", "damarion@cisco.com")),
                of(p("Makefile"), p("src/*.ac"), p("src/*.am"), p("src/*.mk"), p("src/m4/")));
    }

    private static Maintainer m(final String name, final String mail) {
        return new Maintainer(name, mail);
    }

    private static ComponentPath p(final String path) {
        return new ComponentPath(path);
    }

    private static ComponentInfo componentNoComment(final String componentTitle,
                                                    final Set<Maintainer> maintainers,
                                                    final Set<ComponentPath> components) {
        return new ComponentInfo.ComponentInfoBuilder()
                .setTitle(componentTitle)
                .setMaintainers(maintainers)
                .setPaths(components)
                .createMaintainer();
    }

    @Test
    public void testParse() throws URISyntaxException, IOException, MaintainerMismatchException {
        final MaintainersParser parser = new MaintainersParser();

        final URL url = this.getClass().getResource("/maintainers");
        final String content =
                Files.readLines(new File(url.toURI()), StandardCharsets.UTF_8).stream()
                        .collect(Collectors.joining(System.lineSeparator()));
        final List<ComponentInfo> maintainers = parser.parseMaintainers(content);
        assertTrue(!maintainers.isEmpty());

        // tests couple of entries
        assertTrue(compare(maintainers.get(0), buildSystem()));
        assertTrue(compare(maintainers.get(1), buildSystemInternal()));
        assertTrue(compare(maintainers.get(2), doxygen()));
        assertTrue(compare(maintainers.get(3), dpdkDevelopmentPackaging()));
        assertTrue(compare(maintainers.get(4), infrastractureLibrary()));
        assertTrue(compare(maintainers.get(5), vlibLibrary()));
        assertTrue(compare(maintainers.get(6), vlibApiLibraries()));
        assertTrue(compare(maintainers.get(7), vnetBfd()));
        assertEquals(32, maintainers.size());
    }

    private boolean compare(final ComponentInfo first, final ComponentInfo second) {
        return new EqualsBuilder()
                .append(first.getTitle(), second.getTitle())
                .append(true, first.getMaintainers().containsAll(second.getMaintainers()))
                .append(true, second.getMaintainers().containsAll(first.getMaintainers()))
                .append(true, first.getPaths().containsAll(second.getPaths()))
                .append(true, second.getPaths().containsAll(first.getPaths()))
                .append(true, first.getComments().containsAll(second.getComments()))
                .append(true, second.getComments().containsAll(first.getComments()))
                .build();
    }
}
