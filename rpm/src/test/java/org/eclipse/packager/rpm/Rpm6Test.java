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

package org.eclipse.packager.rpm;

import io.github.dwalluck.libmagic.MagicException;
import io.github.dwalluck.libmagic.MagicLibrary;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.eclipse.packager.rpm.app.Dumper;
import org.eclipse.packager.rpm.build.BuilderContext;
import org.eclipse.packager.rpm.build.BuilderOptions;
import org.eclipse.packager.rpm.build.RpmBuilder;
import org.eclipse.packager.rpm.build.RpmFileNameProvider;
import org.eclipse.packager.rpm.coding.PayloadCoding;
import org.eclipse.packager.rpm.coding.PayloadFlags;
import org.eclipse.packager.rpm.parse.InputHeader;
import org.eclipse.packager.rpm.parse.RpmInputStream;
import org.eclipse.packager.rpm.signature.OpenpgpHeaderSignatureProcessor;
import org.eclipse.packager.security.pgp.PgpHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static io.github.dwalluck.libmagic.MagicFlag.CHECK;
import static io.github.dwalluck.libmagic.MagicFlag.COMPRESS;
import static io.github.dwalluck.libmagic.MagicFlag.ERROR;
import static io.github.dwalluck.libmagic.MagicFlag.MIME_TYPE;
import static io.github.dwalluck.libmagic.MagicFlag.NO_CHECK_TOKENS;
import static java.util.EnumSet.of;
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
import static org.apache.commons.compress.archivers.cpio.CpioConstants.S_IFMT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.packager.rpm.FileColor.BLACK;
import static org.eclipse.packager.rpm.FileColor.INCLUDE;
import static org.eclipse.packager.rpm.FileColor.WHITE;
import static org.eclipse.packager.rpm.RpmTag.DEPENDS_DICT;
import static org.eclipse.packager.rpm.RpmTag.FILE_COLORS;
import static org.eclipse.packager.rpm.RpmTag.FILE_DEPENDSN;
import static org.eclipse.packager.rpm.RpmTag.FILE_DEPENDSX;
import static org.eclipse.packager.rpm.RpmTag.FILE_MIME_INDEX;
import static org.eclipse.packager.rpm.RpmTag.MIME_DICT;
import static org.eclipse.packager.rpm.RpmTag.PAYLOAD_CODING;
import static org.eclipse.packager.rpm.RpmTag.PAYLOAD_FLAGS;
import static org.eclipse.packager.rpm.RpmTag.PAYLOAD_FORMAT;
import static org.eclipse.packager.rpm.RpmTag.RPM_FORMAT;

class Rpm6Test {
    private static final Logger logger = LoggerFactory.getLogger(Rpm6Test.class);

    @Test
    void testReadExistingRpm() throws IOException {
        try (final RpmInputStream in = new RpmInputStream(new BufferedInputStream(Files.newInputStream(Path.of("src/test/resources/data/rpm-5.99.90-1.fc41.x86_64.rpm"))))) {
            assertThat(in).isNotNull();
            Dumper.dumpAll(in);
            final InputHeader<RpmTag> header = in.getPayloadHeader();

            assertThat(header.getInteger(RPM_FORMAT)).isEqualTo(6);
            assertThat(header.getString(PAYLOAD_CODING)).isEqualTo("zstd");
            assertThat(header.getString(PAYLOAD_FLAGS)).isEqualTo("19");

            assertThat(header.getIntegerList(FILE_COLORS)).hasSize(290);
            assertThat(header.getIntegerList(FILE_DEPENDSX)).hasSize(302);
            assertThat(header.getIntegerList(FILE_DEPENDSN)).hasSize(290);
            assertThat(header.getIntegerList(DEPENDS_DICT)).hasSize(61);

            assertThat(header.getIntegerList(FILE_MIME_INDEX)).hasSize(290);
            // Maybe use Files.probeContentType?
            assertThat(header.getStringList(MIME_DICT)).hasSize(11).containsExactly("inode/directory",
                "application/x-pie-executable",
                "inode/symlink",
                "text/plain",
                "text/x-shellscript",
                "inode/x-empty",
                "application/octet-stream",
                "application/vnd.sqlite3",
                "text/x-c",
                "application/x-gettext-translation",
                "text/troff");

        }
    }

