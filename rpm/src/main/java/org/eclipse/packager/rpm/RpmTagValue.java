/*
 * Copyright (c) 2015, 2019 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.packager.rpm;

import static java.lang.Integer.MAX_VALUE;

import org.apache.commons.codec.binary.Hex;
import org.eclipse.packager.rpm.header.Type;

import java.nio.ByteBuffer;
import java.sql.Blob;
import java.util.Arrays;

public class RpmTagValue<T> {
    private final int rawType;

    private final Type type;

    private final T value;

    public RpmTagValue(T value) {
        this.rawType = MAX_VALUE;
        this.type = Type.UNKNOWN;
        this.value = value;
    }

    public RpmTagValue(Type type, int rawType, T value) {
        this.rawType = rawType;
        this.type = type;
        this.value = value;
    }

    public int getRawType() {
        return this.rawType;
    }

    public Type getType() {
        return this.type;
    }

    public T getValue() {
        return this.value;
    }

    public byte[] asByteArray() {
        return (byte[]) this.value;
    }

    public Integer[] asIntegerArray() {
        return (Integer[]) this.value;
    }

    public String[] asStringArray() {
        return (String[]) this.value;
    }

    public int asInt() {
        return (int) this.value;
    }

    public Long asLong() {
        if (this.value instanceof Number) {
            return ((Number) this.value).longValue();
        }

        return null;
    }

    public String asString() {
        if (this.value instanceof String[]) {
            String[] values = (String[]) this.value;
            return values.length > 0 ? values[0] : null;
        }

        return (String) this.value;
    }

    public Class<?> getClazz() {
        return this.value.getClass();
    }

    @Override
    public String toString() {
        if (this.value == null) {
            return null;
        }

        if (this.value instanceof Character) {
            return String.valueOf((char) this.value);
        }

        if (this.value instanceof Character[]) {
            return Arrays.toString((Character[]) this.value);
        }

        if (this.value instanceof byte[]) {
            return Hex.encodeHexString((byte[]) this.value);
        }

        if (this.value instanceof Short) {
            return String.valueOf(this.value);
        }

        if (this.value instanceof Short[]) {
            return Arrays.toString((Short[]) this.value);
        }

        if (this.value instanceof Integer) {
            return String.valueOf(this.value);
        }

        if (this.value instanceof Integer[]) {
            return Arrays.toString((Integer[]) this.value);
        }

        if (this.value instanceof Long) {
            return String.valueOf(this.value);
        }

        if (this.value instanceof Long[]) {
            return Arrays.toString((Long[]) this.value);
        }

        if (this.value instanceof String) {
            return (String) this.value;
        }

        if (this.value instanceof String[]) {
            return Arrays.toString((String[]) this.value);
        }

        if (this.value instanceof ByteBuffer) {
            ByteBuffer buf = ((ByteBuffer) this.value);
            byte[] arr = new byte[buf.remaining()];
            buf.get(arr);
            return Hex.encodeHexString(arr);
        }

        return this.value.toString();
    }
}
