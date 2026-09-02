package uk.gov.justice.digital.hmpps.prisonertonomisupdate.csra

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.ArgumentMatchers.eq
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csra.CsraDpsApiExtension.Companion.csraApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csra.CsraDpsApiExtension.Companion.jsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CsraMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CsraCreateResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.withRequestBodyJsonPath
import java.util.UUID

class CsraToNomisIntTest : SqsIntegrationTestBase() {

  @Autowired
  private lateinit var csraNomisApiMockServer: CsraNomisApiMockServer

  @Autowired
  private lateinit var csraMappingApiMockServer: CsraMappingApiMockServer

  @Nested
  @DisplayName("cell.sharing.risk.assessment.created")
  inner class CsraCreated {
    @Nested
    @DisplayName("when NOMIS is the origin of a CSRA create")
    inner class WhenNomisCreated {
      @BeforeEach
      fun setup() {
        publishCsraDomainEvent("cell.sharing.risk.assessment.created", source = InformationSource.NOMIS)
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing it ignored the create`() {
        verify(telemetryClient).trackEvent(
          eq("csra-create-ignored"),
          check {
            assertThat(it).containsEntry("dpsCsraId", CSRA_DPS_ID)
            assertThat(it).containsEntry("offenderNo", CSRA_OFFENDER_NO)
            assertThat(it).containsEntry("source", "NOMIS")
          },
          isNull(),
        )
      }

      @Test
      fun `will not try to create the CSRA in NOMIS`() {
        csraNomisApiMockServer.verify(0, postRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of a CSRA create")
    inner class WhenDpsCreated {
      @Nested
      inner class HappyPath {

        @BeforeEach
        fun setup() {
          csraNomisApiMockServer.stubCreateCsra(CSRA_OFFENDER_NO, CsraCreateResponse(bookingId = CSRA_BOOKING_ID, sequence = CSRA_SEQUENCE))
          csraApi.stubGetCsraReview(UUID.fromString(CSRA_DPS_ID), dpsCsra(UUID.fromString(CSRA_DPS_ID)))
          csraMappingApiMockServer.stubPostMapping()
          publishCsraDomainEvent("cell.sharing.risk.assessment.created")
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will correctly create the CSRA in NOMIS`() {
          csraNomisApiMockServer.verify(
            1,
            postRequestedFor(urlPathEqualTo("/prisoners/$CSRA_OFFENDER_NO/csra"))
              .withRequestBodyJsonPath("$.type", "CSR")
              .withRequestBodyJsonPath("$.assessmentDate", "2026-01-02")
              .withRequestBodyJsonPath("$.evaluationDate", "2026-01-03")
              .withRequestBodyJsonPath("$.calculatedLevel", "STANDARD")
              .withRequestBodyJsonPath("$.status", "A")
              .withRequestBodyJsonPath("$.createdBy", "ME"),
          )
        }

        @Test
        fun `will create the mapping in the mapping service`() {
          csraMappingApiMockServer.verify(1, postRequestedFor(anyUrl()))
        }

        @Test
        fun `will send telemetry event showing the create`() {
          verify(telemetryClient).trackEvent(
            eq("csra-create-success"),
            check {
              assertThat(it).containsEntry("dpsCsraId", CSRA_DPS_ID)
              assertThat(it).containsEntry("nomisBookingId", "$CSRA_BOOKING_ID")
              assertThat(it).containsEntry("nomisSequence", "$CSRA_SEQUENCE")
              assertThat(it).containsEntry("offenderNo", CSRA_OFFENDER_NO)
              assertThat(it).containsEntry("mappingType", "DPS_CREATED")
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class ErrorScenarios {

        @BeforeEach
        fun setup() {
          csraNomisApiMockServer.stubCreateCsra(CSRA_OFFENDER_NO, CsraCreateResponse(bookingId = CSRA_BOOKING_ID, sequence = CSRA_SEQUENCE))
          csraApi.stubGetCsraReview(UUID.fromString(CSRA_DPS_ID), dpsCsra(UUID.fromString(CSRA_DPS_ID)))
        }

        @Test
        fun `mapping temporary error`() {
          csraMappingApiMockServer.stubPostMappingErrorFollowedBySuccess(HttpStatus.SERVICE_UNAVAILABLE)
          publishCsraDomainEvent("cell.sharing.risk.assessment.created")
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("csra-create-mapping-retry"),
              check {
                assertThat(it).containsEntry("dpsCsraId", CSRA_DPS_ID)
                assertThat(it).containsEntry("nomisBookingId", "$CSRA_BOOKING_ID")
                assertThat(it).containsEntry("nomisSequence", "$CSRA_SEQUENCE")
                assertThat(it).containsEntry("offenderNo", CSRA_OFFENDER_NO)
                assertThat(it).containsEntry("mappingType", "DPS_CREATED")
              },
              isNull(),
            )
            verify(telemetryClient).trackEvent(
              eq("csra-mapping-create-failed"),
              check {
                assertThat(it).containsEntry("dpsCsraId", CSRA_DPS_ID)
                assertThat(it).containsEntry("nomisBookingId", "$CSRA_BOOKING_ID")
                assertThat(it).containsEntry("nomisSequence", "$CSRA_SEQUENCE")
                assertThat(it).containsEntry("offenderNo", CSRA_OFFENDER_NO)
                assertThat(it).containsEntry("mappingType", "DPS_CREATED")
                assertThat(it).containsEntry("reason", "503 Service Unavailable from POST http://localhost:8084/mapping/csras")
              },
              isNull(),
            )
            verify(telemetryClient).trackEvent(
              eq("csra-mapping-create-success"),
              check {
                assertThat(it).containsEntry("dpsCsraId", CSRA_DPS_ID)
                assertThat(it).containsEntry("nomisBookingId", "$CSRA_BOOKING_ID")
                assertThat(it).containsEntry("nomisSequence", "$CSRA_SEQUENCE")
                assertThat(it).containsEntry("offenderNo", CSRA_OFFENDER_NO)
                assertThat(it).containsEntry("mappingType", "DPS_CREATED")
              },
              isNull(),
            )
            csraMappingApiMockServer.verify(2, postRequestedFor(anyUrl()))
          }
        }

        @Test
        fun `mapping permanent error`() {
          csraMappingApiMockServer.stubPostMapping(HttpStatus.SERVICE_UNAVAILABLE)
          publishCsraDomainEvent("cell.sharing.risk.assessment.created")
          await untilAsserted {
            csraMappingApiMockServer.verify(3, postRequestedFor(anyUrl()))
            verify(telemetryClient).trackEvent(
              eq("csra-create-mapping-retry"),
              check {
                assertThat(it).containsEntry("dpsCsraId", CSRA_DPS_ID)
                assertThat(it).containsEntry("nomisBookingId", "$CSRA_BOOKING_ID")
                assertThat(it).containsEntry("nomisSequence", "$CSRA_SEQUENCE")
                assertThat(it).containsEntry("offenderNo", CSRA_OFFENDER_NO)
                assertThat(it).containsEntry("mappingType", "DPS_CREATED")
              },
              isNull(),
            )
            verify(telemetryClient).trackEvent(
              eq("csra-mapping-create-failed"),
              check {
                assertThat(it).containsEntry("dpsCsraId", CSRA_DPS_ID)
                assertThat(it).containsEntry("nomisBookingId", "$CSRA_BOOKING_ID")
                assertThat(it).containsEntry("nomisSequence", "$CSRA_SEQUENCE")
                assertThat(it).containsEntry("offenderNo", CSRA_OFFENDER_NO)
                assertThat(it).containsEntry("mappingType", "DPS_CREATED")
                assertThat(it).containsEntry("reason", "503 Service Unavailable from POST http://localhost:8084/mapping/csras")
              },
              isNull(),
            )
          }
          verify(telemetryClient, never()).trackEvent(
            eq("csra-mapping-create-success"),
            anyMap(),
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("cell.sharing.risk.assessment.amended")
  inner class CsraUpdated {
    @Nested
    @DisplayName("when NOMIS is the origin of a CSRA update")
    inner class WhenNomisUpdated {
      @BeforeEach
      fun setup() {
        publishCsraDomainEvent("cell.sharing.risk.assessment.amended", source = InformationSource.NOMIS)
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing it ignored the update`() {
        verify(telemetryClient).trackEvent(
          eq("csra-update-ignored"),
          check {
            assertThat(it).containsEntry("dpsCsraId", CSRA_DPS_ID)
            assertThat(it).containsEntry("offenderNo", CSRA_OFFENDER_NO)
            assertThat(it).containsEntry("source", "NOMIS")
          },
          isNull(),
        )
      }

      @Test
      fun `will not try to update the CSRA in NOMIS`() {
        csraNomisApiMockServer.verify(0, putRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of a CSRA update")
    inner class WhenDpsUpdated {
      @BeforeEach
      fun setup() {
        csraMappingApiMockServer.stubGetByDpsId(
          CSRA_DPS_ID,
          CsraMappingDto(
            dpsCsraId = CSRA_DPS_ID,
            nomisBookingId = CSRA_BOOKING_ID,
            nomisSequence = CSRA_SEQUENCE,
            offenderNo = CSRA_OFFENDER_NO,
            mappingType = CsraMappingDto.MappingType.DPS_CREATED,
          ),
        )
        csraApi.stubGetCsraReview(UUID.fromString(CSRA_DPS_ID), dpsCsra(UUID.fromString(CSRA_DPS_ID)))
        csraNomisApiMockServer.stubUpdateCsra(CSRA_BOOKING_ID, CSRA_SEQUENCE)

        publishCsraDomainEvent("cell.sharing.risk.assessment.amended")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will correctly update the CSRA in NOMIS`() {
        csraNomisApiMockServer.verify(
          1,
          putRequestedFor(urlPathEqualTo("/prisoners/booking-id/$CSRA_BOOKING_ID/csra/$CSRA_SEQUENCE"))
            .withRequestBodyJsonPath("$.reviewLevel", "STANDARD")
            .withRequestBodyJsonPath("$.status", "A")
            .withRequestBodyJsonPath("$.evaluationDate", "2026-01-03")
            .withRequestBodyJsonPath("$.evaluationResultCode", "APP"),
        )
      }

      @Test
      fun `will send telemetry event showing the update`() {
        verify(telemetryClient).trackEvent(
          eq("csra-update-success"),
          check {
            assertThat(it).containsEntry("dpsCsraId", CSRA_DPS_ID)
            assertThat(it).containsEntry("offenderNo", CSRA_OFFENDER_NO)
            assertThat(it).containsEntry("source", "DPS")
          },
          isNull(),
        )
      }
    }
  }

  private fun publishCsraDomainEvent(
    eventType: String,
    dpsId: String = CSRA_DPS_ID,
    offenderNo: String = CSRA_OFFENDER_NO,
    source: InformationSource = InformationSource.DPS,
  ) {
    val event = CsraDomainEvent(
      eventType = eventType,
      version = "1",
      description = "A CSRA review was changed in DPS",
      occurredAt = "2026-07-01T00:00:00Z",
      additionalInformation = CsraDomainAdditionalInformation(
        id = UUID.fromString(dpsId),
        nomsNumber = offenderNo,
        source = source,
      ),
    )
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(jsonMapper.writeValueAsString(event))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue(eventType).build(),
          ),
        )
        .build(),
    ).get()
  }
}
