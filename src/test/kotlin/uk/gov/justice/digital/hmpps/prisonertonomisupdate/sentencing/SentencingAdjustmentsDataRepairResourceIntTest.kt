package uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing

import com.github.tomakehurst.wiremock.client.WireMock.absent
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.eq
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.SentencingAdjustmentsApiExtension.Companion.sentencingAdjustmentsApi

class SentencingAdjustmentsDataRepairResourceIntTest : SqsIntegrationTestBase() {
  @DisplayName("POST /prisoners/{offenderNo}/sentencing-adjustments/repair/{adjustmentId}")
  @Nested
  inner class RepairAdjustments {
    private val offenderNo = "A1234KT"
    private val adjustmentId = "600dd113-b522-45c8-9dd2-9f35323af377"

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/prisoners/$offenderNo/sentencing-adjustments/$adjustmentId/repair")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/prisoners/$offenderNo/sentencing-adjustments/$adjustmentId/repair")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/prisoners/$offenderNo/sentencing-adjustments/$adjustmentId/repair")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPathUpdate {
      private val nomisAdjustmentId = 98765L
      private val sentenceSequence = 1L

      @BeforeEach
      fun setUp() {
        mappingServer.stubGetByAdjustmentId(
          adjustmentId = adjustmentId,
          nomisAdjustmentId = nomisAdjustmentId,
        )

        nomisApi.stubSentenceAdjustmentUpdate(nomisAdjustmentId)

        sentencingAdjustmentsApi.stubAdjustmentGet(
          adjustmentId = adjustmentId,
          sentenceSequence = sentenceSequence,
          active = false,
          adjustmentDays = 99,
          adjustmentDate = "2022-01-01",
          adjustmentType = "RX",
          adjustmentFromDate = "2020-07-19",
          comment = "Adjusted for remand",
          bookingId = 123456,
        )
      }

      @Nested
      inner class WithNoForceStatusFlag {
        @BeforeEach
        fun setUp() {
          webTestClient.post().uri("/prisoners/$offenderNo/sentencing-adjustments/$adjustmentId/repair")
            .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
            .exchange()
            .expectStatus().isOk
        }

        @Test
        fun `will log the repair details`() {
          verify(telemetryClient).trackEvent(
            eq("to-nomis-synch-adjustment-repair"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(offenderNo)
              assertThat(it["adjustmentId"]).isEqualTo(adjustmentId)
            },
            isNull(),
          )
        }

        @Test
        fun `will update a sentence adjustment in NOMIS`() {
          nomisApi.verify(
            putRequestedFor(urlEqualTo("/sentence-adjustments/$nomisAdjustmentId"))
              .withRequestBody(matchingJsonPath("adjustmentTypeCode", equalTo("RX")))
              .withRequestBody(matchingJsonPath("adjustmentDate", equalTo("2022-01-01")))
              .withRequestBody(matchingJsonPath("adjustmentDays", equalTo("99")))
              .withRequestBody(matchingJsonPath("adjustmentFromDate", equalTo("2020-07-19")))
              .withRequestBody(matchingJsonPath("sentenceSequence", equalTo("$sentenceSequence")))
              .withRequestBody(matchingJsonPath("active", absent()))
              .withRequestBody(matchingJsonPath("comment", equalTo("Adjusted for remand"))),
          )
        }
      }

      @Nested
      inner class WithForceStatusFlag {
        @BeforeEach
        fun setUp() {
          webTestClient.post().uri("/prisoners/$offenderNo/sentencing-adjustments/$adjustmentId/repair?force-status=true")
            .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
            .exchange()
            .expectStatus().isOk
        }

        @Test
        fun `will log the repair details`() {
          verify(telemetryClient).trackEvent(
            eq("to-nomis-synch-adjustment-repair"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(offenderNo)
              assertThat(it["adjustmentId"]).isEqualTo(adjustmentId)
            },
            isNull(),
          )
        }

        @Test
        fun `will update a sentence adjustment in NOMIS`() {
          nomisApi.verify(
            putRequestedFor(urlEqualTo("/sentence-adjustments/$nomisAdjustmentId"))
              .withRequestBody(matchingJsonPath("adjustmentTypeCode", equalTo("RX")))
              .withRequestBody(matchingJsonPath("adjustmentDate", equalTo("2022-01-01")))
              .withRequestBody(matchingJsonPath("adjustmentDays", equalTo("99")))
              .withRequestBody(matchingJsonPath("adjustmentFromDate", equalTo("2020-07-19")))
              .withRequestBody(matchingJsonPath("sentenceSequence", equalTo("$sentenceSequence")))
              .withRequestBody(matchingJsonPath("active", equalTo("false")))
              .withRequestBody(matchingJsonPath("comment", equalTo("Adjusted for remand"))),
          )
        }
      }