    @Test
    void testReadWriteRpm(final @TempDir Path outBase) throws IOException {
        final String name = "issue-24-test";
        final String version = "1.0.0";
        final String release = "1";
        final String architecture = "noarch";
        final String expectedRpmFileName = name + "-" + version + "-" + release + "." + architecture + ".rpm";
        final BuilderOptions options = new BuilderOptions();
        options.setFileNameProvider(RpmFileNameProvider.DEFAULT_FILENAME_PROVIDER);
        options.setRpmFormat(6);
        options.setPayloadCoding(PayloadCoding.ZSTD);
        options.setPayloadFlags(new PayloadFlags(PayloadCoding.ZSTD, 19));

        try (final RpmBuilder builder = new RpmBuilder(name, new RpmVersion(version, release), architecture, outBase, options)) {
            final Path outFile = builder.getTargetFile();
            builder.build();
            final String rpmFileName = options.getFileNameProvider().getRpmFileName(builder.getName(), builder.getVersion(), builder.getArchitecture());
            assertThat(rpmFileName).isEqualTo(expectedRpmFileName);
            assertThat(outFile.getFileName()).hasToString(expectedRpmFileName);
        }

        try (final RpmInputStream in = new RpmInputStream(Files.newInputStream(outBase.resolve(expectedRpmFileName)))) {
            Dumper.dumpAll(in);
            final InputHeader<RpmTag> header = in.getPayloadHeader();
            assertThat(header.getInteger(RPM_FORMAT)).isEqualTo(6);
            assertThat(header.getString(PAYLOAD_FORMAT)).isEqualTo("cpio");
            assertThat(header.getString(PAYLOAD_CODING)).isEqualTo(PayloadCoding.ZSTD.getValue());
            assertThat(header.getString(PAYLOAD_FLAGS)).isEqualTo("19");
            assertThat(header.getLong(RpmTag.PAYLOAD_SIZE)).isEqualTo(124L);
            assertThat(header.getLong(RpmTag.PAYLOAD_SIZE_ALT)).isEqualTo(47L);
        }

    }

    private static PGPPrivateKey getPrivateKey(final String keyId, final String password) throws IOException, PGPException {
        try (final InputStream stream = Files.newInputStream(Path.of("src/test/resources/key/myseckeys.asc"))) {
            return PgpHelper.loadPrivateKey(stream, keyId, password);
        }
    }

    @Test
    void testSignature(final @TempDir Path outBase) throws IOException, PGPException {
        final Path outFile;

        final BuilderOptions options = new BuilderOptions();
        options.setRpmFormat(6);

        try (final RpmBuilder builder = new RpmBuilder("test3", RpmVersion.valueOf("1.0.0-1"), "noarch", outBase, options)) {
            final RpmBuilder.PackageInformation pinfo = builder.getInformation();

            pinfo.setLicense("EPL");
            pinfo.setSummary("Foo bar");
            pinfo.setVendor("Eclipse Package Drone Project");
            pinfo.setDescription("This is a test package");
            pinfo.setDistribution("Eclipse Package Drone");

            final BuilderContext ctx = builder.newContext();

            ctx.addDirectory("/etc/test3");
            ctx.addDirectory("etc/test3/a");
            ctx.addDirectory("//etc/test3/b");
            ctx.addDirectory("/etc/");

            ctx.addDirectory("/var/lib/test3", finfo -> finfo.setUser(""));

            //ctx.addFile("/etc/test3/file1", IN_BASE.resolve("file1"), BuilderContext.pathProvider().customize(finfo -> finfo.setFileFlags(of(FileFlags.CONFIGURATION))));

            ctx.addFile("/etc/test3/file2", new ByteArrayInputStream("foo".getBytes(StandardCharsets.UTF_8)), finfo -> {
                finfo.setTimestamp(LocalDateTime.of(2014, 1, 1, 0, 0).toInstant(ZoneOffset.UTC));
                finfo.setFileFlags(of(FileFlags.CONFIGURATION));
            });

            ctx.addSymbolicLink("/etc/test3/file3", "/etc/test3/file1");

            builder.setPreInstallationScript("true # test call");

            final PGPPrivateKey privateKey1 = getPrivateKey("BE23B2E50DE857D8F35DE56ECF9639A55B155F7D", "123");
            final PGPPrivateKey privateKey2 = getPrivateKey("E1431CF2AB5A73408A381FF0F44D281C8F2EAD97", "123");
            final List<PGPPrivateKey> privateKeys = List.of(privateKey1, privateKey2);
            final List<HashAlgorithm> hashAlgorithms = List.of(HashAlgorithm.SHA512, HashAlgorithm.SHA256);
            builder.addSignatureProcessor(new OpenpgpHeaderSignatureProcessor(privateKeys, hashAlgorithms));

            outFile = builder.getTargetFile();

            builder.build();

            assertThat(outFile).exists();

            System.out.format("Minimum required RPM version: %s%n", builder.getRequiredRpmVersion());

            assertThat(builder.getRequiredRpmVersion()).isEqualTo(RpmBuilder.Version.V5_99);
        }

        try (final RpmInputStream in = new RpmInputStream(Files.newInputStream(outFile))) {
            final InputHeader<RpmSignatureTag> sigHeader = in.getSignatureHeader();
            final List<String> encodedSignatures = sigHeader.getStringList(RpmSignatureTag.OPENPGP);
            assertThat(encodedSignatures).hasSize(2);
            final List<byte[]> signatures = encodedSignatures.stream().map(encoded -> Base64.getDecoder().decode(encoded)).collect(Collectors.toList());
            assertThat(signatures).hasSize(2);
            assertThat(signatures.get(0)).hasSize(96).asBase64Encoded().isEqualTo(encodedSignatures.get(0));
            assertThat(signatures.get(1)).hasSize(543).asBase64Encoded().isEqualTo(encodedSignatures.get(1));
        }
    }
}
