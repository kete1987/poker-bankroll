package io.github.kete1987.pokerbankroll.mcp;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

import io.github.kete1987.pokerbankroll.common.error.ApiException;
import io.github.kete1987.pokerbankroll.common.error.ErrorCode;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

/** One schema, typed conversion, validation and error path for both read and write tools. */
@Component
@ConditionalOnProperty(prefix = "poker-bankroll.mcp", name = "enabled", havingValue = "true")
class McpToolExecutor {

    private static final int MAX_RESULT_BYTES = 1024 * 1024;
    private static final Logger log = LoggerFactory.getLogger(McpToolExecutor.class);
    private final JsonMapper mapper;
    private final JsonMapper inputMapper;
    private final Validator validator;
    private final MessageSource messages;
    private final JsonSchemaValidator schemas = McpJsonDefaults.getSchemaValidator();

    McpToolExecutor(JsonMapper mapper, Validator validator, MessageSource messages) {
        this.mapper = mapper;
        this.inputMapper = mapper.rebuild()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).build();
        this.validator = validator;
        this.messages = messages;
    }

    <T> SyncToolSpecification tool(String name, String description, Map<String, Object> schema,
            Class<T> inputType, ToolAnnotations annotations, Function<T, Object> action) {
        Tool tool = Tool.builder().name(name).description(description).inputSchema(schema).annotations(annotations).build();
        return new SyncToolSpecification(tool, (context, request) -> {
            Map<String, Object> arguments = request.arguments() == null ? Map.of() : request.arguments();
            var validation = schemas.validate(schema, arguments);
            if (!validation.valid()) {
                return error(ErrorCode.VALIDATION_FAILED, List.of(Map.of("field", "arguments", "code", "Schema",
                        "message", validation.errorMessage())));
            }
            T input;
            try {
                input = inputMapper.convertValue(arguments, inputType);
            } catch (IllegalArgumentException | tools.jackson.core.JacksonException ex) {
                return error(ErrorCode.VALIDATION_FAILED, List.of(Map.of("field", "arguments", "code", "Type",
                        "message", "Invalid parameter type or format; see the tool input schema.")));
            }
            try {
                validate(input);
                Object value = action.apply(input);
                Object result = value instanceof McpOperationService.OperationResult operation ? operation.resource() : value;
                String json = mapper.writeValueAsString(result);
                if (json.getBytes(StandardCharsets.UTF_8).length > MAX_RESULT_BYTES) {
                    return error(ErrorCode.MCP_RESULT_TOO_LARGE, List.of());
                }
                var response = CallToolResult.builder().structuredContent(result).addTextContent(json).isError(false);
                if (value instanceof McpOperationService.OperationResult operation) {
                    response.meta(Map.of("pokerBankrollOperation", operation.receipt()));
                }
                return response.build();
            } catch (ConstraintViolationException ex) {
                return error(ErrorCode.VALIDATION_FAILED, ex.getConstraintViolations().stream()
                        .map(v -> Map.of("field", v.getPropertyPath().toString(),
                                "code", v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName(),
                                "message", v.getMessage())).toList());
            } catch (ApiException ex) {
                return error(ex.getCode(), List.of(), ex.getArgs());
            } catch (DataIntegrityViolationException ex) {
                return error(ErrorCode.CONFLICT, List.of());
            } catch (Exception ex) {
                log.error("MCP tool {} failed", name, ex);
                return error(ErrorCode.INTERNAL_ERROR, List.of());
            }
        });
    }

    /** Also validate requests assembled from templates, after filling their fields. */
    void validate(Object input) {
        var violations = validator.validate(input);
        if (!violations.isEmpty()) throw new ConstraintViolationException(violations);
    }

    private CallToolResult error(ErrorCode code, List<?> violations, Object... args) {
        Map<String, Object> problem = Map.of("code", code.name(), "detail",
                messages.getMessage(code.messageKey(), args, code.name(), Locale.ENGLISH), "errors", violations);
        return CallToolResult.builder().isError(true).structuredContent(problem)
                .addTextContent(mapper.writeValueAsString(problem)).build();
    }
}

