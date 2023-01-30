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
#include <math.h>

long double __sulong_fp128_add(long double x, long double y) {
    return x + y;
}

long double __sulong_fp128_sub(long double x, long double y) {
    return x - y;
}

long double __sulong_fp128_mul(long double x, long double y) {
    return x * y;
}

long double __sulong_fp128_div(long double x, long double y) {
    return x / y;
}

long double __sulong_fp128_mod(long double x, long double y) {
    return fmodl(x, y);
}

long double __sulong_fp128_pow(long double x, long double y) {
    return powl(x, y);
}

#define DECLARE_UNARY_INTRINSIC(fn)                                                                                                                  \
    long double __sulong_fp128_##fn(long double value) { return fn##l(value); }

DECLARE_UNARY_INTRINSIC(sqrt)
DECLARE_UNARY_INTRINSIC(log)
DECLARE_UNARY_INTRINSIC(log2)
DECLARE_UNARY_INTRINSIC(log10)
DECLARE_UNARY_INTRINSIC(rint)
DECLARE_UNARY_INTRINSIC(ceil)
DECLARE_UNARY_INTRINSIC(floor)
DECLARE_UNARY_INTRINSIC(exp)
DECLARE_UNARY_INTRINSIC(exp2)
DECLARE_UNARY_INTRINSIC(sin)
DECLARE_UNARY_INTRINSIC(cos)
