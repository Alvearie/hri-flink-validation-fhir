/*
 * (C) Copyright IBM Corp. 2021
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.alvearie.hri.flink.fhir

import java.io.{File, FilenameFilter}
import java.nio.charset.StandardCharsets
import java.util

import org.alvearie.hri.flink.core.serialization.HriRecord
import org.alvearie.hri.flink.fhir.TestJson._
import org.apache.commons.io.filefilter.SuffixFileFilter
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers._

import scala.io.Source

class FhirJsonValidationTest extends AnyFunSuite {

  val fhirValidator = new FhirValidator()


  test("isValid should return True for valid FHIR JSON bundle (type=claim) record") {
    val hriRecord = new HriRecord(null, null, ValidFhirOneRecord.getBytes(StandardCharsets.UTF_8),
      DefaultTopic, DefaultPartition, DefaultOffset)
    val validResult = fhirValidator.isValid(hriRecord)
    validResult._1.self shouldBe (true)
    validResult._2.isEmpty shouldBe (true)
    info("** End One Valid FHIR Bundle(Claim) Record test **")
  }

  test("isValid should return Zero errors for Three valid FHIR bundle (type=claim) records") {
    val threeFhirRecs: util.ArrayList[HriRecord] = TestJson.getThreeValidFhirClaimsRecords()

    val record1 = threeFhirRecs.get(0)
    val validRes1 = fhirValidator.isValid(record1)
    validRes1._1.self shouldBe (true)
    validRes1._2.isEmpty shouldBe (true)

    val record2 = threeFhirRecs.get(1)
    val validRes2 = fhirValidator.isValid(record2)
    validRes2._1.self shouldBe (true)
    validRes2._2.isEmpty shouldBe (true)

    val record3 = threeFhirRecs.get(2)
    val validRes3 = fhirValidator.isValid(record3)
    validRes3._1.self shouldBe (true)
    validRes3._2.isEmpty shouldBe (true)
  }

  test("isValid should return False For Json that is Non-FHIR JSON") {
    val nonFhirRecord = getFakeNonFhirRecord()
    val valResult = fhirValidator.isValid(nonFhirRecord)
    valResult._1.self shouldBe (false)
    valResult._2 should be("Missing required element: 'resourceType'")
  }

  test("should return the correct Error/Validation Msgs for FHIR with Bad Resource Type") {
    val hriRecord = new HriRecord(null, null, TestClaimFhirBadResourceType.getBytes(StandardCharsets.UTF_8),
      DefaultTopic, DefaultPartition, DefaultOffset)
    val validResult = fhirValidator.isValid(hriRecord)
    validResult._2 should be("Invalid resource type: 'Bad Type' [Bundle.entry[0]]")
    info("** End Invalid FHIR Bundle(Claim) Record - Bad Resource Type**")
  }

  test("it should return True for for valid FHIR JSON bundle with UTF8 Characters") {
    val testJsonBody = ValidFhirRecUtf8.getBytes(StandardCharsets.UTF_8)
    val hriRecord = new HriRecord(null, null, testJsonBody, DefaultTopic, DefaultPartition, DefaultOffset)
    val (isValid, actualErrMsg) = fhirValidator.isValid(hriRecord)
    isValid.self shouldBe (true)
    actualErrMsg.isEmpty shouldBe (true)
  }

  test("should return False for FHIR JSON bundle (type=claim) with Missing Patient") {
    val hriRecord = new HriRecord(null, null, TestClaimFhirMissingPatient.getBytes(StandardCharsets.UTF_8),
      DefaultTopic, DefaultPartition, DefaultOffset)
    val validResult = fhirValidator.isValid(hriRecord)
    validResult._1.self shouldBe (false)
  }

  test("return Failure with message for null JsonNode input") {
    val validResult = fhirValidator.isValid(null)
    validResult._1.self shouldBe(false)
    validResult._2 should include ("java.lang.NullPointerException")
    validResult._2 should include ("at org.alvearie.hri.flink.fhir.FhirValidator.isValid")
    validResult._2 should include ("at org.alvearie.hri.flink.fhir.FhirJsonValidationTest")
  }

  private def getFakeNonFhirRecord(): HriRecord = {
    val nonFhirJson =
      """
        |{
        |  "monkey": "Joe",
        |  "age": 44,
        |  "city": "Porcupine",
        |  "catz": "KittenZ"
        |}
      """.stripMargin
    new HriRecord(null, null, nonFhirJson.getBytes(StandardCharsets.UTF_8), DefaultTopic, DefaultPartition, DefaultOffset)
  }

  private def validateFiles(testCases:Array[(String, Boolean, String)]): Unit = {
    testCases.foreach( tuple => {
      val source = Source.fromFile(tuple._1)
      val testJsonBody = try source.mkString.getBytes(StandardCharsets.UTF_8) finally source.close

      val hriRecord = new HriRecord(null, null, testJsonBody, DefaultTopic, DefaultPartition, DefaultOffset)

      val (isValid, actualErrMsg) = fhirValidator.isValid(hriRecord)
      System.out.println(actualErrMsg)
      isValid.self shouldBe (tuple._2)
      if (tuple._3.isEmpty){
        actualErrMsg.isEmpty shouldBe (true)
      } else {
        actualErrMsg should include(tuple._3)
      }
    })
  }

  test("should return correct response for large records"){
    validateFiles(Array(
      ("../test/test_data/Beverley336_Wiegand701_2eec998f-ed7d-becc-bec6-2ab92ece4415.json", true, ""),
      ("../test/test_data/Theodore876_Flatley871_cd436865-56fd-4d2d-f25d-2ca8d9634792.json", true, ""),
      ("../test/test_data/Lanny564_VonRueden376_453b1e99-2338-24ea-e8cb-6f9897ee3391.json", false, "Element 'language': does not contain a Coding element with a valid system and code combination for value set: 'http://hl7.org/fhir/ValueSet/all-languages' [Bundle.entry[0].resource]"),
      ("../test/test_data/Richie600_McDermott739_923ae2ac-65a6-865c-ce15-e07dc1498aa1.json", false, "dog [Bundle.entry[0].resource.gender]"),
    ))
  }

  // This unit test if for performance testing. Point it at a directory and it will test all the json files
  // and report how long it took and the size of the data. See the nightly load tests for generating files via synthea.
  ignore("test the performance of FHIRValidator"){
    val directory = new File("../test/test_data/synthea/fhir")
    directory.listFiles(new SuffixFileFilter(".json"):FilenameFilter).foreach( file => {
      val source = Source.fromFile(file.getAbsolutePath)
      val testJsonBody = try source.mkString.getBytes(StandardCharsets.UTF_8) finally source.close

      val hriRecord = new HriRecord(null, null, testJsonBody, DefaultTopic, DefaultPartition, DefaultOffset)

      val start = System.currentTimeMillis()
      val (isValid, actualErrMsg) = fhirValidator.isValid(hriRecord)
      val processingTime = System.currentTimeMillis() - start
      System.out.println(s"${file.getName}, ${processingTime} milliseconds,  ${testJsonBody.size} bytes")

      if (!isValid.self) {
        System.out.println(s"Validation error: ${actualErrMsg}")
      }
    })

  }

}
