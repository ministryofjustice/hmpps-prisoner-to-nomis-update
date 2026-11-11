package uk.gov.justice.digital.hmpps.prisonertonomisupdate.csra

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csra.model.CsraReviewDetail
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.AssessmentLevel

class CsraNomisLevelMappingTest {

  @Nested
  inner class WhenNomisHoldsHighGeneralAndSpecific {
    @ParameterizedTest
    @CsvSource("HIGH,HI", "HIGH_GENERAL,HI_GEN", "HIGH_SPECIFIC,HI_SPFC", "STANDARD,STANDARD")
    fun `final result is sent as its own NOMIS level`(result: CsraReviewDetail.FinalResult, expected: AssessmentLevel) {
      val review = dpsCsra(finalResult = result)

      with(review.toNomisCreateRequest(nomisHighGeneralSpecific = true)) {
        assertThat(calculatedLevel).isEqualTo(expected)
        assertThat(reviewLevel).isEqualTo(expected)
      }
      assertThat(review.toNomisUpdateRequest(nomisHighGeneralSpecific = true).reviewLevel).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource("HIGH,HI", "HIGH_GENERAL,HI_GEN", "HIGH_SPECIFIC,HI_SPFC", "STANDARD,STANDARD")
    fun `interim result is sent as its own NOMIS calculated level`(result: CsraReviewDetail.InterimResult, expected: AssessmentLevel) {
      val review = dpsCsra(finalResult = null).copy(interimResult = result)

      with(review.toNomisCreateRequest(nomisHighGeneralSpecific = true)) {
        assertThat(calculatedLevel).isEqualTo(expected)
        assertThat(reviewLevel).isNull()
      }
    }
  }

  @Nested
  inner class WhenNomisDoesNotHoldHighGeneralAndSpecific {
    @ParameterizedTest
    @CsvSource("HIGH,HI", "HIGH_GENERAL,HI", "HIGH_SPECIFIC,HI", "STANDARD,STANDARD")
    fun `every High final result is sent as HI`(result: CsraReviewDetail.FinalResult, expected: AssessmentLevel) {
      val review = dpsCsra(finalResult = result)

      with(review.toNomisCreateRequest(nomisHighGeneralSpecific = false)) {
        assertThat(calculatedLevel).isEqualTo(expected)
        assertThat(reviewLevel).isEqualTo(expected)
      }
      assertThat(review.toNomisUpdateRequest(nomisHighGeneralSpecific = false).reviewLevel).isEqualTo(expected)
    }

    @ParameterizedTest
    @CsvSource("HIGH,HI", "HIGH_GENERAL,HI", "HIGH_SPECIFIC,HI", "STANDARD,STANDARD")
    fun `every High interim result is sent as HI`(result: CsraReviewDetail.InterimResult, expected: AssessmentLevel) {
      val review = dpsCsra(finalResult = null).copy(interimResult = result)

      assertThat(review.toNomisCreateRequest(nomisHighGeneralSpecific = false).calculatedLevel).isEqualTo(expected)
    }
  }

  @Test
  fun `no result is sent as STANDARD calculated level with no review level`() {
    with(dpsCsra(finalResult = null).toNomisCreateRequest(nomisHighGeneralSpecific = true)) {
      assertThat(calculatedLevel).isEqualTo(AssessmentLevel.STANDARD)
      assertThat(reviewLevel).isNull()
    }
  }
}
