package com.f4xizzz.serverbuddy.cobblemon.boss;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;

public final class LenientEnumTypeAdapterFactory implements TypeAdapterFactory {
   @SuppressWarnings({"unchecked", "rawtypes"})
   public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
      Class<? super T> rawType = type.getRawType();
      if (rawType.isEnum() && Enum.class.isAssignableFrom(rawType)) {
         final Enum<?>[] constants = (Enum[])rawType.getEnumConstants();
         if (constants.length == 0) {
            return null;
         }

         return (TypeAdapter<T>)(TypeAdapter)new TypeAdapter<Enum<?>>() {
            public void write(JsonWriter out, Enum<?> value) throws IOException {
               out.value(value == null ? null : value.name());
            }

            public Enum<?> read(JsonReader in) throws IOException {
               if (in.peek() == JsonToken.NULL) {
                  in.nextNull();
                  return constants[0];
               } else {
                  String raw = in.nextString();
                  if (raw != null && !raw.isBlank()) {
                     for(Enum<?> constant : constants) {
                        if (constant.name().equalsIgnoreCase(raw.trim())) {
                           return constant;
                        }
                     }

                     return constants[0];
                  } else {
                     return constants[0];
                  }
               }
            }
         };
      } else {
         return null;
      }
   }
}
