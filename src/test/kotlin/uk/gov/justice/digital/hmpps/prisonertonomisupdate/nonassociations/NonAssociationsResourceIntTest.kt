package uk.gov.justice.digital.hmpps.prisonertonomisupdate.nonassociations

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.microsoft.applicationinsights.TelemetryClient
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.boot.test.mock.mockito.MockBean
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.NonAssociationIdResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NonAssociationsApiExtension.Companion.nonAssociationsApiServer

class NonAssociationsResourceIntTest : IntegrationTestBase() {
  @MockBean
  lateinit var telemetryClient: TelemetryClient

  @Captor
  lateinit var telemetryCaptor: ArgumentCaptor<Map<String, String>>

  private fun nonAssociationsNomisPagedResponse(
    totalElements: Long = 10,
    numberOfElements: Long = 10,
    pageSize: Long = 10,
    pageNumber: Long = 0,
  ): String {
    val nonAssociationId = (1..numberOfElements).map { it + (pageNumber * pageSize) }
      .map {
        NonAssociationIdResponse(
          offenderNo1 = index1ToNomsId(it),
          offenderNo2 = index2ToNomsId(it),
        )
      }
    val content = nonAssociationId
      .map { """{ "offenderNo1": "${it.offenderNo1}", "offenderNo2": "${it.offenderNo2}" }""" }
      .joinToString { it }
    return pagedResponse(content, pageSize, pageNumber, totalElements, nonAssociationId.size)
  }

  private fun nonAssociationsDpsPagedResponse(
    totalElements: Long = 10,
    numberOfElements: Long = 10,
    pageSize: Long = 10,
    pageNumber: Long = 0,
  ): String {
    return pagedResponse("", pageSize, pageNumber, totalElements, 0)
  }

  private fun pagedResponse(
    content: String,
    pageSize: Long,
    pageNumber: Long,
    totalElements: Long,
    pageElements: Int,
  ) = """
  {
      "content": [
          $content
      ],
      "pageable": {
          "sort": {
              "empty": false,
              "sorted": true,
              "unsorted": false
          },
          "offset": 0,
          "pageSize": $pageSize,
          "pageNumber": $pageNumber,
          "paged": true,
          "unpaged": false
      },
      "last": false,
      "totalPages": ${totalElements / pageSize + 1},
      "totalElements": $totalElements,
      "size": $pageSize,
      "number": $pageNumber,
      "sort": {
          "empty": false,
          "sorted": true,
          "unsorted": false
      },
      "first": true,
      "numberOfElements": $pageElements,
      "empty": false
  }                
  """.trimIndent()

  private fun index1ToNomsId(it: Long) = "A${it.toString().padStart(4, '0')}TY"
  private fun index2ToNomsId(it: Long) = "B${it.toString().padStart(4, '0')}TZ"

  private fun nonAssociationNomisResponse(
    offenderNo: String = "A1234BC",
    nsOffenderNo: String = "D5678EF",
  ): String = """
[
  {
    "offenderNo": "$offenderNo",
    "nsOffenderNo": "$nsOffenderNo",
    "typeSequence": 1,
    "reason": "VIC",
    "recipReason": "PER",
    "type": "LAND",
    "authorisedBy": "Jim Smith",
    "effectiveDate": "2023-08-25",
    "expiryDate": "2023-10-26",
    "comment": "Fight on Wing C"
  }
]
  """.trimIndent()

  private fun nonAssociationApiResponse(type: String) = """
[
  {
    "id": 999,
    "firstPrisonerNumber": "dummy1",
    "firstPrisonerRole": "VICTIM",
    "firstPrisonerRoleDescription": "Victim",
    "secondPrisonerNumber": "dummy2",
    "secondPrisonerRole": "PERPETRATOR",
    "secondPrisonerRoleDescription": "Perpetrator",
    "reason": "BULLYING",
    "reasonDescription": "Bullying",
    "restrictionType": "$type",
    "restrictionTypeDescription": "Cell only",
    "comment": "Fight on Wing C",
    "whenCreated": "2023-08-25T10:55:04",
    "whenUpdated": "2023-08-25T10:55:04",
    "updatedBy": "OFF3_GEN",
    "isClosed": false,
    "closedBy": "null",
    "closedReason": "null",
    "isOpen": true
  }
]
  """.trimIndent()

