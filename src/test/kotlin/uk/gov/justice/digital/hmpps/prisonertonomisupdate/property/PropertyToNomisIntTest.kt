package uk.gov.justice.digital.hmpps.prisonertonomisupdate.property

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.property.PropertyDpsApiExtension.Companion.jsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.property.PropertyDpsApiExtension.Companion.propertyDpsApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.withRequestBodyJsonPath
import java.util.UUID

private val OFFENDER_NO = "A1234KT"
private val DPS_ID = UUID.randomUUID().toString()
private val DPS_LOCATION_ID = UUID.randomUUID().toString()
private val NOMIS_ID = 123456789L
private val NOMIS_LOCATION_ID = 1234567890L
private val BOOKING_ID = 123456L

class PropertyToNomisIntTest : SqsIntegrationTestBase() {

  @Autowired
  private lateinit var propertyNomisApiMockServer: PropertyNomisApiMockServer

  @Autowired
  private lateinit var propertyMappingApiMockServer: PropertyMappingApiMockServer

  @Nested
  @DisplayName("prison-property.container.created")
  inner class PropertyCreated {
    @Nested
    @DisplayName("when NOMIS is the origin of a Property create")
    inner class WhenNomisCreated {
      @BeforeEach
      fun setup() {
        publishCreatePropertyDomainEvent(source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing it ignored the create`() {
        verify(telemetryClient).trackEvent(
          eq("property-create-ignored"),
          any(),
          isNull(),
        )
      }

      @Test
      fun `will not try to create the Property in NOMIS`() {
        propertyNomisApiMockServer.verify(0, postRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of a Property create")
    inner class WhenDpsCreated {
      @Nested
      inner class HappyPath {

        @BeforeEach
        fun setup() {
          propertyNomisApiMockServer.stubPostProperty(NOMIS_ID, BOOKING_ID)
          propertyDpsApi.stubGetProperty(dpsProperty(UUID.fromString(DPS_ID), UUID.fromString(DPS_LOCATION_ID)))
          propertyMappingApiMockServer.stubPostMapping()
          propertyMappingApiMockServer.stubGetLocationByDpsId(DPS_LOCATION_ID, NOMIS_LOCATION_ID)
          publishCreatePropertyDomainEvent()
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will correctly create the Property in NOMIS`() {
          propertyNomisApiMockServer.verify(
            1,
            postRequestedFor(anyUrl())
              .withRequestBodyJsonPath("$.offenderNo", OFFENDER_NO)
              .withRequestBodyJsonPath("$.prisonId", "MDI")
              .withRequestBodyJsonPath("$.active", true)
              .withRequestBodyJsonPath("$.sealMark", "SEAL1234")
              .withRequestBodyJsonPath("$.containerCode", "BULK")
              .withRequestBodyJsonPath("$.internalLocationId", NOMIS_LOCATION_ID)
              .withRequestBodyJsonPath("$.proposedDisposalDate", "2026-03-04"),
          )
        }

        @Test
        fun `will create the mapping in the mapping service`() {
          propertyMappingApiMockServer.verify(1, postRequestedFor(anyUrl()))
        }

        @Test
        fun `will send telemetry event showing the create`() {
          verify(telemetryClient).trackEvent(
            eq("property-create-success"),
            check {
              assertThat(it).containsEntry("dpsPropertyContainerId", DPS_ID)
              assertThat(it).containsEntry("nomisPropertyContainerId", "$NOMIS_ID")
              assertThat(it).containsEntry("locationId", "$NOMIS_LOCATION_ID")
              assertThat(it).containsEntry("offenderNo", OFFENDER_NO)
              assertThat(it).containsEntry("bookingId", "$BOOKING_ID")
              assertThat(it).containsEntry("mappingType", "DPS_CREATED")
            },
            isNull(),
          )
        }
      }
    }
  }

  private fun publishCreatePropertyDomainEvent(
    offenderNo: String = OFFENDER_NO,
    dpsId: String = DPS_ID,
    source: String = "DPS",
  ) {
    val eventType = "prison-property.container.created"
    val event = PropertyDomainEvent(
      eventType = eventType,
      version = 1,
      description = "A prisoner property container was changed in DPS",
      detailUrl = null,
      occurredAt = "2026-07-01T00:00:00Z",
      prisonerNumber = offenderNo,
      source = source,
      additionalInformation = PropertyDomainAdditionalInformation(
        dpsId = dpsId,
        nomisPropertyContainerId = null,
        changedFields = null,
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
