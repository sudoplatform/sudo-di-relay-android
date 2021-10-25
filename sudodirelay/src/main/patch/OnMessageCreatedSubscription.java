package com.sudoplatform.sudodirelay.graphql;

import com.apollographql.apollo.api.InputFieldMarshaller;
import com.apollographql.apollo.api.InputFieldWriter;
import com.apollographql.apollo.api.Operation;
import com.apollographql.apollo.api.OperationName;
import com.apollographql.apollo.api.ResponseField;
import com.apollographql.apollo.api.ResponseFieldMapper;
import com.apollographql.apollo.api.ResponseFieldMarshaller;
import com.apollographql.apollo.api.ResponseReader;
import com.apollographql.apollo.api.ResponseWriter;
import com.apollographql.apollo.api.Subscription;
import com.apollographql.apollo.api.internal.UnmodifiableMapBuilder;
import com.apollographql.apollo.api.internal.Utils;
import com.sudoplatform.sudodirelay.graphql.type.CustomType;
import com.sudoplatform.sudodirelay.graphql.type.Direction;
import java.io.IOException;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Generated;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Generated("Apollo GraphQL")
public final class OnMessageCreatedSubscription implements Subscription<OnMessageCreatedSubscription.Data, OnMessageCreatedSubscription.Data, OnMessageCreatedSubscription.Variables> {
    public static final String OPERATION_DEFINITION = "subscription OnMessageCreated($connectionId: ID!, $direction: Direction!) {\n"
            + "  onMessageCreated(connectionId: $connectionId, direction: $direction) {\n"
            + "    __typename\n"
            + "    messageId\n"
            + "    connectionId\n"
            + "    cipherText\n"
            + "    direction\n"
            + "    utcTimestamp\n"
            + "    nextToken\n"
            + "  }\n"
            + "}";

    public static final String QUERY_DOCUMENT = OPERATION_DEFINITION;

    private static final OperationName OPERATION_NAME = new OperationName() {
        @Override
        public String name() {
            return "OnMessageCreated";
        }
    };

    private final OnMessageCreatedSubscription.Variables variables;

    public OnMessageCreatedSubscription(@Nonnull String connectionId, @Nonnull Direction direction) {
        Utils.checkNotNull(connectionId, "connectionId == null");
        Utils.checkNotNull(direction, "direction == null");
        variables = new OnMessageCreatedSubscription.Variables(connectionId, direction);
    }

    @Override
    public String operationId() {
        return "bafbdbfad4919af2b82d7c9fa0f3e580a3885161895c7bbd02b706d41a9f6e44";
    }

    @Override
    public String queryDocument() {
        return QUERY_DOCUMENT;
    }

    @Override
    public OnMessageCreatedSubscription.Data wrapData(OnMessageCreatedSubscription.Data data) {
        return data;
    }

    @Override
    public OnMessageCreatedSubscription.Variables variables() {
        return variables;
    }

