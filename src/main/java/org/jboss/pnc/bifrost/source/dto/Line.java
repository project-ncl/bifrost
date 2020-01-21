package org.jboss.pnc.bifrost.source.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.annotation.JsonbProperty;

/**
 * @author <a href="mailto:matejonnet@gmail.com">Matej Lazar</a>
 */
@Getter
@Setter
@ToString
public class Line {

    private String id;

    @JsonbProperty("@timestamp")
    private String timestamp;

    private String loggerName;

    private String message;

    private boolean last;

    private String ctx;

    private Boolean tmp;

    private String exp;

    private String subscriptionTopic;

    public static Builder newBuilder() {
        return new Builder();
    }

    public static Line fromString(String json) {
        Jsonb jsonb = JsonbBuilder.create();
        return jsonb.fromJson(json, Line.class);
    }

    public static Builder newBuilder(Line copy) {
        Builder builder = new Builder();
        builder.id = copy.getId();
        builder.timestamp = copy.getTimestamp();
        builder.loggerName = copy.getLoggerName();
        builder.message = copy.getMessage();
        builder.last = copy.isLast();
        builder.ctx = copy.getCtx();
        builder.tmp = copy.getTmp();
        builder.exp = copy.getExp();
        return builder;
    }

    public String asString() {
        return getTimestamp() + " " + getLoggerName() + " " + getMessage();
    }

//    @JsonPOJOBuilder(withPrefix = "")
//    public static final class LineBuilder {
//    }

    public Builder cloneBuilder() {
        return newBuilder(this);
    }

    public static final class Builder {

        private String id;

        private String timestamp;

        private String loggerName;

        private String message;

        private boolean last;

        private String ctx;

        private Boolean tmp;

        private String exp;

        private Builder() {
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder timestamp(String timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder logger(String logger) {
            this.loggerName = logger;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder last(boolean last) {
            this.last = last;
            return this;
        }

        public Builder ctx(String ctx) {
            this.ctx = ctx;
            return this;
        }

        public Builder tmp(Boolean tmp) {
            this.tmp = tmp;
            return this;
        }

        public Builder exp(String exp) {
            this.exp = exp;
            return this;
        }

        public Line build() {
            Line line = new Line();
            line.id = this.id;
            line.timestamp = this.timestamp;
            line.loggerName = this.loggerName;
            line.message = this.message;
            line.last = this.last;
            line.ctx = this.ctx;
            line.tmp = this.tmp;
            line.exp = this.exp;
            return line;
        }
    }
}
