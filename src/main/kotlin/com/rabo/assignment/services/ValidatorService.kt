package com.rabo.assignment.services

import com.rabo.assignment.controllers.ApplicationException
import fr.marcwrobel.jbanking.iban.Iban
import fr.marcwrobel.jbanking.iban.IbanFormatException
import org.apache.commons.csv.CSVFormat
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import org.xml.sax.Attributes
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.math.BigDecimal
import javax.xml.parsers.SAXParser
import javax.xml.parsers.SAXParserFactory


const val START_BALANCE_NULL = "Start Balance is null"
const val END_BALANCE_NULL = "End Balance is null"
const val IBAN_IS_NULL = "IBAN is null"
const val MUTATION_START_CHAR = "Mutation does not start with '+' or '-'"
const val MUTATION_IS_EMPTY = "Mutation is empty"
const val END_BALANCE_CALC_MISSING = "End balance could not be calculated: Missing information."
const val MUTATION_DECIMAL = "Mutation is not a valid decimal value"

private const val CSV_FILE_EXTENSION = ".csv"
private const val XML_FILE_EXTENSION = ".xml"


@Service
class ValidatorService {

    var logger: Logger = LoggerFactory.getLogger(this::class.java)

    companion object {
        const val referenceHeader = "Reference"
        const val accountNumberHeader = "Account Number"
        const val descriptionHeader = "Description"
        const val startBalanceHeader = "Start Balance"
        const val mutationHeader = "Mutation"
        const val endBalanceHeader = "End Balance"

        val csvFormat: CSVFormat = CSVFormat.DEFAULT.builder()
            .setHeader(
                referenceHeader,
                accountNumberHeader,
                descriptionHeader,
                startBalanceHeader,
                mutationHeader,
                endBalanceHeader
            )
            .setSkipHeaderRecord(true)
            .build()

        private val factory: SAXParserFactory = SAXParserFactory.newInstance()
        val saxParser: SAXParser = factory.newSAXParser()
    }

    class InvalidRecord(
        private val reference: String?,
    ) {
        val errors = mutableListOf<String>()

        init {
            if (reference?.toULongOrNull() == null) {
                errors.add("Reference number is not valid: $reference")
            }
        }

        fun addError(error: String) = errors.add(error)
        override fun toString(): String {
            return "InvalidRecord(reference=$reference, errors=$errors)"
        }

    }

    /**
     * Detect duplicate errors and remove non-error records.
     */
    private fun filterRecords(records: Map<String, MutableList<InvalidRecord>>): Map<String, MutableList<InvalidRecord>> {
        records.filter { it.value.size > 1 }.forEach { it.value.forEach { error -> error.addError("Reference ${it.key} is duplicated.") } }
        logger.debug(records.toString())
        return records.filter { it.value.all { record -> record.errors.isNotEmpty() } }
    }

    fun validateXML(inputStream: InputStream):Map<String, MutableList<InvalidRecord>>  {
        val recordXMLHandler = RecordHandler()
        saxParser.parse(inputStream, recordXMLHandler)
        return filterRecords(recordXMLHandler.invalidRecords)
    }

    fun validateCSV(csvFile: Reader): Map<String, MutableList<InvalidRecord>> {
        val records = try { csvFormat.parse(csvFile) } catch (ioException: IOException) { logger.error(ioException.message); null } ?: throw ApplicationException.BadRequest("Could not parse file")
        val invalidRecords: HashMap<String, MutableList<InvalidRecord>> = hashMapOf()

        // Perform Validation row-by-row
        records.forEach { record ->
            // Validate Reference
            val reference = record[referenceHeader].toULongOrNull()?.toString()
            val invalidRecord = InvalidRecord(reference)

            validateIBAN(record[accountNumberHeader], invalidRecord)

            // Assuming description can be null

            val startBalance = record[startBalanceHeader].toBigDecimalOrNull()
            if (startBalance == null) {
                //TODO errors should be constants
                invalidRecord.addError(START_BALANCE_NULL)
            }

            val mutation = validateMutation(record[mutationHeader], invalidRecord)
            if (mutation == null) {
                invalidRecord.addError(MUTATION_DECIMAL)
            }

            val endBalance = record[endBalanceHeader].toBigDecimalOrNull()
            if (endBalance == null) {
                invalidRecord.addError(END_BALANCE_NULL)
            }

            // Validate balance
            validateBalances(startBalance, mutation, endBalance, invalidRecord)

            if (invalidRecords.containsKey(reference)) {
                invalidRecords[reference]?.add(invalidRecord)
            } else {
                invalidRecords[reference!!] = mutableListOf(invalidRecord)
            }
        }

        // Validate reference duplicates
        return filterRecords(invalidRecords)
    }

    fun validateFile(file: MultipartFile): Map<String, MutableList<InvalidRecord>>? {
        return if (file.originalFilename?.endsWith(CSV_FILE_EXTENSION) == true) {
            validateCSV(InputStreamReader(file.inputStream))
        } else if (file.originalFilename?.endsWith(XML_FILE_EXTENSION) == true) {
            validateXML(file.inputStream)
        } else {
            null
        }
    }

}


