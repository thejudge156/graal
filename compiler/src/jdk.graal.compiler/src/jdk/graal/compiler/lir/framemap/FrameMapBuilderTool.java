/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.lir.framemap;

import java.util.List;

import jdk.graal.compiler.lir.VirtualStackSlot;

/**
 * A {@link FrameMapBuilder} that allows access to the underlying {@link FrameMap}.
 */
public abstract class FrameMapBuilderTool extends FrameMapBuilder {

    /**
     * Returns the number of {@link VirtualStackSlot}s created by this {@link FrameMapBuilder}. Can
     * be used as an upper bound for an array indexed by {@link VirtualStackSlot#getId()}.
     */
    public abstract int getNumberOfStackSlots();

    public abstract List<VirtualStackSlot> getStackSlots();

    public abstract FrameMap getFrameMap();

}
