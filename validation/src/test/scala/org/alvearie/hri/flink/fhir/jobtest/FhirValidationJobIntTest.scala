/*
 * (C) Copyright IBM Corp. 2021
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.alvearie.hri.flink.fhir.jobtest

import java.nio.charset.StandardCharsets
import java.util

import org.alvearie.hri.api.{BatchNotification, InvalidRecord, MapBatchLookup}
import org.alvearie.hri.flink.FhirValidationJob
import org.alvearie.hri.flink.core.jobtest.sources.{HriTestRecsSourceFunction, NotificationSourceFunction, TestRecordHeaders}
import org.alvearie.hri.flink.core.serialization.{HriRecord, NotificationRecord}
import org.apache.flink.test.util.MiniClusterWithClientResource
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.apache.flink.test.streaming.runtime.util.TestListResultSink
import org.apache.kafka.common.header.Headers
import org.scalatest.BeforeAndAfter
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._

import scala.collection.JavaConverters
import scala.collection.JavaConverters._

/*
 * A series of End-to-End "Integration" Tests of the FhirValidation Pipeline using ScalaTest of the full Validation Job functionality.
 * Uses the Flink Framework Unit Test support of the MiniClusterWithClientResource ->
 *   @See https://ci.apache.org/projects/flink/flink-docs-stable/dev/stream/testing.html#junit-rule-miniclusterwithclientresource)
 *
 * NOTE: We are explicitly setting the number of Task Slots to 2 and setting Parallelism to 2 for each Test case to
 * try to identify error scenarios that may occur in a production deployment that will certainly have both of these env vars set to > 1
 *   See https://ci.apache.org/projects/flink/flink-docs-stable/concepts/runtime.html#task-slots-and-resources
 */
class FhirValidationJobIntTest extends AnyFlatSpec with BeforeAndAfter{

  private var SlotsPerTaskMgr = 2
  val flinkCluster = new MiniClusterWithClientResource(new MiniClusterResourceConfiguration.Builder()
    .setNumberSlotsPerTaskManager(SlotsPerTaskMgr)
    .setNumberTaskManagers(1)
    .build)

  private val DefaultTestNotificationKey = "testNotificationKey350"
  private val TestJobParallelism = 2
  private val HriRecordKeyRoot = "hriRec"
  private val DefaultHriRecordKeySuffix = "Aa"
  private val DefaultHriRecordKey = HriRecordKeyRoot + DefaultHriRecordKeySuffix

  before {
    flinkCluster.before()
  }

  after {
    flinkCluster.after()
  }

  //**Happy Path Pipeline Test:
  //1. send in notification with status = started
  //2. pause for x (150) millis
  //3. send in 1 valid record (HRIRecrod)
  //4. pause for y (350) millis From NotificationSourceFunction.run() Start (y should be AT LEAST x*2)
  //5. send in notification sent with status = sendCompleted
  //6. NOTE: expected Output Recs count = # input Res * SlotsPerTaskMgr)
  "FhirValidation pipeline" should "send One Valid HriRecord to the valid records output stream with 2 valid records output (With 2 Task Slots) and receive one send Completed Notification" in {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(TestJobParallelism)

    //Creates Test Sinks to send into Job
    val validRecsSink = new TestListResultSink[HriRecord]
    val invalidRecsSink = new TestListResultSink[InvalidRecord]
    val recCountSink = new TestListResultSink[NotificationRecord]

    val totalNumOfRecordsToSend = 1
    val expectedRecordCount = totalNumOfRecordsToSend * SlotsPerTaskMgr
    val testHeaders = FlinkJobTestHelper.getDefaultTestHeaders()
    val batchId = FlinkJobTestHelper.DefaultTestBatchId

    val testNotifications = getTestNotifications(testHeaders,
      expectedRecordCount, batchId)
    val testHriRecords = getOneValidFhirHriRecord(testHeaders)
    val notificationSrc = new NotificationSourceFunction(testNotifications, 350)
    // had to increase the ending delay to get this test to pass.
    val testRecsSrc = new HriTestRecsSourceFunction(testHriRecords, 150, 2000)
    val batchLookup = new MapBatchLookup(testNotifications.get.map(_.value))

    val validationJob = new FhirValidationJob(notificationSrc,
      testRecsSrc, validRecsSink, invalidRecsSink, recCountSink, batchLookup, 100)

    validationJob.call()

    //verify Output Message in ValidRecs - 2 valid HRI Recs
    val validRecsList = validRecsSink.getResult()
    verifyDefaultValidRecOutput(testHeaders, validRecsList, expectedRecordCount)

    //verify recCount updated
    val countResultList = recCountSink.getResult()
    countResultList should have size 1
    val recCountNotification = countResultList.get(0)
    recCountNotification.value.getExpectedRecordCount() should equal (expectedRecordCount)
    recCountNotification.value.getStatus should equal(BatchNotification.Status.COMPLETED)
    recCountNotification.value.getId should equal (batchId)

    val invalidRecsList = invalidRecsSink.getResult()
    invalidRecsList should have size 0
  }

