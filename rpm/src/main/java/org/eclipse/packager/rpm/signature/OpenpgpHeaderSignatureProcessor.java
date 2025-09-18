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

package org.eclipse.packager.rpm.signature;

import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureGenerator;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentSignerBuilder;
import org.eclipse.packager.rpm.HashAlgorithm;
import org.eclipse.packager.rpm.RpmSignatureTag;
import org.eclipse.packager.rpm.header.Header;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * An RSA signature processor for the header section only.
 */
public class OpenpgpHeaderSignatureProcessor implements SignatureProcessor {
    private final List<PGPPrivateKey> privateKeys;

    private final List<Integer> hashAlgorithms;

    private String[] values;

    public OpenpgpHeaderSignatureProcessor(final List<PGPPrivateKey> privateKeys, final List<HashAlgorithm> hashAlgorithms) {
        Objects.requireNonNull(privateKeys);
        this.privateKeys = privateKeys;
        this.hashAlgorithms = hashAlgorithms.stream().map(HashAlgorithm::getValue).collect(Collectors.toList());
        this.values = new String[privateKeys.size()];
    }

    @Override
    public void feedHeader(final ByteBuffer header) {
        try {
            for (int i = 0; i < this.privateKeys.size(); i++) {
                final PGPPrivateKey privateKey = this.privateKeys.get(i);
                final int hashAlgorithm = this.hashAlgorithms.get(i);
                this.values[i] = signHeader(header, privateKey, hashAlgorithm);
            }
        } catch (final PGPException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String signHeader(final ByteBuffer header, final PGPPrivateKey privateKey, final int hashAlgorithm) throws PGPException, IOException {
        final BcPGPContentSignerBuilder contentSignerBuilder = new BcPGPContentSignerBuilder(privateKey.getPublicKeyPacket().getAlgorithm(), hashAlgorithm);
        final PGPSignatureGenerator signatureGenerator = new PGPSignatureGenerator(contentSignerBuilder);

        signatureGenerator.init(PGPSignature.BINARY_DOCUMENT, privateKey);

        if (header.hasArray()) {
            signatureGenerator.update(header.array(), header.position(), header.remaining());
        } else {
            final byte[] buffer = new byte[header.remaining()];
            header.get(buffer);
            signatureGenerator.update(buffer);
        }

        final byte[] encoded = signatureGenerator.generate().getEncoded();
        Base64.Encoder encoder = Base64.getEncoder();
        return encoder.encodeToString(encoded);
    }

    @Override
    public void feedPayloadData(final ByteBuffer data) {
        // we only work on the header data
    }

    @Override
    public void finish(final Header<RpmSignatureTag> signature) {
        signature.putStringArray(RpmSignatureTag.OPENPGP, this.values);
    }
}