    @Override
    public ResponseFieldMapper<OnMessageCreatedSubscription.Data> responseFieldMapper() {
        return new Data.Mapper();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public OperationName name() {
        return OPERATION_NAME;
    }

    public static final class Builder {
        private @Nonnull String connectionId;

        private @Nonnull Direction direction;

        Builder() {
        }

        public Builder connectionId(@Nonnull String connectionId) {
            this.connectionId = connectionId;
            return this;
        }

        public Builder direction(@Nonnull Direction direction) {
            this.direction = direction;
            return this;
        }

        public OnMessageCreatedSubscription build() {
            Utils.checkNotNull(connectionId, "connectionId == null");
            Utils.checkNotNull(direction, "direction == null");
            return new OnMessageCreatedSubscription(connectionId, direction);
        }
    }

    public static final class Variables extends Operation.Variables {
        private final @Nonnull String connectionId;

        private final @Nonnull Direction direction;

        private final transient Map<String, Object> valueMap = new LinkedHashMap<>();

        Variables(@Nonnull String connectionId, @Nonnull Direction direction) {
            this.connectionId = connectionId;
            this.direction = direction;
            this.valueMap.put("connectionId", connectionId);
            this.valueMap.put("direction", direction.name()); // modification for JSONObject() parsing
        }

        public @Nonnull String connectionId() {
            return connectionId;
        }

        public @Nonnull Direction direction() {
            return direction;
        }

        @Override
        public Map<String, Object> valueMap() {
            return Collections.unmodifiableMap(valueMap);
        }

        @Override
        public InputFieldMarshaller marshaller() {
            return new InputFieldMarshaller() {
                @Override
                public void marshal(InputFieldWriter writer) throws IOException {
                    writer.writeString("connectionId", connectionId);
                    writer.writeString("direction", direction.name());
                }
            };
        }
    }

    public static class Data implements Operation.Data {
        static final ResponseField[] $responseFields = {
                ResponseField.forObject("onMessageCreated", "onMessageCreated", new UnmodifiableMapBuilder<String, Object>(2)
                        .put("connectionId", new UnmodifiableMapBuilder<String, Object>(2)
                                .put("kind", "Variable")
                                .put("variableName", "connectionId")
                                .build())
                        .put("direction", new UnmodifiableMapBuilder<String, Object>(2)
                                .put("kind", "Variable")
                                .put("variableName", "direction")
                                .build())
                        .build(), true, Collections.<ResponseField.Condition>emptyList())
        };

        final @Nullable OnMessageCreated onMessageCreated;

        private volatile String $toString;

        private volatile int $hashCode;

        private volatile boolean $hashCodeMemoized;

        public Data(@Nullable OnMessageCreated onMessageCreated) {
            this.onMessageCreated = onMessageCreated;
        }

        public @Nullable OnMessageCreated onMessageCreated() {
            return this.onMessageCreated;
        }

        public ResponseFieldMarshaller marshaller() {
            return new ResponseFieldMarshaller() {
                @Override
                public void marshal(ResponseWriter writer) {
                    writer.writeObject($responseFields[0], onMessageCreated != null ? onMessageCreated.marshaller() : null);
                }
            };
        }

        @Override
        public String toString() {
            if ($toString == null) {
                $toString = "Data{"
                        + "onMessageCreated=" + onMessageCreated
                        + "}";
            }
            return $toString;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (o instanceof Data) {
                Data that = (Data) o;
                return ((this.onMessageCreated == null) ? (that.onMessageCreated == null) : this.onMessageCreated.equals(that.onMessageCreated));
            }
            return false;
        }

        @Override
        public int hashCode() {
            if (!$hashCodeMemoized) {
                int h = 1;
                h *= 1000003;
                h ^= (onMessageCreated == null) ? 0 : onMessageCreated.hashCode();
                $hashCode = h;
                $hashCodeMemoized = true;
            }
            return $hashCode;
        }

        public static final class Mapper implements ResponseFieldMapper<Data> {
            final OnMessageCreated.Mapper onMessageCreatedFieldMapper = new OnMessageCreated.Mapper();

            @Override
            public Data map(ResponseReader reader) {
                final OnMessageCreated onMessageCreated = reader.readObject($responseFields[0], new ResponseReader.ObjectReader<OnMessageCreated>() {
                    @Override
                    public OnMessageCreated read(ResponseReader reader) {
                        return onMessageCreatedFieldMapper.map(reader);
                    }
                });
                return new Data(onMessageCreated);
            }
        }
    }

    public static class OnMessageCreated {
        static final ResponseField[] $responseFields = {
                ResponseField.forString("__typename", "__typename", null, false, Collections.<ResponseField.Condition>emptyList()),
                ResponseField.forCustomType("messageId", "messageId", null, false, CustomType.ID, Collections.<ResponseField.Condition>emptyList()),
                ResponseField.forCustomType("connectionId", "connectionId", null, false, CustomType.ID, Collections.<ResponseField.Condition>emptyList()),
                ResponseField.forString("cipherText", "cipherText", null, false, Collections.<ResponseField.Condition>emptyList()),
                ResponseField.forString("direction", "direction", null, false, Collections.<ResponseField.Condition>emptyList()),
                ResponseField.forString("utcTimestamp", "utcTimestamp", null, false, Collections.<ResponseField.Condition>emptyList()),
                ResponseField.forString("nextToken", "nextToken", null, true, Collections.<ResponseField.Condition>emptyList())
        };

        final @Nonnull String __typename;

        final @Nonnull String messageId;

        final @Nonnull String connectionId;

        final @Nonnull String cipherText;

        final @Nonnull Direction direction;

        final @Nonnull String utcTimestamp;

        final @Nullable String nextToken;

        private volatile String $toString;

        private volatile int $hashCode;

        private volatile boolean $hashCodeMemoized;

        public OnMessageCreated(@Nonnull String __typename, @Nonnull String messageId,
                                @Nonnull String connectionId, @Nonnull String cipherText, @Nonnull Direction direction,
                                @Nonnull String utcTimestamp, @Nullable String nextToken) {
            this.__typename = Utils.checkNotNull(__typename, "__typename == null");
            this.messageId = Utils.checkNotNull(messageId, "messageId == null");
            this.connectionId = Utils.checkNotNull(connectionId, "connectionId == null");
            this.cipherText = Utils.checkNotNull(cipherText, "cipherText == null");
            this.direction = Utils.checkNotNull(direction, "direction == null");
            this.utcTimestamp = Utils.checkNotNull(utcTimestamp, "utcTimestamp == null");
            this.nextToken = nextToken;
        }

        public @Nonnull String __typename() {
            return this.__typename;
        }

        public @Nonnull String messageId() {
            return this.messageId;
        }

        public @Nonnull String connectionId() {
            return this.connectionId;
        }

        public @Nonnull String cipherText() {
            return this.cipherText;
        }

        public @Nonnull Direction direction() {
            return this.direction;
        }

        public @Nonnull String utcTimestamp() {
            return this.utcTimestamp;
        }

        public @Nullable String nextToken() {
            return this.nextToken;
        }

        public ResponseFieldMarshaller marshaller() {
            return new ResponseFieldMarshaller() {
                @Override
                public void marshal(ResponseWriter writer) {
                    writer.writeString($responseFields[0], __typename);
                    writer.writeCustom((ResponseField.CustomTypeField) $responseFields[1], messageId);
                    writer.writeCustom((ResponseField.CustomTypeField) $responseFields[2], connectionId);
                    writer.writeString($responseFields[3], cipherText);
                    writer.writeString($responseFields[4], direction.name());
                    writer.writeString($responseFields[5], utcTimestamp);
                    writer.writeString($responseFields[6], nextToken);
                }
            };
        }

        @Override
        public String toString() {
            if ($toString == null) {
                $toString = "OnMessageCreated{"
                        + "__typename=" + __typename + ", "
                        + "messageId=" + messageId + ", "
                        + "connectionId=" + connectionId + ", "
                        + "cipherText=" + cipherText + ", "
                        + "direction=" + direction + ", "
                        + "utcTimestamp=" + utcTimestamp + ", "
                        + "nextToken=" + nextToken
                        + "}";
            }
            return $toString;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (o instanceof OnMessageCreated) {
                OnMessageCreated that = (OnMessageCreated) o;
                return this.__typename.equals(that.__typename)
                        && this.messageId.equals(that.messageId)
                        && this.connectionId.equals(that.connectionId)
                        && this.cipherText.equals(that.cipherText)
                        && this.direction.equals(that.direction)
                        && this.utcTimestamp.equals(that.utcTimestamp)
                        && ((this.nextToken == null) ? (that.nextToken == null) : this.nextToken.equals(that.nextToken));
            }
            return false;
        }

        @Override
        public int hashCode() {
            if (!$hashCodeMemoized) {
                int h = 1;
                h *= 1000003;
                h ^= __typename.hashCode();
                h *= 1000003;
                h ^= messageId.hashCode();
                h *= 1000003;
                h ^= connectionId.hashCode();
                h *= 1000003;
                h ^= cipherText.hashCode();
                h *= 1000003;
                h ^= direction.hashCode();
                h *= 1000003;
                h ^= utcTimestamp.hashCode();
                h *= 1000003;
                h ^= (nextToken == null) ? 0 : nextToken.hashCode();
                $hashCode = h;
                $hashCodeMemoized = true;
            }
            return $hashCode;
        }

        public static final class Mapper implements ResponseFieldMapper<OnMessageCreated> {
            @Override
            public OnMessageCreated map(ResponseReader reader) {
                final String __typename = reader.readString($responseFields[0]);
                final String messageId = reader.readCustomType((ResponseField.CustomTypeField) $responseFields[1]);
                final String connectionId = reader.readCustomType((ResponseField.CustomTypeField) $responseFields[2]);
                final String cipherText = reader.readString($responseFields[3]);
                final String directionStr = reader.readString($responseFields[4]);
                final Direction direction;
                if (directionStr != null) {
                    direction = Direction.valueOf(directionStr);
                } else {
                    direction = null;
                }
                final String utcTimestamp = reader.readString($responseFields[5]);
                final String nextToken = reader.readString($responseFields[6]);
                return new OnMessageCreated(__typename, messageId, connectionId, cipherText, direction, utcTimestamp, nextToken);
            }
        }
    }
}