  //**Happy Path Pipeline Test case #2: sends in:
  //1. Start notification,
  //2. Sends in 1 valid record,
  //3. Send in 2 Invalid Hri Records
  //  NO other notifications!  (No sendComplete or Terminated notifications)
  // Expect Output of 2 Valid HRI Recs and 4 Invalid HRI Recs (Output = # input Res * SlotsPerTaskMgr)
  "(With 2 Task Slots)  FhirValidation pipeline" should "processes 1 valid & 2 invalid HriRecord, " +
    "AND the corresponding output sinks receive 2 and 4 records, respectively" in {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(TestJobParallelism)

    //Creates Test Sinks to send into Job
    val validRecsSink = new TestListResultSink[HriRecord]
    val invalidRecsSink = new TestListResultSink[InvalidRecord]
    val recCountSink = new TestListResultSink[NotificationRecord]

    val validRecsToSend = 1
    val invalidRecsToSend = 2
    val totalNumOfRecordsToSend = validRecsToSend + invalidRecsToSend
    val expectedValidRecCount = validRecsToSend * SlotsPerTaskMgr
    val testHeaders = FlinkJobTestHelper.getDefaultTestHeaders()
    val batchId = FlinkJobTestHelper.DefaultTestBatchId

    val testNotifications = getOnlyStartedNotification(testHeaders,
      totalNumOfRecordsToSend, batchId)
    val notificationSrc = new NotificationSourceFunction(testNotifications)
    val hriRecKey2 = HriRecordKeyRoot + "02"
    val hriRecKey3 = HriRecordKeyRoot + "Cd"
    val testHriRecs = getOneValidTwoInvalidFhirHriRecord(testHeaders, hriRecKey2, hriRecKey3)
    val testHriRecsSrc = new HriTestRecsSourceFunction(testHriRecs, 150)
    val batchLookup = new MapBatchLookup(testNotifications.get.map(_.value))

    val validationJob = new FhirValidationJob(notificationSrc,
      testHriRecsSrc, validRecsSink, invalidRecsSink, recCountSink, batchLookup,100)

    validationJob.call()

    //verify Output Message in ValidRecs - 2 valid HRI Recs
    val validRecsList = validRecsSink.getResult()
    verifyDefaultValidRecOutput(testHeaders, validRecsList, expectedValidRecCount)

    //verify Invalid Output Rec content (4 Invalid HRI Recs expected)
    val invalidRecsList = invalidRecsSink.getResult
    val expectedInvalidOutputCount = invalidRecsToSend * SlotsPerTaskMgr
    invalidRecsList should have size expectedInvalidOutputCount

    for (index <- 0 to 3) {
      val invalidRecord = invalidRecsList.get(index)
      val sentInvalidRecord = testHriRecs.get((index/2).toInt + 1)
      invalidRecord.getBatchId should be (extractBatchId(sentInvalidRecord.headers))
      invalidRecord.getTopic should equal(sentInvalidRecord.topic)
    }
    //record Count Notification output Sink should be empty - no messages
    val countResultList = recCountSink.getResult
    countResultList should have size 0
  }

