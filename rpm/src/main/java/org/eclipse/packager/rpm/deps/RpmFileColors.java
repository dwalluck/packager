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

package org.eclipse.packager.rpm.deps;

import io.github.dwalluck.libmagic.MagicException;
import io.github.dwalluck.libmagic.MagicLibrary;
import net.fornwall.jelf.ElfFile;
import org.eclipse.packager.rpm.FileColor;
import org.eclipse.packager.rpm.FileClassification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static io.github.dwalluck.libmagic.MagicFlag.CHECK;
import static io.github.dwalluck.libmagic.MagicFlag.COMPRESS;
import static io.github.dwalluck.libmagic.MagicFlag.ERROR;
import static io.github.dwalluck.libmagic.MagicFlag.MIME_TYPE;
import static io.github.dwalluck.libmagic.MagicFlag.NO_CHECK_TOKENS;
import static jnr.constants.platform.Errno.ENOENT;
import static jnr.posix.FileStat.S_IFBLK;
import static jnr.posix.FileStat.S_IFCHR;
import static jnr.posix.FileStat.S_IFDIR;
import static jnr.posix.FileStat.S_IFIFO;
import static jnr.posix.FileStat.S_IFLNK;
import static jnr.posix.FileStat.S_IFREG;
import static jnr.posix.FileStat.S_IFSOCK;
import static jnr.posix.FileStat.S_IXGRP;
import static jnr.posix.FileStat.S_IXOTH;
import static jnr.posix.FileStat.S_IXUSR;
import static net.fornwall.jelf.ElfFile.CLASS_32;
import static net.fornwall.jelf.ElfFile.CLASS_64;
import static org.apache.commons.compress.archivers.cpio.CpioConstants.S_IFMT;
import static org.eclipse.packager.rpm.FileColor.BLACK;
import static org.eclipse.packager.rpm.FileColor.ELF32;
import static org.eclipse.packager.rpm.FileColor.ELF64;
import static org.eclipse.packager.rpm.FileColor.INCLUDE;
import static org.eclipse.packager.rpm.FileColor.WHITE;

public final class RpmFileColors {
    private static final Logger logger = LoggerFactory.getLogger(RpmFileColors.class);

    private static class SkippedExtension {
        private final String fileType;

        private final String fileMime;

        private SkippedExtension(final String fileType, final String fileMime) {
            this.fileType = fileType;
            this.fileMime = fileMime;
        }
    }

    private static final Map<String, SkippedExtension> SKIPPED_EXTENSIONSs = Map.of(
        ".pm", new SkippedExtension("Perl5 module source text", "text/plain"),
        ".c", new SkippedExtension("C Code", "text/x-c"),
        ".h", new SkippedExtension("C Header", "text/x-c"),
        ".la", new SkippedExtension("libtool library file", "text/plain"),
        ".pc", new SkippedExtension("pkgconfig file", "text/plain"),
        ".html", new SkippedExtension("HTML document", "text/html"),
        ".png", new SkippedExtension("PNG image data", "image/png"),
        ".svg", new SkippedExtension("SVG Scalable Vector Graphics image", "image/svg+xml")
    );

    private RpmFileColors() {

    }

    public static FileClassification classify(final FileClassification fileClassification, final String fileName, final int mode) throws IOException, MagicException {
        try (final MagicLibrary ms = MagicLibrary.open(CHECK, COMPRESS, NO_CHECK_TOKENS, ERROR); final MagicLibrary mime = MagicLibrary.open(CHECK, COMPRESS, NO_CHECK_TOKENS, ERROR, MIME_TYPE)) {
            ms.load();
            mime.load();
            String fileMime = null;
            String fileType = null;
            final Set<FileColor> fileColors = EnumSet.of(BLACK);
            final boolean isExecutable = (mode & (S_IXUSR | S_IXGRP | S_IXOTH)) != 0;

            final Path filePath = Path.of(fileName);
            switch (mode & S_IFMT) {
                case S_IFCHR:
                    fileType = "character special";
                    break;
                case S_IFBLK:
                    fileType = "block special";
                    break;
                case S_IFIFO:
                    fileType = "fifo (named pipe)";
                    break;
                case S_IFSOCK:
                    fileType = "socket";
                    break;
                case S_IFDIR:
                    fileType = "directory";
                    break;
                case S_IFLNK:
                case S_IFREG:
                default:
                    final SkippedExtension skippedExtension = SKIPPED_EXTENSIONSs.get(getFileExtension(fileName));

                    if (skippedExtension != null) {
                        fileType = skippedExtension.fileType;
                        fileMime = skippedExtension.fileMime;
                        break;
                    }

                    fileType = ms.file(filePath);

                    if (ms.errno() == ENOENT.intValue()) {
                        fileType = "";
                    }

                    if (fileType == null) {
                        if (logger.isErrorEnabled()) {
                            logger.error("Recognition of file \"{}\" failed: mode {} {}", fileName, mode, ms.error());
                        }

                        if (!isExecutable) {
                            fileType = "data";
                        }
                    }
            }

            if (fileMime == null) {
                fileMime = mime.file(filePath);

                if (fileMime == null && mime.errno() == ENOENT.intValue()) {
                    fileMime = "";
                }
            }

            if (fileMime == null) {
                if (logger.isErrorEnabled()) {
                    logger.error("Recognition of file mtype \"{}\" failed: mode {} {}", fileName, String.format("%06o", mode), ms.error());
                }

                if (!isExecutable) {
                    fileMime = "application/octet-stream";
                }
            }

            logger.debug("{}: {} ({})", fileName, fileMime, fileType);

            fileClassification.setFileName(fileName);
            fileColors.addAll(getFileColorForFileType(fileType));
            fileClassification.setFileColors(fileColors);
            fileClassification.setFileMime(fileMime);

            if (!fileColors.contains(WHITE) && fileColors.contains(INCLUDE)) {
                fileClassification.setFileType(fileType);
            }

            if ((mode & S_IFREG) != 0 && isExecutable) {
                fileClassification.setFileColors(getElfColor(fileName));
            }
        }

        return fileClassification;
    }


