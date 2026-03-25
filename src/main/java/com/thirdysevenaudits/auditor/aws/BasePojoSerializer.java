/*
 * Copyright © 2026 37 Audits (thiago.moreira@37audits.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.thirdysevenaudits.auditor.aws;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;

import com.amazonaws.services.lambda.runtime.CustomPojoSerializer;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public class BasePojoSerializer implements CustomPojoSerializer {

    private static final ObjectMapper mapper = createMapper();

    private static ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(SerializationFeature.FLUSH_AFTER_WRITE_VALUE);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper;
    }

    public BasePojoSerializer() {
    }

    @Override
    public <T> T fromJson(InputStream input, Type type) {
        try {
            JavaType javaType = mapper.getTypeFactory().constructType(type);
            return mapper.readValue(input, javaType);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public <T> T fromJson(String input, Type type) {
        try {
            JavaType javaType = mapper.getTypeFactory().constructType(type);
            return mapper.readValue(input, javaType);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public <T> void toJson(T value, OutputStream output, Type type) {
        try {
            mapper.writeValue(output, value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
