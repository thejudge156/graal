/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.replacements;

import static jdk.graal.compiler.nodeinfo.InputType.Memory;
import static jdk.graal.compiler.nodeinfo.InputType.State;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_0;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.DeoptimizingNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.memory.AbstractMemoryCheckpoint;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.spi.Lowerable;
import org.graalvm.word.LocationIdentity;

@NodeInfo(cycles = CYCLES_2, size = SIZE_0, allowedUsageTypes = Memory)
public class FastNotifyNode extends AbstractMemoryCheckpoint implements Lowerable, SingleMemoryKill, DeoptimizingNode.DeoptDuring {

    public static final NodeClass<FastNotifyNode> TYPE = NodeClass.create(FastNotifyNode.class);
    private final boolean notifyAll;

    private final int bci;

    @Input ValueNode object;

    @OptionalInput(State) FrameState stateDuring;

    public FastNotifyNode(ValueNode object, boolean notifyAll, int bci) {
        super(TYPE, StampFactory.forVoid());
        this.object = object;
        this.notifyAll = notifyAll;
        this.bci = bci;
    }

    public boolean isNotifyAll() {
        return notifyAll;
    }

    @Override
    public LocationIdentity getKilledLocationIdentity() {
        return LocationIdentity.any();
    }

    @Override
    public FrameState stateDuring() {
        return stateDuring;
    }

    @Override
    public void setStateDuring(FrameState stateDuring) {
        updateUsages(this.stateDuring, stateDuring);
        this.stateDuring = stateDuring;
    }

    @Override
    public void computeStateDuring(FrameState currentStateAfter) {
        FrameState newStateDuring = currentStateAfter.duplicateModifiedDuringCall(bci, getStackKind());
        setStateDuring(newStateDuring);
    }

    @Override
    public boolean canDeoptimize() {
        return true;
    }

    public int getBci() {
        return bci;
    }
}
