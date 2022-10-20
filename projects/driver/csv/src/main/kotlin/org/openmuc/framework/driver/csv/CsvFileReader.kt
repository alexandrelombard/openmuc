/*
 * Copyright 2011-2022 Fraunhofer ISE
 *
 * This file is part of OpenMUC.
 * For more information visit http://www.openmuc.org
 *
 * OpenMUC is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * OpenMUC is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OpenMUC.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package org.openmuc.framework.driver.csv

import com.univocity.parsers.common.processor.ColumnProcessor
import com.univocity.parsers.csv.CsvParser
import com.univocity.parsers.csv.CsvParserSettings
import org.openmuc.framework.driver.spi.ConnectionException
import java.io.FileReader
import java.io.IOException

/**
 * Class to parse the CSV file into a map of column names and their respective list of values
 */
class CsvFileReader {
    var data: HashMap<String, List<Double>>? = null

    companion object {
        @JvmStatic
        @Throws(ConnectionException::class)
        fun readCsvFile(fileName: String): Map<String, List<String>> {

            // https://github.com/uniVocity/univocity-parsers#reading-columns-instead-of-rows
            val processor =
                ColumnProcessor()
            val parserSettings =
                CsvParserSettings()
            parserSettings.format.setLineSeparator("\n")
            parserSettings.isHeaderExtractionEnabled = true
            parserSettings.setProcessor(processor)
            val parser = CsvParser(parserSettings)
            val reader: FileReader
            try {
                reader = FileReader(fileName)
                parser.parse(reader)
                reader.close()
            } catch (e: IOException) {
                throw ConnectionException("Unable to parse file.", e)
            }

            // Finally, we can get the column values:
            return processor.columnValuesAsMapOfNames
        }
    }
}
