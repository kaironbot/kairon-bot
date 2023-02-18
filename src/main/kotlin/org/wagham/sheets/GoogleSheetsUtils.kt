package org.wagham.sheets

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.sheets.v4.model.ValueRange
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.ImpersonatedCredentials
import com.google.auth.oauth2.ServiceAccountCredentials

object GoogleSheetsUtils {

    private val sheetService =
        ServiceAccountCredentials
            .fromPkcs8(
                System.getenv("GOOGLE_CLIENT_ID")!!,
                System.getenv("GOOGLE_SERVICE_EMAIL")!!,
                System.getenv("GOOGLE_PRIVATE_KEY")!!.replace("\\\\n", "\n"),
                System.getenv("GOOGLE_PRIVATE_KEY_ID")!!,
                listOf("https://www.googleapis.com/auth/iam")
            ).let {
                ImpersonatedCredentials
                    .create(
                        it,
                        System.getenv("GOOGLE_SERVICE_EMAIL")!!,
                        null,
                        listOf(SheetsScopes.SPREADSHEETS_READONLY),
                        300
                    )
            }.let {
                Sheets.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    HttpCredentialsAdapter(it)
                )
                    .setApplicationName("WaghamBot")
                    .build()
            }

        fun downloadRawDataFromSheet(sheetId: String, range: String): ValueRange =
            sheetService.spreadsheets().values().get(sheetId, range).execute()

}

fun ValueRange.getHeaderMapping() =
    this.getValues()
        .first()
        .foldIndexed(emptyMap<String, Int>()) { idx, acc, it ->
            acc + ( (it as String) to idx )
        }
