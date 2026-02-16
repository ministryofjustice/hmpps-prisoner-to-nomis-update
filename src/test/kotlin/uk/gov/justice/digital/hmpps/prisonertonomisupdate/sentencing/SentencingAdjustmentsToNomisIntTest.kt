package uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing

import com.github.tomakehurst.wiremock.client.WireMock.absent
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.eq
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreatingSystem
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.SentencingAdjustmentsApiExtension.Companion.sentencingAdjustmentsApi
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import java.time.LocalDate
import java.util.UUID

private val ADJUSTMENT_ID = UUID.randomUUID().toString()
const val OFFENDER_NUMBER = "A1234TT"
const val BOOKING_ID = 987651L

class SentencingAdjustmentsToNomisTest : SqsIntegrationTestBase() {

  @Nested
  inner class CreateSentencingAdjustment {
    @Nested
    inner class WhenAdjustmentHasJustBeenCreatedByAdjustmentService {

      @BeforeEach
      fun setUp() {
        mappingServer.stubGetByAdjustmentIdWithError(ADJUSTMENT_ID, 404)
        mappingServer.stubCreateSentencingAdjustment()
      }

      @Nested
      inner class WhenSentenceSequenceIsProvided {
        private val sentenceSequence = 1L
        private val nomisAdjustmentId = 98765L

        @BeforeEach
        fun setUp() {
          nomisApi.stubSentenceAdjustmentCreate(
            bookingId = BOOKING_ID,
            sentenceSequence = sentenceSequence,
            adjustmentId = nomisAdjustmentId,
          )

          sentencingAdjustmentsApi.stubAdjustmentGet(
            adjustmentId = ADJUSTMENT_ID,
            sentenceSequence = sentenceSequence,
            active = true,
            adjustmentDays = 99,
            adjustmentDate = "2022-01-01",
            adjustmentType = "RX",
            adjustmentFromDate = "2020-07-19",
            comment = "Adjusted for remand",
            bookingId = BOOKING_ID,
          )
          publishCreateAdjustmentDomainEvent()
        }

        @Test
        fun `will callback back to adjustment service to get more details`() {
          await untilAsserted {
            sentencingAdjustmentsApi.verify(getRequestedFor(urlEqualTo("/legacy/adjustments/$ADJUSTMENT_ID")))
          }
          await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
        }

        @Test
        fun `will create a sentence adjustment in NOMIS`() {
          await untilAsserted {
            nomisApi.verify(
              postRequestedFor(urlEqualTo("/prisoners/booking-id/$BOOKING_ID/sentences/$sentenceSequence/adjustments"))
                .withRequestBody(matchingJsonPath("adjustmentTypeCode", equalTo("RX")))
                .withRequestBody(matchingJsonPath("adjustmentDate", equalTo("2022-01-01")))
                .withRequestBody(matchingJsonPath("adjustmentDays", equalTo("99")))
                .withRequestBody(matchingJsonPath("adjustmentFromDate", equalTo("2020-07-19")))
                .withRequestBody(matchingJsonPath("comment", equalTo("Adjusted for remand"))),
            )
          }
          await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
        }

        @Test
        fun `will create a mapping between the two adjustments`() {
          await untilAsserted {
            mappingServer.verify(
              postRequestedFor(urlEqualTo("/mapping/sentencing/adjustments"))
                .withRequestBody(matchingJsonPath("nomisAdjustmentId", equalTo(nomisAdjustmentId.toString())))
                .withRequestBody(matchingJsonPath("nomisAdjustmentCategory", equalTo("SENTENCE")))
                .withRequestBody(matchingJsonPath("adjustmentId", equalTo(ADJUSTMENT_ID))),
            )
          }
          await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
        }

        @Test
        fun `will create success telemetry`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("sentencing-adjustment-create-success"),
              check {
                assertThat(it["adjustmentId"]).isEqualTo(ADJUSTMENT_ID)
                assertThat(it["nomisAdjustmentId"]).isEqualTo(nomisAdjustmentId.toString())
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NUMBER)
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      inner class WhenTCAAdjustment {
        private val sentenceSequence = 1L
        private val nomisAdjustmentId = 98765L

        @BeforeEach
        fun setUp() {
          nomisApi.stubSentenceAdjustmentCreate(
            bookingId = BOOKING_ID,
            sentenceSequence = sentenceSequence,
            adjustmentId = nomisAdjustmentId,
          )

          sentencingAdjustmentsApi.stubAdjustmentGet(
            adjustmentId = ADJUSTMENT_ID,
            sentenceSequence = sentenceSequence,
            active = true,
            adjustmentDays = 99,
            adjustmentDate = "2022-01-01",
            adjustmentType = "TCA",
            adjustmentFromDate = "2020-07-19",
            comment = "Adjusted for remand",
            bookingId = BOOKING_ID,
          )
          publishCreateAdjustmentDomainEvent()
        }

        @Test
        fun `will create success telemetry`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("sentencing-adjustment-create-success"),
              check {
                assertThat(it["adjustmentId"]).isEqualTo(ADJUSTMENT_ID)
                assertThat(it["nomisAdjustmentId"]).isEqualTo(nomisAdjustmentId.toString())
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NUMBER)
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      inner class WhenSentenceSequenceIsNotProvided {
        private val nomisAdjustmentId = 98765L

        @BeforeEach
        fun setUp() {
          nomisApi.stubKeyDateAdjustmentCreate(
            bookingId = BOOKING_ID,
            adjustmentId = nomisAdjustmentId,
          )

          sentencingAdjustmentsApi.stubAdjustmentGet(
            adjustmentId = ADJUSTMENT_ID,
            sentenceSequence = null,
            active = true,
            adjustmentDays = 99,
            adjustmentDate = "2022-01-01",
            adjustmentType = "ADA",
            adjustmentFromDate = "2020-07-19",
            comment = "Adjusted for absence",
            bookingId = BOOKING_ID,
          )
          publishCreateAdjustmentDomainEvent()
        }

        @Test
        fun `will callback back to adjustment service to get more details`() {
          await untilAsserted {
            sentencingAdjustmentsApi.verify(getRequestedFor(urlEqualTo("/legacy/adjustments/$ADJUSTMENT_ID")))
          }
          await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
        }

        @Test
        fun `will create a key date adjustment in NOMIS`() {
          await untilAsserted {
            nomisApi.verify(
              postRequestedFor(urlEqualTo("/prisoners/booking-id/$BOOKING_ID/adjustments"))
                .withRequestBody(matchingJsonPath("adjustmentTypeCode", equalTo("ADA")))
                .withRequestBody(matchingJsonPath("adjustmentDate", equalTo("2022-01-01")))
                .withRequestBody(matchingJsonPath("adjustmentDays", equalTo("99")))
                .withRequestBody(matchingJsonPath("adjustmentFromDate", equalTo("2020-07-19")))
                .withRequestBody(matchingJsonPath("comment", equalTo("Adjusted for absence"))),
            )
          }
          await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
        }

        @Test
        fun `will allow a null adjustmentDate from the Adjustments API`() {
          sentencingAdjustmentsApi.stubAdjustmentGet(
            adjustmentId = ADJUSTMENT_ID,
            sentenceSequence = null,
            active = true,
            adjustmentDays = 99,
            adjustmentType = "LAL",
            adjustmentFromDate = "2020-07-19",
            comment = "Adjusted for absence",
            bookingId = BOOKING_ID,
          )
          await untilAsserted {
            nomisApi.verify(
              postRequestedFor(urlEqualTo("/prisoners/booking-id/$BOOKING_ID/adjustments"))
                .withRequestBody(matchingJsonPath("adjustmentTypeCode", equalTo("LAL")))
                .withRequestBody(matchingJsonPath("adjustmentDate", equalTo(LocalDate.now().toString())))
                .withRequestBody(matchingJsonPath("adjustmentDays", equalTo("99")))
                .withRequestBody(matchingJsonPath("adjustmentFromDate", equalTo("2020-07-19")))
                .withRequestBody(matchingJsonPath("comment", equalTo("Adjusted for absence"))),
            )
          }
          await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
        }

        @Test
        fun `will create a mapping between the two adjustments`() {
          await untilAsserted {
            mappingServer.verify(
              postRequestedFor(urlEqualTo("/mapping/sentencing/adjustments"))
                .withRequestBody(matchingJsonPath("nomisAdjustmentId", equalTo(nomisAdjustmentId.toString())))
                .withRequestBody(matchingJsonPath("nomisAdjustmentCategory", equalTo("KEY-DATE")))
                .withRequestBody(matchingJsonPath("adjustmentId", equalTo(ADJUSTMENT_ID))),
            )
          }
          await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
        }

        @Test
        fun `will create success telemetry`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("sentencing-adjustment-create-success"),
              check {
                assertThat(it["adjustmentId"]).isEqualTo(ADJUSTMENT_ID)
                assertThat(it["nomisAdjustmentId"]).isEqualTo(nomisAdjustmentId.toString())
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NUMBER)
              },
              isNull(),
            )
          }
        }
      }
    }

    @Nested
    inner class WhenAdjustmentHasJustBeenCreatedByNOMIS {
      val creatingSystem = "NOMIS"

      @Nested
      inner class WhenSentenceSequenceIsProvided {
        private val sentenceSequence = 1L

        @BeforeEach
        fun setUp() {
          sentencingAdjustmentsApi.stubAdjustmentGet(
            adjustmentId = ADJUSTMENT_ID,
            sentenceSequence = sentenceSequence,
            active = true,
            adjustmentDays = 99,
            adjustmentDate = "2022-01-01",
            adjustmentType = "RX",
            adjustmentFromDate = "2020-07-19",
            comment = "Adjusted for remand",
            bookingId = BOOKING_ID,
          )
          publishCreateAdjustmentDomainEvent(creatingSystem)
        }

        @Test
        fun `will not create a sentence adjustment in NOMIS`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("sentencing-adjustment-create-ignored"),
              any(),
              isNull(),
            )
          }
          sentencingAdjustmentsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/adjustments/$ADJUSTMENT_ID")))
          nomisApi.verify(
            0,
            postRequestedFor(urlEqualTo("/prisoners/booking-id/$BOOKING_ID/sentences/$sentenceSequence/adjustments")),
          )
        }
      }

      @Nested
      inner class WhenSentenceSequenceIsNotProvided {
        @BeforeEach
        fun setUp() {
          sentencingAdjustmentsApi.stubAdjustmentGet(
            adjustmentId = ADJUSTMENT_ID,
            sentenceSequence = null,
            active = true,
            adjustmentDays = 99,
            adjustmentDate = "2022-01-01",
            adjustmentType = "ADA",
            adjustmentFromDate = "2020-07-19",
            comment = "Adjusted for absence",
            bookingId = BOOKING_ID,
          )
          publishCreateAdjustmentDomainEvent(creatingSystem)
        }

        @Test
        fun `will not callback back to adjustment service to get more details`() {
          await untilAsserted {
            sentencingAdjustmentsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/adjustments/$ADJUSTMENT_ID")))
          }
        }

        @Test
        fun `will not create a key date adjustment in NOMIS`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("sentencing-adjustment-create-ignored"),
              any(),
              isNull(),
            )
          }
          nomisApi.verify(
            0,
            postRequestedFor(urlEqualTo("/prisoners/booking-id/$BOOKING_ID/adjustments")),
          )
        }
      }
    }

    @Nested
    inner class WhenMappingAlreadyCreatedForAdjustment {

      @BeforeEach
      fun setUp() {
        mappingServer.stubGetByAdjustmentId(ADJUSTMENT_ID)
      }

      @Nested
      inner class WhenSentenceSequenceIsProvided {
        private val sentenceSequence = 1L

        @BeforeEach
        fun setUp() {
          sentencingAdjustmentsApi.stubAdjustmentGet(
            adjustmentId = ADJUSTMENT_ID,
            sentenceSequence = sentenceSequence,
            active = true,
            adjustmentDays = 99,
            adjustmentDate = "2022-01-01",
            adjustmentType = "RX",
            adjustmentFromDate = "2020-07-19",
            comment = "Adjusted for remand",
            bookingId = BOOKING_ID,
          )
          publishCreateAdjustmentDomainEvent()
        }

        @Test
        fun `will callback back to mapping service to check message has not already been processed`() {
          await untilAsserted {
            mappingServer.verify(
              getRequestedFor(urlEqualTo("/mapping/sentencing/adjustments/adjustment-id/$ADJUSTMENT_ID")),
            )
          }
        }

        @Test
        fun `will not create a sentence adjustment in NOMIS`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("sentencing-adjustment-create-duplicate"),
              any(),
              isNull(),
            )
          }
          nomisApi.verify(
            0,
            postRequestedFor(urlEqualTo("/prisoners/booking-id/$BOOKING_ID/sentences/$sentenceSequence/adjustments")),
          )
        }
      }

      @Nested
      inner class WhenSentenceSequenceIsNotProvided {
        @BeforeEach
        fun setUp() {
          sentencingAdjustmentsApi.stubAdjustmentGet(
            adjustmentId = ADJUSTMENT_ID,
            sentenceSequence = null,
            active = true,
            adjustmentDays = 99,
            adjustmentDate = "2022-01-01",
            adjustmentType = "ADA",
            adjustmentFromDate = "2020-07-19",
            comment = "Adjusted for absence",
            bookingId = BOOKING_ID,
          )
          publishCreateAdjustmentDomainEvent()
        }

        @Test
        fun `will callback back to mapping service to check message has not already been processed`() {
          await untilAsserted {
            mappingServer.verify(
              getRequestedFor(urlEqualTo("/mapping/sentencing/adjustments/adjustment-id/$ADJUSTMENT_ID")),
            )
          }
        }

        @Test
        fun `will not create a key date adjustment in NOMIS`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("sentencing-adjustment-create-duplicate"),
              any(),
              isNull(),
            )
          }
          nomisApi.verify(
            0,
            postRequestedFor(urlEqualTo("/prisoners/booking-id/$BOOKING_ID/adjustments")),
          )
        }
      }
    }

    @Nested
    inner class ExceptionHandling {
      val sentenceSequence = 1L

      @Nested
      inner class WhenAdjustmentServiceFailsOnce {
        @BeforeEach
        fun setUp() {
          mappingServer.stubGetByAdjustmentIdWithError(ADJUSTMENT_ID, 404)
          mappingServer.stubCreateSentencingAdjustment()
          nomisApi.stubSentenceAdjustmentCreate(BOOKING_ID, sentenceSequence)
          sentencingAdjustmentsApi.stubAdjustmentGetWithErrorFollowedBySlowSuccess(
            adjustmentId = ADJUSTMENT_ID,
            sentenceSequence = sentenceSequence,
            bookingId = BOOKING_ID,
            adjustmentDays = 99,
            adjustmentType = "RX",
            adjustmentDate = "2022-01-01",
          )
          publishCreateAdjustmentDomainEvent()
        }

        @Test
        fun `will callback back to adjustment service twice to get more details`() {
          await untilAsserted {
            sentencingAdjustmentsApi.verify(2, getRequestedFor(urlEqualTo("/legacy/adjustments/$ADJUSTMENT_ID")))
          }
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("sentencing-adjustment-create-success"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will eventually create a sentence adjustment in NOMIS`() {
          await untilAsserted {
            nomisApi.verify(
              1,
              postRequestedFor(urlEqualTo("/prisoners/booking-id/$BOOKING_ID/sentences/$sentenceSequence/adjustments"))
                .withRequestBody(matchingJsonPath("adjustmentTypeCode", equalTo("RX")))
                .withRequestBody(matchingJsonPath("adjustmentDate", equalTo("2022-01-01")))
                .withRequestBody(matchingJsonPath("adjustmentDays", equalTo("99"))),
            )
          }
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("sentencing-adjustment-create-success"),
              any(),
              isNull(),
            )
          }
        }
      }

      @Nested
      inner class WhenAdjustmentNoLongerExists {
        @BeforeEach
        fun setUp() {
          mappingServer.stubGetByAdjustmentIdWithError(
            adjustmentId = ADJUSTMENT_ID,
            404,
          )

          sentencingAdjustmentsApi.stubAdjustmentGetWithError(
            adjustmentId = ADJUSTMENT_ID,
            404,
          )
          publishCreateAdjustmentDomainEvent().also { waitForAnyProcessingToComplete(2) }
        }

        @Test
        fun `will skip adjustment insert`() {
          nomisApi.verify(
            0,
            postRequestedFor(urlEqualTo("/prisoners/booking-id/$BOOKING_ID/sentences/$sentenceSequence/adjustments")),
          )
          verify(telemetryClient).trackEvent(
            eq("sentencing-adjustment-create-skipped"),
            any(),
            isNull(),
          )
        }
      }

      @Nested
      inner class WhenAdjustmentServiceKeepsFailing {
        @BeforeEach
        fun setUp() {
          mappingServer.stubGetByAdjustmentIdWithError(ADJUSTMENT_ID, 404)
          sentencingAdjustmentsApi.stubAdjustmentGetWithError(
            adjustmentId = ADJUSTMENT_ID,
            503,
          )
          await untilCallTo {
            awsSqsSentencingDlqClient!!.countAllMessagesOnQueue(sentencingDlqUrl!!).get()
          } matches { it == 0 }

          publishCreateAdjustmentDomainEvent()
        }

        @Test
        fun `will callback back to adjustment service 3 times before given up`() {
          await untilAsserted {
            sentencingAdjustmentsApi.verify(3, getRequestedFor(urlEqualTo("/legacy/adjustments/$ADJUSTMENT_ID")))
          }
        }

        @Test
        fun `will add message to dead letter queue`() {
          await untilCallTo {
            awsSqsSentencingDlqClient!!.countAllMessagesOnQueue(sentencingDlqUrl!!).get()
          } matches { it == 1 }
        }
      }

      @Nested
      inner class WhenNomisServiceFailsOnce {
        @BeforeEach
        fun setUp() {
          mappingServer.stubGetByAdjustmentIdWithError(ADJUSTMENT_ID, 404)
          mappingServer.stubCreateSentencingAdjustment()
          nomisApi.stubSentenceAdjustmentCreateWithErrorFollowedBySlowSuccess(BOOKING_ID, sentenceSequence)
          sentencingAdjustmentsApi.stubAdjustmentGet(
            adjustmentId = ADJUSTMENT_ID,
            sentenceSequence = sentenceSequence,
            bookingId = BOOKING_ID,
            adjustmentDays = 99,
            adjustmentType = "RX",
            adjustmentDate = "2022-01-01",
          )
          publishCreateAdjustmentDomainEvent()
        }

        @Test
        fun `will callback back to adjustment and NOMIS service twice`() {
          await untilAsserted {
            sentencingAdjustmentsApi.verify(2, getRequestedFor(urlEqualTo("/legacy/adjustments/$ADJUSTMENT_ID")))
          }
          await untilAsserted {
            nomisApi.verify(
              2,
              postRequestedFor(urlEqualTo("/prisoners/booking-id/$BOOKING_ID/sentences/$sentenceSequence/adjustments")),
            )
          }
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("sentencing-adjustment-create-success"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will eventually create a mapping after NOMIS adjustment is created`() {
          await untilAsserted {
            mappingServer.verify(
              postRequestedFor(urlEqualTo("/mapping/sentencing/adjustments")),
            )
          }
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("sentencing-adjustment-create-success"),
              any(),
              isNull(),
            )
          }
        }
      }

      @Nested
      inner class WhenNomisServiceKeepsFailing {
        @BeforeEach
        fun setUp() {
          mappingServer.stubGetByAdjustmentIdWithError(ADJUSTMENT_ID, 404)
          sentencingAdjustmentsApi.stubAdjustmentGet(
            adjustmentId = ADJUSTMENT_ID,
            sentenceSequence = sentenceSequence,
            bookingId = BOOKING_ID,
            adjustmentDays = 99,
            adjustmentType = "RX",
            adjustmentDate = "2022-01-01",
          )
          nomisApi.stubSentenceAdjustmentCreateWithError(BOOKING_ID, sentenceSequence, 503)
          await untilCallTo {
            awsSqsSentencingDlqClient!!.countAllMessagesOnQueue(sentencingDlqUrl!!).get()
          } matches { it == 0 }

          publishCreateAdjustmentDomainEvent()
        }

        @Test
        fun `will callback back to adjustment service 3 times before given up`() {
          await untilAsserted {
            nomisApi.verify(
              3,
              postRequestedFor(urlEqualTo("/prisoners/booking-id/$BOOKING_ID/sentences/$sentenceSequence/adjustments")),
            )
          }
        }

        @Test
        fun `will add message to dead letter queue`() {
          await untilCallTo {
            awsSqsSentencingDlqClient!!.countAllMessagesOnQueue(sentencingDlqUrl!!).get()
          } matches { it == 1 }
        }
      }

      @Nested
      inner class WhenMappingServiceFailsOnce {
        @BeforeEach
        fun setUp() {
          mappingServer.stubGetByAdjustmentIdWithError(ADJUSTMENT_ID, 404)
          mappingServer.stubCreateSentencingAdjustmentWithErrorFollowedBySlowSuccess()
          nomisApi.stubSentenceAdjustmentCreate(BOOKING_ID, sentenceSequence)
          sentencingAdjustmentsApi.stubAdjustmentGet(
            adjustmentId = ADJUSTMENT_ID,
            sentenceSequence = sentenceSequence,
            bookingId = BOOKING_ID,
            adjustmentDays = 99,
            adjustmentType = "RX",
            adjustmentDate = "2022-01-01",
          )
          publishCreateAdjustmentDomainEvent()
        }

        @Test
        fun `should only create the NOMIS adjustment once`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("sentencing-adjustment-create-success"),
              any(),
              isNull(),
            )
          }
          nomisApi.verify(
            1,
            postRequestedFor(urlEqualTo("/prisoners/booking-id/$BOOKING_ID/sentences/$sentenceSequence/adjustments")),
          )
        }

        @Test
        fun `will eventually create a mapping after NOMIS adjustment is created`() {
          await untilAsserted {
            mappingServer.verify(
              2,
              postRequestedFor(urlEqualTo("/mapping/sentencing/adjustments")),
            )
          }
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("sentencing-adjustment-create-success"),
              any(),
              isNull(),
            )
          }
        }
      }

      @Nested
      inner class WhenMappingServiceKeepsFailing {
        @BeforeEach
        fun setUp() {
          mappingServer.stubGetByAdjustmentIdWithError(ADJUSTMENT_ID, 404)
          mappingServer.stubCreateSentencingAdjustmentWithError(status = 503)
          nomisApi.stubSentenceAdjustmentCreate(BOOKING_ID, sentenceSequence)
          sentencingAdjustmentsApi.stubAdjustmentGet(
            adjustmentId = ADJUSTMENT_ID,
            sentenceSequence = sentenceSequence,
            bookingId = BOOKING_ID,
            adjustmentDays = 99,
            adjustmentType = "RX",
            adjustmentDate = "2022-01-01",
          )
          await untilCallTo {
            awsSqsSentencingDlqClient!!.countAllMessagesOnQueue(sentencingDlqUrl!!).get()
          } matches { it == 0 }

          publishCreateAdjustmentDomainEvent()
        }

        @Test
        fun `will try to create mapping 4 times only create the NOMIS adjustment once`() {
          await untilAsserted {
            mappingServer.verify(
              4,
              postRequestedFor(urlEqualTo("/mapping/sentencing/adjustments")),
            )
          }
          nomisApi.verify(
            1,
            postRequestedFor(urlEqualTo("/prisoners/booking-id/$BOOKING_ID/sentences/$sentenceSequence/adjustments")),
          )
        }

        @Test
        fun `will add message to dead letter queue`() {
          await untilCallTo {
            awsSqsSentencingDlqClient!!.countAllMessagesOnQueue(sentencingDlqUrl!!).get()
          } matches { it == 1 }
        }
      }

      @Nested
      inner class WhenMappingServiceDetectsADuplicate {
        private val nomisAdjustmentId = 98765L
        private val duplicateNomisAdjustmentId = 56789L

        @BeforeEach
        fun setUp() {
          mappingServer.stubGetByAdjustmentIdWithError(ADJUSTMENT_ID, 404)
          mappingServer.stubCreateSentencingAdjustmentWithDuplicateError(
            adjustmentId = ADJUSTMENT_ID,
            nomisAdjustmentId = nomisAdjustmentId,
            duplicateNomisAdjustmentId = duplicateNomisAdjustmentId,
          )
          nomisApi.stubSentenceAdjustmentCreate(BOOKING_ID, sentenceSequence)
          sentencingAdjustmentsApi.stubAdjustmentGet(
            adjustmentId = ADJUSTMENT_ID,
            sentenceSequence = sentenceSequence,
            bookingId = BOOKING_ID,
            adjustmentDays = 99,
            adjustmentType = "RX",
            adjustmentDate = "2022-01-01",
          )
          publishCreateAdjustmentDomainEvent()
        }

        @Test
        fun `should only create the NOMIS adjustment once`() {
          await untilAsserted {
            verify(telemetryClient, atLeastOnce()).trackEvent(
              any(),
              any(),
              isNull(),
            )
          }
          nomisApi.verify(
            1,
            postRequestedFor(urlEqualTo("/prisoners/booking-id/$BOOKING_ID/sentences/$sentenceSequence/adjustments")),
          )
        }

        @Test
        fun `will only try to create mapping once`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("sentencing-adjustment-mapping-create-failed"),
              any(),
              isNull(),
            )
          }
          await untilAsserted {
            mappingServer.verify(
              1,
              postRequestedFor(urlEqualTo("/mapping/sentencing/adjustments")),
            )
          }
        }

        @Test
        fun `will also log duplicate mapping so that an alert can be raised`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("to-nomis-synch-sentencing-adjustment-duplicate"),
              check {
                assertThat(it["existingAdjustmentId"]).isEqualTo(ADJUSTMENT_ID)
                assertThat(it["existingNomisAdjustmentId"]).isEqualTo(nomisAdjustmentId.toString())
                assertThat(it["existingNomisAdjustmentCategory"]).isEqualTo("SENTENCE")
                assertThat(it["duplicateAdjustmentId"]).isEqualTo(ADJUSTMENT_ID)
                assertThat(it["duplicateNomisAdjustmentId"]).isEqualTo(duplicateNomisAdjustmentId.toString())
                assertThat(it["duplicateNomisAdjustmentCategory"]).isEqualTo("SENTENCE")
              },
              isNull(),
            )
          }
        }
      }
    }
  }

  @Nested
  inner class UpdateSentencingAdjustment {
    @Nested
    inner class WhenAdjustmentHasBeenUpdatedByAdjustmentService {
      private val nomisAdjustmentId = 98765L

      @BeforeEach
      fun setUp() {
        mappingServer.stubGetByAdjustmentId(
          adjustmentId = ADJUSTMENT_ID,
          nomisAdjustmentId = nomisAdjustmentId,
        )
      }

      @Nested
      inner class WhenSentenceSequenceIsProvided {
        private val sentenceSequence = 1L

        @BeforeEach
        fun setUp() {
          nomisApi.stubSentenceAdjustmentUpdate(nomisAdjustmentId)

          sentencingAdjustmentsApi.stubAdjustmentGet(
            adjustmentId = ADJUSTMENT_ID,
            sentenceSequence = sentenceSequence,
            active = true,
            adjustmentDays = 99,
            adjustmentDate = "2022-01-01",
            adjustmentType = "RX",
            adjustmentFromDate = "2020-07-19",
            comment = "Adjusted for remand",
            bookingId = BOOKING_ID,
          )
          publishUpdateAdjustmentDomainEvent()
        }

        @Test
        fun `will callback back to adjustment service to get more details`() {
          await untilAsserted {
            sentencingAdjustmentsApi.verify(getRequestedFor(urlEqualTo("/legacy/adjustments/$ADJUSTMENT_ID")))
          }
          await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
        }

        @Test
        fun `will update a sentence adjustment in NOMIS`() {
          await untilAsserted {
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
          await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
        }

        @Test
        fun `will create success telemetry`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("sentencing-adjustment-updated-success"),
              check {
                assertThat(it["adjustmentId"]).isEqualTo(ADJUSTMENT_ID)
                assertThat(it["nomisAdjustmentId"]).isEqualTo(nomisAdjustmentId.toString())
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NUMBER)
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      inner class WhenSentenceSequenceIsNotProvided {
        @BeforeEach
        fun setUp() {
          nomisApi.stubKeyDateAdjustmentUpdate(nomisAdjustmentId)

          sentencingAdjustmentsApi.stubAdjustmentGet(
            adjustmentId = ADJUSTMENT_ID,
            sentenceSequence = null,
            active = true,
            adjustmentDays = 99,
            adjustmentDate = "2022-01-01",
            adjustmentType = "ADA",
            adjustmentFromDate = "2020-07-19",
            comment = "Adjusted for absence",
            bookingId = BOOKING_ID,
          )
          publishUpdateAdjustmentDomainEvent()
        }

        @Test
        fun `will callback back to adjustment service to get more details`() {
          await untilAsserted {
            sentencingAdjustmentsApi.verify(getRequestedFor(urlEqualTo("/legacy/adjustments/$ADJUSTMENT_ID")))
          }
          await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
        }

        @Test
        fun `will update a key date adjustment in NOMIS`() {
          await untilAsserted {
            nomisApi.verify(
              putRequestedFor(urlEqualTo("/key-date-adjustments/$nomisAdjustmentId"))
                .withRequestBody(matchingJsonPath("adjustmentTypeCode", equalTo("ADA")))
                .withRequestBody(matchingJsonPath("adjustmentDate", equalTo("2022-01-01")))
                .withRequestBody(matchingJsonPath("adjustmentDays", equalTo("99")))
                .withRequestBody(matchingJsonPath("adjustmentFromDate", equalTo("2020-07-19")))
                .withRequestBody(matchingJsonPath("active", absent()))
                .withRequestBody(matchingJsonPath("comment", equalTo("Adjusted for absence"))),
            )
          }
          await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
        }

        @Test
        fun `will create success telemetry`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("sentencing-adjustment-updated-success"),
              check {
                assertThat(it["adjustmentId"]).isEqualTo(ADJUSTMENT_ID)
                assertThat(it["nomisAdjustmentId"]).isEqualTo(nomisAdjustmentId.toString())
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NUMBER)
              },
              isNull(),
            )
          }
        }
      }
    }

    @Nested
    inner class WhenAdjustmentHasBeenUpdatedByNOMIS {
      private val creatingSystem = "NOMIS"
      private val nomisAdjustmentId = 98765L

      @BeforeEach
      fun setUp() {
        mappingServer.stubGetByAdjustmentId(
          adjustmentId = ADJUSTMENT_ID,
          nomisAdjustmentId = nomisAdjustmentId,
        )
        sentencingAdjustmentsApi.stubAdjustmentGet(
          adjustmentId = ADJUSTMENT_ID,
          sentenceSequence = 1,
          active = true,
          adjustmentDays = 99,
          adjustmentDate = "2022-01-01",
          adjustmentType = "RX",
          adjustmentFromDate = "2020-07-19",
          comment = "Adjusted for remand",
          bookingId = BOOKING_ID,
        )
        publishUpdateAdjustmentDomainEvent(creatingSystem)
      }

      @Test
      fun `will not update the sentence adjustment in NOMIS`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("sentencing-adjustment-updated-ignored"),
            any(),
            isNull(),
          )
        }
        sentencingAdjustmentsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/adjustments/$ADJUSTMENT_ID")))
        nomisApi.verify(
          0,
          putRequestedFor(urlEqualTo("/sentence-adjustments/$nomisAdjustmentId")),
        )
      }
    }

    @Nested
    @DisplayName("When mapping does not exist but adjustment exists - we never received a create domain event")
    inner class WhenMappingDoesNotExits {
      @BeforeEach
      fun setUp() {
        mappingServer.stubGetByAdjustmentIdWithError(
          adjustmentId = ADJUSTMENT_ID,
          404,
        )

        sentencingAdjustmentsApi.stubAdjustmentGet(
          adjustmentId = ADJUSTMENT_ID,
          sentenceSequence = 1,
          active = true,
          adjustmentDays = 99,
          adjustmentDate = "2022-01-01",
          adjustmentType = "RX",
          adjustmentFromDate = "2020-07-19",
          comment = "Adjusted for remand",
          bookingId = BOOKING_ID,
        )

        await untilCallTo {
          awsSqsSentencingDlqClient!!.countAllMessagesOnQueue(sentencingDlqUrl!!).get()
        } matches { it == 0 }

        publishUpdateAdjustmentDomainEvent()
      }

      @Test
      fun `will send message to dead letter queue`() {
        await untilCallTo {
          awsSqsSentencingDlqClient!!.countAllMessagesOnQueue(sentencingDlqUrl!!).get()
        } matches { it == 1 }
      }
    }

    @Nested
    @DisplayName("When mapping does not exist and adjustment does not exist")
    inner class WhenMappingAndAdjustmentDoNotExist {
      @BeforeEach
      fun setUp() {
        mappingServer.stubGetByAdjustmentIdWithError(
          adjustmentId = ADJUSTMENT_ID,
          404,
        )

        sentencingAdjustmentsApi.stubAdjustmentGetWithError(
          adjustmentId = ADJUSTMENT_ID,
          404,
        )

        publishUpdateAdjustmentDomainEvent()
      }

      @Test
      fun `will track skipped event and not send to dead letter queue`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("sentencing-adjustment-updated-skipped"),
            check {
              assertThat(it["adjustmentId"]).isEqualTo(ADJUSTMENT_ID)
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NUMBER)
            },
            isNull(),
          )
        }

        await untilCallTo {
          awsSqsSentencingDlqClient!!.countAllMessagesOnQueue(sentencingDlqUrl!!).get()
        } matches { it == 0 }
      }
    }
  }

  @Nested
  inner class DeleteSentencingAdjustment {
    @Nested
    inner class WhenMappingForAdjustmentExists {
      val nomisAdjustmentId = 12345L

      @BeforeEach
      fun setUp() {
        mappingServer.stubDeleteByAdjustmentId(ADJUSTMENT_ID)
      }

      @Nested
      inner class WhenMappingIsForAKeyDateAdjustment {
        @BeforeEach
        fun setUp() {
          mappingServer.stubGetByAdjustmentId(
            adjustmentId = ADJUSTMENT_ID,
            nomisAdjustmentId = nomisAdjustmentId,
            nomisAdjustmentCategory = "KEY-DATE",
          )

          nomisApi.stubKeyDateAdjustmentDelete(nomisAdjustmentId)

          publishDeleteAdjustmentDomainEvent()
        }

        @Test
        fun `will retrieve mapping do get the nomis details`() {
          await untilAsserted {
            mappingServer.verify(
              getRequestedFor(urlEqualTo("/mapping/sentencing/adjustments/adjustment-id/$ADJUSTMENT_ID")),
            )
          }
          await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
        }

        @Test
        fun `will delete the key date adjustment`() {
          await untilAsserted {
            nomisApi.verify(
              deleteRequestedFor(urlEqualTo("/key-date-adjustments/$nomisAdjustmentId")),
            )
          }
          await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
        }

        @Test
        fun `will also delete the adjustment mapping`() {
          await untilAsserted {
            mappingServer.verify(
              deleteRequestedFor(urlEqualTo("/mapping/sentencing/adjustments/adjustment-id/$ADJUSTMENT_ID")),
            )
          }
          await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
        }

        @Test
        fun `will track telemetry event for the delete`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("sentencing-adjustment-deleted-success"),
              check {
                assertThat(it["adjustmentId"]).isEqualTo(ADJUSTMENT_ID)
                assertThat(it["nomisAdjustmentId"]).isEqualTo(nomisAdjustmentId.toString())
                assertThat(it["nomisAdjustmentCategory"]).isEqualTo("KEY-DATE")
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      inner class WhenMappingIsForASentenceAdjustment {
        @BeforeEach
        fun setUp() {
          mappingServer.stubGetByAdjustmentId(
            adjustmentId = ADJUSTMENT_ID,
            nomisAdjustmentId = nomisAdjustmentId,
            nomisAdjustmentCategory = "SENTENCE",
          )

          nomisApi.stubSentenceAdjustmentDelete(nomisAdjustmentId)

          publishDeleteAdjustmentDomainEvent()
        }

        @Test
        fun `will retrieve mapping do get the nomis details`() {
          await untilAsserted {
            mappingServer.verify(
              getRequestedFor(urlEqualTo("/mapping/sentencing/adjustments/adjustment-id/$ADJUSTMENT_ID")),
            )
          }
          await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
        }

        @Test
        fun `will delete the sentence adjustment`() {
          await untilAsserted {
            nomisApi.verify(
              deleteRequestedFor(urlEqualTo("/sentence-adjustments/$nomisAdjustmentId")),
            )
          }
          await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
        }

        @Test
        fun `will also delete the adjustment mapping`() {
          await untilAsserted {
            mappingServer.verify(
              deleteRequestedFor(urlEqualTo("/mapping/sentencing/adjustments/adjustment-id/$ADJUSTMENT_ID")),
            )
          }
          await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
        }

        @Test
        fun `will track telemetry event for the delete`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("sentencing-adjustment-deleted-success"),
              check {
                assertThat(it["adjustmentId"]).isEqualTo(ADJUSTMENT_ID)
                assertThat(it["nomisAdjustmentId"]).isEqualTo(nomisAdjustmentId.toString())
                assertThat(it["nomisAdjustmentCategory"]).isEqualTo("SENTENCE")
              },
              isNull(),
            )
          }
        }
      }
    }

    @Nested
    inner class WhenMappingForAdjustmentDoesNotExist {
      @BeforeEach
      fun setUp() {
        mappingServer.stubGetByAdjustmentIdWithError(ADJUSTMENT_ID, 404)
        await untilCallTo {
          awsSqsSentencingDlqClient!!.countAllMessagesOnQueue(sentencingDlqUrl!!).get()
        } matches { it == 0 }
        publishDeleteAdjustmentDomainEvent()
      }

      @Test
      fun `will ignore the event and assume adjustment was deleted by migration service`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("sentencing-adjustment-deleted-ignored"),
            check {
              assertThat(it["adjustmentId"]).isEqualTo(ADJUSTMENT_ID)
              assertThat(it["reason"]).isEqualTo("mapping-already-deleted")
            },
            isNull(),
          )
        }
      }
    }
  }

  private fun publishCreateAdjustmentDomainEvent(source: String = CreatingSystem.DPS.name) {
    val eventType = "release-date-adjustments.adjustment.inserted"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(sentencingAdjustmentMessagePayload(ADJUSTMENT_ID, OFFENDER_NUMBER, eventType, source))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue(eventType).build(),
          ),
        ).build(),
    ).get()
  }

  private fun publishUpdateAdjustmentDomainEvent(source: String = CreatingSystem.DPS.name) {
    val eventType = "release-date-adjustments.adjustment.updated"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(sentencingAdjustmentMessagePayload(ADJUSTMENT_ID, OFFENDER_NUMBER, eventType, source))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue(eventType).build(),
          ),
        ).build(),
    ).get()
  }

  private fun publishDeleteAdjustmentDomainEvent(source: String = CreatingSystem.DPS.name) {
    val eventType = "release-date-adjustments.adjustment.deleted"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(sentencingAdjustmentMessagePayload(ADJUSTMENT_ID, OFFENDER_NUMBER, eventType, source))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue(eventType).build(),
          ),
        ).build(),
    ).get()
  }

  fun sentencingAdjustmentMessagePayload(adjustmentId: String, nomsNumber: String, eventType: String, source: String = "DPS") = """{"eventType":"$eventType", "additionalInformation": {"id":"$adjustmentId", "offenderNo": "$nomsNumber", "source": "$source"}}"""
}