  @DisplayName("PUT /non-associations/reports/reconciliation")
  @Nested
  inner class GenerateNonAssociationReconciliationReport {
    @BeforeEach
    fun setUp() {
      reset(telemetryClient)

      val numberOfNonAssociations = 34L
      nomisApi.stubGetNonAssociationsInitialCount(nonAssociationsNomisPagedResponse(totalElements = numberOfNonAssociations, pageSize = 1))
      nomisApi.stubGetNonAssociationsPage(0, 10, nonAssociationsNomisPagedResponse(totalElements = numberOfNonAssociations, numberOfElements = 10, pageNumber = 0, pageSize = 10))
      nomisApi.stubGetNonAssociationsPage(1, 10, nonAssociationsNomisPagedResponse(totalElements = numberOfNonAssociations, numberOfElements = 10, pageNumber = 1, pageSize = 10))
      nomisApi.stubGetNonAssociationsPage(2, 10, nonAssociationsNomisPagedResponse(totalElements = numberOfNonAssociations, numberOfElements = 10, pageNumber = 2, pageSize = 10))
      nomisApi.stubGetNonAssociationsPage(3, 10, nonAssociationsNomisPagedResponse(totalElements = numberOfNonAssociations, numberOfElements = 4, pageNumber = 3, pageSize = 10))
      (1..numberOfNonAssociations).forEach {
        val offenderNo1 = index1ToNomsId(it)
        val offenderNo2 = index2ToNomsId(it)
        nomisApi.stubGetNonAssociationsAll(offenderNo1, offenderNo2, nonAssociationNomisResponse(offenderNo1, offenderNo2))
        nonAssociationsApiServer.stubGetNonAssociationsBetween(offenderNo1, offenderNo2, nonAssociationApiResponse(if (it.toInt() % 10 == 0) "WING" else "LANDING")) // // every 10th prisoner has an WING type
      }
      nonAssociationsApiServer.stubGetNonAssociationsPage(0, 1, nonAssociationsDpsPagedResponse(34))
    }

    @Test
    fun `will output report requested telemetry`() {
      webTestClient.put().uri("/non-associations/reports/reconciliation")
        .exchange()
        .expectStatus().isAccepted

      verify(telemetryClient).trackEvent(
        eq("non-associations-reports-reconciliation-requested"),
        check {
          assertThat(it).containsEntry("non-associations-nomis-total", "34")
        },
        isNull(),
      )

      awaitReportFinished()
    }

    @Test
    fun `should execute batches of prisoners`() {
      // given "reports.non-associations.reconciliation.page-size=10"

      webTestClient.put().uri("/non-associations/reports/reconciliation")
        .exchange()
        .expectStatus().isAccepted

      awaitReportFinished()
      nomisApi.verify(
        WireMock.getRequestedFor(urlPathEqualTo("/non-associations/ids"))
          .withQueryParam("size", WireMock.equalTo("1")),
      )
      nomisApi.verify(
        4, // 34 prisoners will be spread over 4 pages of 10 prisoners each
        WireMock.getRequestedFor(urlPathEqualTo("/non-associations/ids"))
          .withQueryParam("size", WireMock.equalTo("10")),
      )
    }

    @Test
    fun `should emit a mismatched custom event for each mismatch along with a summary`() {
      webTestClient.put().uri("/non-associations/reports/reconciliation")
        .exchange()
        .expectStatus().isAccepted

      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("non-associations-reports-reconciliation-success"),
        check {
          assertThat(it).containsEntry("mismatch-count", "3")
          assertThat(it).containsEntry(
            "NonAssociationIdResponse(offenderNo1=A0010TY, offenderNo2=B0010TZ)",
            "nomis=NonAssociationReportDetail(type=LAND, createdDate=2023-08-25, expiryDate=2023-10-26, closed=null, roleReason=VIC, roleReason2=PER, dpsReason=, comment=Fight on Wing C)," +
              " dps=NonAssociationReportDetail(type=WING, createdDate=2023-08-25T10:55:04, expiryDate=, closed=false, roleReason=VICTIM, roleReason2=PERPETRATOR, dpsReason=BULLYING, comment=Fight on Wing C)",
          )
          assertThat(it).containsEntry(
            "NonAssociationIdResponse(offenderNo1=A0020TY, offenderNo2=B0020TZ)",
            "nomis=NonAssociationReportDetail(type=LAND, createdDate=2023-08-25, expiryDate=2023-10-26, closed=null, roleReason=VIC, roleReason2=PER, dpsReason=, comment=Fight on Wing C)," +
              " dps=NonAssociationReportDetail(type=WING, createdDate=2023-08-25T10:55:04, expiryDate=, closed=false, roleReason=VICTIM, roleReason2=PERPETRATOR, dpsReason=BULLYING, comment=Fight on Wing C)",
          )
          assertThat(it).containsEntry(
            "NonAssociationIdResponse(offenderNo1=A0030TY, offenderNo2=B0030TZ)",
            "nomis=NonAssociationReportDetail(type=LAND, createdDate=2023-08-25, expiryDate=2023-10-26, closed=null, roleReason=VIC, roleReason2=PER, dpsReason=, comment=Fight on Wing C)," +
              " dps=NonAssociationReportDetail(type=WING, createdDate=2023-08-25T10:55:04, expiryDate=, closed=false, roleReason=VICTIM, roleReason2=PERPETRATOR, dpsReason=BULLYING, comment=Fight on Wing C)",
          )
        },
        isNull(),
      )

      verify(telemetryClient, times(3)).trackEvent(
        eq("non-associations-reports-reconciliation-mismatch"),
        telemetryCaptor.capture(),
        isNull(),
      )

      with(telemetryCaptor.allValues[0]) {
        assertThat(this).containsEntry("offenderNo1", "A0010TY")
        assertThat(this).containsEntry("offenderNo2", "B0010TZ")
        assertThat(this).containsEntry(
          "nomis",
          "NonAssociationReportDetail(type=LAND, createdDate=2023-08-25, expiryDate=2023-10-26, closed=null, roleReason=VIC, roleReason2=PER, dpsReason=, comment=Fight on Wing C)",
        )
        assertThat(this).containsEntry(
          "dps",
          "NonAssociationReportDetail(type=WING, createdDate=2023-08-25T10:55:04, expiryDate=, closed=false, roleReason=VICTIM, roleReason2=PERPETRATOR, dpsReason=BULLYING, comment=Fight on Wing C)",
        )
      }
      with(telemetryCaptor.allValues[1]) {
        assertThat(this).containsEntry("offenderNo1", "A0020TY")
        assertThat(this).containsEntry("offenderNo2", "B0020TZ")
        assertThat(this).containsEntry(
          "nomis",
          "NonAssociationReportDetail(type=LAND, createdDate=2023-08-25, expiryDate=2023-10-26, closed=null, roleReason=VIC, roleReason2=PER, dpsReason=, comment=Fight on Wing C)",
        )
        assertThat(this).containsEntry(
          "dps",
          "NonAssociationReportDetail(type=WING, createdDate=2023-08-25T10:55:04, expiryDate=, closed=false, roleReason=VICTIM, roleReason2=PERPETRATOR, dpsReason=BULLYING, comment=Fight on Wing C)",
        )
      }
      with(telemetryCaptor.allValues[2]) {
        assertThat(this).containsEntry("offenderNo1", "A0030TY")
        assertThat(this).containsEntry("offenderNo2", "B0030TZ")
        assertThat(this).containsEntry(
          "nomis",
          "NonAssociationReportDetail(type=LAND, createdDate=2023-08-25, expiryDate=2023-10-26, closed=null, roleReason=VIC, roleReason2=PER, dpsReason=, comment=Fight on Wing C)",
        )
        assertThat(this).containsEntry(
          "dps",
          "NonAssociationReportDetail(type=WING, createdDate=2023-08-25T10:55:04, expiryDate=, closed=false, roleReason=VICTIM, roleReason2=PERPETRATOR, dpsReason=BULLYING, comment=Fight on Wing C)",
        )
      }
    }