/**
 * An IBAN consists of a two-letter ISO 3166-1 country code, followed by two check digits and up to thirty alphanumeric
 * characters for a BBAN (Basic Bank Account Number) which has a fixed length per country and, included within it, a bank
 * identifier with a fixed position and a fixed length per country. The check digits are calculated based on the scheme defined
 * in ISO/IEC 7064 (MOD97-10). Note that an IBAN is case-insensitive.
 *
 * <p>
 * Specified by the <a href="https://www.iso13616.org">ISO 13616:2007 standard</a>.
 */
fun validateIBAN(raw: String?, invalidRecord: ValidatorService.InvalidRecord) {
    try {
        // Something like https://www.swift.com/our-solutions/compliance-and-shared-services/swiftref/swiftref-payments-processing/payment-data-files/iban-plus?akcorpredir=true
        // should be used to validate IBANS for now use a library.
        Iban(raw)
    } catch (e: IbanFormatException) {
        invalidRecord.addError("'$raw' is not a valid IBAN")
    } catch (e: IllegalArgumentException) {
        invalidRecord.addError(IBAN_IS_NULL)
    }
}


fun validateMutation(raw: String, invalidRecord: ValidatorService.InvalidRecord): BigDecimal? {
    if (raw.isBlank()) {
        invalidRecord.addError(MUTATION_IS_EMPTY)
        return null
    }
    return when (raw.first()) {
        '+' -> raw.substring(1).toBigDecimalOrNull()
        '-' -> raw.substring(1).toBigDecimalOrNull()?.negate()
        else -> {
            invalidRecord.addError(MUTATION_START_CHAR)
            null
        }
    }
}

fun validateBalances(
    startBalance: BigDecimal?,
    mutation: BigDecimal?,
    endBalance: BigDecimal?,
    invalidRecord: ValidatorService.InvalidRecord
) {
    if (startBalance != null && mutation != null && endBalance != null) {
        val calculatedBalance = startBalance.plus(mutation)
        if (calculatedBalance.compareTo(endBalance) != 0) {
            invalidRecord.addError("End balance is not correct: ($startBalance + $mutation) $calculatedBalance != $endBalance")
        }
    } else  {
        invalidRecord.addError(END_BALANCE_CALC_MISSING)
    }
}

class RecordHandler : DefaultHandler() {
    private var logger: Logger = LoggerFactory.getLogger(this::class.java)

    private var lastRecord: ValidatorService.InvalidRecord? = null
    val invalidRecords: HashMap<String, MutableList<ValidatorService.InvalidRecord>> = hashMapOf()

    private var startBalance: BigDecimal? = null
    private var mutation: BigDecimal? = null
    private var endBalance: BigDecimal? = null

    private var elementValue: StringBuilder? = null
    @Throws(SAXException::class)
    override fun characters(ch: CharArray?, start: Int, length: Int) {
        if (elementValue == null) {
            elementValue = StringBuilder()
        } else {
            elementValue!!.appendRange(ch!!, start, start + length)
        }
    }

    @Throws(SAXException::class)
    override fun startDocument() {
        logger.debug("Starting document read")
    }

    @Throws(SAXException::class)
    override fun startElement(uri: String?, lName: String?, qName: String?, attr: Attributes?) {
        when (qName) {
            RECORD -> {
                attr?.getValue(REFERENCE_HEADER).let {
                    lastRecord = ValidatorService.InvalidRecord(it)
                    invalidRecords[it]?.add(lastRecord!!) ?: invalidRecords.put(it ?: "null", mutableListOf(lastRecord!!))
                }
            }

            // Record fields
            else -> elementValue = StringBuilder()
        }
    }

    @Throws(SAXException::class)
    override fun endElement(uri: String?, localName: String?, qName: String?) {
        when (qName) {
            ACCOUNT_NUMBER_HEADER -> validateIBAN(elementValue.toString(), lastRecord!!)
            START_BALANCE_HEADER -> {
                startBalance = elementValue.toString().toBigDecimalOrNull()
                startBalance ?: lastRecord?.addError(START_BALANCE_NULL)
            }
            MUTATION_HEADER -> mutation = validateMutation(elementValue.toString(), lastRecord!!)
            END_BALANCE_HEADER -> {
                endBalance = elementValue.toString().toBigDecimalOrNull()
                endBalance ?: lastRecord?.addError(END_BALANCE_NULL)
            }
            RECORD -> {
                if (lastRecord != null) {
                    validateBalances(startBalance, mutation, endBalance, lastRecord!!)
                }
                startBalance = null
                mutation = null
                endBalance = null
            }
        }
    }

    companion object {
        private const val RECORD = "record"

        private const val REFERENCE_HEADER = "reference"
        private const val ACCOUNT_NUMBER_HEADER = "accountNumber"
        private const val START_BALANCE_HEADER = "startBalance"
        private const val MUTATION_HEADER = "mutation"
        private const val END_BALANCE_HEADER = "endBalance"
    }
}
