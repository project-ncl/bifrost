package org.jboss.pnc.bifrost.common;

import org.junit.jupiter.api.Test;

import jakarta.validation.ValidationException;
import jakarta.xml.bind.DatatypeConverter;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.*;

class ChecksumValidatingStreamTest {

    @Test
    void validate() throws Exception {
        String hello = "hello";
        InputStream targetStream = new ByteArrayInputStream(hello.getBytes());

        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] theMD5digest = md.digest(hello.getBytes());
        String md5computedSum = DatatypeConverter.printHexBinary(theMD5digest);

        ChecksumValidatingStream validatingStream = ChecksumValidatingStream.validate(targetStream, md5computedSum);

        validatingStream.transferTo(OutputStream.nullOutputStream());

        validatingStream.validate();

        assertEquals(hello.getBytes(StandardCharsets.UTF_8).length, validatingStream.readSize());
    }

    @Test
    void validateFail() throws Exception {
        String hello = "hello";
        InputStream targetStream = new ByteArrayInputStream(hello.getBytes());

        // wrong sha
        ChecksumValidatingStream validatingStream = ChecksumValidatingStream.validate(targetStream, "abcd");

        validatingStream.transferTo(OutputStream.nullOutputStream());

        assertThrows(ValidationException.class, validatingStream::validate);
    }
}