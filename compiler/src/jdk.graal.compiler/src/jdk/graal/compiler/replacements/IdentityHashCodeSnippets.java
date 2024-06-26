/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements;

import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.NOT_FREQUENT_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.probability;

import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.nodes.IdentityHashCodeNode;
import jdk.graal.compiler.replacements.SnippetTemplate.AbstractTemplates;
import jdk.graal.compiler.replacements.SnippetTemplate.Arguments;
import jdk.graal.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.word.LocationIdentity;

public abstract class IdentityHashCodeSnippets implements Snippets {

    @Snippet
    private int identityHashCodeSnippet(final Object thisObj) {
        if (probability(NOT_FREQUENT_PROBABILITY, thisObj == null)) {
            return 0;
        }
        return computeIdentityHashCode(thisObj);
    }

    protected abstract int computeIdentityHashCode(Object thisObj);

    public static class Templates extends AbstractTemplates {

        private final SnippetInfo identityHashCodeSnippet;

        @SuppressWarnings("this-escape")
        public Templates(IdentityHashCodeSnippets receiver, OptionValues options, Providers providers, LocationIdentity locationIdentity) {
            super(options, providers);

            identityHashCodeSnippet = snippet(providers,
                            IdentityHashCodeSnippets.class,
                            "identityHashCodeSnippet",
                            null,
                            receiver,
                            locationIdentity);
        }

        public void lower(IdentityHashCodeNode node, LoweringTool tool) {
            StructuredGraph graph = node.graph();
            Arguments args = new Arguments(identityHashCodeSnippet, graph.getGuardsStage(), tool.getLoweringStage());
            args.add("thisObj", node.object());
            SnippetTemplate template = template(tool, node, args);
            template.instantiate(tool.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }
}
