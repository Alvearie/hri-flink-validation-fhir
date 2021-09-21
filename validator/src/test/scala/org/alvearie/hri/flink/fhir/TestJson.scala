/*
 * (C) Copyright IBM Corp. 2021
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.alvearie.hri.flink.fhir

import org.alvearie.hri.flink.core.serialization.HriRecord

import java.nio.charset.StandardCharsets
import java.util

object TestJson {
  val DefaultTopic = "ingest.porcupine.data-int1.in"
  val DefaultPartition = 1
  val DefaultOffset = 1234L

  val ValidFhirOneRecord = """{"resourceType":"Bundle","type":"collection","entry":[{"resource":{"resourceType":"Claim","identifier":[{"value":"000000000000000000016372981"}],"status":"active","type":{"text":"pharmacy"},"use":"claim","patient":{"identifier":{"value":"DPFO199578"}},"created":"2020-03-18","provider":{"identifier":{"value":"1019343"}},"priority":{"text":"normal"},"insurance":[{"sequence":1,"focal":true,"coverage":{"identifier":{"value":"placeholder"}}}]}},{"resource":{"resourceType":"ClaimResponse","status":"active","type":{"text":"pharmacy"},"use":"claim","patient":{"identifier":{"value":"DPFO199578"}},"created":"2020-03-18","insurer":{"identifier":{"value":"placeholder"}},"outcome":"queued","adjudication":[{"category":{"text":"insurance"},"amount":{"value":0.00}},{"category":{"text":"copay"},"amount":{"value":0.00}},{"category":{"text":"deductible"},"amount":{"value":0.00}},{"category":{"text":"despensingfee"},"amount":{"value":0.40}},{"category":{"text":"ingredient cost"},"amount":{"value":12.27}},{"category":{"text":"Net Payment"},"amount":{"value":27.67}},{"category":{"text":"sales tax"},"amount":{"value":0.00}},{"category":{"text":"Tax Amount"},"amount":{"value":0.00}},{"category":{"text":"Third Party Amount"},"amount":{"value":0.00}}]}},{"resource":{"resourceType":"MedicationDispense","status":"in-progress","medicationCodeableConcept":{"text":"~"},"quantity":{"value":0.5},"daysSupply":{"value":1.0}}},{"resource":{"resourceType":"Medication","ingredient":[{"itemCodeableConcept":{"text":"~"}}]}}]}"""
  val ValidFhirRecTwo = """{"resourceType":"Bundle","type":"collection","entry":[{"resource":{"resourceType":"Claim","identifier":[{"value":"000000000000000000019721078"}],"status":"active","type":{"text":"pharmacy"},"use":"claim","patient":{"identifier":{"value":"EBEY299851"}},"created":"2020-03-18","provider":{"identifier":{"value":"1019812"}},"priority":{"text":"normal"},"insurance":[{"sequence":1,"focal":true,"coverage":{"identifier":{"value":"placeholder"}}}]}},{"resource":{"resourceType":"ClaimResponse","status":"active","type":{"text":"pharmacy"},"use":"claim","patient":{"identifier":{"value":"EBEY299851"}},"created":"2020-03-18","insurer":{"identifier":{"value":"placeholder"}},"outcome":"queued","adjudication":[{"category":{"text":"insurance"},"amount":{"value":0.00}},{"category":{"text":"copay"},"amount":{"value":0.00}},{"category":{"text":"deductible"},"amount":{"value":8.18}},{"category":{"text":"despensingfee"},"amount":{"value":0.00}},{"category":{"text":"ingredient cost"},"amount":{"value":8.18}},{"category":{"text":"Net Payment"},"amount":{"value":0.00}},{"category":{"text":"sales tax"},"amount":{"value":0.00}},{"category":{"text":"Tax Amount"},"amount":{"value":0.00}},{"category":{"text":"Third Party Amount"},"amount":{"value":0.00}}]}},{"resource":{"resourceType":"MedicationDispense","status":"in-progress","medicationCodeableConcept":{"text":"~"},"quantity":{"value":30.0},"daysSupply":{"value":30.0}}},{"resource":{"resourceType":"Medication","ingredient":[{"itemCodeableConcept":{"text":"~"}}]}}]}"""
  val ValidFhirRecThree = """{"resourceType":"Bundle","type":"collection","entry":[{"resource":{"resourceType":"Claim","identifier":[{"value":"000000000000000000016686490"}],"status":"active","type":{"text":"pharmacy"},"use":"claim","patient":{"identifier":{"value":"EFNI225094"}},"created":"2020-03-18","provider":{"identifier":{"value":"1019947"}},"priority":{"text":"normal"},"insurance":[{"sequence":1,"focal":true,"coverage":{"identifier":{"value":"placeholder"}}}]}},{"resource":{"resourceType":"ClaimResponse","status":"active","type":{"text":"pharmacy"},"use":"claim","patient":{"identifier":{"value":"EFNI225094"}},"created":"2020-03-18","insurer":{"identifier":{"value":"placeholder"}},"outcome":"queued","adjudication":[{"category":{"text":"insurance"},"amount":{"value":21.22}},{"category":{"text":"copay"},"amount":{"value":0.00}},{"category":{"text":"deductible"},"amount":{"value":0.00}},{"category":{"text":"despensingfee"},"amount":{"value":0.11}},{"category":{"text":"ingredient cost"},"amount":{"value":212.11}},{"category":{"text":"Net Payment"},"amount":{"value":191.00}},{"category":{"text":"sales tax"},"amount":{"value":0.00}},{"category":{"text":"Tax Amount"},"amount":{"value":0.00}},{"category":{"text":"Third Party Amount"},"amount":{"value":0.00}}]}},{"resource":{"resourceType":"MedicationDispense","status":"in-progress","medicationCodeableConcept":{"text":"~"},"quantity":{"value":90.0},"daysSupply":{"value":90.0}}},{"resource":{"resourceType":"Medication","ingredient":[{"itemCodeableConcept":{"text":"~"}}]}}]}"""

  val ValidFhirRecUtf8 = """{"resourceType":"Bundle","type":"collection","entry":[{"resource":{"resourceType":"Claim","identifier":[{"value":"000000000000000000016686490"}],"status":"active","type":{"text":"中文 pharmacy"},"use":"claim","patient":{"identifier":{"value":"EFNI225094"}},"created":"2020-03-18","provider":{"identifier":{"value":"1019947"}},"priority":{"text":"中文 normal"},"insurance":[{"sequence":1,"focal":true,"coverage":{"identifier":{"value":"placeholder"}}}]}},{"resource":{"resourceType":"ClaimResponse","status":"active","type":{"text":"中文 pharmacy"},"use":"claim","patient":{"identifier":{"value":"EFNI225094"}},"created":"2020-03-18","insurer":{"identifier":{"value":"中文 placeholder"}},"outcome":"queued","adjudication":[{"category":{"text":"中文 insurance"},"amount":{"value":21.22}},{"category":{"text":"中文 copay"},"amount":{"value":0.00}},{"category":{"text":"中文 deductible"},"amount":{"value":0.00}},{"category":{"text":"中文 despensing fee"},"amount":{"value":0.11}},{"category":{"text":"中文 ingredient cost"},"amount":{"value":212.11}},{"category":{"text":"中文 Net Payment"},"amount":{"value":191.00}},{"category":{"text":"中文 sales tax"},"amount":{"value":0.00}},{"category":{"text":"中文 Tax Amount"},"amount":{"value":0.00}},{"category":{"text":"中文 Third Party Amount"},"amount":{"value":0.00}}]}},{"resource":{"resourceType":"MedicationDispense","status":"in-progress","medicationCodeableConcept":{"text":"some 中文 chars"},"quantity":{"value":90.0},"daysSupply":{"value":90.0}}},{"resource":{"resourceType":"Medication","ingredient":[{"itemCodeableConcept":{"text":"some 中文 chars"}}]}}]}"""

  val InvalidResourceTypeValidationErrorStr = "$.resourceType: must be a constant value Account, $.resourceType: must be a constant value ActivityDefinition, $.subject: is missing but it is required, $.patient: is missing but it is required, $.participant: is missing but it is required, $.appointment: is missing but it is required, $.agent: is missing but it is required, $.source: is missing but it is required, $.code: is missing but it is required, $.resourceType: must be a constant value Binary, $.resourceType: must be a constant value BiologicallyDerivedProduct, $.entry[0].resource.resourceType: must be a constant value Account, $.entry[0].resource.priority: object found, string expected, $.entry[0].resource.resourceType: must be a constant value ActivityDefinition, $.entry[0].resource.subject: is missing but it is required, $.entry[0].resource.type: does not have a value in the enumeration [allergy, intolerance], $.entry[0].resource.resourceType: must be a constant value AllergyIntolerance, $.entry[0].resource.participant: is missing but it is required, $.entry[0].resource.appointment: is missing but it is required, $.entry[0].resource.agent: is missing but it is required, $.entry[0].resource.source: is missing but it is required, $.entry[0].resource.code: is missing but it is required, $.entry[0].resource.resourceType: must be a constant value Binary, $.entry[0].resource.resourceType: must be a constant value BiologicallyDerivedProduct, $.entry[0].resource.status: does not have a value in the enumeration [available, unavailable], $.entry[0].resource.resourceType: must be a constant value BodyStructure, $.entry[0].resource.type: does not have a value in the enumeration [document, message, transaction, transaction-response, batch, batch-response, history, searchset, collection], $.entry[0].resource.resourceType: must be a constant value Bundle, $.entry[0].resource.resourceType: must be a constant value CapabilityStatement, $.entry[0].resource.resourceType: must be a constant value CareTeam, $.entry[0].resource.referencedItem: is missing but it is required, $.entry[0].resource.resourceType: must be a constant value ChargeItemDefinition, $.entry[0].resource.resourceType: must be a constant value Claim, $.entry[0].resource.insurer: is missing but it is required, $.entry[0].resource.resourceType: must be a constant value CodeSystem, $.entry[0].resource.resourceType: must be a constant value Communication, $.entry[0].resource.resourceType: must be a constant value CommunicationRequest, $.entry[0].resource.resourceType: must be a constant value CompartmentDefinition, $.entry[0].resource.author: is missing but it is required, $.entry[0].resource.resourceType: must be a constant value ConceptMap, $.entry[0].resource.scope: is missing but it is required, $.entry[0].resource.category: is missing but it is required, $.entry[0].resource.resourceType: must be a constant value Contract, $.entry[0].resource.payor: is missing but it is required, $.entry[0].resource.beneficiary: is missing but it is required, $.entry[0].resource.request: is missing but it is required, $.entry[0].resource.resourceType: must be a constant value DetectedIssue, $.entry[0].resource.resourceType: must be a constant value Device, $.entry[0].resource.resourceType: must be a constant value DeviceDefinition, $.entry[0].resource.resourceType: must be a constant value DeviceMetric, $.entry[0].resource.device: is missing but it is required, $.entry[0].resource.content: is missing but it is required, $.entry[0].resource.exposureAlternative: is missing but it is required, $.entry[0].resource.exposure: is missing but it is required, $.entry[0].resource.outcome: is missing but it is required, $.entry[0].resource.population: is missing but it is required, $.entry[0].resource.class: is missing but it is required, $.entry[0].resource.payloadType: is missing but it is required, $.entry[0].resource.connectionType: is missing but it is required, $.entry[0].resource.resourceType: must be a constant value EnrollmentRequest, $.entry[0].resource.resourceType: must be a constant value EnrollmentResponse, $.entry[0].resource.type: object found, array expected, $.entry[0].resource.resourceType: must be a constant value EpisodeOfCare, $.entry[0].resource.trigger: is missing but it is required, $.entry[0].resource.exposureBackground: is missing but it is required, $.entry[0].resource.characteristic: is missing but it is required, $.entry[0].resource.resourceType: must be a constant value ExampleScenario, $.entry[0].resource.relationship: is missing but it is required, $.entry[0].resource.description: is missing but it is required, $.entry[0].resource.resourceType: must be a constant value GraphDefinition, $.entry[0].resource.type: does not have a value in the enumeration [person, animal, practitioner, device, medication, substance], $.entry[0].resource.resourceType: must be a constant value Group, $.entry[0].resource.resourceType: must be a constant value GuidanceResponse, $.entry[0].resource.status: does not have a value in the enumeration [success, data-requested, data-required, in-progress, failure, entered-in-error], $.entry[0].resource.resourceType: must be a constant value HealthcareService, $.entry[0].resource.vaccineCode: is missing but it is required, $.entry[0].resource.doseStatus: is missing but it is required, $.entry[0].resource.targetDisease: is missing but it is required, $.entry[0].resource.immunizationEvent: is missing but it is required, $.entry[0].resource.recommendation: is missing but it is required, $.entry[0].resource.resourceType: must be a constant value ImplementationGuide, $.entry[0].resource.resourceType: must be a constant value InsurancePlan, $.entry[0].resource.resourceType: must be a constant value Invoice, $.entry[0].resource.status: does not have a value in the enumeration [draft, issued, balanced, cancelled, entered-in-error], $.entry[0].resource.resourceType: must be a constant value Library, $.entry[0].resource.item: is missing but it is required, $.entry[0].resource.resourceType: must be a constant value List, $.entry[0].resource.status: does not have a value in the enumeration [current, retired, entered-in-error], $.entry[0].resource.resourceType: must be a constant value Location, $.entry[0].resource.resourceType: must be a constant value Measure, $.entry[0].resource.period: is missing but it is required, $.entry[0].resource.measure: is missing but it is required, $.entry[0].resource.resourceType: must be a constant value Medication, $.entry[0].resource.resourceType: must be a constant value MedicationDispense, $.entry[0].resource.resourceType: must be a constant value MedicationKnowledge, $.entry[0].resource.name: is missing but it is required, $.entry[0].resource.resourceType: must be a constant value MedicinalProductAuthorization, $.entry[0].resource.resourceType: must be a constant value MedicinalProductContraindication, $.entry[0].resource.resourceType: must be a constant value MedicinalProductIndication, $.entry[0].resource.role: is missing but it is required, $.entry[0].resource.resourceType: must be a constant value MedicinalProductInteraction, $.entry[0].resource.quantity: is missing but it is required, $.entry[0].resource.manufacturedDoseForm: is missing but it is required, $.entry[0].resource.packageItem: is missing but it is required, $.entry[0].resource.administrableDoseForm: is missing but it is required, $.entry[0].resource.routeOfAdministration: is missing but it is required, $.entry[0].resource.resourceType: must be a constant value MedicinalProductUndesirableEffect, $.entry[0].resource.resourceType: must be a constant value MessageDefinition, $.entry[0].resource.type: does not have a value in the enumeration [aa, dna, rna], $.entry[0].resource.resourceType: must be a constant value MolecularSequence, $.entry[0].resource.uniqueId: is missing but it is required, $.entry[0].resource.resourceType: must be a constant value NutritionOrder, $.entry[0].resource.type: object found, boolean expected, $.entry[0].resource.resourceType: must be a constant value OperationDefinition, $.entry[0].resource.issue: is missing but it is required, $.entry[0].resource.resourceType: must be a constant value Organization, $.entry[0].resource.resourceType: must be a constant value OrganizationAffiliation, $.entry[0].resource.resourceType: must be a constant value Parameters, $.entry[0].resource.resourceType: must be a constant value Patient, $.entry[0].resource.amount: is missing but it is required, $.entry[0].resource.recipient: is missing but it is required, $.entry[0].resource.payment: is missing but it is required, $.entry[0].resource.paymentAmount: is missing but it is required, $.entry[0].resource.resourceType: must be a constant value Person, $.entry[0].resource.resourceType: must be a constant value PlanDefinition, $.entry[0].resource.resourceType: must be a constant value Practitioner, $.entry[0].resource.resourceType: must be a constant value PractitionerRole, $.entry[0].resource.target: is missing but it is required, $.entry[0].resource.resourceType: must be a constant value Questionnaire, $.entry[0].resource.resourceType: must be a constant value QuestionnaireResponse, $.entry[0].resource.status: does not have a value in the enumeration [in-progress, completed, amended, entered-in-error, stopped], $.entry[0].resource.resourceType: must be a constant value RelatedPerson, $.entry[0].resource.resourceType: must be a constant value RequestGroup, $.entry[0].resource.resourceType: must be a constant value ResearchStudy, $.entry[0].resource.study: is missing but it is required, $.entry[0].resource.individual: is missing but it is required, $.entry[0].resource.actor: is missing but it is required, $.entry[0].resource.type: does not have a value in the enumeration [number, date, string, token, reference, composite, quantity, uri, special], $.entry[0].resource.resourceType: must be a constant value SearchParameter, $.entry[0].resource.schedule: is missing but it is required, $.entry[0].resource.resourceType: must be a constant value Specimen, $.entry[0].resource.status: does not have a value in the enumeration [available, unavailable, unsatisfactory, entered-in-error], $.entry[0].resource.resourceType: must be a constant value SpecimenDefinition, $.entry[0].resource.type: object found, string expected, $.entry[0].resource.resourceType: must be a constant value StructureDefinition, $.entry[0].resource.group: is missing but it is required, $.entry[0].resource.channel: is missing but it is required, $.entry[0].resource.resourceType: must be a constant value SubstanceNucleicAcid, $.entry[0].resource.resourceType: must be a constant value SubstancePolymer, $.entry[0].resource.resourceType: must be a constant value SubstanceProtein, $.entry[0].resource.resourceType: must be a constant value SubstanceReferenceInformation, $.entry[0].resource.resourceType: must be a constant value SubstanceSourceMaterial, $.entry[0].resource.resourceType: must be a constant value SubstanceSpecification, $.entry[0].resource.resourceType: must be a constant value SupplyDelivery, $.entry[0].resource.status: does not have a value in the enumeration [in-progress, completed, abandoned, entered-in-error], $.entry[0].resource.status: does not have a value in the enumeration [draft, requested, received, accepted, rejected, ready, cancelled, in-progress, on-hold, failed, completed, entered-in-error], $.entry[0].resource.resourceType: must be a constant value Task, $.entry[0].resource.resourceType: must be a constant value TerminologyCapabilities, $.entry[0].resource.testScript: is missing but it is required, $.entry[0].resource.resourceType: must be a constant value TestScript, $.entry[0].resource.resourceType: must be a constant value ValueSet, $.entry[0].resource.resourceType: must be a constant value VerificationResult, $.entry[0].resource.prescriber: is missing but it is required, $.entry[0].resource.lensSpecification: is missing but it is required, $.resourceType: must be a constant value CapabilityStatement, $.resourceType: must be a constant value CarePlan, $.resourceType: must be a constant value CareTeam, $.referencedItem: is missing but it is required, $.resourceType: must be a constant value CatalogEntry, $.resourceType: must be a constant value ChargeItem, $.resourceType: must be a constant value ChargeItemDefinition, $.insurance: is missing but it is required, $.provider: is missing but it is required, $.priority: is missing but it is required, $.resourceType: must be a constant value Claim, $.insurer: is missing but it is required, $.resourceType: must be a constant value ClaimResponse, $.resourceType: must be a constant value ClinicalImpression, $.resourceType: must be a constant value CodeSystem, $.resourceType: must be a constant value Communication, $.resourceType: must be a constant value CommunicationRequest, $.resourceType: must be a constant value CompartmentDefinition, $.author: is missing but it is required, $.resourceType: must be a constant value Composition, $.resourceType: must be a constant value ConceptMap, $.resourceType: must be a constant value Condition, $.scope: is missing but it is required, $.category: is missing but it is required, $.resourceType: must be a constant value Consent, $.resourceType: must be a constant value Contract, $.payor: is missing but it is required, $.beneficiary: is missing but it is required, $.resourceType: must be a constant value Coverage, $.resourceType: must be a constant value CoverageEligibilityRequest, $.request: is missing but it is required, $.resourceType: must be a constant value CoverageEligibilityResponse, $.resourceType: must be a constant value DetectedIssue, $.resourceType: must be a constant value Device, $.resourceType: must be a constant value DeviceDefinition, $.resourceType: must be a constant value DeviceMetric, $.resourceType: must be a constant value DeviceRequest, $.device: is missing but it is required, $.resourceType: must be a constant value DeviceUseStatement, $.resourceType: must be a constant value DiagnosticReport, $.content: is missing but it is required, $.resourceType: must be a constant value DocumentManifest, $.resourceType: must be a constant value DocumentReference, $.exposureAlternative: is missing but it is required, $.exposure: is missing but it is required, $.outcome: is missing but it is required, $.population: is missing but it is required, $.resourceType: must be a constant value EffectEvidenceSynthesis, $.class: is missing but it is required, $.type: string found, array expected, $.resourceType: must be a constant value Encounter, $.payloadType: is missing but it is required, $.connectionType: is missing but it is required, $.resourceType: must be a constant value Endpoint, $.resourceType: must be a constant value EnrollmentRequest, $.resourceType: must be a constant value EnrollmentResponse, $.resourceType: must be a constant value EpisodeOfCare, $.trigger: is missing but it is required, $.resourceType: must be a constant value EventDefinition, $.exposureBackground: is missing but it is required, $.resourceType: must be a constant value Evidence, $.characteristic: is missing but it is required, $.type: does not have a value in the enumeration [dichotomous, continuous, descriptive], $.resourceType: must be a constant value EvidenceVariable, $.resourceType: must be a constant value ExampleScenario, $.resourceType: must be a constant value ExplanationOfBenefit, $.relationship: is missing but it is required, $.resourceType: must be a constant value FamilyMemberHistory, $.resourceType: must be a constant value Flag, $.description: is missing but it is required, $.resourceType: must be a constant value Goal, $.resourceType: must be a constant value GraphDefinition, $.type: does not have a value in the enumeration [person, animal, practitioner, device, medication, substance], $.resourceType: must be a constant value Group, $.resourceType: must be a constant value GuidanceResponse, $.resourceType: must be a constant value HealthcareService, $.resourceType: must be a constant value ImagingStudy, $.vaccineCode: is missing but it is required, $.resourceType: must be a constant value Immunization, $.doseStatus: is missing but it is required, $.targetDisease: is missing but it is required, $.immunizationEvent: is missing but it is required, $.resourceType: must be a constant value ImmunizationEvaluation, $.recommendation: is missing but it is required, $.resourceType: must be a constant value ImmunizationRecommendation, $.resourceType: must be a constant value ImplementationGuide, $.resourceType: must be a constant value InsurancePlan, $.resourceType: must be a constant value Invoice, $.resourceType: must be a constant value Library, $.item: is missing but it is required, $.resourceType: must be a constant value Linkage, $.entry[0].item: is missing but it is required, $.entry[1].item: is missing but it is required, $.entry[2].item: is missing but it is required, $.entry[3].item: is missing but it is required, $.resourceType: must be a constant value List, $.resourceType: must be a constant value Location, $.resourceType: must be a constant value Measure, $.period: is missing but it is required, $.measure: is missing but it is required, $.type: does not have a value in the enumeration [individual, subject-list, summary, data-collection], $.resourceType: must be a constant value MeasureReport, $.resourceType: must be a constant value Media, $.resourceType: must be a constant value Medication, $.resourceType: must be a constant value MedicationAdministration, $.resourceType: must be a constant value MedicationDispense, $.resourceType: must be a constant value MedicationKnowledge, $.resourceType: must be a constant value MedicationRequest, $.resourceType: must be a constant value MedicationStatement, $.name: is missing but it is required, $.resourceType: must be a constant value MedicinalProduct, $.resourceType: must be a constant value MedicinalProductAuthorization, $.resourceType: must be a constant value MedicinalProductContraindication, $.resourceType: must be a constant value MedicinalProductIndication, $.role: is missing but it is required, $.resourceType: must be a constant value MedicinalProductIngredient, $.resourceType: must be a constant value MedicinalProductInteraction, $.quantity: is missing but it is required, $.manufacturedDoseForm: is missing but it is required, $.resourceType: must be a constant value MedicinalProductManufactured, $.packageItem: is missing but it is required, $.resourceType: must be a constant value MedicinalProductPackaged, $.administrableDoseForm: is missing but it is required, $.routeOfAdministration: is missing but it is required, $.resourceType: must be a constant value MedicinalProductPharmaceutical, $.resourceType: must be a constant value MedicinalProductUndesirableEffect, $.resourceType: must be a constant value MessageDefinition, $.resourceType: must be a constant value MessageHeader, $.type: does not have a value in the enumeration [aa, dna, rna], $.resourceType: must be a constant value MolecularSequence, $.uniqueId: is missing but it is required, $.resourceType: must be a constant value NamingSystem, $.resourceType: must be a constant value NutritionOrder, $.resourceType: must be a constant value Observation, $.resourceType: must be a constant value ObservationDefinition, $.type: does not match the regex pattern ^true|false$, $.type: string found, boolean expected, $.resourceType: must be a constant value OperationDefinition, $.issue: is missing but it is required, $.resourceType: must be a constant value OperationOutcome, $.resourceType: must be a constant value Organization, $.resourceType: must be a constant value OrganizationAffiliation, $.resourceType: must be a constant value Parameters, $.resourceType: must be a constant value Patient, $.amount: is missing but it is required, $.recipient: is missing but it is required, $.payment: is missing but it is required, $.resourceType: must be a constant value PaymentNotice, $.paymentAmount: is missing but it is required, $.resourceType: must be a constant value PaymentReconciliation, $.resourceType: must be a constant value Person, $.resourceType: must be a constant value PlanDefinition, $.resourceType: must be a constant value Practitioner, $.resourceType: must be a constant value PractitionerRole, $.resourceType: must be a constant value Procedure, $.target: is missing but it is required, $.resourceType: must be a constant value Provenance, $.resourceType: must be a constant value Questionnaire, $.resourceType: must be a constant value QuestionnaireResponse, $.resourceType: must be a constant value RelatedPerson, $.resourceType: must be a constant value RequestGroup, $.resourceType: must be a constant value ResearchDefinition, $.type: does not have a value in the enumeration [population, exposure, outcome], $.resourceType: must be a constant value ResearchElementDefinition, $.resourceType: must be a constant value ResearchStudy, $.study: is missing but it is required, $.individual: is missing but it is required, $.resourceType: must be a constant value ResearchSubject, $.resourceType: must be a constant value RiskAssessment, $.resourceType: must be a constant value RiskEvidenceSynthesis, $.actor: is missing but it is required, $.resourceType: must be a constant value Schedule, $.type: does not have a value in the enumeration [number, date, string, token, reference, composite, quantity, uri, special], $.resourceType: must be a constant value SearchParameter, $.resourceType: must be a constant value ServiceRequest, $.schedule: is missing but it is required, $.resourceType: must be a constant value Slot, $.resourceType: must be a constant value Specimen, $.resourceType: must be a constant value SpecimenDefinition, $.resourceType: must be a constant value StructureDefinition, $.group: is missing but it is required, $.resourceType: must be a constant value StructureMap, $.channel: is missing but it is required, $.resourceType: must be a constant value Subscription, $.resourceType: must be a constant value Substance, $.resourceType: must be a constant value SubstanceNucleicAcid, $.resourceType: must be a constant value SubstancePolymer, $.resourceType: must be a constant value SubstanceProtein, $.resourceType: must be a constant value SubstanceReferenceInformation, $.resourceType: must be a constant value SubstanceSourceMaterial, $.resourceType: must be a constant value SubstanceSpecification, $.resourceType: must be a constant value SupplyDelivery, $.resourceType: must be a constant value SupplyRequest, $.resourceType: must be a constant value Task, $.resourceType: must be a constant value TerminologyCapabilities, $.testScript: is missing but it is required, $.resourceType: must be a constant value TestReport, $.resourceType: must be a constant value TestScript, $.resourceType: must be a constant value ValueSet, $.resourceType: must be a constant value VerificationResult, $.prescriber: is missing but it is required, $.lensSpecification: is missing but it is required, $.resourceType: must be a constant value VisionPrescription]"

  def getThreeValidFhirClaimsRecords(): util.ArrayList[HriRecord] = {
    val threeRecords = new util.ArrayList[HriRecord]()
    threeRecords.add(new HriRecord(null, null, ValidFhirOneRecord.getBytes(StandardCharsets.UTF_8),
      DefaultTopic, DefaultPartition, DefaultOffset))
    threeRecords.add(new HriRecord(null, null, ValidFhirRecTwo.getBytes(StandardCharsets.UTF_8),
      DefaultTopic, DefaultPartition, DefaultOffset))
    threeRecords.add(new HriRecord(null, null, ValidFhirRecThree.getBytes(StandardCharsets.UTF_8),
      DefaultTopic, DefaultPartition, DefaultOffset))
    threeRecords
  }

  val TestClaimFhirBadResourceType =
    """
      |{
      |  "resourceType": "Bundle",
      |  "type": "collection",
      |  "entry": [
      |    {
      |      "resource": {
      |        "resourceType": "Bad Type",
      |        "identifier": [
      |          {
      |            "value": "000000000000000000016372981"
      |          }
      |        ],
      |        "status": "active",
      |        "type": {
      |          "text": "pharmacy"
      |        },
      |        "use": "claim",
      |        "patient": {
      |          "identifier": {
      |            "value": "DPFO199578"
      |          }
      |        },
      |        "created": "2020-03-18",
      |        "provider": {
      |          "identifier": {
      |            "value": "1019343"
      |          }
      |        },
      |        "priority": {
      |          "text": "normal"
      |        },
      |        "insurance": [
      |          {
      |            "sequence": 1,
      |            "focal": true,
      |            "coverage": {
      |              "identifier": {
      |                "value": "placeholder"
      |              }
      |            }
      |          }
      |        ]
      |      }
      |    },
      |    {
      |      "resource": {
      |        "resourceType": "ClaimResponse",
      |        "status": "active",
      |        "type": {
      |          "text": "pharmacy"
      |        },
      |        "use": "claim",
      |        "patient": {
      |          "identifier": {
      |            "value": "DPFO199578"
      |          }
      |        },
      |        "created": "2020-03-18",
      |        "insurer": {
      |          "identifier": {
      |            "value": "placeholder"
      |          }
      |        },
      |        "outcome": "queued",
      |        "adjudication": [
      |          {
      |            "category": {
      |              "text": "insurance"
      |            },
      |            "amount": {
      |              "value": 0
      |            }
      |          },
      |          {
      |            "category": {
      |              "text": "copay"
      |            },
      |            "amount": {
      |              "value": 0
      |            }
      |          },
      |          {
      |            "category": {
      |              "text": "deductible"
      |            },
      |            "amount": {
      |              "value": 0
      |            }
      |          },
      |          {
      |            "category": {
      |              "text": "despensingfee"
      |            },
      |            "amount": {
      |              "value": 0.4
      |            }
      |          },
      |          {
      |            "category": {
      |              "text": "ingredient cost"
      |            },
      |            "amount": {
      |              "value": 12.27
      |            }
      |          },
      |          {
      |            "category": {
      |              "text": "Net Payment"
      |            },
      |            "amount": {
      |              "value": 27.67
      |            }
      |          },
      |          {
      |            "category": {
      |              "text": "sales tax"
      |            },
      |            "amount": {
      |              "value": 0
      |            }
      |          },
      |          {
      |            "category": {
      |              "text": "Tax Amount"
      |            },
      |            "amount": {
      |              "value": 0
      |            }
      |          },
      |          {
      |            "category": {
      |              "text": "Third Party Amount"
      |            },
      |            "amount": {
      |              "value": 0
      |            }
      |          }
      |        ]
      |      }
      |    },
      |    {
      |      "resource": {
      |        "resourceType": "MedicationDispense",
      |        "status": "in-progress",
      |        "medicationCodeableConcept": {
      |          "text": "~"
      |        },
      |        "quantity": {
      |          "value": 0.5
      |        },
      |        "daysSupply": {
      |          "value": 1
      |        }
      |      }
      |    },
      |    {
      |      "resource": {
      |        "resourceType": "Medication",
      |        "ingredient": [
      |          {
      |            "itemCodeableConcept": {
      |              "text": "~"
      |            }
      |          }
      |        ]
      |      }
      |    }
      |  ]
      |}
      """.stripMargin

  val TestClaimFhirMissingPatient =
    """
      |{
      |  "resourceType": "Bundle",
      |  "type": "collection",
      |  "entry": [
      |    {
      |      "resource": {
      |        "resourceType": "Claim",
      |        "identifier": [
      |          {
      |            "value": "000000000000000000016686490"
      |          }
      |        ],
      |        "status": "active",
      |        "type": {
      |          "text": "pharmacy"
      |        },
      |        "use": "claim",
      |        "created": "2020-03-18",
      |        "provider": {
      |          "identifier": {
      |            "value": "1019947"
      |          }
      |        },
      |        "priority": {
      |          "text": "normal"
      |        },
      |        "insurance": [
      |          {
      |            "sequence": 1,
      |            "focal": true,
      |            "coverage": {
      |              "identifier": {
      |                "value": "placeholder"
      |              }
      |            }
      |          }
      |        ]
      |      }
      |    },
      |    {
      |      "resource": {
      |        "resourceType": "ClaimResponse",
      |        "status": "active",
      |        "type": {
      |          "text": "pharmacy"
      |        },
      |        "use": "claim",
      |        "patient": {
      |          "identifier": {
      |            "value": "EFNI225094"
      |          }
      |        },
      |        "created": "2020-03-18",
      |        "insurer": {
      |          "identifier": {
      |            "value": "placeholder"
      |          }
      |        },
      |        "outcome": "queued",
      |        "adjudication": [
      |          {
      |            "category": {
      |              "text": "insurance"
      |            },
      |            "amount": {
      |              "value": 21.22
      |            }
      |          },
      |          {
      |            "category": {
      |              "text": "copay"
      |            },
      |            "amount": {
      |              "value": 0
      |            }
      |          },
      |          {
      |            "category": {
      |              "text": "deductible"
      |            },
      |            "amount": {
      |              "value": 0
      |            }
      |          },
      |          {
      |            "category": {
      |              "text": "despensingfee"
      |            },
      |            "amount": {
      |              "value": 0.11
      |            }
      |          },
      |          {
      |            "category": {
      |              "text": "ingredient cost"
      |            },
      |            "amount": {
      |              "value": 212.11
      |            }
      |          },
      |          {
      |            "category": {
      |              "text": "Net Payment"
      |            },
      |            "amount": {
      |              "value": 191
      |            }
      |          },
      |          {
      |            "category": {
      |              "text": "sales tax"
      |            },
      |            "amount": {
      |              "value": 0
      |            }
      |          },
      |          {
      |            "category": {
      |              "text": "Tax Amount"
      |            },
      |            "amount": {
      |              "value": 0
      |            }
      |          },
      |          {
      |            "category": {
      |              "text": "Third Party Amount"
      |            },
      |            "amount": {
      |              "value": 0
      |            }
      |          }
      |        ]
      |      }
      |    },
      |    {
      |      "resource": {
      |        "resourceType": "MedicationDispense",
      |        "status": "in-progress",
      |        "medicationCodeableConcept": {
      |          "text": "~"
      |        },
      |        "quantity": {
      |          "value": 90
      |        },
      |        "daysSupply": {
      |          "value": 90
      |        }
      |      }
      |    },
      |    {
      |      "resource": {
      |        "resourceType": "Medication",
      |        "ingredient": [
      |          {
      |            "itemCodeableConcept": {
      |              "text": "~"
      |            }
      |          }
      |        ]
      |      }
      |    }
      |  ]
      |}
      |
      |""".stripMargin

}
