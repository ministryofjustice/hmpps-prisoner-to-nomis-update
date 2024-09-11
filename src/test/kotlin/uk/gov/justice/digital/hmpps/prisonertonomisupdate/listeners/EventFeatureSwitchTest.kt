package uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase

@TestPropertySource(
  properties = [
    "feature.event.prison-visit.revised=false",
    "feature.event.prison-visit.booked=true",
  ],
)
internal class EventFeatureSwitchTest : SqsIntegrationTestBase() {

  @Autowired
  private lateinit var featureSwitch: EventFeatureSwitch

  @Test
  fun `should return true when feature is enabled`() {
    assertThat(featureSwitch.isEnabled("prison-visit.booked")).isTrue
  }

  @Test
  fun `should return false when feature is disabled`() {
    assertThat(featureSwitch.isEnabled("prison-visit.revised")).isFalse
  }

  @Test
  fun `should return true when feature switch is not present`() {
    assertThat(featureSwitch.isEnabled("prison-visit.cancelled")).isTrue
  }

  @Nested
  inner class Info {
    @Test
    fun `should report feature switches in info endpoint`() {
      webTestClient.get().uri("/info")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("event-feature-switches").value<Map<String, Boolean>> {
          assertThat(it).containsExactlyEntriesOf(
            mapOf(
              "prison-visit.booked" to true,
              "prison-visit.revised" to false,
            ),
          )
        }
    }
  }
}
