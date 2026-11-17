package com.example.pairup.shared.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tratamento centralizado de exceções.
 *
 * Regras de segurança aplicadas aqui:
 *  - Stack traces NUNCA chegam ao cliente — são apenas logados no servidor.
 *  - Erros de negócio (ResponseStatusException) retornam o status/mensagem intencionais.
 *  - Erros inesperados retornam 500 com mensagem genérica.
 *  - Erros de validação retornam 400 com a lista de campos inválidos.
 *  - Arquivo grande retorna 413 com mensagem amigável.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Erros de negócio lançados explicitamente pelos controllers/services */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        return buildResponse(ex.getStatusCode().value(), ex.getReason());
    }

    /** Erros de @Valid / @NotBlank — retorna lista de campos inválidos */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        List<String> erros = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.toList());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 400);
        body.put("erro", "Dados inválidos");
        body.put("campos", erros);
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.badRequest().body(body);
    }

    /** Arquivo acima do limite configurado em spring.servlet.multipart.max-file-size */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUpload(MaxUploadSizeExceededException ex) {
        return buildResponse(HttpStatus.PAYLOAD_TOO_LARGE.value(),
                "Arquivo muito grande. O tamanho máximo permitido é 5 MB.");
    }

    /** JSON malformado, parâmetro ausente ou de tipo errado (ex.: enum inválido): 400, não 500 */
    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class, HandlerMethodValidationException.class,
            MultipartException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception ex) {
        log.debug("Requisição inválida: {}", ex.getClass().getSimpleName());
        return buildResponse(HttpStatus.BAD_REQUEST.value(), "Requisição inválida");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return buildResponse(HttpStatus.METHOD_NOT_ALLOWED.value(), "Método não suportado");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return buildResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(), "Content-Type não suportado");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NoResourceFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND.value(), "Recurso não encontrado");
    }

    /** Qualquer outro erro inesperado — loga no servidor, mensagem genérica ao cliente */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        // Stack trace apenas no servidor — nunca exposto ao cliente
        log.error("Erro interno não tratado: {}", ex.getClass().getName(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Ocorreu um erro interno. Tente novamente mais tarde.");
    }

    private ResponseEntity<Map<String, Object>> buildResponse(int status, String mensagem) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        body.put("erro", mensagem);
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(status).body(body);
    }
}