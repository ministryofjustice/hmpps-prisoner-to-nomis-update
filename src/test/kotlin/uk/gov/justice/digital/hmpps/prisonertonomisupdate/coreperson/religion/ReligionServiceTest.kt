package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.religion

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.json.JsonTest
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.CorePersonCprApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.CorePersonNomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.CanonicalEthnicity
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.CanonicalIdentifiers
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.CanonicalReligion
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.CanonicalSex
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.CanonicalSexualOrientation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.CanonicalTitle
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.DpsPrisonRecord
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.PrisonReligion
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.PrisonReligion.ReligionCode
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.PrisonReligion.ReligionCode.AGNO
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.PrisonReligion.ReligionCode.BAHA
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ReligionMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.PersonReference
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.PersonReferenceList
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

@JsonTest
internal class ReligionServiceTest(@Autowired jsonMapper: JsonMapper) {

  private val telemetryClient: TelemetryClient = mock()
  private val corePersonNomisApiService: CorePersonNomisApiService = mock()
  private val mapping: ReligionMappingApiService = mock()
  private val corePersonCprApiService: CorePersonCprApiService = mock()

  private val religionService =
    ReligionService(telemetryClient, corePersonCprApiService, mapping, corePersonNomisApiService)

  @Nested
  inner class ReligionCreated {

    @Test
    fun `should log a religion being created`() = runTest {
      val cprReligionId = UUID.randomUUID()
      val prisonNumber = "A1234BC"
      religionService.religionCreated(
        ReligionService.ReligionEvent(
          "core-person-record.prison.religion.created",
          ReligionService.CprReligionCreatedInfo(cprReligionId),
          PersonReferenceList(listOf(PersonReference("prisonNumber", prisonNumber))),
        ),
      )

      verify(telemetryClient).trackEvent(
        eq("cpr-religion-created-success"),
        check {
          assertThat(it["prisonNumber"]).isEqualTo(prisonNumber)
          assertThat(it["cprReligionId"]).isEqualTo(cprReligionId.toString())
        },
        isNull(),
      )
    }
  }

  @Nested
  inner class ReligionsMerged {
    @Test
    fun `should call the nomis api with the correct list of religions`() = runTest {
      val prisonNumber = "A1234BC"
      val cprReligions = listOf(
        prisonReligion(
          religionCode = AGNO,
          endDate = LocalDate.of(2024, 4, 12),
          startDate = LocalDateTime.of(2021, 1, 25, 0, 0).toLocalDate(),
        ),
        prisonReligion(
          religionCode = BAHA,
          startDate = LocalDateTime.of(2024, 4, 12, 0, 0).toLocalDate(),
        ),
      )
      val mappings = listOf(
        religionMappingDto(
          cprReligions[0].cprReligionId!!, // Mapping for the first result
          nomisReligionId = 10000L,
          nomisPrisonNumber = prisonNumber,
        ),
        religionMappingDto(
          cprReligions[1].cprReligionId!!, // Mapping for the second result
          nomisReligionId = 10001L,
          nomisPrisonNumber = prisonNumber,
        ),
      )
      whenever(corePersonCprApiService.getCorePerson(prisonNumber)).thenReturn(
        personRecord(prisonNumber, cprReligions),
      )
      whenever(mapping.getByCprIds(cprReligions.map { it.cprReligionId!! })).thenReturn(
        mappings,
      )

      // Method under test
      religionService.mergeReligions(prisonNumber)

      verify(corePersonNomisApiService).mergeReligions(
        eq("A1234BC"),
        check {
          assertThat(it.religions[0].beliefId).isEqualTo(10000L)
          assertThat(it.religions[0].endDate).isEqualTo(cprReligions[0].endDate)
          assertThat(it.religions[1].beliefId).isEqualTo(10001L)
          assertThat(it.religions[1].endDate).isEqualTo(cprReligions[1].endDate)
        },
      )

      verify(telemetryClient).trackEvent(
        eq("cpr-religions-merged-success"),
        check {
          assertThat(it["prisonNumber"]).isEqualTo(prisonNumber)
        },
        isNull(),
      )
    }

    @Test
    fun `should not call the nomis api when there are no religions`() = runTest {
      val prisonNumber = "A1234BC"
      whenever(corePersonCprApiService.getCorePerson(prisonNumber)).thenReturn(
        personRecord(prisonNumber),
      )

      whenever(mapping.getByCprIds(any())).thenReturn(
        emptyList(),
      )

      // Method under test
      religionService.mergeReligions(prisonNumber)

      verify(corePersonNomisApiService, never()).mergeReligions(any(), any())
    }

    @Test
    fun `should throw if there is a missing mapping`() = runTest {
      val prisonNumber = "A1234BC"
      whenever(corePersonCprApiService.getCorePerson(prisonNumber)).thenReturn(
        personRecord(
          prisonNumber,
          listOf(
            prisonReligion(
              religionCode = BAHA,
              startDate = LocalDateTime.of(2024, 4, 12, 0, 0).toLocalDate(),
            ),
          ),
        ),
      )

      whenever(mapping.getByCprIds(any())).thenReturn(
        emptyList(),
      )

      // Method under test
      assertThrows<IllegalStateException> { religionService.mergeReligions(prisonNumber) }
    }
  }

  private fun religionMappingDto(cprReligionId: String, nomisReligionId: Long, nomisPrisonNumber: String) = ReligionMappingDto(
    cprId = cprReligionId,
    nomisId = nomisReligionId,
    nomisPrisonNumber = nomisPrisonNumber,
    mappingType = ReligionMappingDto.MappingType.MIGRATED,
    label = null,
    whenCreated = null,
  )

  private fun prisonReligion(
    religionCode: ReligionCode = ReligionCode.ADV,
    endDate: LocalDate? = null,
    cprReligionId: String = UUID.randomUUID().toString(),
    startDate: LocalDate,
  ) = PrisonReligion(
    religionCode = religionCode,
    changeReasonKnown = false,
    startDate = startDate,
    endDate = endDate,
    current = endDate == null,
    createDateTime = LocalDateTime.of(2021, 1, 25, 0, 0),
    createUserId = "createUserId",
    cprReligionId = cprReligionId,
  )

  private fun personRecord(prisonNumber: String, religions: List<PrisonReligion> = emptyList()) = DpsPrisonRecord(
    title = CanonicalTitle(),
    sex = CanonicalSex(),
    sexualOrientation = CanonicalSexualOrientation(),
    religion = CanonicalReligion(),
    ethnicity = CanonicalEthnicity(),
    aliases = emptyList(),
    nationalities = emptyList(),
    addresses = emptyList(),
    identifiers = CanonicalIdentifiers(
      crns = emptyList(),
      prisonNumbers = listOf(prisonNumber),
      defendantIds = emptyList(),
      cids = emptyList(),
      pncs = emptyList(),
      cros = emptyList(),
      nationalInsuranceNumbers = emptyList(),
      driverLicenseNumbers = emptyList(),
      arrestSummonsNumbers = emptyList(),
      otherIdentifiers = emptyList(),
    ),
    religionHistory = religions,
  )
}
