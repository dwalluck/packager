/*
 * Copyright (c) 2016, 2019 Contributors to the Eclipse Foundation
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

package org.eclipse.packager.rpm.parse;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.eclipse.packager.rpm.ReadableHeader;
import org.eclipse.packager.rpm.RpmBaseTag;

public class InputHeader<T extends RpmBaseTag> implements ReadableHeader<T> {
    private final Map<Integer, HeaderValue> entries;

    private final long start;

    private final long length;

    public InputHeader(final HeaderValue[] entries, final long start, final long length) {
        final Map<Integer, HeaderValue> tags = new LinkedHashMap<>(entries.length);
        for (final HeaderValue entry : entries) {
            tags.put(entry.getTag(), entry);
        }

        this.entries = Collections.unmodifiableMap(tags);

        this.start = start;
        this.length = length;
    }

    /**
     * Get the start position of the header section in the stream
     *
     * @return the start position
     */
    public long getStart() {
        return this.start;
    }

    /**
     * Get the length of header section in the stream
     *
     * @return the length of the header in bytes
     */
    public long getLength() {
        return this.length;
    }

    public Object getTag(final T tag) {
        return getTagOrDefault(tag, null);
    }

    public Object getTag(final int tag) {
        return getTagOrDefault(tag, null);
    }

    @Override
    public Optional<Object> getValue(final T tag) {
        return Optional.ofNullable(getTag(tag));
    }

    public Optional<Object> getOptionalTag(final T tag) {
        return getEntry(tag).map(HeaderValue::getValue);
    }

    public Optional<Object> getOptionalTag(final int tag) {
        return getEntry(tag).map(HeaderValue::getValue);
    }

    public Optional<HeaderValue> getEntry(final T tag) {
        return Optional.ofNullable(this.entries.get(tag.getValue()));
    }

    public Optional<HeaderValue> getEntry(final int tag) {
        return Optional.ofNullable(this.entries.get(tag));
    }

    public Object getTagOrDefault(final T tag, final Object defaultValue) {
        return getOptionalTag(tag).orElse(defaultValue);
    }

    public Object getTagOrDefault(final int tag, final Object defaultValue) {
        return getOptionalTag(tag).orElse(defaultValue);
    }

    public Map<Integer, HeaderValue> getRawTags() {
        return this.entries;
    }

}
