package uk.gov.justice.digital.hmpps.prisonertonomisupdate.nonassociations

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.NonAssociationIdResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NonAssociationsApiExtension.Companion.nonAssociationsApiServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.generateOffenderNo

@ExtendWith(MockitoExtension::class)
class NonAssociationsResourceIntTest(
  @Autowired private val nonAssociationsReconciliationService: NonAssociationsReconciliationService,
) : IntegrationTestBase() {

  @Captor
  lateinit var telemetryCaptor: ArgumentCaptor<Map<String, String>>

  private fun nonAssociationsNomisPagedResponse(
    totalElements: Int = 10,
    numberOfElements: Int = 10,
    pageSize: Int = 10,
    pageNumber: Int = 0,
  ): String {
    val nonAssociationId = (1..numberOfElements)
      .map { it + (pageNumber * pageSize) }
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
    totalElements: Int = 10,
    numberOfElements: Int = 10,
    pageSize: Int = 10,
    pageNumber: Int = 0,
  ): String {
    val content =
      (1..numberOfElements)
        .map { it + (pageNumber * pageSize) }
        .map { nonAssociationDpsJson("CELL", first = index1ToNomsId(it), second = index2ToNomsId(it)) }
        .joinToString { it }
    return pagedResponse(content, pageSize, pageNumber, totalElements, numberOfElements)
  }

  private fun pagedResponse(
    content: String,
    pageSize: Int,
    pageNumber: Int,
    totalElements: Int,
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

  private fun index1ToNomsId(it: Int) = generateOffenderNo(prefix = "A", sequence = it.toLong(), suffix = "TY")
  private fun index2ToNomsId(it: Int) = generateOffenderNo(prefix = "B", sequence = it.toLong(), suffix = "TZ")

  private fun nonAssociationNomisResponse(offenderNo: String, nsOffenderNo: String): String = "[ ${nonAssociationNomisJson(offenderNo, nsOffenderNo)} ]"

  private fun nonAssociationNomisJson(offenderNo: String, nsOffenderNo: String, open: Boolean = true): String = """
  {
    "offenderNo": "$offenderNo",
    "nsOffenderNo": "$nsOffenderNo",
    "typeSequence": 1,
    "reason": "VIC",
    "recipReason": "PER",
    "type": "LAND",
    "authorisedBy": "Jim Smith",
    "effectiveDate": "2023-08-25",
    "expiryDate": "${if (open) "2053-10-26" else "2023-09-15"}",
    "comment": "Fight on Wing C",
    "updatedBy": "del_gen"
  }
  """.trimIndent()

  private fun nonAssociationApiResponse(type: String) = "[ ${nonAssociationDpsJson(type)} ]"

  private fun nonAssociationDpsJson(type: String, first: String = "dummy1", second: String = "dummy2", open: Boolean = true) = """
  {
    "id": 999,
    "firstPrisonerNumber": "$first",
    "firstPrisonerRole": "VICTIM",
    "firstPrisonerRoleDescription": "Victim",
    "secondPrisonerNumber": "$second",
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
    "isClosed": ${!open},
    "closedBy": "null",
    "closedReason": "null",
    "isOpen": $open
  }
  """.trimIndent()

  @DisplayName("Non-associations reconciliation report")
  @Nested
  inner class GenerateNonAssociationReconciliationReport {
    @BeforeEach
    fun setUp() {
      reset(telemetryClient)

      val numberOfNonAssociations = 34
      nomisApi.stubGetNonAssociationsInitialCount(nonAssociationsNomisPagedResponse(totalElements = numberOfNonAssociations, pageSize = 1))
      nomisApi.stubGetNonAssociationsPage(0, 10, nonAssociationsNomisPagedResponse(numberOfNonAssociations, 10, pageNumber = 0, pageSize = 10))
      nomisApi.stubGetNonAssociationsPage(1, 10, nonAssociationsNomisPagedResponse(numberOfNonAssociations, 10, pageNumber = 1, pageSize = 10))
      nomisApi.stubGetNonAssociationsPage(2, 10, nonAssociationsNomisPagedResponse(numberOfNonAssociations, 10, pageNumber = 2, pageSize = 10))
      nomisApi.stubGetNonAssociationsPage(3, 10, nonAssociationsNomisPagedResponse(numberOfNonAssociations, 4, pageNumber = 3, pageSize = 10))
      (1..numberOfNonAssociations).forEach {
        val offenderNo1 = index1ToNomsId(it)
        val offenderNo2 = index2ToNomsId(it)
        when (it) {
          25 -> {
            // this one has 2 NA details which match
            nomisApi.stubGetNonAssociationsAll(
              offenderNo1,
              offenderNo2,
              "[ ${nonAssociationNomisJson(offenderNo1, offenderNo2, false)}, ${nonAssociationNomisJson(offenderNo1, offenderNo2)} ]",
            )
            nonAssociationsApiServer.stubGetNonAssociationsBetween(
              offenderNo1,
              offenderNo2,
              "[ ${nonAssociationDpsJson("LANDING", offenderNo1, offenderNo2, false)}, ${nonAssociationDpsJson("LANDING", offenderNo1, offenderNo2)} ]",
            )
          }

          26 -> {
            // this one has 2 NA open details in Nomis
            nomisApi.stubGetNonAssociationsAll(
              offenderNo1,
              offenderNo2,
              "[ ${nonAssociationNomisJson(offenderNo1, offenderNo2)}, ${nonAssociationNomisJson(offenderNo1, offenderNo2)} ]",
            )
            nonAssociationsApiServer.stubGetNonAssociationsBetween(
              offenderNo1,
              offenderNo2,
              "[ ${nonAssociationDpsJson("LANDING", offenderNo1, offenderNo2)} ]",
            )
          }

          else -> {
            nomisApi.stubGetNonAssociationsAll(offenderNo1, offenderNo2, nonAssociationNomisResponse(offenderNo1, offenderNo2))
            nonAssociationsApiServer.stubGetNonAssociationsBetween(offenderNo1, offenderNo2, nonAssociationApiResponse(if (it % 10 == 0) "WING" else "LANDING")) // every 10th prisoner has a WING type
          }
        }
      }
      nonAssociationsApiServer.stubGetNonAssociationsPage(0, 1, nonAssociationsDpsPagedResponse(37, 37)) // some extra in DPS
      nonAssociationsApiServer.stubGetNonAssociationsPage(0, 10, nonAssociationsDpsPagedResponse(37, 10, 10, 0))
      nonAssociationsApiServer.stubGetNonAssociationsPage(1, 10, nonAssociationsDpsPagedResponse(37, 10, 10, 1))
      nonAssociationsApiServer.stubGetNonAssociationsPage(2, 10, nonAssociationsDpsPagedResponse(37, 10, 10, 2))
      nonAssociationsApiServer.stubGetNonAssociationsPage(3, 10, nonAssociationsDpsPagedResponse(37, 7, 10, 3))
    }

    @Test
    fun `will output report requested telemetry`() = runTest {
      nonAssociationsReconciliationService.generateReconciliationReport()

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
    fun `should execute batches of prisoners`() = runTest {
      // given "reports.non-associations.reconciliation.page-size=10"

      nonAssociationsReconciliationService.generateReconciliationReport()

      awaitReportFinished()
      nomisApi.verify(
        WireMock.getRequestedFor(urlPathEqualTo("/non-associations/ids"))
          .withQueryParam("size", WireMock.equalTo("1")),
      )
      nomisApi.verify(
        // 34 prisoners will be spread over 4 pages of 10 prisoners each
        4,
        WireMock.getRequestedFor(urlPathEqualTo("/non-associations/ids"))
          .withQueryParam("size", WireMock.equalTo("10")),
      )
    }

    @Test
    fun `should emit a mismatched custom event for each mismatch along with a summary`() = runTest {
      nonAssociationsReconciliationService.generateReconciliationReport()

      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("non-associations-reports-reconciliation-success"),
        check {
          assertThat(it).containsEntry("mismatch-count", "6")
          assertThat(it).containsEntry(
            "NonAssociationIdResponse(offenderNo1=A0010TY, offenderNo2=B0010TZ)",
            "nomis=NonAssociationReportDetail(type=LAND, createdDate=2023-08-25, expiryDate=2053-10-26, closed=null, roleReason=VIC, roleReason2=PER, dpsReason=null)," +
              " dps=NonAssociationReportDetail(type=WING, createdDate=2023-08-25, expiryDate=null, closed=false, roleReason=VICTIM, roleReason2=PERPETRATOR, dpsReason=BULLYING)",
          )
          assertThat(it).containsEntry(
            "NonAssociationIdResponse(offenderNo1=A0020TY, offenderNo2=B0020TZ)",
            "nomis=NonAssociationReportDetail(type=LAND, createdDate=2023-08-25, expiryDate=2053-10-26, closed=null, roleReason=VIC, roleReason2=PER, dpsReason=null)," +
              " dps=NonAssociationReportDetail(type=WING, createdDate=2023-08-25, expiryDate=null, closed=false, roleReason=VICTIM, roleReason2=PERPETRATOR, dpsReason=BULLYING)",
          )
          assertThat(it).containsEntry(
            "NonAssociationIdResponse(offenderNo1=A0030TY, offenderNo2=B0030TZ)",
            "nomis=NonAssociationReportDetail(type=LAND, createdDate=2023-08-25, expiryDate=2053-10-26, closed=null, roleReason=VIC, roleReason2=PER, dpsReason=null)," +
              " dps=NonAssociationReportDetail(type=WING, createdDate=2023-08-25, expiryDate=null, closed=false, roleReason=VICTIM, roleReason2=PERPETRATOR, dpsReason=BULLYING)",
          )
          assertThat(it).containsEntry(
            "NonAssociationIdResponse(offenderNo1=A0035TY, offenderNo2=B0035TZ)",
            "nomis=null," +
              " dps=NonAssociationReportDetail(type=CELL, createdDate=2023-08-25, expiryDate=null, closed=false, roleReason=VICTIM, roleReason2=PERPETRATOR, dpsReason=BULLYING)",
          )
          assertThat(it).containsEntry(
            "NonAssociationIdResponse(offenderNo1=A0036TY, offenderNo2=B0036TZ)",
            "nomis=null," +
              " dps=NonAssociationReportDetail(type=CELL, createdDate=2023-08-25, expiryDate=null, closed=false, roleReason=VICTIM, roleReason2=PERPETRATOR, dpsReason=BULLYING)",
          )
          assertThat(it).containsEntry(
            "NonAssociationIdResponse(offenderNo1=A0037TY, offenderNo2=B0037TZ)",
            "nomis=null," +
              " dps=NonAssociationReportDetail(type=CELL, createdDate=2023-08-25, expiryDate=null, closed=false, roleReason=VICTIM, roleReason2=PERPETRATOR, dpsReason=BULLYING)",
          )
        },
        isNull(),
      )

      verify(telemetryClient, times(3)).trackEvent(
        eq("non-associations-reports-reconciliation-mismatch"),
        telemetryCaptor.capture(),
        isNull(),
      )

      verify(telemetryClient, times(3)).trackEvent(
        eq("non-associations-reports-reconciliation-dps-only"),
        telemetryCaptor.capture(),
        isNull(),
      )

      with(telemetryCaptor.allValues[0]) {
        assertThat(this).containsEntry("offenderNo1", "A0010TY")
        assertThat(this).containsEntry("offenderNo2", "B0010TZ")
        assertThat(this).containsEntry(
          "nomis",
          "NonAssociationReportDetail(type=LAND, createdDate=2023-08-25, expiryDate=2053-10-26, closed=null, roleReason=VIC, roleReason2=PER, dpsReason=null)",
        )
        assertThat(this).containsEntry(
          "dps",
          "NonAssociationReportDetail(type=WING, createdDate=2023-08-25, expiryDate=null, closed=false, roleReason=VICTIM, roleReason2=PERPETRATOR, dpsReason=BULLYING)",
        )
      }
      with(telemetryCaptor.allValues[1]) {
        assertThat(this).containsEntry("offenderNo1", "A0020TY")
        assertThat(this).containsEntry("offenderNo2", "B0020TZ")
        assertThat(this).containsEntry(
          "nomis",
          "NonAssociationReportDetail(type=LAND, createdDate=2023-08-25, expiryDate=2053-10-26, closed=null, roleReason=VIC, roleReason2=PER, dpsReason=null)",
        )
        assertThat(this).containsEntry(
          "dps",
          "NonAssociationReportDetail(type=WING, createdDate=2023-08-25, expiryDate=null, closed=false, roleReason=VICTIM, roleReason2=PERPETRATOR, dpsReason=BULLYING)",
        )
      }
      with(telemetryCaptor.allValues[2]) {
        assertThat(this).containsEntry("offenderNo1", "A0030TY")
        assertThat(this).containsEntry("offenderNo2", "B0030TZ")
        assertThat(this).containsEntry(
          "nomis",
          "NonAssociationReportDetail(type=LAND, createdDate=2023-08-25, expiryDate=2053-10-26, closed=null, roleReason=VIC, roleReason2=PER, dpsReason=null)",
        )
        assertThat(this).containsEntry(
          "dps",
          "NonAssociationReportDetail(type=WING, createdDate=2023-08-25, expiryDate=null, closed=false, roleReason=VICTIM, roleReason2=PERPETRATOR, dpsReason=BULLYING)",
        )
      }
      with(telemetryCaptor.allValues[3]) {
        assertThat(this).containsEntry("offenderNo1", "A0035TY")
        assertThat(this).containsEntry("offenderNo2", "B0035TZ")
        assertThat(this).doesNotContainKey("nomis")
        assertThat(this).containsEntry(
          "dps",
          "NonAssociationReportDetail(type=CELL, createdDate=2023-08-25, expiryDate=null, closed=false, roleReason=VICTIM, roleReason2=PERPETRATOR, dpsReason=BULLYING)",
        )
      }
      with(telemetryCaptor.allValues[4]) {
        assertThat(this).containsEntry("offenderNo1", "A0036TY")
        assertThat(this).containsEntry("offenderNo2", "B0036TZ")
        assertThat(this).containsEntry(
          "dps",
          "NonAssociationReportDetail(type=CELL, createdDate=2023-08-25, expiryDate=null, closed=false, roleReason=VICTIM, roleReason2=PERPETRATOR, dpsReason=BULLYING)",
        )
      }
      with(telemetryCaptor.allValues[5]) {
        assertThat(this).containsEntry("offenderNo1", "A0037TY")
        assertThat(this).containsEntry("offenderNo2", "B0037TZ")
        assertThat(this).containsEntry(
          "dps",
          "NonAssociationReportDetail(type=CELL, createdDate=2023-08-25, expiryDate=null, closed=false, roleReason=VICTIM, roleReason2=PERPETRATOR, dpsReason=BULLYING)",
        )
      }
    }

    @Test
    fun `will attempt to complete a report even if some of the checks fail`() = runTest {
      nomisApi.stubGetNonAssociationsAllWithError("A0002TY", "B0002TZ", 500)
      nonAssociationsApiServer.stubGetNonAssociationsBetweenWithError("A0020TY", "B0020TZ", 500)

      nonAssociationsReconciliationService.generateReconciliationReport()

      awaitReportFinished()

      verify(telemetryClient, times(2)).trackEvent(
        eq("non-associations-reports-reconciliation-mismatch-error"),
        any(),
        isNull(),
      )

      verify(telemetryClient).trackEvent(
        eq("non-associations-reports-reconciliation-success"),
        check {
          assertThat(it).containsEntry("mismatch-count", "5")
          assertThat(it).containsEntry(
            "NonAssociationIdResponse(offenderNo1=A0010TY, offenderNo2=B0010TZ)",
            "nomis=NonAssociationReportDetail(type=LAND, createdDate=2023-08-25, expiryDate=2053-10-26, closed=null, roleReason=VIC, roleReason2=PER, dpsReason=null)," +
              " dps=NonAssociationReportDetail(type=WING, createdDate=2023-08-25, expiryDate=null, closed=false, roleReason=VICTIM, roleReason2=PERPETRATOR, dpsReason=BULLYING)",
          )
          assertThat(it).containsEntry(
            "NonAssociationIdResponse(offenderNo1=A0030TY, offenderNo2=B0030TZ)",
            "nomis=NonAssociationReportDetail(type=LAND, createdDate=2023-08-25, expiryDate=2053-10-26, closed=null, roleReason=VIC, roleReason2=PER, dpsReason=null)," +
              " dps=NonAssociationReportDetail(type=WING, createdDate=2023-08-25, expiryDate=null, closed=false, roleReason=VICTIM, roleReason2=PERPETRATOR, dpsReason=BULLYING)",
          )
        },
        isNull(),
      )
    }

    @Test
    fun `when initial prison count fails the whole report fails`() = runTest {
      nomisApi.stubGetNonAssociationsPageWithError(0, 500)

      assertThrows<RuntimeException> {
        nonAssociationsReconciliationService.generateReconciliationReport()
      }
    }

    @Test
    fun `will attempt to complete a report even if whole pages of the Nomis and DPS checks fail`() = runTest {
      nomisApi.stubGetNonAssociationsPageWithError(2, 500)
      nonAssociationsApiServer.stubGetNonAssociationsPageWithError(2, 10)

      nonAssociationsReconciliationService.generateReconciliationReport()

      awaitReportFinished()

      verify(telemetryClient, times(2)).trackEvent(
        eq("non-associations-reports-reconciliation-mismatch-page-error"),
        any(),
        isNull(),
      )

      verify(telemetryClient).trackEvent(
        eq("non-associations-reports-reconciliation-success"),
        check {
          assertThat(it).containsEntry("mismatch-count", "5")
          assertThat(it).containsEntry(
            "NonAssociationIdResponse(offenderNo1=A0010TY, offenderNo2=B0010TZ)",
            "nomis=NonAssociationReportDetail(type=LAND, createdDate=2023-08-25, expiryDate=2053-10-26, closed=null, roleReason=VIC, roleReason2=PER, dpsReason=null)," +
              " dps=NonAssociationReportDetail(type=WING, createdDate=2023-08-25, expiryDate=null, closed=false, roleReason=VICTIM, roleReason2=PERPETRATOR, dpsReason=BULLYING)",
          )
          assertThat(it).containsEntry(
            "NonAssociationIdResponse(offenderNo1=A0020TY, offenderNo2=B0020TZ)",
            "nomis=NonAssociationReportDetail(type=LAND, createdDate=2023-08-25, expiryDate=2053-10-26, closed=null, roleReason=VIC, roleReason2=PER, dpsReason=null)," +
              " dps=NonAssociationReportDetail(type=WING, createdDate=2023-08-25, expiryDate=null, closed=false, roleReason=VICTIM, roleReason2=PERPETRATOR, dpsReason=BULLYING)",
          )
        },
        isNull(),
      )
    }
  }

  @DisplayName("GET /non-associations/reconciliation/{prisonNumber1}/{prisonNumber2}")
  @Nested
  inner class GenerateReconciliationReportForPrisoner {
    private val prisonNumber1 = "A0008BB"
    private val prisonNumber2 = "A0008CC"

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/non-associations/reconciliation/$prisonNumber1/ns/$prisonNumber2")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/non-associations/reconciliation/$prisonNumber1/ns/$prisonNumber2")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/non-associations/reconciliation/$prisonNumber1/ns/$prisonNumber2")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setup() {
      }

      @Test
      fun `will return no differences`() {
        nomisApi.stubGetNonAssociationsAll(
          prisonNumber1,
          prisonNumber2,
          "[ ${nonAssociationNomisJson(prisonNumber1, prisonNumber2, false)}, ${nonAssociationNomisJson(prisonNumber1, prisonNumber2)} ]",
        )
        nonAssociationsApiServer.stubGetNonAssociationsBetween(
          prisonNumber1,
          prisonNumber2,
          "[ ${nonAssociationDpsJson("LANDING", prisonNumber1, prisonNumber2, false)}, ${nonAssociationDpsJson("LANDING", prisonNumber1, prisonNumber2)} ]",
        )
        webTestClient.get().uri("/non-associations/reconciliation/$prisonNumber1/ns/$prisonNumber2")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus()
          .isOk
          .expectBody().isEmpty

        verifyNoInteractions(telemetryClient)
      }

      @Test
      fun `will return mismatch with nomis`() {
        nomisApi.stubGetNonAssociationsAll(prisonNumber1, prisonNumber2, nonAssociationNomisResponse(prisonNumber1, prisonNumber2))
        nonAssociationsApiServer.stubGetNonAssociationsBetween(prisonNumber1, prisonNumber2, nonAssociationApiResponse("WING"))

        webTestClient.get().uri("/non-associations/reconciliation/$prisonNumber1/ns/$prisonNumber2")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus()
          .isOk
          .expectBody()
          .jsonPath("[0].id.offenderNo1").isEqualTo(prisonNumber1)
          .jsonPath("[0].id.offenderNo2").isEqualTo(prisonNumber2)
          .jsonPath("[0].nomisNonAssociation.type").isEqualTo("LAND")
          .jsonPath("[0].nomisNonAssociation.createdDate").isEqualTo("2023-08-25")
          .jsonPath("[0].nomisNonAssociation.expiryDate").isEqualTo("2053-10-26")
          .jsonPath("[0].nomisNonAssociation.closed").isEmpty
          .jsonPath("[0].nomisNonAssociation.roleReason").isEqualTo("VIC")
          .jsonPath("[0].nomisNonAssociation.roleReason2").isEqualTo("PER")
          .jsonPath("[0].nomisNonAssociation.dpsReason").isEmpty
          .jsonPath("[0].dpsNonAssociation.type").isEqualTo("WING")
          .jsonPath("[0].dpsNonAssociation.createdDate").isEqualTo("2023-08-25")
          .jsonPath("[0].dpsNonAssociation.expiryDate").isEmpty
          .jsonPath("[0].dpsNonAssociation.closed").isEqualTo(false)
          .jsonPath("[0].dpsNonAssociation.roleReason").isEqualTo("VICTIM")
          .jsonPath("[0].dpsNonAssociation.roleReason2").isEqualTo("PERPETRATOR")
          .jsonPath("[0].dpsNonAssociation.dpsReason").isEqualTo("BULLYING")
          .jsonPath("length()").isEqualTo(1)

        verify(telemetryClient).trackEvent(
          eq("non-associations-reports-reconciliation-mismatch"),
          any(),
          isNull(),
        )
      }
    }
  }

  private fun awaitReportFinished() {
    await untilAsserted { verify(telemetryClient).trackEvent(eq("non-associations-reports-reconciliation-success"), any(), isNull()) }
  }
}
