package org.wagham.sheets.data

import org.wagham.db.models.*
import org.wagham.db.models.embed.ItemWithProbability
import org.wagham.db.models.embed.Prize
import org.wagham.sheets.GoogleSheetsUtils
import org.wagham.sheets.getHeaderMapping
import org.wagham.utils.formatToFloat
import org.wagham.utils.formatToInt
import org.wagham.utils.toListOfMap

class BountiesRow(
    val operation: ImportOperation = ImportOperation.UPDATE,
    val bounty: Bounty
) {

    companion object {

        private val sheetId = System.getenv("SHEET_ID")!!
        private const val prizeListRange = "PREMI_LISTE_ITEMS!B1:E100"
        private const val bountiesRange = "PREMI_EDIFICI!B1:I100"

        fun parseRows(): List<BountiesRow> {
            val prizeLists = GoogleSheetsUtils
                .downloadRawDataFromSheet(sheetId, prizeListRange)
                .let { values ->
                    val header = values.getHeaderMapping()
                    values.getValues()
                        .subList(1, values.getValues().size)
                        .toListOfMap(header, 0)
                        .fold(emptyMap<String, List<ItemWithProbability>>()) { acc, row ->
                            val id = row["IDLista_Premi"]!!
                            val item = ItemWithProbability(
                                itemId = row["Item_ID"]!!,
                                qty = row["Qty"]!!.formatToInt(),
                                probability = row["Probabilità (0-1)"]!!.formatToFloat()
                            )
                            acc + (id to (acc[id]?.let{ it + item} ?: listOf(item)))
                        }.map { entry ->
                            val zeroProbCount = entry.value.count { it.probability == 0f }
                            val nonZeroProbSum = entry.value.filter { it.probability != 0f }.map { it.probability }.sum()
                            val p = (1 - nonZeroProbSum) / zeroProbCount
                            (entry.key to entry.value.map {
                                if(it.probability != 0f) it
                                else it.copy(probability = p)
                            })
                        }.toMap()
                }
            return GoogleSheetsUtils
                .downloadRawDataFromSheet(sheetId, bountiesRange)
                .let { values ->
                    val header = values.getHeaderMapping()
                    values.getValues()
                        .subList(1, values.getValues().size)
                        .toListOfMap(header, 0)
                        .fold(emptyMap<String, Bounty>()) { acc, row ->
                            val id = row["IDPremio"]!!
                            val prize = Prize(
                                probability = row["Probabilità (0-1)"]!!.formatToFloat(),
                                moneyDelta = row["GiveTakeMoney Money_Qty"]!!.formatToInt(),
                                guaranteedItems = row["GUARANTEED_ITEMS"]!!
                                    .split(";")
                                    .filter { it.isNotBlank() }
                                    .associate {
                                        val (item, qty) = it.split("x")
                                        item to qty.toInt()
                                },
                                announceId = try {
                                    AnnouncementType.valueOf(row["AnnuncioPremio"]!!)
                                } catch (_: IllegalArgumentException) {
                                    null
                                },
                                randomItems = prizeLists[row["GiveItemFrom Premi_Lista"]] ?: emptyList()
                            )
                            acc + (id to
                                    (acc[id]?.copy(prizes = acc[id]!!.prizes + prize) ?: Bounty(
                                    id = id,
                                    prizes = listOf(prize))
                                )
                            )
                        }.map {
                            BountiesRow(bounty = it.value)
                        }

                }
        }
    }
}