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

import static com.google.common.base.Preconditions.checkState;
import static java.lang.String.format;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;


public final class MaintainersParser {

    private static final String HEADER_SPLITTER = "-----";
    private static final String MAINTAINER_TOKEN = "M:";
    private static final String FILEPATH_TOKEN = "F:";
    private static final String COMMENT_TOKEN = "C:";
    private static final String EMAIL_START_TOKEN = "<";
    private static final String EMAIL_END_TOKEN = ">";

    private static ComponentInfo parseBlock(final Set<String> blockLines) throws MaintainerMismatchException {
        checkState(blockLines.size() >= 3, "Unable to parse block from %s", blockLines);

        String title = null;
        Set<Maintainer> maintainers = new HashSet<>();
        Set<ComponentPath> paths = new HashSet<>();
        Set<String> comments = new HashSet<>();

        for (String line : blockLines) {
            if (line.startsWith(MAINTAINER_TOKEN)) {
                maintainers.add(new Maintainer.MaintainerBuilder()
                        .setName(extractMaintainer(line))
                        .setEmail(extractEmail(line))
                        .createMaintainer());
                continue;
            }

            if (line.startsWith(FILEPATH_TOKEN)) {
                paths.add(new ComponentPath(extractComponentPath(line)));
                continue;
            }

            if (line.startsWith(COMMENT_TOKEN)) {
                comments.add(line);
                continue;
            }

            if (title != null) {
                throw new MaintainerMismatchException(format("Multiple title specified for block %s", blockLines));
            }

            title = line.trim();
        }

        return new ComponentInfo.ComponentInfoBuilder()
                .setTitle(title)
                .setMaintainers(maintainers)
                .setPaths(paths)
                .setComments(comments)
                .createMaintainer();
    }

    // raw input in format : M:	Name Surname <example@example.com>
    private static String extractMaintainer(final String rawString) {
        return rawString.substring(0, rawString.indexOf(EMAIL_START_TOKEN)).replace(MAINTAINER_TOKEN, "").trim();
    }

    // raw input in format : .... <example@example.com>
    private static String extractEmail(final String rawString) {
        return rawString.substring(rawString.indexOf(EMAIL_START_TOKEN) + 1, rawString.indexOf(EMAIL_END_TOKEN)).trim();
    }

    // raw input in format : F:	src/tools/perftool/
    private static String extractComponentPath(final String rawString) {
        return rawString.replace(FILEPATH_TOKEN, "").trim();
    }

    public List<ComponentInfo> parseMaintainers(@Nonnull final String rawContent) throws MaintainerMismatchException {
        final List<String> lines =
                Arrays.stream(rawContent.split(System.lineSeparator())).collect(Collectors.toList());
        int lastHeaderLine;
        for (lastHeaderLine = 0; lastHeaderLine < lines.size(); lastHeaderLine++) {
            if (lines.get(lastHeaderLine).contains(HEADER_SPLITTER)) {
                break;
            }
        }

        List<String> headerLessLines = lines.stream().skip(lastHeaderLine + 1).collect(Collectors.toList());
        final List<Set<String>> blocks = new LinkedList<>();

        while (!headerLessLines.isEmpty()) {
            final int nextBlockEnd = nextBlockEnd(headerLessLines);
            final Set<String> nextBlock = headerLessLines.stream().limit(nextBlockEnd + 1).map(
                    String::trim).filter(line -> !line.isEmpty()).collect(Collectors.toSet());
            blocks.add(nextBlock);
            headerLessLines =
                    headerLessLines.stream().unordered().skip(nextBlockEnd + 1).collect(Collectors.toList());
        }

        List<ComponentInfo> componentInfos = new ArrayList<>();
        for (Set<String> block : blocks) {
            if (block.size() > 0) {
                componentInfos.add(parseBlock(block));
            }
        }
        return componentInfos;
    }

    private int nextBlockEnd(final List<String> lines) {

        for (int end = 0; end < lines.size(); end++) {
            if (lines.get(end).trim().isEmpty()) {
                return end;
            }
        }
        //EOF
        return lines.size();
    }
}