  //Test Case #3: Expect Failed Batch Notification Message
  //1. send in notification with status = started
  //2. send in 2 valid record (HRI Record) And 1 Invalid HRI Rec
  //3. pause sending additional Notification Msg for y (100) millis From NotificationSourceFunction.run() Start
  //4. send in notification (with status = sendCompleted) with expectedRecordCount = 2 (< recCount received = 8)
  //5. pause sending in 4th HRI Record for 500ms
  //5. send in Additional (Invalid) HRI Record
  //6. Verify received 1 Expected Notification output msg with Status = Failed
  "FhirValidation pipeline" should "handle 2 valid & 2 invalid HriRec & send a " +
      " a Notification message with Failed Status WHEN expectedRecCount LESS THAN HRI Recs output count" in {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(TestJobParallelism)

    //Creates Test Sinks to send into Job
    val validRecsSink = new TestListResultSink[HriRecord]
    val invalidRecsSink = new TestListResultSink[InvalidRecord]
    val recCountSink = new TestListResultSink[NotificationRecord]

    val validRecsToSend = 2
    val invalidRecsToSend = 2
    val expectedValidRecCount = validRecsToSend * SlotsPerTaskMgr //4
    val expectedInvalidRecCount = invalidRecsToSend * SlotsPerTaskMgr //4
    val incorrectExpectedRecCount = 4
    val testHeaders = FlinkJobTestHelper.getDefaultTestHeaders()
    val lastHriRecDelay = 500L
    val testHeadersWithDelay = FlinkJobTestHelper.getHeadersWithDelayHeader(lastHriRecDelay)
    val batchId = FlinkJobTestHelper.DefaultTestBatchId

    val testNotifications = getTestNotifications(testHeaders,
      incorrectExpectedRecCount, batchId)
    val notificationSrc = new NotificationSourceFunction(testNotifications, 100)
    val hriRecKey2 = HriRecordKeyRoot + "02"
    val hriRecKey3 = HriRecordKeyRoot + "03"
    val hriRecKey4 = HriRecordKeyRoot + "04"
    val testHriRecords = getTwoValidAndTwoInvalidFhirHriRecs(testHeaders, testHeadersWithDelay,
      hriRecKey2, hriRecKey3, hriRecKey4)
    val testRecsSrc = new HriTestRecsSourceFunction(testHriRecords)
    val batchLookup = new MapBatchLookup(testNotifications.get.map(_.value))

    val validationJob = new FhirValidationJob(notificationSrc,
      testRecsSrc, validRecsSink, invalidRecsSink, recCountSink, batchLookup, 100)

    validationJob.call()

    //verify at least 1 output Notification Message with Status -> Failed
    val countResultList = recCountSink.getResult()
    countResultList.size() should be >= 1
    val notificationsResultsBuf = JavaConverters.asScalaBuffer(countResultList)
    val failedNotficationsRes = notificationsResultsBuf.filter(
      x => x.value.getStatus == BatchNotification.Status.FAILED)
    failedNotficationsRes.length should be > 0
    println("FailedNotifications Len: " + failedNotficationsRes.length)
    failedNotficationsRes.last.value.getId should equal(batchId)
    failedNotficationsRes.last.value.getStatus should equal(BatchNotification.Status.FAILED)

    //Verify that the valid HRI record output is correct
    val validRecsList = validRecsSink.getResult()
    validRecsList should have size expectedValidRecCount
    val validRecsHeadersArr = validRecsList.get(0).headers.toArray
    validRecsHeadersArr should have size (testHeaders.toArray.size)
    val outputKey1 = (validRecsList.get(0).key.map(_.toChar)).mkString
    outputKey1 should equal(DefaultHriRecordKey)
    val outputKey2 = (validRecsList.get(1).key.map(_.toChar)).mkString
    outputKey2 should startWith(HriRecordKeyRoot)
    outputKey2 should endWith regex DefaultHriRecordKeySuffix + "|02"
    val outputVal1 = new String(validRecsList.get(0).value, StandardCharsets.UTF_8)
    outputVal1 should equal(FlinkJobTestHelper.ValidFhirOneRecord)

    //Verify Invalid Hri Rec output is Correct
    val invalidRecsList = invalidRecsSink.getResult
    invalidRecsList should have size expectedInvalidRecCount
    for (index <- 0 to 3) {
      val invalidRecord = invalidRecsList.get(index)
      val sentInvalidRecord = testHriRecords.get((index/2).toInt + 2)
      invalidRecord.getBatchId should be (extractBatchId(sentInvalidRecord.headers))
      invalidRecord.getTopic should equal(sentInvalidRecord.topic)
    }
  }

  //Test Case #4: handle a SendCompleted Notification with NoOp When No HriRecords are processed"
  //1. send in notification with status = started
  //2. pause for x (200) millis  (Do NOT Send in any HRI Records)
  //3. send in notification rec with Status = sendComplete
  //4. Verify there are 0 valid HRI Recs output, 0 Invalid HRIRecs Recs Output AND 0 Notification Recs output
  "FhirValidation pipeline" should "seamlessly handle a SendCompleted Notification " +
    "WHEN NO HRI Records have been received/processed" in {

    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(TestJobParallelism)

    //Creates Test Sinks to send into Job
    val validRecsSink = new TestListResultSink[HriRecord]
    val invalidRecsSink = new TestListResultSink[InvalidRecord]
    val recCountSink = new TestListResultSink[NotificationRecord]

    val expectedRecordCount = 1   //Not Really - We aren't sending in any HRI Recs
    val testHeaders = FlinkJobTestHelper.getDefaultTestHeaders()
    val batchId = FlinkJobTestHelper.DefaultTestBatchId

    val testNotifications = getTestNotifications(testHeaders,
      expectedRecordCount, batchId)
    val notificationSrc = new NotificationSourceFunction(testNotifications, 200)
    val testHriRecords = None   // No HRI Recs
    val testRecsSrc = new HriTestRecsSourceFunction(testHriRecords)
    val batchLookup = new MapBatchLookup(testNotifications.get.map(_.value))

    val validationJob = new FhirValidationJob(notificationSrc,
      testRecsSrc, validRecsSink, invalidRecsSink, recCountSink, batchLookup, 100)

    validationJob.call()

    //confirm that we have 0 notification msg Recs output
    val countResultList = recCountSink.getResult()
    countResultList should have size 0

    //confirm that we have 0 Valid Recs
    val validRecsList = validRecsSink.getResult()
    validRecsList should have size 0

    //confirm that we have 0 Invalid Recs
    val invalidRecsList = invalidRecsSink.getResult()
    invalidRecsList should have size 0
  }

