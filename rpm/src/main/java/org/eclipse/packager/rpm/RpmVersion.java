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

import com.google.common.base.CharMatcher;

import java.util.Objects;
import java.util.Optional;

public class RpmVersion implements Comparable<RpmVersion> {
    private static final CharMatcher DIGIT_MATCHER = CharMatcher.inRange('0', '9');

    private static final CharMatcher ALPHA_MATCHER = CharMatcher.inRange('a', 'z').or(CharMatcher.inRange('A', 'Z'));

    private static final CharMatcher ALPHANUM_MATCHER = DIGIT_MATCHER.or(ALPHA_MATCHER);

    public static final CharMatcher NAME_MATCHER = ALPHANUM_MATCHER.or(CharMatcher.anyOf(".-_+%{}"));

    public static final CharMatcher FIRSTCHARS_NAME_MATCHER = ALPHANUM_MATCHER.or(CharMatcher.anyOf("_%"));

    public static final CharMatcher VERREL_MATCHER = ALPHANUM_MATCHER.or(CharMatcher.anyOf("._+~^"));

    public static final CharMatcher EVR_MATCHER = VERREL_MATCHER.or(CharMatcher.anyOf("-:"));

    private final Optional<Integer> epoch;

    private final String version;

    private final Optional<String> release;

    public RpmVersion(final String version) {
        this(version, null);
    }

    public RpmVersion(final String version, final String release) {
        this(null, version, release);
    }

    public RpmVersion(final Integer epoch, final String version, final String release) {
        this(Optional.ofNullable(epoch), version, Optional.ofNullable(release));
    }

    public RpmVersion(final Optional<Integer> epoch, final String version, final Optional<String> release) {
        this.epoch = Objects.requireNonNull(epoch);
        this.version = checkChars(Objects.requireNonNull(version), VERREL_MATCHER);
        this.release = Objects.requireNonNull(release);
        this.release.ifPresent(s -> checkChars(s, VERREL_MATCHER));
    }

    public Optional<Integer> getEpoch() {
        return this.epoch;
    }

    public String getVersion() {
        return this.version;
    }

    public Optional<String> getRelease() {
        return this.release;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();

        this.epoch.ifPresent(v -> sb.append(v).append(':'));

        sb.append(this.version);

        if (this.release.isPresent() && !this.release.get().isEmpty()) {
            sb.append('-').append(this.release.get());
        }

        return sb.toString();
    }

    public static RpmVersion valueOf(final String version) {
        if (version == null || version.isEmpty()) {
            return null;
        }

        checkChars(version, EVR_MATCHER);

        final String[] toks1 = version.split(":", 2);

        final String n;
        Integer epoch = null;
        if (toks1.length > 1) {
            final String epochStr = toks1[0];
            checkChars(epochStr, DIGIT_MATCHER);
            epoch = Integer.parseInt(epochStr);
            n = toks1[1];
        } else {
            n = toks1[0];
        }

        final String[] toks2 = n.split("-", 2);

        final String ver = toks2[0];
        final String rel = toks2.length > 1 ? toks2[1] : null;

        return new RpmVersion(epoch, ver, rel);
    }

    public static String checkChars(final String field, final CharMatcher allowedCharsMatcher) {
        return checkChars(field, allowedCharsMatcher, null);
    }

    public static String checkChars(final String field, final CharMatcher allowedCharsMatcher, final CharMatcher allowedFirstCharsMatcher) {
        int start;

        if (allowedFirstCharsMatcher == null) {
            start = 0;
        } else {
            char c = field.charAt(0);

            if (!allowedFirstCharsMatcher.matches(c)) {
                throw new IllegalArgumentException("Illegal char '" + c + "' (0x" + Integer.toHexString(c) + ") in '" + field + "'");
            }

            start = 1;
        }

        int length = field.length();

        for (int i = start; i < length; i++) {
            char c = field.charAt(i);

            if (!allowedCharsMatcher.matches(c)) {
                throw new IllegalArgumentException("Illegal char '" + c + "' (0x" + Integer.toHexString(c) + ") in '" + field + "'");
            }
        }

        if (field.contains("..")) {
            throw new IllegalArgumentException("Illegal sequence '..' in '" + field + "'");
        }

        return field;
   }

    public static int compare(final String a, final String b) {
        if (a.equals(b)) {
            return 0;
        }

        RpmVersionScanner scanner1 = new RpmVersionScanner(a);
        RpmVersionScanner scanner2 = new RpmVersionScanner(b);

        while (scanner1.hasNext() || scanner2.hasNext()) {
            if (scanner1.hasNextTilde() || scanner2.hasNextTilde()) {
                if (!scanner1.hasNextTilde()) {
                    return 1;
                }

                if (!scanner2.hasNextTilde()) {
                    return -1;
                }

                scanner1.next();
                scanner2.next();
                continue;
            }

            if (scanner1.hasNextCarat() || scanner2.hasNextCarat()) {
                if (!scanner1.hasNext()) {
                    return -1;
                }

                if (!scanner2.hasNext()) {
                    return 1;
                }

                if (!scanner1.hasNextCarat()) {
                    return 1;
                }

                if (!scanner2.hasNextCarat()) {
                    return -1;
                }

                scanner1.next();
                scanner2.next();
                continue;
            }

            if (scanner1.hasNextAlpha() && scanner2.hasNextAlpha()) {
                final CharSequence one = scanner1.next();
                final CharSequence two = scanner2.next();
                final int i = CharSequence.compare(one, two);

                if (i != 0) {
                    return signum(i);
                }
            } else {
                final boolean digit1 = scanner1.hasNextDigit();
                final boolean digit2 = scanner2.hasNextDigit();

                if (digit1 && digit2) {
                    final CharSequence one = scanner1.next();
                    final CharSequence two = scanner2.next();
                    final int oneLength = one.length();
                    final int twoLength = two.length();

                    if (oneLength > twoLength) {
                        return 1;
                    }

                    if (twoLength > oneLength) {
                        return -1;
                    }

                    final int i = CharSequence.compare(one, two);

                    if (i != 0) {
                        return signum(i);
                    }
                } else if (digit1) {
                    return 1;
                } else if (digit2) {
                    return -1;
                } else if (scanner1.hasNext()) {
                    return 1;
                } else {
                    return -1;
                }
            }
        }

        return 0;
    }

    private static int signum(final int i) {
        return (i < 0 ? -1 : 1);
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        RpmVersion that = (RpmVersion) o;
        return Objects.equals(epoch, that.epoch) && Objects.equals(version, that.version) && Objects.equals(release, that.release);
    }

    @Override
    public int hashCode() {
        return Objects.hash(epoch, version, release);
    }

    @Override
    public int compareTo(final RpmVersion version) {
        if (epoch.isPresent() && version.epoch.isEmpty()) {
            return 1;
        } else if (epoch.isEmpty() && version.epoch.isPresent()) {
            return -1;
        }

        int signum = Integer.compare(epoch.orElse(0), version.epoch.orElse(0));

        if (signum != 0) {
            return signum;
        }

        signum =  compare(this.version, version.version);

        if (signum != 0) {
            return signum;
        }

        if (release.isPresent() && version.release.isEmpty()) {
            return 1;
        } else if (release.isEmpty() && version.release.isPresent()) {
            return -1;
        } else if (release.isPresent()) {
            return compare(release.get(), version.release.get());
        }

        return 0;
    }
}
