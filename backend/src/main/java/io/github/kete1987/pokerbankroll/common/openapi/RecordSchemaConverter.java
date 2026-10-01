package io.github.kete1987.pokerbankroll.common.openapi;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.Schema;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Makes the schemas of the request and response records say what the Java types say, so the
 * generated frontend types are exact: a component is required unless it is {@link Nullable}, in
 * which case it accepts {@code null}; and only the record components are properties (not other
 * accessors such as {@code isCashGame()}).
 */
@Component
class RecordSchemaConverter implements ModelConverter {

    private static final String BASE_PACKAGE = "io.github.kete1987.pokerbankroll";
    private static final String REF_PREFIX = "#/components/schemas/";
    private static final String NULL = "null";

    @Override
    @SuppressWarnings("rawtypes")
    public @Nullable Schema resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
        Schema schema = chain.hasNext() ? chain.next().resolve(type, context, chain) : null;
        if (schema == null) {
            return null;
        }
        Class<?> javaClass = Json.mapper().constructType(type.getType()).getRawClass();
        if (javaClass.isRecord() && javaClass.getPackageName().startsWith(BASE_PACKAGE)) {
            Schema model = schema.get$ref() == null
                    ? schema
                    : context.getDefinedModels().get(schema.get$ref().substring(REF_PREFIX.length()));
            if (model != null && model.getProperties() != null) {
                describe(model, javaClass.getRecordComponents());
            }
        }
        return schema;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void describe(Schema model, RecordComponent[] components) {
        Map<String, Schema> properties = model.getProperties();
        Set<String> names = new LinkedHashSet<>();
        List<String> required = new ArrayList<>();
        for (RecordComponent component : components) {
            Schema property = properties.get(component.getName());
            if (property == null) {
                continue;
            }
            names.add(component.getName());
            if (component.getAnnotatedType().isAnnotationPresent(Nullable.class)) {
                properties.put(component.getName(), nullable(property));
            } else {
                required.add(component.getName());
            }
        }
        properties.keySet().retainAll(names);
        model.setRequired(required.isEmpty() ? null : required);
    }

    /** OpenAPI 3.1: a nullable type is {@code ["string", "null"]}; a nullable reference, {@code anyOf}. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Schema nullable(Schema property) {
        if (property.get$ref() != null) {
            Schema reference = new Schema().$ref(property.get$ref());
            Schema wrapper = new Schema().description(property.getDescription());
            wrapper.setAnyOf(List.of(reference, new Schema().types(Set.of(NULL))));
            return wrapper;
        }
        Set<String> types = property.getTypes() == null ? new LinkedHashSet<>() : new LinkedHashSet<>(property.getTypes());
        if (types.isEmpty() && property.getType() != null) {
            types.add(property.getType());
        }
        if (!types.isEmpty() && types.add(NULL)) {
            property.setTypes(types);
        }
        return property;
    }
}
