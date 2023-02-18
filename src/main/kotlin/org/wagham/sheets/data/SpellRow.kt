package org.wagham.sheets.data

import org.wagham.db.models.Spell
import org.wagham.sheets.GoogleSheetsUtils
import org.wagham.sheets.getHeaderMapping
import org.wagham.utils.formatToInt
import org.wagham.utils.toListOfMap

class SpellRow(
    val operation: ImportOperation = ImportOperation.UPDATE,
    val spell: Spell
) {

    companion object {

        private val sheetId = System.getenv("DND_SHEET_ID")!!
        private const val range = "All_Spells!A1:P600"

        fun parseRows(): List<SpellRow> =
            GoogleSheetsUtils
                .downloadRawDataFromSheet(sheetId, range)
                .let { values ->
                    val header = values.getHeaderMapping()
                    values.getValues()
                        .subList(1, values.getValues().size)
                        .toListOfMap(header, 0)
                        .fold(emptyList()) { acc, it ->
                            acc + SpellRow(
                                spell = Spell(
                                    name = it["Nome Spell"]!!,
                                    level = it["Livello"]!!.formatToInt(),
                                    srd = it["SRD"] == "Si",
                                    school = it["Scuola"]!!,
                                    dndClass = it["Spell List"]!!.split(","),
                                    ritual = it["Rituale"] == "TRUE",
                                    link = it["Link"]!!,
                                    manual = it["Manuale"]!!
                                )
                            )
                        }
                }

    }

}