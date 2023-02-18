package org.wagham.sheets.data

import org.wagham.db.models.Announcement
import org.wagham.db.models.AnnouncementBatch
import org.wagham.sheets.GoogleSheetsUtils
import org.wagham.sheets.getHeaderMapping
import org.wagham.utils.toListOfMap

class AnnouncementRow(
    val operation: ImportOperation = ImportOperation.UPDATE,
    val announcements: AnnouncementBatch
) {

    companion object {

        private val sheetId = System.getenv("SHEET_ID")!!
        private const val range = "PREMI_ANNUNCI!B1:C100"

        fun parseRows(batchId: String): AnnouncementRow =
            GoogleSheetsUtils
                .downloadRawDataFromSheet(sheetId, range)
                .let { values ->
                    val header = values.getHeaderMapping()
                    values.getValues()
                        .subList(1, values.getValues().size)
                        .toListOfMap(header, 0)
                        .fold(AnnouncementBatch(
                            id = batchId,
                            criticalFail = emptyList(),
                            fail = emptyList(),
                            success = emptyList(),
                            jackpot = emptyList(),
                            winBeast = emptyList(),
                            lostBeast = emptyList()
                        )) { acc, it ->
                            when(it["Tipo Annuncio"]) {
                                "CriticalFail" -> acc.copy(criticalFail = acc.criticalFail + Announcement(it["Testo Annuncio"]!!))
                                "Fail" -> acc.copy(fail = acc.criticalFail + Announcement(it["Testo Annuncio"]!!))
                                "Success" -> acc.copy(success = acc.criticalFail + Announcement(it["Testo Annuncio"]!!))
                                "Jackpot" -> acc.copy(jackpot = acc.criticalFail + Announcement(it["Testo Annuncio"]!!))
                                "LostBeast" -> acc.copy(lostBeast = acc.criticalFail + Announcement(it["Testo Annuncio"]!!))
                                "WinBeast" -> acc.copy(winBeast = acc.criticalFail + Announcement(it["Testo Annuncio"]!!))
                                else -> acc
                            }
                        }.let {
                            AnnouncementRow(announcements = it)
                        }
                }



    }

}