package org.wagham.sheets.data

import org.wagham.db.models.Race
import org.wagham.sheets.GoogleSheetsUtils
import org.wagham.sheets.getHeaderMapping
import org.wagham.utils.toListOfMap

class RaceRow(
    val operation: ImportOperation = ImportOperation.UPDATE,
    val race: Race
) {

    companion object {

        private val sheetId = System.getenv("DND_SHEET_ID")!!
        private const val infoRange = "Razze!A1:J100"
        private const val refRange = "Ref!J1:K100"

        fun parseRows(): List<RaceRow> {
            val territoryLink = GoogleSheetsUtils
                .downloadRawDataFromSheet(sheetId, refRange)
                .let { values ->
                    val header = values.getHeaderMapping()
                    values.getValues()
                        .subList(1, values.getValues().size)
                        .toListOfMap(header, 0).associate {
                            it["Popolo"]!! to it["Link_Popolo"]!!
                        }
                }
            return GoogleSheetsUtils
                .downloadRawDataFromSheet(sheetId, infoRange)
                .let { values ->
                    val header = values.getHeaderMapping()
                    values.getValues()
                        .subList(1, values.getValues().size)
                        .toListOfMap(header, 0)
                        .fold(emptyList()) { acc, it ->
                            acc + RaceRow(
                                race = Race(
                                    id = "${it["Razza"]}:${it["Sottorazza"]}",
                                    race = it["Razza"]!!,
                                    subrace = it["Sottorazza"]!!.takeIf { subrace -> subrace != "no" },
                                    link = it["Link Sottorazza"]!!,
                                    territories = (1 .. 7).mapNotNull { idx ->
                                        if(it["T_${idx}"] != null)
                                            (it["T_${idx}"]!! to territoryLink[it["T_${idx}"]!!]!!)
                                        else null
                                    }.toMap()
                                )
                            )
                        }
                }
        }

    }

}