    @Test
    fun `will attempt to complete a report even if some of the checks fail`() {
      nomisApi.stubGetNonAssociationsAllWithError("A0002TY", "B0002TZ", 500)
      nonAssociationsApiServer.stubGetNonAssociationsBetweenWithError("A0020TY", "B0020TZ", 500)

      webTestClient.put().uri("/non-associations/reports/reconciliation")
        .exchange()
        .expectStatus().isAccepted

      awaitReportFinished()

      verify(telemetryClient, times(2)).trackEvent(
        eq("non-associations-reports-reconciliation-mismatch-error"),
        any(),
        isNull(),
      )

      verify(telemetryClient).trackEvent(
        eq("non-associations-reports-reconciliation-success"),
        check {
          assertThat(it).containsEntry("mismatch-count", "2")
          assertThat(it).containsEntry(
            "NonAssociationIdResponse(offenderNo1=A0010TY, offenderNo2=B0010TZ)",
            "nomis=NonAssociationReportDetail(type=LAND, createdDate=2023-08-25, expiryDate=2023-10-26, closed=null, roleReason=VIC, roleReason2=PER, dpsReason=, comment=Fight on Wing C)," +
              " dps=NonAssociationReportDetail(type=WING, createdDate=2023-08-25T10:55:04, expiryDate=, closed=false, roleReason=VICTIM, roleReason2=PERPETRATOR, dpsReason=BULLYING, comment=Fight on Wing C)",
          )
          assertThat(it).containsEntry(
            "NonAssociationIdResponse(offenderNo1=A0030TY, offenderNo2=B0030TZ)",
            "nomis=NonAssociationReportDetail(type=LAND, createdDate=2023-08-25, expiryDate=2023-10-26, closed=null, roleReason=VIC, roleReason2=PER, dpsReason=, comment=Fight on Wing C)," +
              " dps=NonAssociationReportDetail(type=WING, createdDate=2023-08-25T10:55:04, expiryDate=, closed=false, roleReason=VICTIM, roleReason2=PERPETRATOR, dpsReason=BULLYING, comment=Fight on Wing C)",
          )
        },
        isNull(),
      )
    }