    private static final int EM_BPF = 247;

    private static Set<FileColor> getElfColor(final String s) throws IOException {
        final ElfFile elfFile = ElfFile.from(Path.of(s).toFile());

        if (elfFile.e_machine == EM_BPF) {
            return EnumSet.of(BLACK);
        }

        final FileColor color;

        switch (elfFile.ei_class) {
            case CLASS_64:
                color = ELF64;
                break;
            case CLASS_32:
                color = ELF32;
                break;
            default:
                color = BLACK;
        }

        return EnumSet.of(color);
    }

    private static final Map<String, Set<FileColor>> RPM_FILE_CLASS_TOKENS = Map.ofEntries(
        Map.entry("directory", EnumSet.of(INCLUDE)),
       Map.entry("ELF 32-bit", EnumSet.of(ELF32, INCLUDE)),
       Map.entry("ELF 64-bit", EnumSet.of(ELF64, INCLUDE)),
       Map.entry("troff or preprocessor input", EnumSet.of(INCLUDE)),
       Map.entry("GNU Info", EnumSet.of(INCLUDE)),
       Map.entry("perl ", EnumSet.of(INCLUDE)),
       Map.entry("Perl5 module source text", EnumSet.of(INCLUDE)),
       Map.entry("python ", EnumSet.of(INCLUDE)),
       Map.entry("libtool library ", EnumSet.of(INCLUDE)),
       Map.entry("pkgconfig ", EnumSet.of(INCLUDE)),
       Map.entry("Objective caml ", EnumSet.of(INCLUDE)),
       Map.entry("Mono/.Net assembly", EnumSet.of(INCLUDE)),
       Map.entry("current ar archive", EnumSet.of(INCLUDE)),
       Map.entry("Zip archive data", EnumSet.of(INCLUDE)),
       Map.entry("tar archive", EnumSet.of(INCLUDE)),
       Map.entry("cpio archive", EnumSet.of(INCLUDE)),
       Map.entry("RPM v3", EnumSet.of(INCLUDE)),
       Map.entry("RPM v4", EnumSet.of(INCLUDE)),
       Map.entry(" image", EnumSet.of(INCLUDE)),
       Map.entry(" font", EnumSet.of(INCLUDE)),
       Map.entry(" Font", EnumSet.of(INCLUDE)),
       Map.entry(" commands", EnumSet.of(INCLUDE)),
       Map.entry(" script", EnumSet.of(INCLUDE)),
       Map.entry("empty", EnumSet.of(INCLUDE)),
       Map.entry("HTML", EnumSet.of(INCLUDE)),
       Map.entry("SGML", EnumSet.of(INCLUDE)),
       Map.entry("XML", EnumSet.of(INCLUDE)),
       Map.entry(" source", EnumSet.of(INCLUDE)),
       Map.entry("GLS_BINARY_LSB_FIRST", EnumSet.of(INCLUDE)),
       Map.entry(" DB ", EnumSet.of(INCLUDE)),
       Map.entry(" text", EnumSet.of(INCLUDE)));

    private static Set<FileColor> getFileColorForFileType(final String ftype) {
        final Set<FileColor> fcolor = EnumSet.of(BLACK);
        final Set<FileColor> colors = RPM_FILE_CLASS_TOKENS.get(ftype);

        if (colors != null) {
            fcolor.addAll(colors);
        }

        return fcolor;
    }

    private static String getFileExtension(final String fileName) {
        final int i = fileName.lastIndexOf(".");
        return i > fileName.length() - 1 ? fileName.substring(i) : "";
    }
}
