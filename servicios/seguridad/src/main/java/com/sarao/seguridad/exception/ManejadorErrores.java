package com.sarao.seguridad.exception;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;

@Slf4j
@RestControllerAdvice
public class ManejadorErrores extends ResponseEntityExceptionHandler {

	@ExceptionHandler(ErrorNegocioException.class)
	public ProblemDetail manejarErrorNegocio(ErrorNegocioException ex) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(ex.getEstadoHttp(), ex.getMessage());
		problema.setProperty("codigo", ex.getCodigo());
		return problema;
	}

	@ExceptionHandler(AuthenticationException.class)
	public ProblemDetail manejarNoAutenticado(AuthenticationException ex) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
				"Debe iniciar sesión para acceder a este recurso.");
		problema.setProperty("codigo", "NO_AUTENTICADO");
		return problema;
	}

	// Sin este handler, el de Exception convertiría el rechazo por rol en un 500
	@ExceptionHandler(AccessDeniedException.class)
	public ProblemDetail manejarAccesoDenegado(AccessDeniedException ex) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
				"No tiene permiso para acceder a este recurso.");
		problema.setProperty("codigo", "ACCESO_DENEGADO");
		return problema;
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		return datosInvalidos("Hay campos a corregir.", ex.getBindingResult().getFieldErrors().stream()
				.map(violacion -> error(violacion.getField(),
						violacion.getDefaultMessage() == null ? "no es válido" : violacion.getDefaultMessage()))
				.toList());
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		return datosInvalidos("La solicitud tiene datos con formato inválido.", campoIlegible(ex)
				.map(campo -> List.of(error(campo, "no tiene un formato válido")))
				.orElse(List.of()));
	}

	// Parámetros de la ruta o de la consulta que no se pueden convertir, como un rol inexistente o un id no numérico
	@Override
	protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		return datosInvalidos("La solicitud tiene datos con formato inválido.", ex.getPropertyName() == null
				? List.of()
				: List.of(error(ex.getPropertyName(), "no tiene un formato válido")));
	}

	@ExceptionHandler(Exception.class)
	public ProblemDetail manejarErrorInesperado(Exception ex) {
		log.error("Error no controlado", ex);
		return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado.");
	}

	private static ResponseEntity<Object> datosInvalidos(String detalle, List<Map<String, String>> errores) {
		ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detalle);
		problema.setProperty("codigo", "DATOS_INVALIDOS");
		problema.setProperty("errores", errores);
		return ResponseEntity.badRequest().body(problema);
	}

	private static Map<String, String> error(String campo, String mensaje) {
		return Map.of("campo", campo, "mensaje", mensaje);
	}

	private static Optional<String> campoIlegible(HttpMessageNotReadableException ex) {
		for (Throwable causa = ex.getCause(); causa != null; causa = causa.getCause()) {
			if (causa instanceof JacksonException jackson && !jackson.getPath().isEmpty()) {
				return Optional.ofNullable(jackson.getPath().getLast().getPropertyName());
			}
		}
		return Optional.empty();
	}
}