      @Nested
      inner class WithSetActiveFlag {
        @BeforeEach
        fun setUp() {
          webTestClient.post().uri("/prisoners/$offenderNo/sentencing-adjustments/$adjustmentId/repair?set-active=true")
            .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
            .exchange()
            .expectStatus().isOk
        }

        @Test
        fun `will log the repair details`() {
          verify(telemetryClient).trackEvent(
            eq("to-nomis-synch-adjustment-repair"),
            check {
              assertThat(it["offenderNo"]).isEqualTo(offenderNo)
              assertThat(it["adjustmentId"]).isEqualTo(adjustmentId)
            },
            isNull(),
          )
        }

        @Test
        fun `will update a sentence adjustment in NOMIS`() {
          nomisApi.verify(
            putRequestedFor(urlEqualTo("/sentence-adjustments/$nomisAdjustmentId"))
              .withRequestBody(matchingJsonPath("adjustmentTypeCode", equalTo("RX")))
              .withRequestBody(matchingJsonPath("adjustmentDate", equalTo("2022-01-01")))
              .withRequestBody(matchingJsonPath("adjustmentDays", equalTo("99")))
              .withRequestBody(matchingJsonPath("adjustmentFromDate", equalTo("2020-07-19")))
              .withRequestBody(matchingJsonPath("sentenceSequence", equalTo("$sentenceSequence")))
              .withRequestBody(matchingJsonPath("active", equalTo("true")))
              .withRequestBody(matchingJsonPath("comment", equalTo("Adjusted for remand"))),
          )
        }
      }
    }

    @Nested
    inner class HappyPathCreate {
      private val nomisAdjustmentId = 98765L
      private val sentenceSequence = 1L
      private val bookingId = 123456L

      @BeforeEach
      fun setUp() {
        mappingServer.stubGetByAdjustmentIdWithError(
          adjustmentId = adjustmentId,
          status = 404,
        )

        sentencingAdjustmentsApi.stubAdjustmentGet(
          adjustmentId = adjustmentId,
          sentenceSequence = sentenceSequence,
          active = true,
          adjustmentDays = 99,
          adjustmentDate = "2022-01-01",
          adjustmentType = "RX",
          adjustmentFromDate = "2020-07-19",
          comment = "Adjusted for remand",
          bookingId = bookingId,
        )
        nomisApi.stubSentenceAdjustmentCreate(bookingId = bookingId, sentenceSequence = sentenceSequence, adjustmentId = nomisAdjustmentId)

        mappingServer.stubCreateSentencingAdjustment()

        webTestClient.post().uri("/prisoners/$offenderNo/sentencing-adjustments/$adjustmentId/repair")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isOk
      }

      @Test
      fun `will log the repair details`() {
        verify(telemetryClient).trackEvent(
          eq("to-nomis-synch-adjustment-repair"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["adjustmentId"]).isEqualTo(adjustmentId)
          },
          isNull(),
        )
      }

      @Test
      fun `will create a sentence adjustment in NOMIS`() {
        nomisApi.verify(
          postRequestedFor(urlEqualTo("/prisoners/booking-id/$bookingId/sentences/$sentenceSequence/adjustments"))
            .withRequestBody(matchingJsonPath("adjustmentTypeCode", equalTo("RX")))
            .withRequestBody(matchingJsonPath("adjustmentDate", equalTo("2022-01-01")))
            .withRequestBody(matchingJsonPath("adjustmentDays", equalTo("99")))
            .withRequestBody(matchingJsonPath("adjustmentFromDate", equalTo("2020-07-19")))
            .withRequestBody(matchingJsonPath("comment", equalTo("Adjusted for remand"))),
        )
      }

      @Test
      fun `will create a mapping between the two adjustments`() {
        mappingServer.verify(
          postRequestedFor(urlEqualTo("/mapping/sentencing/adjustments"))
            .withRequestBody(matchingJsonPath("nomisAdjustmentId", equalTo(nomisAdjustmentId.toString())))
            .withRequestBody(matchingJsonPath("nomisAdjustmentCategory", equalTo("SENTENCE")))
            .withRequestBody(matchingJsonPath("adjustmentId", equalTo(adjustmentId))),
        )
      }
    }
  }
}
