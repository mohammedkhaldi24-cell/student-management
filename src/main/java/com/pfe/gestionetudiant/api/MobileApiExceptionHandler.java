package com.pfe.gestionetudiant.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice(basePackages = "com.pfe.gestionetudiant.api")
public class MobileApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(MobileApiExceptionHandler.class);

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<MobileDtos.ApiMessage> handleBusinessException(Exception ex) {
        return ResponseEntity.badRequest().body(new MobileDtos.ApiMessage(ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<MobileDtos.ApiMessage> handleForbidden(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new MobileDtos.ApiMessage("Acces refuse."));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<MobileDtos.ApiMessage> handleFileTooLarge(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new MobileDtos.ApiMessage("Fichier trop volumineux."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<MobileDtos.ApiMessage> handleUnexpected(Exception ex) {
        log.error("Unexpected mobile api error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new MobileDtos.ApiMessage("Erreur interne du serveur."));
    }
}
