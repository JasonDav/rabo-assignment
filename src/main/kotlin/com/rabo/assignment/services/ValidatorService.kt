package com.rabo.assignment.services

import com.rabo.assignment.controllers.ApplicationException
import fr.marcwrobel.jbanking.iban.Iban
import fr.marcwrobel.jbanking.iban.IbanFormatException
import org.apache.commons.csv.CSVFormat
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.xml.sax.Attributes
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.IOException
import java.io.InputStream
import java.io.Reader
import javax.xml.parsers.SAXParser
import javax.xml.parsers.SAXParserFactory
import kotlin.math.log


@Service
class ValidatorService() {

    var logger: Logger = LoggerFactory.getLogger(this::class.java)

    companion object {
        const val referenceHeader = "Reference"
        const val accountNumberHeader = "Account Number"
        const val descriptionHeader = "Description"
        const val startBalanceHeader = "Start Balance"
        const val mutationHeader = "Mutation"
        const val endBalanceHeader = "End Balance"

        val csvFormat: CSVFormat = CSVFormat.DEFAULT.builder()
            .setHeader(referenceHeader, accountNumberHeader, descriptionHeader, startBalanceHeader, mutationHeader, endBalanceHeader)
            .setSkipHeaderRecord(true)
            .build()

        private val factory: SAXParserFactory = SAXParserFactory.newInstance()
        val saxParser: SAXParser = factory.newSAXParser()
        val recordXMLHandler = RecordHandler()
    }

    class InvalidRecord(
        private val reference: String?,
    ) {
        val errors = mutableListOf<String>()

        fun addError(error: String) = errors.add(error)
        override fun toString(): String {
            return "InvalidRecord(reference=$reference, errors=$errors)"
        }

    }

    fun validateXML(inputStream: InputStream) {
        saxParser.parse(inputStream, recordXMLHandler);
        logger.debug(recordXMLHandler.records.toString())
    }

    fun validateCSV(csvFile: Reader): Map<String, MutableList<InvalidRecord>> {
        val records = try { csvFormat.parse(csvFile) } catch (ioException: IOException) { logger.error(ioException.message); null } ?: throw ApplicationException.BadRequest("Could not parse file")
        // In large files sequential lookups every iteration will be expensive and slow
        val invalidRecords: HashMap<String, MutableList<InvalidRecord>> = hashMapOf()

        // Perform Validation row-by-row
        for (record in records) {
            // Validate Reference
            // No assumption made on length of reference number
            val reference = record[referenceHeader].toULongOrNull()?.toString()
            val invalidRecord = InvalidRecord(reference)

            validateIBAN(record[accountNumberHeader], invalidRecord)
            // Assuming description can be null
//            record[ValidatorService.descriptionHeader] ?: invalidRecord.addError("Description is null")

            val startBalance = record[startBalanceHeader].toBigDecimalOrNull()
            if (startBalance == null) {
                invalidRecord.addError("Start Balance is null")
            }

            val mutation = record[mutationHeader]?.let {
                when (it.first()) {
                    '+', '-' -> it.substring(1)
                    else -> {
                        invalidRecord.addError("Mutation does not start with '+' or '-'")
                        it
                    }

                }.toBigDecimalOrNull()
            }
            if (mutation == null) {
                invalidRecord.addError("Mutation is not a valid decimal value")
            }

            val endBalance = record[endBalanceHeader].toBigDecimalOrNull()
            if (endBalance == null) {
                invalidRecord.addError("End Balance is null")
            }

            // Validate balance
            if (startBalance != null && mutation != null && endBalance != null) {
                val calculatedBalance = startBalance.plus(mutation)
                if (calculatedBalance != endBalance) {
                    invalidRecord.addError("End balance is not correct: ($startBalance + $mutation) $calculatedBalance != $endBalance")
                }
            }

            if (invalidRecords.containsKey(reference)) {
                invalidRecords[reference]?.add(invalidRecord)
            } else {
                invalidRecords[reference!!] = mutableListOf(invalidRecord)
            }
        }

        // Validate reference duplicates
        invalidRecords.filter { it.value.size > 1 }.forEach { it.value.forEach { error -> error.addError("Reference ${it.key} is duplicated.") } }
        logger.debug(invalidRecords.toString())

        // Return only records with errors
        return invalidRecords.filter { it.value.all { record -> record.errors.isNotEmpty() } }
    }

    // Validate Reference number
    // Check if type is correct
    // TODO references cannot be negative or zero?
    private fun validateRecordNumber(raw: String): ULong {
        return raw.toULongOrNull() ?: throw ValidationException("Reference number is not a valid number")
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
    private fun validateIBAN(raw: String?, invalidRecord: InvalidRecord) {
        try {
            // Something like https://www.swift.com/our-solutions/compliance-and-shared-services/swiftref/swiftref-payments-processing/payment-data-files/iban-plus?akcorpredir=true
            // should be used to validate IBANS for now use a library.
            Iban(raw)
        } catch (e: IbanFormatException) {
            invalidRecord.addError("$raw is not a valid IBAN")
        } catch (e: IllegalArgumentException) {
            invalidRecord.addError("IBAN is null")
        }
    }

}

class ValidationException(msg: String): RuntimeException(msg)


class Record (
    val reference: String? = null
) {
    var accountNumber: String? = null
    var description: String? = null
    var startBalance: String? = null
    var mutation: String? = null
    var endBalance: String? = null
    override fun toString(): String {
        return "Record(reference=$reference, accountNumber=$accountNumber, description=$description, startBalance=$startBalance, mutation=$mutation, endBalance=$endBalance)"
    }


}

class RecordHandler : DefaultHandler() {
    var logger: Logger = LoggerFactory.getLogger(this::class.java)

    val records = mutableListOf<Record>()
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
            // Levels
            RECORDS -> logger.debug("Reading list of records")
            RECORD -> records.add(Record(attr?.getValue(REFERENCE_HEADER)))

            // Record fields
            ACCOUNT_NUMBER_HEADER -> elementValue = StringBuilder()
            DESCRIPTION_HEADER -> elementValue = StringBuilder()
            START_BALANCE_HEADER -> elementValue = StringBuilder()
            MUTATION_HEADER -> elementValue = StringBuilder()
            END_BALANCE_HEADER -> elementValue = StringBuilder()
        }
    }

    @Throws(SAXException::class)
    override fun endElement(uri: String?, localName: String?, qName: String?) {
        when (qName) {
            ACCOUNT_NUMBER_HEADER -> latestRecord().accountNumber = elementValue.toString()
            DESCRIPTION_HEADER -> latestRecord().description = elementValue.toString()
            START_BALANCE_HEADER -> latestRecord().startBalance = elementValue.toString()
            MUTATION_HEADER -> latestRecord().mutation = elementValue.toString()
            END_BALANCE_HEADER -> latestRecord().endBalance = elementValue.toString()
        }
    }

    private fun latestRecord(): Record {
        return records.last()
    }

    companion object {
        private const val RECORDS = "records"
        private const val RECORD = "record"

        private const val REFERENCE_HEADER = "reference"
        private const val ACCOUNT_NUMBER_HEADER = "accountNumber"
        private const val DESCRIPTION_HEADER = "description"
        private const val START_BALANCE_HEADER = "startBalance"
        private const val MUTATION_HEADER = "mutation"
        private const val END_BALANCE_HEADER = "endBalance"
    }
}