  def extractBatchId(headers: Headers): String = {
    if (headers != null) return headers.asScala.find(_.key() == "batchId")
      .map(h => new String(h.value(), StandardCharsets.UTF_8))
      .getOrElse(null)
    null
  }

  private def getTestNotifications(testHeaders: TestRecordHeaders,
                                   expectedRecordCount: Int,
                                   testBatchId: String): Option[Seq[NotificationRecord]] = {

    val testNotificationKey = DefaultTestNotificationKey
    val startedNotification = FlinkJobTestHelper.createTestNotification(testHeaders, testNotificationKey.getBytes,
      testBatchId, expectedRecordCount, BatchNotification.Status.STARTED)
    val completedNotification = FlinkJobTestHelper.createTestNotification(testHeaders, testNotificationKey.getBytes,
      testBatchId, expectedRecordCount, BatchNotification.Status.SEND_COMPLETED)
    Some(Seq(startedNotification, completedNotification))
  }

  private def getOnlyStartedNotification(testHeaders: TestRecordHeaders,
                                          expectedRecordCount: Int,
                                          testBatchId: String): Option[Seq[NotificationRecord]] = {
    val testNotificationKey = DefaultTestNotificationKey
    val startedNotification = FlinkJobTestHelper.createTestNotification(testHeaders, testNotificationKey.getBytes,
      testBatchId, expectedRecordCount, BatchNotification.Status.STARTED)
    Some(Seq(startedNotification))
  }

  private def getOneValidFhirHriRecord(testHeaders: TestRecordHeaders): Option[Seq[HriRecord]] = {
    val oneHriRec = FlinkJobTestHelper.createOneValidHriRecord(testHeaders,
      DefaultHriRecordKey.getBytes(StandardCharsets.UTF_8))
    Some(Seq(oneHriRec))
  }

  private def getOneValidTwoInvalidFhirHriRecord(testHeaders: TestRecordHeaders,
                                                 hriRecKey2: String,
                                                 hriRecKey3: String ): Option[Seq[HriRecord]] = {
    val validHriRec = FlinkJobTestHelper.createOneValidHriRecord(testHeaders,
      DefaultHriRecordKey.getBytes(StandardCharsets.UTF_8))
    val invalidHriRec1 = FlinkJobTestHelper.createOneInvalidHriRecord(testHeaders,
      hriRecKey2.getBytes(StandardCharsets.UTF_8))
    val invalidHriRec2 = FlinkJobTestHelper.createOneInvalidHriRecord(testHeaders,
      hriRecKey3.getBytes(StandardCharsets.UTF_8))
    Some(Seq(validHriRec, invalidHriRec1, invalidHriRec2))
  }

  private def getTwoValidAndTwoInvalidFhirHriRecs(testHeaders: TestRecordHeaders,
                                                  testHeadersWithDelay: TestRecordHeaders,
                                                  hriRecKey2: String,
                                                  hriRecKey3: String, hriRecKey4: String): Option[Seq[HriRecord]] = {
    val validHriRec = FlinkJobTestHelper.createOneValidHriRecord(testHeaders,
      DefaultHriRecordKey.getBytes(StandardCharsets.UTF_8))
    val validHriRec2 = FlinkJobTestHelper.createOneValidHriRecord(testHeaders,
      hriRecKey2.getBytes(StandardCharsets.UTF_8))
    val invalidHriRec1 = FlinkJobTestHelper.createOneInvalidHriRecord(testHeaders,
      hriRecKey3.getBytes(StandardCharsets.UTF_8))
    val invalidHriRec2 = FlinkJobTestHelper.createOneInvalidHriRecord(testHeadersWithDelay,
      hriRecKey4.getBytes(StandardCharsets.UTF_8))

    Some(Seq(validHriRec, validHriRec2, invalidHriRec1, invalidHriRec2))
  }

  private def verifyDefaultValidRecOutput(testHeaders: TestRecordHeaders,
                                          validRecsList: util.List[HriRecord],
                                          totalRecsExpectedCount: Int) = {
    validRecsList should have size (totalRecsExpectedCount)  //Record throughput is multiplied by slotsPerTaskManager
    val outputKey = (validRecsList.get(0).key.map(_.toChar)).mkString
    outputKey should equal(DefaultHriRecordKey)
    val headersArr = validRecsList.get(0).headers.toArray
    headersArr should have size (testHeaders.toArray.size)
  }

}
