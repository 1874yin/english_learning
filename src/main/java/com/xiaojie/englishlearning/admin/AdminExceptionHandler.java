package com.xiaojie.englishlearning.admin;

import com.xiaojie.englishlearning.lesson.LessonConflictException;
import java.io.IOException;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

@RestControllerAdvice
public class AdminExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AdminExceptionHandler.class);

    @ExceptionHandler(AdminValidationException.class)
    public ResponseEntity<ApiError> validation(AdminValidationException ex) {
        return ResponseEntity.badRequest().body(new ApiError(ex.getMessage()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiError> notFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(ex.getMessage() == null ? "素材不存在。" : ex.getMessage()));
    }

    @ExceptionHandler(LessonConflictException.class)
    public ResponseEntity<ApiError> lessonConflict(LessonConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(ex.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> tooLarge(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new ApiError("音频文件过大，请上传不超过 50 MB 的 MP3。"));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiError> multipart(MultipartException ex) {
        return ResponseEntity.badRequest().body(new ApiError("上传数据不完整，请重新选择文件后提交。"));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<ApiError> io(IOException ex) {
        log.error("Storage operation failed", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("保存素材失败，请查看服务端日志。"));
    }
}