    @Test
    fun `when initial prison count fails the whole report fails`() {
      nomisApi.stubGetNonAssociationsPageWithError(0, 500)

      webTestClient.put().uri("/non-associations/reports/reconciliation")
        .exchange()
        .expectStatus().is5xxServerError
    }

    @Test
    fun `will attempt to complete a report even if whole pages of the checks fail`() {
      nomisApi.stubGetNonAssociationsPageWithError(2, 500)
      nonAssociationsApiServer.stubGetNonAssociationsPage(0, 1, nonAssociationsDpsPagedResponse(24))

      webTestClient.put().uri("/non-associations/reports/reconciliation")
        .exchange()
        .expectStatus().isAccepted

      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("non-associations-reports-reconciliation-mismatch-page-error"),
        any(),
        isNull(),
      )

      verify(telemetryClient).trackEvent(
        eq("non-associations-reports-reconciliation-success"),
        check {
          assertThat(it).containsEntry("mismatch-count", "2")
          assertThat(it).containsEntry(
            "NonAssociationIdResponse(offenderNo1=A0010TY, offenderNo2=B0010TZ)",
            "nomis=NonAssociationReportDetail(type=LAND, createdDate=2023-08-25, expiryDate=2023-10-26, closed=null, roleReason=VIC, roleReason2=PER, dpsReason=, comment=Fight on Wing C)," +
              " dps=NonAssociationReportDetail(type=WING, createdDate=2023-08-25T10:55:04, expiryDate=, closed=false, roleReason=VICTIM, roleReason2=PERPETRATOR, dpsReason=BULLYING, comment=Fight on Wing C)",
          )
          assertThat(it).containsEntry(
            "NonAssociationIdResponse(offenderNo1=A0020TY, offenderNo2=B0020TZ)",
            "nomis=NonAssociationReportDetail(type=LAND, createdDate=2023-08-25, expiryDate=2023-10-26, closed=null, roleReason=VIC, roleReason2=PER, dpsReason=, comment=Fight on Wing C)," +
              " dps=NonAssociationReportDetail(type=WING, createdDate=2023-08-25T10:55:04, expiryDate=, closed=false, roleReason=VICTIM, roleReason2=PERPETRATOR, dpsReason=BULLYING, comment=Fight on Wing C)",
          )
        },
        isNull(),
      )
    }
  }

  private fun awaitReportFinished() {
    await untilAsserted { verify(telemetryClient).trackEvent(eq("non-associations-reports-reconciliation-success"), any(), isNull()) }
  }
}
