package com.rabo.assignment

import com.rabo.assignment.services.ValidatorService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.io.FileSystemResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.context.ActiveProfiles
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import java.io.File
import java.nio.file.Path


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AssignmentApplicationTests @Autowired constructor(
	private val restTemplate: TestRestTemplate,
	private val validatorService: ValidatorService
) {

	@Test
	fun contextLoads() {}

	@Test
	fun testUploadBadFileNameFile() {
		val response = uploadFile("bad_file.bad")
		assert(response?.statusCode == HttpStatus.BAD_REQUEST)
	}

	@Test
	fun testUploadXMLFile() {
		val response = uploadFile("xml/records.xml")
		assert(response?.statusCode == HttpStatus.OK)
		println(response)
	}

	@Test
	fun testUploadCSVFile() {
		val response = uploadFile("csv/records.csv")
		assert(response?.statusCode == HttpStatus.OK)
		println(response)
	}

	@Test
	fun shouldValidateCSV() {
		validatorService.validateCSV(getFile("csv/records.csv").reader())
	}

	@Test
	fun shouldValidateCSV__Should_report_3_empty_ibans() {
		val report = validatorService.validateCSV(getFile("csv/null_iban.csv").reader())
		assert(report.any { it.value.any { record -> record.errors.any { error -> error.contains("is not a valid IBAN") } } })
	}

	@Test
	fun shouldValidateCSV__Should_survive_many_records() {
		// 20k records ~ 500ms = 0.025ms per record
		validatorService.validateCSV(getFile("csv/many_records.csv").reader())
	}

	@Test
	fun shouldValidateCSV__Should_error_on_bad_mutations() {
		val report = validatorService.validateCSV(getFile("csv/bad_mutations.csv").reader())
		assert(report.any { it.value.any { record -> record.errors.any { error -> error.contains("Mutation is not a valid decimal value") } } })
		assert(report.any { it.value.any { record -> record.errors.any { error -> error.contains("Mutation does not start with '+' or '-'") } } })
		assert(report.any { it.value.any { record -> record.errors.any { error -> error.contains("End balance could not be calculated: Missing information.") } } })
	}

	private fun uploadFile(path: String): ResponseEntity<String>? {
		val headers = HttpHeaders()
		headers.contentType = MediaType.MULTIPART_FORM_DATA

		val csv = getFileResource(path)

		val body: MultiValueMap<String, Any> = LinkedMultiValueMap()
		body.add("file", csv)

		return restTemplate.postForEntity("/validators/files", HttpEntity(body, headers), String::class.java)
	}
}

private fun getPath(path: String): Path {
	return Path.of(ClassLoader.getSystemResource(path).toURI())
}

private fun getFile(path: String): File {
	return Path.of(ClassLoader.getSystemResource(path).toURI()).toFile()
}

private fun getFileResource(path: String): FileSystemResource {
	return FileSystemResource(getPath(path))
}