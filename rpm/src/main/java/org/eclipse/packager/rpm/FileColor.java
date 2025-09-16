/*
 * Copyright (c) 2015, 2022 Contributors to the Eclipse Foundation
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

import java.util.EnumSet;
import java.util.Set;

public enum FileColor {
    BLACK( 0),
    ELF32(1 << 0),
    ELF64 (1 << 1),
    ELFMIPSN32(1 << 2),
    WHITE(1 << 29),
    INCLUDE(1 << 30),
    ERROR (-1);

    private final int value;

    FileColor(final int value) {
        this.value = value;
    }

    public int getValue() {
        return this.value;
    }

    public static boolean isElf(final int fileColor) {
        final Set<FileColor> fileColors = decode(fileColor);
        return fileColors.contains(ELF32) || fileColors.contains(ELF64) || fileColors.contains(ELFMIPSN32);
    }

    public static Set<FileColor> decode(final int fileColor) {
        final Set<FileColor> fileFlags = EnumSet.noneOf(FileColor.class);
        if (fileColor != 0) {
            for (final FileColor fileFlag : FileColor.values()) {
                if ((fileFlag.getValue() & fileColor) == fileFlag.getValue()) {
                    fileFlags.add(fileFlag);
                }
            }
        }
        return fileFlags;
    }

    public static int encode(final Set<FileColor> fileColors) {
        int flags = 0;

        for (final FileColor fileColor : fileColors) {
            flags |= fileColor.getValue();
        }

        return flags;
    }
}
