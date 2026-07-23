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
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.NOMIS_BOOKING_ID
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.property.PropertyDomainAdditionalInformation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.property.PropertyDomainEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.property.PropertyDpsApiExtension.Companion.jsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.property.PropertyMappingApiMockServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.property.PropertyNomisApiMockServer
import java.util.UUID

private val OFFENDER_NO = "A1234KT"
private val DPS_ID = UUID.randomUUID().toString()
private val NOMIS_ID = 123456789L
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
          propertyNomisApiMockServer.stubPostProperty(NOMIS_ID)
          propertyMappingApiMockServer.stubPostMapping()
          publishCreatePropertyDomainEvent()
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will create the Property in NOMIS`() {
          propertyNomisApiMockServer.verify(1, postRequestedFor(anyUrl()))
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
              assertThat(it).containsEntry("dpsPropertyId", DPS_ID)
              assertThat(it).containsEntry("nomisPropertyId", "$NOMIS_ID")
              assertThat(it).containsEntry("offenderNo", OFFENDER_NO)
              assertThat(it).containsEntry("mappingType", "DPS_CREATED")
              assertThat(it).containsEntry("bookingId", "$NOMIS_BOOKING_ID")
            },
            isNull(),
          )
        }
      }
    }
  }

  private fun publishCreatePropertyDomainEvent(
    offenderNo: String = OFFENDER_NO,
    dpsId: String = UUID.randomUUID().toString(),
    nomisBookingId: Long = BOOKING_ID,
    source: String = "DPS",
  ) {
    val event = PropertyDomainEvent(
      eventType = "prison-property.container.created",
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
        .build(),
    ).get()
  }
}
