/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2020-2022 Red Hat, Inc., and individual contributors
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

import org.jboss.logging.Logger;

import javax.validation.ValidationException;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
public class DateUtil {

    private static Logger logger = Logger.getLogger(DateUtil.class);

    // accepts 2020-06-04T18:16:02.027+0000
    private static final DateTimeFormatter fallbackDateTimeFormatter = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXX");

    /**
     * Validate and try to fix date format
     */
    public static String validateAndFixInputDate(String dateTime) {
        if (dateTime == null) {
            return null;
        }
        TemporalAccessor parsed;
        try {
            parsed = DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(dateTime);
        } catch (DateTimeParseException e) {
            try {
                logger.warn("Received unexpected date format " + dateTime + ", converting to ISO_OFFSET_DATE_TIME ...");
                parsed = fallbackDateTimeFormatter.parse(dateTime);
            } catch (DateTimeParseException e1) {
                throw new ValidationException("Invalid date-time format, expected ISO_OFFSET_DATE_TIME.", e1);
            }
        }
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(parsed);
    }
}
