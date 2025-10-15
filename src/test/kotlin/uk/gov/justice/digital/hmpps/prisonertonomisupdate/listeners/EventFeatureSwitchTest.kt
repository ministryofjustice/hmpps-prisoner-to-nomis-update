package uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase

@TestPropertySource(
  properties = [
    "feature.event.prison-visit.revised=true",
    "feature.event.casenote.prison-visit.booked=false",
    "feature.event.OTHER_EVENT=false",
  ],
)
internal class EventFeatureSwitchTest : SqsIntegrationTestBase() {

  @Autowired
  private lateinit var featureSwitch: EventFeatureSwitch

  @Test
  fun `should return true when feature is enabled`() {
    assertThat(featureSwitch.isEnabled("prison-visit.revised", domain = "billybob")).isTrue
  }

  @Test
  fun `should return false when feature is disabled`() {
    assertThat(featureSwitch.isEnabled("OTHER_EVENT", domain = "billybob")).isFalse
  }

  @Test
  fun `should return true when feature switch is not present`() {
    assertThat(featureSwitch.isEnabled("NO_SWITCH_EVENT", domain = "billybob")).isTrue
  }

  @Test
  fun `should return true when feature switch is not present for that domain`() {
    assertThat(featureSwitch.isEnabled("prison-visit.booked", domain = "billybob")).isTrue
  }

  @Test
  fun `should return true when feature switch is not present for domain`() {
    assertThat(featureSwitch.isEnabled("prison-visit.booked", domain = "alert")).isTrue
  }

  @Test
  fun `should return false when feature switch is present for domain`() {
    assertThat(featureSwitch.isEnabled("prison-visit.booked", domain = "casenote")).isFalse()
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
              "OTHER_EVENT" to false,
              "casenote.prison-visit.booked" to false,
              "prison-visit.revised" to true,
            ),
          )
        }
    }
  }
}
