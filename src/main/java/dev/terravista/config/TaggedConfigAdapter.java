package dev.terravista.config;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/** 按 TYPE 字段选择 JSON 子类型并读写配置对象。 */
final class TaggedConfigAdapter<T> implements TypeAdapterFactory {
    private final Class<T> family;
    private final Map<String, Class<? extends T>> names = new HashMap<>();
    private final Map<Class<? extends T>, String> types = new HashMap<>();
    TaggedConfigAdapter(Class<T> family) { this.family = family; }
    TaggedConfigAdapter<T> register(String name, Class<? extends T> type) {
        if (names.put(name, type) != null) throw new IllegalStateException("TerraVista Config: Type name already registered: " + name);
        if (types.put(type, name) != null) throw new IllegalStateException("TerraVista Config: Class already registered with type name: " + name + ", " + type);
        return this;
    }
    private T read(Gson gson, JsonElement json) {
        var type = names.get(json.getAsJsonObject().remove("TYPE").getAsString());
        return gson.getDelegateAdapter(this, TypeToken.get(type)).fromJsonTree(json);
    }
    @SuppressWarnings("unchecked")
    private JsonElement write(Gson gson, T value) {
        String name = types.get(value.getClass());
        if (name == null) name = "UNKNOWN_TYPE_{" + value.getClass().getName() + "}";
        var fields = gson.getDelegateAdapter(this, TypeToken.get((Class<T>) value.getClass())).toJsonTree(value);
        var json = new JsonObject();
        json.addProperty("TYPE", name);
        fields.getAsJsonObject().asMap().forEach(json::add);
        return json;
    }
    @Override @SuppressWarnings("unchecked")
    public <X> TypeAdapter<X> create(Gson gson, TypeToken<X> type) {
        if (!family.isAssignableFrom(type.getRawType())) return null;
        var json = gson.getAdapter(JsonElement.class);
        return (TypeAdapter<X>) new TypeAdapter<T>() {
            @Override public void write(JsonWriter output, T value) throws IOException { json.write(output, TaggedConfigAdapter.this.write(gson, value)); }
            @Override public T read(JsonReader input) throws IOException { return TaggedConfigAdapter.this.read(gson, json.read(input)); }
        };
    }
}
