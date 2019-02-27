/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.configure;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import com.oracle.svm.configure.json.JsonWriter;
import com.oracle.svm.configure.trace.TraceProcessor;
import com.oracle.svm.core.util.VMError;

public class ConfigurationTool {

    private static final String HELP_TEXT = getResource("/Help.txt") + System.lineSeparator();

    private static class UsageException extends RuntimeException {
        static final long serialVersionUID = 1L;

        UsageException(String message) {
            super(message);
        }
    }

    public static void main(String[] args) {
        try {
            if (args.length == 0) {
                throw new UsageException("No arguments provided.");
            }
            Iterator<String> argsIter = Arrays.asList(args).iterator();
            String first = argsIter.next();
            if (first.equals("process-trace")) {
                processTrace(argsIter);
            } else if (first.equals("help") || first.equals("--help")) {
                System.out.println(HELP_TEXT);
            } else {
                throw new UsageException("Unknown subcommand: " + first);
            }
        } catch (UsageException e) {
            System.err.println(e.getMessage() + System.lineSeparator() +
                            "Use 'native-image-configure help' for usage.");
            System.exit(2);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void processTrace(Iterator<String> argsIter) throws IOException {
        List<Path> traceInputPaths = new ArrayList<>();
        boolean filter = true;
        Path reflectOutputPath = null;
        Path jniOutputPath = null;
        Path proxyOutputPath = null;
        Path resourcesOutputPath = null;

        while (argsIter.hasNext()) {
            String current = argsIter.next();
            switch (current) {
                case "--reflect-output":
                    reflectOutputPath = Paths.get(argsIter.next());
                    break;
                case "--jni-output":
                    jniOutputPath = Paths.get(argsIter.next());
                    break;
                case "--proxy-output":
                    proxyOutputPath = Paths.get(argsIter.next());
                    break;
                case "--resources-output":
                    resourcesOutputPath = Paths.get(argsIter.next());
                    break;
                case "--no-filter":
                    filter = false;
                    break;
                default:
                    traceInputPaths.add(Paths.get(current));
                    break;
            }
        }

        TraceProcessor p = new TraceProcessor();
        p.setFilterEnabled(filter);
        if (traceInputPaths.isEmpty()) {
            throw new UsageException("No trace files specified.");
        }
        for (Path path : traceInputPaths) {
            try (Reader reader = Files.newBufferedReader(path)) {
                p.process(reader);
            }
        }
        if (reflectOutputPath != null) {
            try (JsonWriter writer = new JsonWriter(reflectOutputPath)) {
                p.getReflectionConfiguration().printJson(writer);
            }
        }
        if (jniOutputPath != null) {
            try (JsonWriter writer = new JsonWriter(jniOutputPath)) {
                p.getJniConfiguration().printJson(writer);
            }
        }
        if (proxyOutputPath != null) {
            try (JsonWriter writer = new JsonWriter(proxyOutputPath)) {
                p.getProxyConfiguration().printJson(writer);
            }
        }
        if (resourcesOutputPath != null) {
            try (Writer writer = Files.newBufferedWriter(resourcesOutputPath, StandardCharsets.UTF_8)) {
                p.getResourceConfiguration().write(writer);
            }
        }
    }

    private static String getResource(String resourceName) {
        try (InputStream input = ConfigurationTool.class.getResourceAsStream(resourceName)) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            VMError.shouldNotReachHere(e);
        }
        return null;
    }
}
