package uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.eq
import org.mockito.kotlin.any
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.SentencingAdjustmentsApiExtension.Companion.sentencingAdjustmentsApi
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue

const val ADJUSTMENT_ID = "1234"
const val OFFENDER_NUMBER = "A1234TT"
const val BOOKING_ID = 987651L

class SentencingAdjustmentsToNomisTest : SqsIntegrationTestBase() {

  @Nested
  inner class CreateSentencingAdjustment {
    @Nested
    inner class WhenAdjustmentHasJustBeenCreatedByAdjustmentService {
      val creatingSystem = "SENTENCE_ADJUSTMENTS"

      @BeforeEach
      fun setUp() {
        mappingServer.stubGetBySentenceAdjustmentIdWithError(ADJUSTMENT_ID, 404)
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
            adjustmentId = nomisAdjustmentId
          )

          sentencingAdjustmentsApi.stubAdjustmentGet(
            adjustmentId = ADJUSTMENT_ID,
            sentenceSequence = sentenceSequence,
            creatingSystem = creatingSystem,
            adjustmentDays = 99,
            adjustmentDate = "2022-01-01",
            adjustmentType = "RX",
            adjustmentStartPeriod = "2020-07-19",
            comment = "Adjusted for remand",
            bookingId = BOOKING_ID
          )
          publishCreateAdjustmentDomainEvent()
        }

        @Test
        fun `will callback back to adjustment service to get more details`() {
          await untilAsserted {
            sentencingAdjustmentsApi.verify(getRequestedFor(urlEqualTo("/adjustments/$ADJUSTMENT_ID")))
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
                .withRequestBody(matchingJsonPath("adjustmentFomDate", equalTo("2020-07-19")))
                .withRequestBody(matchingJsonPath("comment", equalTo("Adjusted for remand")))
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
                .withRequestBody(matchingJsonPath("nomisAdjustmentType", equalTo("SENTENCE")))
                .withRequestBody(matchingJsonPath("sentenceAdjustmentId", equalTo(ADJUSTMENT_ID)))
            )
          }
          await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
        }

        @Test
        fun `will create success telemetry`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("sentencing-adjustment-create-success"),
              org.mockito.kotlin.check {
                assertThat(it["sentenceAdjustmentId"]).isEqualTo(ADJUSTMENT_ID)
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
            adjustmentId = nomisAdjustmentId
          )

