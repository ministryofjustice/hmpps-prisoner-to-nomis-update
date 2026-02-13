package uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.eq
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase

class VisitSlotsToNomisIntTest : SqsIntegrationTestBase() {
  @Nested
  @DisplayName("official-visits-api.time-slot.created")
  inner class TimeSlotCreated {

    @Nested
    @DisplayName("when DPS is the origin of a time slot create")
    inner class WhenDpsCreated {

      @BeforeEach
      fun setUp() {
        publishCreateTimeSlotDomainEvent(timeSlotId = "12345", prisonId = "MDI", source = "DPS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the create`() {
        verify(telemetryClient).trackEvent(
          eq("time-slot-create-success"),
          check {
            assertThat(it["dpsTimeSlotId"]).isEqualTo("12345")
            assertThat(it["prisonId"]).isEqualTo("MDI")
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("official-visits-api.time-slot.updated")
  inner class TimeSlotUpdated {

    @Nested
    @DisplayName("when DPS is the origin of a time slot update")
    inner class WhenDpsUpdated {

      @BeforeEach
      fun setUp() {
        publishUpdateTimeSlotDomainEvent(timeSlotId = "12345", prisonId = "MDI", source = "DPS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the update`() {
        verify(telemetryClient).trackEvent(
          eq("time-slot-update-success"),
          check {
            assertThat(it["dpsTimeSlotId"]).isEqualTo("12345")
            assertThat(it["prisonId"]).isEqualTo("MDI")
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("official-visits-api.time-slot.deleted")
  inner class TimeSlotDeleted {

    @Nested
    @DisplayName("when DPS is the origin of a time slot delete")
    inner class WhenDpsDeleted {

      @BeforeEach
      fun setUp() {
        publishDeleteTimeSlotDomainEvent(timeSlotId = "12345", prisonId = "MDI", source = "DPS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the delete`() {
        verify(telemetryClient).trackEvent(
          eq("time-slot-delete-success"),
          check {
            assertThat(it["dpsTimeSlotId"]).isEqualTo("12345")
            assertThat(it["prisonId"]).isEqualTo("MDI")
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("official-visits-api.visit-slot.created")
  inner class VisitSlotCreated {

    @Nested
    @DisplayName("when DPS is the origin of a visit slot create")
    inner class WhenDpsCreated {

      @BeforeEach
      fun setUp() {
        publishCreateVisitSlotDomainEvent(visitSlotId = "12345", prisonId = "MDI", source = "DPS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the create`() {
        verify(telemetryClient).trackEvent(
          eq("visit-slot-create-success"),
          check {
            assertThat(it["dpsVisitSlotId"]).isEqualTo("12345")
            assertThat(it["prisonId"]).isEqualTo("MDI")
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("official-visits-api.visit-slot.updated")
  inner class VisitSlotUpdated {

    @Nested
    @DisplayName("when DPS is the origin of a visit slot update")
    inner class WhenDpsUpdated {

      @BeforeEach
      fun setUp() {
        publishUpdateVisitSlotDomainEvent(visitSlotId = "12345", prisonId = "MDI", source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the update`() {
        verify(telemetryClient).trackEvent(
          eq("visit-slot-update-success"),
          check {
            assertThat(it["dpsVisitSlotId"]).isEqualTo("12345")
            assertThat(it["prisonId"]).isEqualTo("MDI")
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("official-visits-api.visit-slot.deleted")
  inner class VisitSlotDeleted {

    @Nested
    @DisplayName("when DPS is the origin of a visit slot delete")
    inner class WhenDpsDeleted {

      @BeforeEach
      fun setUp() {
        publishDeleteVisitSlotDomainEvent(visitSlotId = "12345", prisonId = "MDI", source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the delete`() {
        verify(telemetryClient).trackEvent(
          eq("visit-slot-delete-success"),
          check {
            assertThat(it["dpsVisitSlotId"]).isEqualTo("12345")
            assertThat(it["prisonId"]).isEqualTo("MDI")
          },
          isNull(),
        )
      }
    }
  }

  @Suppress("SameParameterValue")
  private fun publishCreateTimeSlotDomainEvent(
    timeSlotId: String,
    source: String = "DPS",
    prisonId: String,
  ) {
    with("official-visits-api.time-slot.created") {
      publishDomainEvent(eventType = this, payload = timeSlotMessagePayload(eventType = this, timeSlotId = timeSlotId, source = source, prisonId = prisonId))
    }
  }

  @Suppress("SameParameterValue")
  private fun publishUpdateTimeSlotDomainEvent(
    timeSlotId: String,
    source: String = "DPS",
    prisonId: String,
  ) {
    with("official-visits-api.time-slot.updated") {
      publishDomainEvent(eventType = this, payload = timeSlotMessagePayload(eventType = this, timeSlotId = timeSlotId, source = source, prisonId = prisonId))
    }
  }

  @Suppress("SameParameterValue")
  private fun publishDeleteTimeSlotDomainEvent(
    timeSlotId: String,
    source: String = "DPS",
    prisonId: String,
  ) {
    with("official-visits-api.time-slot.deleted") {
      publishDomainEvent(eventType = this, payload = timeSlotMessagePayload(eventType = this, timeSlotId = timeSlotId, source = source, prisonId = prisonId))
    }
  }

  @Suppress("SameParameterValue")
  private fun publishCreateVisitSlotDomainEvent(
    visitSlotId: String,
    source: String = "DPS",
    prisonId: String,
  ) {
    with("official-visits-api.visit-slot.created") {
      publishDomainEvent(eventType = this, payload = visitSlotMessagePayload(eventType = this, visitSlotId = visitSlotId, source = source, prisonId = prisonId))
    }
  }

  @Suppress("SameParameterValue")
  private fun publishUpdateVisitSlotDomainEvent(
    visitSlotId: String,
    source: String = "DPS",
    prisonId: String,
  ) {
    with("official-visits-api.visit-slot.updated") {
      publishDomainEvent(eventType = this, payload = visitSlotMessagePayload(eventType = this, visitSlotId = visitSlotId, source = source, prisonId = prisonId))
    }
  }

  @Suppress("SameParameterValue")
  private fun publishDeleteVisitSlotDomainEvent(
    visitSlotId: String,
    source: String = "DPS",
    prisonId: String,
  ) {
    with("official-visits-api.visit-slot.deleted") {
      publishDomainEvent(eventType = this, payload = visitSlotMessagePayload(eventType = this, visitSlotId = visitSlotId, source = source, prisonId = prisonId))
    }
  }

  private fun publishDomainEvent(
    eventType: String,
    payload: String,
  ) {
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(payload)
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue(eventType).build(),
          ),
        ).build(),
    ).get()
  }
}

fun timeSlotMessagePayload(
  eventType: String,
  timeSlotId: String,
  prisonId: String = "MDI",
  source: String = "DPS",
) = //language=JSON
  """
    {
      "eventType":"$eventType", 
      "additionalInformation": {
        "timeSlotId": "$timeSlotId",
        "prisonId": "$prisonId",
        "source": "$source"
      }
    }
    """

fun visitSlotMessagePayload(
  eventType: String,
  visitSlotId: String,
  prisonId: String = "MDI",
  source: String = "DPS",
) = //language=JSON
  """
    {
      "eventType":"$eventType", 
      "additionalInformation": {
        "visitSlotId": "$visitSlotId",
        "prisonId": "$prisonId",
        "source": "$source"
      }
    }
    """
