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

package org.eclipse.packager.rpm.build;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import org.eclipse.packager.rpm.Architecture;
import org.eclipse.packager.rpm.OperatingSystem;
import org.eclipse.packager.rpm.RpmLead;
import org.eclipse.packager.rpm.RpmTag;
import org.eclipse.packager.rpm.RpmVersion;
import org.eclipse.packager.rpm.Type;
import org.eclipse.packager.rpm.header.Header;

public class LeadBuilder {
    private String name;

    private RpmVersion version;

    private Type type = Type.BINARY;

    private short architecture;

    private short operatingSystem;

    public LeadBuilder() {
    }

    public LeadBuilder(final String name, final RpmVersion version) {
        this.name = name;
        this.version = version;
    }

    public void setType(final Type type) {
        this.type = type;
    }

    public String getName() {
        return this.name;
    }

    public Type getType() {
        return this.type;
    }

    public RpmVersion getVersion() {
        return this.version;
    }

    public short getArchitecture() {
        return this.architecture;
    }

    public short getOperatingSystem() {
        return this.operatingSystem;
    }

    public void fillFlagsFromHeader(final Header<RpmTag> header, final Function<String, Optional<Architecture>> architectureMapper, final Function<String, Optional<OperatingSystem>> operatingSystemMapper) {
        Objects.requireNonNull(header);
        Objects.requireNonNull(architectureMapper);
        Objects.requireNonNull(operatingSystemMapper);

        final byte[] osValue = header.get(RpmTag.OS).asByteArray();
        final byte[] archValue = header.get(RpmTag.ARCH).asByteArray();

        final String os0 = osValue != null && osValue.length > 1 ? new String(osValue, StandardCharsets.UTF_8) : null;
        final String arch0 = archValue != null && archValue.length > 1 ? new String(archValue, StandardCharsets.UTF_8) : null;

        if (os0 != null) {
            final String os = os0.substring(0, os0.length() - 1);
            this.architecture = architectureMapper.apply(os).orElse(Architecture.NOARCH).getValue();
        }
        if (arch0 != null) {
            final String arch = arch0.substring(0, arch0.length() - 1);
            this.operatingSystem = operatingSystemMapper.apply(arch).orElse(OperatingSystem.UNKNOWN).getValue();
        }
    }

    public void fillFlagsFromHeader(final Header<RpmTag> header) {
        fillFlagsFromHeader(header, Architecture::fromAlias, OperatingSystem::fromAlias);
    }

    public RpmLead build() {
        if (this.name == null || this.name.isEmpty()) {
            throw new IllegalStateException("A name must be set");
        }
        if (this.version == null) {
            throw new IllegalStateException("A version must be set");
        }
        return new RpmLead((byte) 3, (byte) 0, RpmLead.toLeadName(this.name, this.version), 5, this.type.getValue(), this.architecture, this.operatingSystem);
    }
}
