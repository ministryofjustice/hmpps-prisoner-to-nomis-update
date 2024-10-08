package uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip

import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip.model.Attendee
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip.model.ContributoryFactor
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip.model.CsipRecord
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip.model.DecisionAndActions
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip.model.IdentifiedNeed
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip.model.Interview
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip.model.Investigation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip.model.Referral
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip.model.Review
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip.model.SaferCustodyScreeningOutcome
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CSIPFactorMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CSIPFullMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.Actions
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.ActionsRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.AttendeeRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.AuditDetailsRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CSIPFactorRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.DecisionRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.InterviewDetailRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.InvestigationDetailRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.PlanRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.ReviewRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.SaferCustodyScreeningRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpsertCSIPRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpsertReportDetailsRequest

fun CsipRecord.toNomisUpsertRequest(mapping: CSIPFullMappingDto? = null) =
  UpsertCSIPRequest(
    id = mapping?.nomisCSIPReportId,
    offenderNo = prisonNumber,
    incidentDate = referral.incidentDate,
    incidentTime = referral.incidentTime,
    typeCode = referral.incidentType.code,
    locationCode = referral.incidentLocation.code,
    areaOfWorkCode = referral.refererArea.code,
    reportedBy = referral.referredBy,
    reportedDate = referral.referralDate,
    proActiveReferral = referral.isProactiveReferral ?: false,
    staffAssaulted = referral.isStaffAssaulted ?: false,
    logNumber = logCode,
    staffAssaultedName = referral.assaultedStaffName,
    auditDetails = AuditDetailsRequest(
      createUsername = createdBy,
      modifyUsername = lastModifiedBy,
    ),
    prisonCodeWhenRecorded = prisonCodeWhenRecorded,
    reportDetailRequest = referral.toNomisReportDetails(mapping?.factorMappings),
    saferCustodyScreening = referral.saferCustodyScreeningOutcome?.toSCSRequest(),
    investigation = referral.investigation?.toNomisInvestigationRequest(),
    decision = referral.decisionAndActions?.toNomisDecisionRequest(),
    caseManager = plan?.caseManager,
    planReason = plan?.reasonForPlan,
    firstCaseReviewDate = plan?.firstCaseReviewDate,
    plans = plan?.identifiedNeeds?.map { it.toNomisPlanRequest() },
    reviews = plan?.reviews?.map { it.toNomisReviewRequest() },
  )

fun Review.toNomisReviewRequest(nomisReviewId: Long? = null) =
  ReviewRequest(
    id = nomisReviewId,
    remainOnCSIP = actions.contains(Review.Actions.REMAIN_ON_CSIP),
    csipUpdated = actions.contains(Review.Actions.CSIP_UPDATED),
    caseNote = actions.contains(Review.Actions.CASE_NOTE),
    closeCSIP = actions.contains(Review.Actions.CLOSE_CSIP),
    peopleInformed = actions.contains(Review.Actions.RESPONSIBLE_PEOPLE_INFORMED),
    // TODO why is this nullable? CHECK/FIX
    recordedDate = reviewDate!!,
    recordedBy = recordedBy,
    summary = summary,
    nextReviewDate = nextReviewDate,
    closeDate = csipClosedDate,
    auditDetails = AuditDetailsRequest(
      createUsername = createdBy,
      modifyUsername = lastModifiedBy,
    ),
    attendees = attendees.map { it.toNomisAttendeeRequest() },
  )

fun Attendee.toNomisAttendeeRequest(nomisAttendeeId: Long? = null) =
  AttendeeRequest(
    // TODO id = nomisAttendeeId,
    attended = isAttended ?: false,
    name = name,
    role = role,
    contribution = contribution,
    auditDetails = AuditDetailsRequest(
      createUsername = createdBy,
      modifyUsername = lastModifiedBy,
    ),
  )

