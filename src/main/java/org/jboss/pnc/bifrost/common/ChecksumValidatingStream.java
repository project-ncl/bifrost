/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2021 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.pnc.bifrost.common;

import jakarta.validation.ValidationException;
import jakarta.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class ChecksumValidatingStream extends InputStream {

    protected volatile InputStream in;

    private final MessageDigest md5;

    private final String md5sum;

    private long size = 0;

    private ChecksumValidatingStream(InputStream stream, MessageDigest md5, String md5sum) {
        this.in = stream;
        this.md5 = md5;
        this.md5sum = md5sum;
    }

    public static ChecksumValidatingStream validate(InputStream is, String md5sum) {
        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        DigestInputStream md5dis = new DigestInputStream(is, md5);
        return new ChecksumValidatingStream(md5dis, md5, md5sum);
    }

    public void validate() throws ValidationException {
        String md5computedSum = DatatypeConverter.printHexBinary(md5.digest());
        if (!md5computedSum.equalsIgnoreCase(md5sum)) {
            throw new ValidationException(
                    "Stream validation failed, expected " + md5sum + " got " + md5computedSum + ".");
        }
    }

    public long readSize() {
        return size;
    }

    public int read() throws IOException {
        int read = this.in.read();
        if (read >= 0) {
            size++;
        }
        return read;
    }

    public int read(byte[] b) throws IOException {
        int read = this.read(b, 0, b.length);
        if (read >= 0) {
            size += read;
        }
        return read;
    }

    public int read(byte[] b, int off, int len) throws IOException {
        int read = this.in.read(b, off, len);
        if (read >= 0) {
            size += read;
        }
        return read;
    }

    public long skip(long n) throws IOException {
        long skip = this.in.skip(n);
        size += skip;
        return skip;
    }

    public int available() throws IOException {
        return this.in.available();
    }

    public void close() throws IOException {
        this.in.close();
    }

}
