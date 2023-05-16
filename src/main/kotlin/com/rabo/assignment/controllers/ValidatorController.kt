package com.rabo.assignment.controllers

import com.rabo.assignment.services.ValidatorService
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.context.request.WebRequest
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.io.InputStreamReader


@RestController
@RequestMapping("/validators")
class ValidatorController (
    val validatorService: ValidatorService
) {


    // TODO API Docs
    @PostMapping("/files")
    fun validateMT940(@RequestParam("file") file: MultipartFile?): ResponseEntity<Any> {
        if (file == null || file.isEmpty) return ResponseEntity.badRequest().build()

        val report = validatorService.validateFile(file)
        return if (report != null) ResponseEntity.ok(report) else ResponseEntity.badRequest().build()
    }

    @PostMapping("/spring")
    fun handleFileUpload(
        @RequestParam("file") file: MultipartFile,
        redirectAttributes: RedirectAttributes
    ): String? {
        redirectAttributes.addFlashAttribute(
            "message",
            "You successfully uploaded " + file.originalFilename + "!"
        )
        return "redirect:/"
    }

}

sealed class ApplicationException(val status: Int = HttpStatus.BAD_REQUEST.value(), message: String?) : RuntimeException(message) {
    constructor(status: HttpStatus, message: String?) : this(status.value(), message)

    class BadRequest(message: String?): ApplicationException(HttpStatus.BAD_REQUEST, message)
}

@Order
@RestControllerAdvice
class RestExceptionHandler {

    @ExceptionHandler(Exception::class)
    fun processException(exception: Exception?): ResponseEntity<Any> {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
    }

    @ExceptionHandler(ApplicationException::class)
    fun handleDomainExceptions(ex: ApplicationException, request: WebRequest): ResponseEntity<String> {
        return ResponseEntity.status(ex.status).body(ex.message)
    }
}