          sentencingAdjustmentsApi.stubAdjustmentGet(
            adjustmentId = ADJUSTMENT_ID,
            sentenceSequence = null,
            creatingSystem = creatingSystem,
            adjustmentDays = 99,
            adjustmentDate = "2022-01-01",
            adjustmentType = "ADA",
            adjustmentStartPeriod = "2020-07-19",
            comment = "Adjusted for absence",
            bookingId = BOOKING_ID
          )
          publishCreateAdjustmentDomainEvent()
        }

        @Test
        fun `will callback back to adjustment service to get more details`() {
          await untilAsserted {
            sentencingAdjustmentsApi.verify(getRequestedFor(urlEqualTo("/adjustments/$ADJUSTMENT_ID")))
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
                .withRequestBody(matchingJsonPath("adjustmentFomDate", equalTo("2020-07-19")))
                .withRequestBody(matchingJsonPath("comment", equalTo("Adjusted for absence")))
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
                .withRequestBody(matchingJsonPath("nomisAdjustmentType", equalTo("BOOKING")))
                .withRequestBody(matchingJsonPath("sentenceAdjustmentId", equalTo(ADJUSTMENT_ID)))
            )
          }
          await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
        }

        @Test
        fun `will create success telemetry`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("sentencing-adjustment-create-success"),
              org.mockito.kotlin.check {
                assertThat(it["sentenceAdjustmentId"]).isEqualTo(ADJUSTMENT_ID)
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
            creatingSystem = creatingSystem,
            adjustmentDays = 99,
            adjustmentDate = "2022-01-01",
            adjustmentType = "RX",
            adjustmentStartPeriod = "2020-07-19",
            comment = "Adjusted for remand",
            bookingId = BOOKING_ID
          )
          publishCreateAdjustmentDomainEvent()
        }

        @Test
        fun `will callback back to adjustment service to get more details`() {
          await untilAsserted {
            sentencingAdjustmentsApi.verify(getRequestedFor(urlEqualTo("/adjustments/$ADJUSTMENT_ID")))
          }
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
          nomisApi.verify(
            0,
            postRequestedFor(urlEqualTo("/prisoners/booking-id/$BOOKING_ID/sentences/$sentenceSequence/adjustments"))
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
            creatingSystem = creatingSystem,
            adjustmentDays = 99,
            adjustmentDate = "2022-01-01",
            adjustmentType = "ADA",
            adjustmentStartPeriod = "2020-07-19",
            comment = "Adjusted for absence",
            bookingId = BOOKING_ID
          )
          publishCreateAdjustmentDomainEvent()
        }

        @Test
        fun `will callback back to adjustment service to get more details`() {
          await untilAsserted {
            sentencingAdjustmentsApi.verify(getRequestedFor(urlEqualTo("/adjustments/$ADJUSTMENT_ID")))
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
            postRequestedFor(urlEqualTo("/prisoners/booking-id/$BOOKING_ID/adjustments"))
          )
        }
      }
    }

    @Nested
    inner class WhenMappingAlreadyCreatedForAdjustment {
      val creatingSystem = "SENTENCE_ADJUSTMENTS"

      @BeforeEach
      fun setUp() {
        mappingServer.stubGetBySentenceAdjustmentId(ADJUSTMENT_ID)
      }

      @Nested
      inner class WhenSentenceSequenceIsProvided {
        private val sentenceSequence = 1L

        @BeforeEach
        fun setUp() {
          sentencingAdjustmentsApi.stubAdjustmentGet(
            adjustmentId = ADJUSTMENT_ID,
            sentenceSequence = sentenceSequence,
            creatingSystem = creatingSystem,
            adjustmentDays = 99,
            adjustmentDate = "2022-01-01",
            adjustmentType = "RX",
            adjustmentStartPeriod = "2020-07-19",
            comment = "Adjusted for remand",
            bookingId = BOOKING_ID
          )
          publishCreateAdjustmentDomainEvent()
        }

        @Test
        fun `will callback back to mapping service to check message has not already been processed`() {
          await untilAsserted {
            mappingServer.verify(
              getRequestedFor(urlEqualTo("/mapping/sentencing/adjustments/sentence-adjustment-id/$ADJUSTMENT_ID"))
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
            postRequestedFor(urlEqualTo("/prisoners/booking-id/$BOOKING_ID/sentences/$sentenceSequence/adjustments"))
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
            creatingSystem = creatingSystem,
            adjustmentDays = 99,
            adjustmentDate = "2022-01-01",
            adjustmentType = "ADA",
            adjustmentStartPeriod = "2020-07-19",
            comment = "Adjusted for absence",
            bookingId = BOOKING_ID
          )
          publishCreateAdjustmentDomainEvent()
        }

        @Test
        fun `will callback back to mapping service to check message has not already been processed`() {
          await untilAsserted {
            mappingServer.verify(
              getRequestedFor(urlEqualTo("/mapping/sentencing/adjustments/sentence-adjustment-id/$ADJUSTMENT_ID"))
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
            postRequestedFor(urlEqualTo("/prisoners/booking-id/$BOOKING_ID/adjustments"))
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
          mappingServer.stubGetBySentenceAdjustmentIdWithError(ADJUSTMENT_ID, 404)
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
            sentencingAdjustmentsApi.verify(2, getRequestedFor(urlEqualTo("/adjustments/$ADJUSTMENT_ID")))
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
                .withRequestBody(matchingJsonPath("adjustmentDays", equalTo("99")))
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
      inner class WhenAdjustmentServiceKeepsFailing {
        @BeforeEach
        fun setUp() {
          mappingServer.stubGetBySentenceAdjustmentIdWithError(ADJUSTMENT_ID, 404)
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
            sentencingAdjustmentsApi.verify(3, getRequestedFor(urlEqualTo("/adjustments/$ADJUSTMENT_ID")))
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
          mappingServer.stubGetBySentenceAdjustmentIdWithError(ADJUSTMENT_ID, 404)
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
            sentencingAdjustmentsApi.verify(2, getRequestedFor(urlEqualTo("/adjustments/$ADJUSTMENT_ID")))
          }
          await untilAsserted {
            nomisApi.verify(
              2,
              postRequestedFor(urlEqualTo("/prisoners/booking-id/$BOOKING_ID/sentences/$sentenceSequence/adjustments"))
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
              postRequestedFor(urlEqualTo("/mapping/sentencing/adjustments"))
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
          mappingServer.stubGetBySentenceAdjustmentIdWithError(ADJUSTMENT_ID, 404)
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
              postRequestedFor(urlEqualTo("/prisoners/booking-id/$BOOKING_ID/sentences/$sentenceSequence/adjustments"))
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
          mappingServer.stubGetBySentenceAdjustmentIdWithError(ADJUSTMENT_ID, 404)
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
        fun `should only create the NOMSI adjustment once`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("sentencing-adjustment-create-success"),
              any(),
              isNull(),
            )
          }
          nomisApi.verify(
            1,
            postRequestedFor(urlEqualTo("/prisoners/booking-id/$BOOKING_ID/sentences/$sentenceSequence/adjustments"))
          )
        }

        @Test
        fun `will eventually create a mapping after NOMIS adjustment is created`() {
          await untilAsserted {
            mappingServer.verify(
              2,
              postRequestedFor(urlEqualTo("/mapping/sentencing/adjustments"))
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
          mappingServer.stubGetBySentenceAdjustmentIdWithError(ADJUSTMENT_ID, 404)
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
        fun `should only create the NOMSI adjustment once`() {
          await untilAsserted {
            nomisApi.verify(
              1,
              postRequestedFor(urlEqualTo("/prisoners/booking-id/$BOOKING_ID/sentences/$sentenceSequence/adjustments"))
            )
          }
        }

        @Test
        fun `will try to create mapping 4 times (1 for original attempt and 3 for the retry message) before given up`() {
          await untilAsserted {
            mappingServer.verify(
              4,
              postRequestedFor(urlEqualTo("/mapping/sentencing/adjustments"))
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
    }
  }

  private fun publishCreateAdjustmentDomainEvent() {
    val eventType = "sentencing.sentence.adjustment.created"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(sentencingAdjustmentMessagePayload(ADJUSTMENT_ID, OFFENDER_NUMBER, eventType))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue(eventType).build(),
          )
        ).build()
    ).get()
  }

  fun sentencingAdjustmentMessagePayload(adjustmentId: String, nomsNumber: String, eventType: String) =
    """{"eventType":"$eventType", "additionalInformation": {"id":"$adjustmentId", "nomsNumber": "$nomsNumber"}}"""
}
