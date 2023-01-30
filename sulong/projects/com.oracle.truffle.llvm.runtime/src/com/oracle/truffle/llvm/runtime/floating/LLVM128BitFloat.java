/*
 * Copyright (c) 2023, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.oracle.truffle.llvm.runtime.floating;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.NodeChild;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidBufferOffsetException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.memory.ByteArraySupport;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.llvm.runtime.ContextExtension;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.NativeContextExtension;
import com.oracle.truffle.llvm.runtime.floating.LLVM128BitFloatFactory.LLVM128BitFloatNativeCallNodeGen;
import com.oracle.truffle.llvm.runtime.floating.LLVM128BitFloatFactory.LLVM128BitFloatUnaryNativeCallNodeGen;
import com.oracle.truffle.llvm.runtime.interop.LLVMInternalTruffleObject;
import com.oracle.truffle.llvm.runtime.interop.nfi.LLVMNativeConvertNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.nfi.api.SerializableLibrary;
import com.oracle.truffle.nfi.api.SignatureLibrary;

import java.nio.ByteOrder;
import java.util.Arrays;

@ValueType
@ExportLibrary(value = SerializableLibrary.class, useForAOT = false)
public final class LLVM128BitFloat extends LLVMInternalTruffleObject {
    public static final long SIGN_BIT = 1L << 63;
    private static final int FRACTION_BIT_WIDTH = 112;
    public static final int BIT_WIDTH = 128;
    public static final int EXPONENT_POSITION = FRACTION_BIT_WIDTH - Long.SIZE; // 112 - 64 = 48
    public static final int BYTE_WIDTH = BIT_WIDTH / Byte.SIZE;
    public static final long EXPONENT_MASK = 0b0111111111111111L << EXPONENT_POSITION;
    public static final long FRACTION_MASK = (1L << EXPONENT_POSITION) - 1;
    private static final LLVM128BitFloat POSITIVE_INFINITY = LLVM128BitFloat.fromRawValues(false, EXPONENT_MASK, 0);
    private static final LLVM128BitFloat NEGATIVE_INFINITY = LLVM128BitFloat.fromRawValues(true, EXPONENT_MASK, 0);
    private static final LLVM128BitFloat POSITIVE_ZERO = LLVM128BitFloat.fromRawValues(false, 0, 0);
    private static final LLVM128BitFloat NEGATIVE_ZERO = LLVM128BitFloat.fromRawValues(true, 0, 0);
    private static final int EXPONENT_BIAS = 16383;
    private static final int FLOAT_EXPONENT_BIAS = 127;

    @Override
    public String toString() {
        return toLLVMString(this);
    }

    @CompilerDirectives.TruffleBoundary
    public static String toLLVMString(LLVM128BitFloat value) {
        if (value.isInfinity()) {
            return "INF";
        } else {
            return String.format("0xK%016x%016x", value.expSignFraction, value.fraction);
        }
    }

    private final long expSignFraction; // 64 bit -- the left over of the fraction goes into here.
    private final long fraction; // 64 bit -- fill this part first.

    public LLVM128BitFloat(long expSignFraction, long fraction) {
        this.expSignFraction = expSignFraction;
        this.fraction = fraction;
    }

    private LLVM128BitFloat(LLVM128BitFloat value) {
        this.expSignFraction = value.expSignFraction;
        this.fraction = value.fraction;
    }

    public static LLVM128BitFloat fromRawValues(boolean sign, long exponentFraction, long fraction) {
        assert (exponentFraction & SIGN_BIT) == 0;
        long expSignFraction = exponentFraction;
        if (sign) {
            expSignFraction |= SIGN_BIT;
        }
        return new LLVM128BitFloat(expSignFraction, fraction);
    }

    public long getExponent() {
        return (expSignFraction & EXPONENT_MASK) >> EXPONENT_POSITION;
    }

    public long getExpSignFractionPart() {
        return expSignFraction;
    }

    public boolean getSign() {
        return (expSignFraction & SIGN_BIT) != 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof LLVM128BitFloat)) {
            return false;
        }
        LLVM128BitFloat other = ((LLVM128BitFloat) obj);
        return this.expSignFraction == other.expSignFraction && this.fraction == other.fraction;
    }

    public boolean isPositiveInfinity() {
        return POSITIVE_INFINITY.equals(this);
    }

    public boolean isNegativeInfinity() {
        return NEGATIVE_INFINITY.equals(this);
    }

    public boolean isInfinity() {
        return isPositiveInfinity() || isNegativeInfinity();
    }

    public byte[] getBytesBigEndian() {
        byte[] array = new byte[BYTE_WIDTH];
        ByteArraySupport.bigEndian().putLong(array, 0, expSignFraction);
        ByteArraySupport.bigEndian().putLong(array, 8, fraction);
        return array;
    }

    public static LLVM128BitFloat createPositiveZero() {
        if (CompilerDirectives.inCompiledCode()) {
            return LLVM128BitFloat.fromRawValues(false, 0, 0);
        } else {
            return POSITIVE_ZERO;
        }
    }

    public static long bit(long i) {
        return 1L << i;
    }

    public static LLVM128BitFloat fromLong(long val) {
        if (val == 0) {
            return createPositiveZero();
        }
        boolean sign = val < 0;
        return fromLong(Math.abs(val), sign);
    }

    public long getSecondFractionPart() {
        return fraction;
    }

    public long getFirstFractionPart() {
        return expSignFraction & FRACTION_MASK;
    }

    private long getUnbiasedExponent() {
        return ((expSignFraction & EXPONENT_MASK) >>> (EXPONENT_POSITION)) - (EXPONENT_BIAS);
    }

    // 0x...p10 -- to the power of 2, 2p10. p48, 0x1p48.
    // x...e10 -- rounding error
    private long getFractionAsLong() {
        long unbiasedExponent = getUnbiasedExponent();
        long returnFraction = (1L << unbiasedExponent);
        if (unbiasedExponent < 0) {
            return 0;
        } else if (unbiasedExponent <= 48) {
            returnFraction |= (expSignFraction & FRACTION_MASK) >>> ((EXPONENT_POSITION) - unbiasedExponent);
        } else if (unbiasedExponent < 64) {
            returnFraction |= (expSignFraction & FRACTION_MASK) << (unbiasedExponent - EXPONENT_POSITION);
            returnFraction |= fraction >>> (Long.SIZE - (unbiasedExponent - EXPONENT_POSITION));
        } else { // TODO: problematic when unbiasedExponent > 112.
            returnFraction = 0L; // TODO: overflow case.
            // TODO: need test cases for each condition of the unbiasedExponent.
        }
        return returnFraction;
    }

    public int toIntValue() {
        int value = (int) getFractionAsLong();
        return getSign() ? -value : value;
    }

    public long toLongValue() {
        long value = getFractionAsLong();
        return getSign() ? -value : value;
    }

    public double toDoubleValue() {
        if (isPositiveInfinity()) {
            return DoubleHelper.POSITIVE_INFINITY;
        } else if (isNegativeInfinity()) {
            return DoubleHelper.NEGATIVE_INFINITY;
        } else {
            long doubleExponent = getUnbiasedExponent() + DoubleHelper.DOUBLE_EXPONENT_BIAS;
            long doubleFraction = (expSignFraction & FRACTION_MASK) << (DoubleHelper.DOUBLE_FRACTION_BIT_WIDTH - EXPONENT_POSITION);
            doubleFraction |= fraction >>> (Long.SIZE - (DoubleHelper.DOUBLE_FRACTION_BIT_WIDTH - EXPONENT_POSITION));
            long shiftedSignBit = (getSign() ? 1L : 0L) << DoubleHelper.DOUBLE_SIGN_POS;
            // TODO(PLi): test overflow.
            long shiftedExponent = doubleExponent << DoubleHelper.DOUBLE_FRACTION_BIT_WIDTH;
            long rawVal = doubleFraction | shiftedExponent | shiftedSignBit;
            return Double.longBitsToDouble(rawVal);
        }
    }

    public float toFloatValue() {
        if (isPositiveInfinity()) {
            return FloatHelper.POSITIVE_INFINITY;
        } else if (isNegativeInfinity()) {
            return FloatHelper.NEGATIVE_INFINITY;
        } else {
            long floatExponent = getUnbiasedExponent() + FLOAT_EXPONENT_BIAS;
            long floatFraction = (expSignFraction & FRACTION_MASK) >>> (EXPONENT_POSITION - FloatHelper.FLOAT_FRACTION_BIT_WIDTH);
            long shiftedSignBit = (getSign() ? 1 : 0) << FloatHelper.FLOAT_SIGN_POS;
            long shiftedExponent = floatExponent << FloatHelper.FLOAT_FRACTION_BIT_WIDTH;
            long rawVal = floatFraction | shiftedExponent | shiftedSignBit;
            return Float.intBitsToFloat((int) rawVal);
        }
    }

    public byte[] getBytes() {
        byte[] array = new byte[BYTE_WIDTH];
        ByteArraySupport.littleEndian().putLong(array, 0, fraction);
        ByteArraySupport.littleEndian().putLong(array, 8, expSignFraction);
        return array;
    }

    public static LLVM128BitFloat fromBytesBigEndian(byte[] bytes) {
        assert bytes.length == BYTE_WIDTH;
        long fraction = ByteArraySupport.bigEndian().getLong(bytes, 0);
        long expSignFraction = ByteArraySupport.bigEndian().getLong(bytes, 8);
        return new LLVM128BitFloat(expSignFraction, fraction);
    }

    public static LLVM128BitFloat fromInt(int val) {
        boolean sign = val < 0;
        return fromInt(val, sign);
    }

    private static LLVM128BitFloat fromInt(int val, boolean sign) {
        return fromLong(Math.abs(val), sign);
    }

    // 1 << 47
    // 1 << 48
    // 1 << 49
    private static LLVM128BitFloat fromLong(long val, boolean sign) {
        int leadingOnePosition = Long.SIZE - Long.numberOfLeadingZeros(val);
        long exponent = EXPONENT_BIAS + (leadingOnePosition - 1);
        long shiftAmount = FRACTION_BIT_WIDTH - leadingOnePosition + 1;
        long fraction;
        long exponentFraction;
        if (shiftAmount >= Long.SIZE) { // TODO: Need to test both cases.
            exponentFraction = (exponent << EXPONENT_POSITION) | (val << (shiftAmount - Long.SIZE));
            fraction = 0;
        } else {
            exponentFraction = (exponent << EXPONENT_POSITION) | (val >> (Long.SIZE - shiftAmount));
            fraction = val << (shiftAmount);
        }
        return LLVM128BitFloat.fromRawValues(sign, exponentFraction, fraction);
    }

    public static LLVM128BitFloat fromFloat(float val) {
        return fromDouble(val);
    }

    public static LLVM128BitFloat fromDouble(double val) {
        boolean sign = val < 0;
        if (DoubleHelper.isPositiveZero(val)) {
            return new LLVM128BitFloat(POSITIVE_ZERO);
        } else if (DoubleHelper.isNegativeZero(val)) {
            return new LLVM128BitFloat(NEGATIVE_ZERO);
        } else {
            long rawValue = Double.doubleToRawLongBits(val);
            int doubleExponent = DoubleHelper.getUnbiasedExponent(val);
            int biasedExponent = doubleExponent + EXPONENT_BIAS;
            long doubleFraction = rawValue & DoubleHelper.FRACTION_MASK;
            long shiftAmount = FRACTION_BIT_WIDTH - DoubleHelper.DOUBLE_FRACTION_BIT_WIDTH;
            long fraction = doubleFraction << (shiftAmount);
            long biasedExponentFraction = ((long) biasedExponent << EXPONENT_POSITION) | (doubleFraction >> (Long.SIZE - shiftAmount));
            return LLVM128BitFloat.fromRawValues(sign, biasedExponentFraction, fraction);
        }
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(getBytes());
    }

    public static int compare(LLVM128BitFloat val1, LLVM128BitFloat val2) {
        return val1.compareOrdered(val2);
    }

    int compareOrdered(LLVM128BitFloat val) {
        if (isNegativeInfinity()) {
            if (val.isNegativeInfinity()) {
                return 0;
            } else {
                return -1;
            }
        }

        if (val.isNegativeInfinity()) {
            return 1;
        }

        if (getSign() == val.getSign()) {
            long expDifference = getExponent() - val.getExponent();
            if (expDifference == 0) {
                long fractionFirstPartDifference = getFirstFractionPart() - val.getFirstFractionPart();
                if (fractionFirstPartDifference == 0) {
                    long fractionSecondPartDifference = getSecondFractionPart() - val.getSecondFractionPart();
                    if (fractionSecondPartDifference == 0) {
                        return 0;
                    } else if (fractionSecondPartDifference < 0) {
                        return -1;
                    } else {
                        return 1;
                    }
                } else {
                    return (int) fractionFirstPartDifference;
                }
            } else {
                return (int) expDifference;
            }
        } else {
            if (isZero() && val.isZero()) {
                return 0;
            } else if (getSign()) {
                return -1;
            } else {
                return 1;
            }
        }
    }

    public boolean isZero() {
        return isPositiveZero() || isNegativeZero();
    }

    private boolean isPositiveZero() {
        return equals(POSITIVE_ZERO);
    }

    private boolean isNegativeZero() {
        return equals(NEGATIVE_ZERO);
    }

    @ExplodeLoop
    public static boolean areOrdered(LLVM128BitFloat... vals) {
        CompilerAsserts.compilationConstant(vals.length);
        for (LLVM128BitFloat val : vals) {
            if (!val.isOrdered()) {
                return false;
            }
        }
        return true;
    }

    public boolean isNaN() {
        if (getExponent() == 0x7FFF) {
            return !isInfinity() && (getSecondFractionPart() != 0L || getFirstFractionPart() != 0L);
        }
        return false;
    }

    public boolean isOrdered() {
        return !isNaN();
    }

    // serialization for NFI

    @ExportMessage
    @SuppressWarnings("static-method")
    boolean isSerializable() {
        return true;
    }

    @ExportMessage(limit = "1")
    void serialize(Object buffer,
                    @CachedLibrary("buffer") InteropLibrary interop) {
        try {
            interop.writeBufferLong(buffer, ByteOrder.LITTLE_ENDIAN, 0, fraction);
            interop.writeBufferLong(buffer, ByteOrder.LITTLE_ENDIAN, 8, expSignFraction);
        } catch (UnsupportedMessageException | InvalidBufferOffsetException ex) {
            throw CompilerDirectives.shouldNotReachHere(ex);
        }
    }

    public abstract static class FP128Node extends LLVMExpressionNode {

        final String name;
        private final String functionName;
        private final String signature;

        final ContextExtension.Key<NativeContextExtension> nativeCtxExtKey;

        public abstract LLVM128BitFloat execute(Object... args);

        FP128Node(String name, String signature) {
            this.name = name;
            this.functionName = "__sulong_fp128_" + name;
            this.signature = signature;
            this.nativeCtxExtKey = LLVMLanguage.get(this).lookupContextExtension(NativeContextExtension.class);
        }

        protected NativeContextExtension.WellKnownNativeFunctionNode createFunction() {
            LLVMContext context = LLVMContext.get(this);
            NativeContextExtension nativeContextExtension = context.getContextExtensionOrNull(NativeContextExtension.class);
            if (nativeContextExtension == null) {
                return null;
            } else {
                return nativeContextExtension.getWellKnownNativeFunction(functionName, signature);
            }
        }

        protected NativeContextExtension.WellKnownNativeFunctionAndSignature getFunction() {
            NativeContextExtension nativeContextExtension = nativeCtxExtKey.get(LLVMContext.get(this));
            return nativeContextExtension.getWellKnownNativeFunctionAndSignature(functionName, signature);
        }

        protected LLVMNativeConvertNode createToFP128() {
            return LLVMNativeConvertNode.createFromNative(PrimitiveType.F128);
        }
    }

    @NodeChild(value = "x", type = LLVMExpressionNode.class)
    @NodeChild(value = "y", type = LLVMExpressionNode.class)
    abstract static class LLVM128BitFloatNativeCallNode extends FP128Node {

        LLVM128BitFloatNativeCallNode(String name) {
            super(name, "(FP128,FP128):FP128");
        }

        @Specialization(guards = "function != null")
        protected LLVM128BitFloat doCall(Object x, Object y,
                        @Cached("createFunction()") NativeContextExtension.WellKnownNativeFunctionNode function,
                        @Cached("createToFP128()") LLVMNativeConvertNode nativeConvert) {
            try {
                Object ret = function.execute(x, y);
                return (LLVM128BitFloat) nativeConvert.executeConvert(ret);
            } catch (InteropException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @Specialization(guards = "nativeCtxExtKey != null", replaces = "doCall")
        protected LLVM128BitFloat doCallAOT(Object x, Object y,
                        @CachedLibrary(limit = "1") SignatureLibrary signatureLibrary,
                        @Cached("createToFP128()") LLVMNativeConvertNode nativeConvert) {
            NativeContextExtension.WellKnownNativeFunctionAndSignature wkFunSig = getFunction();
            try {
                Object ret = signatureLibrary.call(wkFunSig.getSignature(), wkFunSig.getFunction(), x, y);
                return (LLVM128BitFloat) nativeConvert.executeConvert(ret);
            } catch (ArityException | UnsupportedTypeException | UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @CompilerDirectives.TruffleBoundary
        @Specialization(guards = "nativeCtxExtKey == null")
        protected LLVM128BitFloat doCallNoNative(LLVM128BitFloat x, LLVM128BitFloat y) {
            // imprecise workaround for cases in which NFI isn't available
            double xDouble = x.toDoubleValue();
            double yDouble = y.toDoubleValue();
            double result;
            switch (name) {
                case "add":
                    result = xDouble + yDouble;
                    break;
                case "sub":
                    result = xDouble - yDouble;
                    break;
                case "mul":
                    result = xDouble * yDouble;
                    break;
                case "div":
                    result = xDouble / yDouble;
                    break;
                case "mod":
                    result = xDouble % yDouble;
                    break;
                default:
                    throw new AssertionError("unexpected 128 bit float operation: " + name);
            }
            return LLVM128BitFloat.fromDouble(result);
        }

        @Override
        public String toString() {
            return "fp128 " + name;
        }
    }

    @NodeChild(value = "x", type = LLVMExpressionNode.class)
    abstract static class LLVM128BitFloatUnaryNativeCallNode extends FP128Node {

        LLVM128BitFloatUnaryNativeCallNode(String name) {
            super(name, "(FP128):FP128");
        }

        @Specialization(guards = "function != null")
        protected LLVM128BitFloat doCall(Object x,
                        @Cached("createFunction()") NativeContextExtension.WellKnownNativeFunctionNode function,
                        @Cached("createToFP128()") LLVMNativeConvertNode nativeConvert) {
            try {
                Object ret = function.execute(x);
                return (LLVM128BitFloat) nativeConvert.executeConvert(ret);
            } catch (InteropException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        @Specialization(guards = "nativeCtxExtKey != null", replaces = "doCall")
        protected LLVM128BitFloat doCallAOT(Object x,
                        @CachedLibrary(limit = "1") SignatureLibrary signatureLibrary,
                        @Cached("createToFP128()") LLVMNativeConvertNode nativeConvert) {
            NativeContextExtension.WellKnownNativeFunctionAndSignature wkFunSig = getFunction();
            try {
                Object ret = signatureLibrary.call(wkFunSig.getSignature(), wkFunSig.getFunction(), x);
                return (LLVM128BitFloat) nativeConvert.executeConvert(ret);
            } catch (ArityException | UnsupportedTypeException | UnsupportedMessageException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }
    }

    public static FP128Node createAddNode() {
        return LLVM128BitFloatFactory.LLVM128BitFloatNativeCallNodeGen.create("add", null, null);
    }

    public static FP128Node createSubNode() {
        return LLVM128BitFloatFactory.LLVM128BitFloatNativeCallNodeGen.create("sub", null, null);
    }

    public static FP128Node createMulNode() {
        return LLVM128BitFloatNativeCallNodeGen.create("mul", null, null);
    }

    public static FP128Node createDivNode() {
        return LLVM128BitFloatNativeCallNodeGen.create("div", null, null);
    }

    public static FP128Node createRemNode() {
        return LLVM128BitFloatNativeCallNodeGen.create("mod", null, null);
    }

    public static FP128Node createPowNode(LLVMExpressionNode x, LLVMExpressionNode y) {
        return LLVM128BitFloatNativeCallNodeGen.create("pow", x, y);
    }

    public static FP128Node createUnary(String name, LLVMExpressionNode x) {
        return LLVM128BitFloatUnaryNativeCallNodeGen.create(name, x);
    }
}