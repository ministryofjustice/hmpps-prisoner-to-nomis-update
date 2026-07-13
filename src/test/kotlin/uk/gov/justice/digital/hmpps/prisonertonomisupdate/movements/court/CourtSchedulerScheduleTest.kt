package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class CourtSchedulerScheduleTest {

  @Nested
  inner class ShouldIgnoreSyncEvent {

    @ParameterizedTest
    @CsvSource(
      ",person.court-appearance.scheduled,false,false,Should never ignore if null external reference",
      ",person.court-appearance.scheduled,true,false,Should never ignore if null external reference",
      ",person.court-appearance.some-other-event,true,false,Should never ignore if null external reference",
      "urn:some-urn,person.court-appearance.scheduled,false,true,Should ignore specific event types",
      "urn:some-urn,person.court-appearance.some-other-event,false,false,Should not ignore other event types",
      "urn:some-urn,person.court-appearance.some-other-event,true,true,Should ignore other event types if ignoreAllSentencingEvents feature is enabled",
    )
    fun `should ignore sync event`(
      externalReference: String?,
      eventType: String,
      ignoreAllSentencingEvents: Boolean,
      expectedResult: Boolean,
      failureMessage: String,
    ) {
      assertThat(shouldIgnoreSyncEvent(externalReference, eventType, ignoreAllSentencingEvents))
        .withFailMessage(failureMessage)
        .isEqualTo(expectedResult)
    }
  }
}
