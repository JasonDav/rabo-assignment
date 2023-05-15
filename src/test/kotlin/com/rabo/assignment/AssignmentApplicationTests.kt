package com.rabo.assignment

import com.rabo.assignment.services.ValidatorService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.io.FileSystemResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.test.context.ActiveProfiles
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import java.io.File
import java.nio.file.Files
import java.nio.file.Path


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AssignmentApplicationTests @Autowired constructor(
	private val restTemplate: TestRestTemplate,
	private val validatorService: ValidatorService
) {


	@Test
	fun contextLoads() {
	}

	@Test
	fun testUploadXMLFile() {
		val response = uploadFile("xml/records.xml")
		println(response)
	}

	@Test
	fun testUploadCSVFile() {
		val headers = HttpHeaders()
		headers.contentType = MediaType.MULTIPART_FORM_DATA

		val csv = getFileResource("csv/records.csv")

		val body: MultiValueMap<String, Any> = LinkedMultiValueMap()
		body.add("file", csv)

		val response = restTemplate
			.postForEntity("/validator/file", HttpEntity(body, headers), String::class.java)
		println(response)
	}

	@Test
	fun shouldValidateCSV() {
		validatorService.validateCSV(getFile("csv/records.csv").reader())
	}

	@Test
	fun shouldValidateCSV__Should_report_3_null_ibans() {
		validatorService.validateCSV(getFile("csv/null_iban.csv").reader())
	}

	private fun uploadFile(path: String): ResponseEntity<String>? {
		val headers = HttpHeaders()
		headers.contentType = MediaType.MULTIPART_FORM_DATA

		val csv = getFileResource(path)

		val body: MultiValueMap<String, Any> = LinkedMultiValueMap()
		body.add("file", csv)

		return restTemplate
			.postForEntity("/validator/file", HttpEntity(body, headers), String::class.java)
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


private fun getFileBytes(path:String): ByteArray {
	return Files.readAllBytes(getPath(path))
}