fun DecisionAndActions.toNomisDecisionRequest() =
  DecisionRequest(
    conclusion = conclusion,
    decisionOutcomeCode = outcome?.code,
    signedOffRoleCode = signedOffByRole?.code,
    recordedBy = recordedBy,
    recordedDate = date,
    nextSteps = nextSteps,
    otherDetails = actionOther,
    actions = toNomisActionRequest(),
  )

fun DecisionAndActions.toNomisActionRequest() =
  ActionsRequest(
    openCSIPAlert = actions.contains(DecisionAndActions.Actions.OPEN_CSIP_ALERT),
    nonAssociationsUpdated = actions.contains(DecisionAndActions.Actions.NON_ASSOCIATIONS_UPDATED),
    observationBook = actions.contains(DecisionAndActions.Actions.OBSERVATION_BOOK),
    unitOrCellMove = actions.contains(DecisionAndActions.Actions.UNIT_OR_CELL_MOVE),
    csraOrRsraReview = actions.contains(DecisionAndActions.Actions.CSRA_OR_RSRA_REVIEW),
    serviceReferral = actions.contains(DecisionAndActions.Actions.SERVICE_REFERRAL),
    simReferral = actions.contains(DecisionAndActions.Actions.SIM_REFERRAL),
  )
fun Investigation.toNomisInvestigationRequest() =
  InvestigationDetailRequest(
    staffInvolved = staffInvolved,
    evidenceSecured = evidenceSecured,
    reasonOccurred = occurrenceReason,
    usualBehaviour = personsUsualBehaviour,
    trigger = personsTrigger,
    protectiveFactors = protectiveFactors,
    interviews = interviews.map { it.toNomisInterviewRequest() },
  )

fun Interview.toNomisInterviewRequest(nomisInterviewId: Long? = null) =
  InterviewDetailRequest(
    // TODO id = nomisInterviewId,
    interviewee = interviewee,
    date = interviewDate,
    roleCode = intervieweeRole.code,
    comments = interviewText,
    auditDetails = AuditDetailsRequest(
      createUsername = createdBy,
      modifyUsername = lastModifiedBy,
    ),
  )

fun Referral.toNomisReportDetails(mappings: List<CSIPFactorMappingDto>? = null) =
  UpsertReportDetailsRequest(
    saferCustodyTeamInformed = (isSaferCustodyTeamInformed == Referral.IsSaferCustodyTeamInformed.YES),
    referralComplete = isReferralComplete ?: false,
    involvementCode = incidentInvolvement?.code,
    concern = descriptionOfConcern,
    knownReasons = knownReasons,
    otherInformation = otherInformation,
    referralCompletedBy = referralCompletedBy,
    referralCompletedDate = referralCompletedDate,
    factors = contributoryFactors.map { dpsFactor ->
      dpsFactor.toNomisFactorRequest(
        mappings?.find {
            mappingFactor ->
          mappingFactor.dpsCSIPFactorId == dpsFactor.factorUuid.toString()
        }?.nomisCSIPFactorId,
      )
    },
  )
fun ContributoryFactor.toNomisFactorRequest(nomisFactorId: Long? = null) =
  CSIPFactorRequest(
    id = nomisFactorId,
    // TODO dpsId = factorUuid.toString(),
    typeCode = factorType.code,
    comment = comment,
    auditDetails = AuditDetailsRequest(
      createUsername = createdBy,
      modifyUsername = lastModifiedBy,
    ),
  )

fun SaferCustodyScreeningOutcome.toSCSRequest() = SaferCustodyScreeningRequest(
  scsOutcomeCode = outcome.code,
  recordedBy = recordedBy,
  recordedDate = date,
  reasonForDecision = reasonForDecision,
)
fun IdentifiedNeed.toNomisPlanRequest(nomisPlanId: Long? = null) =
  PlanRequest(
    id = nomisPlanId,
    identifiedNeed = identifiedNeed,
    intervention = intervention,
    createdDate = createdAt.toLocalDate(),
    targetDate = targetDate,
    progression = progression,
    referredBy = responsiblePerson,

    closedDate = closedDate,
    auditDetails = AuditDetailsRequest(
      createUsername = createdBy,
      modifyUsername = lastModifiedBy,
    ),
  )
