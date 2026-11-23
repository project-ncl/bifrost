package org.jboss.pnc.bifrost.source.db.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.ws.rs.BadRequestException;

import org.jboss.pnc.common.concurrent.Sequence;
import org.jboss.pnc.common.pnc.LongBase32IdConverter;
import org.junit.jupiter.api.Test;

public class IdConverterTest {

    private final IdConverter converter = new IdConverter();

    @Test
    public void invalidValueShouldThrowBadRequestWithRawInput() {
        BadRequestException e = assertThrows(BadRequestException.class, () -> converter.convert("build-abc"));
        assertTrue(e.getMessage().contains("build-abc"));
    }

    @Test
    public void validValuesShouldConvert() {
        long id = Sequence.nextId();
        String base32 = LongBase32IdConverter.toString(id);
        assertEquals(id, converter.convert(base32));
        assertEquals(id, converter.convert("build-" + base32));
    }

    @Test
    public void nullShouldConvertToNull() {
        assertNull(converter.convert(null));
    }
}
