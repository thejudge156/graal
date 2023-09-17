/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.code;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.bytecode.BytecodeProvider;
import org.graalvm.compiler.bytecode.ResolvedJavaMethodBytecodeProvider;
import org.graalvm.compiler.core.common.spi.ConstantFieldProvider;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.common.spi.MetaAccessExtensionProvider;
import org.graalvm.compiler.nodes.spi.LoopsDataProvider;
import org.graalvm.compiler.nodes.spi.LoweringProvider;
import org.graalvm.compiler.nodes.spi.PlatformConfigurationProvider;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.nodes.spi.StampProvider;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.word.WordTypes;

import com.oracle.graal.pointsto.heap.ImageHeapScanner;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.code.SubstrateBackendFactory;
import com.oracle.svm.core.graal.code.SubstratePlatformConfigurationProvider;
import com.oracle.svm.core.graal.meta.SubstrateLoweringProvider;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.ameta.AnalysisConstantReflectionProvider;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;
import com.oracle.svm.hosted.meta.HostedConstantFieldProvider;
import com.oracle.svm.hosted.meta.HostedConstantReflectionProvider;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedSnippetReflectionProvider;
import com.oracle.svm.hosted.meta.HostedUniverse;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.meta.ConstantReflectionProvider;

public class HostedRuntimeConfigurationBuilder extends SharedRuntimeConfigurationBuilder {

    private final HostedUniverse universe;
    private final HostedProviders analysisProviders;
    private final ImageHeapScanner heapScanner;

    public HostedRuntimeConfigurationBuilder(OptionValues options, ImageHeapScanner heapScanner, SVMHost hostVM, HostedUniverse universe, HostedMetaAccess metaAccess,
                    HostedProviders analysisProviders, ClassInitializationSupport classInitializationSupport, LoopsDataProvider originalLoopsDataProvider,
                    SubstratePlatformConfigurationProvider platformConfig) {
        super(options, hostVM, metaAccess, SubstrateBackendFactory.get()::newBackend, classInitializationSupport, originalLoopsDataProvider, platformConfig);
        this.universe = universe;
        this.analysisProviders = analysisProviders;
        this.heapScanner = heapScanner;
    }

    @Override
    protected Providers createProviders(CodeCacheProvider codeCache, ConstantReflectionProvider constantReflection, ConstantFieldProvider constantFieldProvider, ForeignCallsProvider foreignCalls,
                    LoweringProvider lowerer, Replacements replacements, StampProvider stampProvider, SnippetReflectionProvider snippetReflection,
                    PlatformConfigurationProvider platformConfigurationProvider, MetaAccessExtensionProvider metaAccessExtensionProvider, WordTypes wordTypes, LoopsDataProvider loopsDataProvider) {
        return new HostedProviders(metaAccess, codeCache, constantReflection, constantFieldProvider, foreignCalls, lowerer, replacements, stampProvider, snippetReflection,
                        wordTypes, platformConfigurationProvider, metaAccessExtensionProvider, loopsDataProvider);
    }

    @Override
    protected ConstantReflectionProvider createConstantReflectionProvider() {
        return new HostedConstantReflectionProvider(hostVM, (AnalysisConstantReflectionProvider) analysisProviders.getConstantReflection(), universe, (HostedMetaAccess) metaAccess);
    }

    @Override
    protected ConstantFieldProvider createConstantFieldProvider() {
        return new HostedConstantFieldProvider(metaAccess, universe.hostVM());
    }

    @Override
    protected LoweringProvider createLoweringProvider(ForeignCallsProvider foreignCalls, MetaAccessExtensionProvider metaAccessExtensionProvider) {
        return SubstrateLoweringProvider.createForHosted(metaAccess, foreignCalls, platformConfig, metaAccessExtensionProvider);
    }

    @Override
    protected SnippetReflectionProvider createSnippetReflectionProvider(WordTypes wordTypes) {
        return new HostedSnippetReflectionProvider(heapScanner, wordTypes);
    }

    @Override
    protected Replacements createReplacements(Providers p, SnippetReflectionProvider reflectionProvider) {
        BytecodeProvider bytecodeProvider = new ResolvedJavaMethodBytecodeProvider();
        return new HostedReplacements(universe, p, reflectionProvider, ConfigurationValues.getTarget(), analysisProviders, bytecodeProvider, p.getWordTypes());
    }

    @Override
    protected CodeCacheProvider createCodeCacheProvider(RegisterConfig registerConfig) {
        return new HostedCodeCacheProvider(ConfigurationValues.getTarget(), registerConfig);
    }
}
