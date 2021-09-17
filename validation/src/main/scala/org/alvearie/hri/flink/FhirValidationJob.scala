/*
 * (C) Copyright IBM Corp. 2021
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.alvearie.hri.flink

import java.io.{PrintStream, PrintWriter}
import java.util.concurrent.Callable

import org.alvearie.hri.api.{BatchLookup, InvalidRecord}
import org.alvearie.hri.flink.core.BaseValidationJob
import org.alvearie.hri.flink.core.serialization.{HriRecord, NotificationRecord}
import org.alvearie.hri.flink.fhir.FhirValidator
import org.apache.flink.streaming.api.functions.sink.RichSinkFunction
import org.apache.flink.streaming.api.functions.source.RichParallelSourceFunction
import org.slf4j.LoggerFactory
import picocli.CommandLine
import picocli.CommandLine.ArgGroup

@CommandLine.Command(
  name = "ValidateFhir",
  synopsisSubcommandLabel = "COMMAND",
  mixinStandardHelpOptions = true,
  version = Array("1.0"),
  description = Array("HRI Flink FHIR schema validation job")
)class FhirValidationJob extends Callable[Int] {
  private val log = LoggerFactory.getLogger(this.getClass)
  private var isTestJob = false
  private var notificationSrc: RichParallelSourceFunction[NotificationRecord] = null
  private var dataRecsSrc: RichParallelSourceFunction[HriRecord] = null
  private var validRecsSink: RichSinkFunction[HriRecord] = null
  private var invalidRecsSink: RichSinkFunction[InvalidRecord] = null
  private var recCountSink: RichSinkFunction[NotificationRecord] = null
  private var batchLookup: BatchLookup = null

  @CommandLine.Option(names = Array("-b", "--brokers"), split = ",", description = Array("Comma-separated list of Event Streams (Kafka) brokers"), required = true)
  private var brokers: Array[String] = _

  @CommandLine.Option(names = Array("-p", "--password"), description = Array("IBM Cloud Event Streams password"), required = true)
  private var password: String = _

  @CommandLine.Option(names = Array("-i", "--input"), description = Array("IBM Cloud Event Streams (Kafka) input topic"), required = true)
  private var inputTopic: String = _

  @CommandLine.Option(names = Array("-d", "--batch-completion-delay"), defaultValue = "300000", description = Array("Amount of time to wait in milliseconds for extra records before completing a batch. Default ${DEFAULT-VALUE}"))
  private var batchCompletionDelay: Long = _

  // This says the parameters defined in the class are exclusive. Only one of them must be defined.
  @ArgGroup(exclusive = true, multiplicity = "1")
  private var standaloneOrMgmtParams: StandaloneOrMgmtParams = _

  //Alternate Constructor to be used for End-to-End Job tests: see FhirValidationJobIntTest
  def this(testNotificationSrc: RichParallelSourceFunction[NotificationRecord],
           testDataRecsSrc: RichParallelSourceFunction[HriRecord],
           testValidRecsSink: RichSinkFunction[HriRecord],
           testInvalidRecsSink: RichSinkFunction[InvalidRecord],
           testRecCountSink: RichSinkFunction[NotificationRecord],
           testBatchLookup: BatchLookup,
           batchCompletionDelay: Long) {
    this()
    this.isTestJob = true
    this.notificationSrc = testNotificationSrc
    this.dataRecsSrc = testDataRecsSrc
    this.validRecsSink = testValidRecsSink
    this.invalidRecsSink = testInvalidRecsSink
    this.recCountSink = testRecCountSink
    this.batchLookup = testBatchLookup
    this.batchCompletionDelay = batchCompletionDelay
  }

  override def call(): Int = {
    var baseValidationJob: BaseValidationJob = null
    var jobNamePrefix = "Fhir Schema Validation"

    if (isTestJob) {
      jobNamePrefix = "End-to-End (Flink Validator) Validation Test"
      baseValidationJob = new BaseValidationJob(new FhirValidator(), notificationSrc, dataRecsSrc,
        validRecsSink, invalidRecsSink, recCountSink, batchLookup, batchCompletionDelay)
    } else if (standaloneOrMgmtParams.standalone) {
      baseValidationJob = new BaseValidationJob(inputTopic, brokers, password, new FhirValidator(), batchCompletionDelay)
    } else {
      baseValidationJob = new BaseValidationJob(inputTopic, brokers, password, new FhirValidator(),
        standaloneOrMgmtParams.mgmtParams.mgmtUrl, standaloneOrMgmtParams.mgmtParams.mgmtClientId,
        standaloneOrMgmtParams.mgmtParams.mgmtClientSecret, standaloneOrMgmtParams.mgmtParams.mgmtAudience,
        standaloneOrMgmtParams.mgmtParams.oauthServiceBaseUrl, batchCompletionDelay)
    }

    baseValidationJob.startJob(jobNamePrefix)

    return 0
  }
}

// The picocli docs/examples use static inner classes for Argument Groups, but I couldn't get that to work in Scala.
// So I just used additional outer classes. Case classes won't work either, because the members must be mutable.

class StandaloneOrMgmtParams {
  @CommandLine.Option(names = Array("--standalone"), description = Array("Deploys in standalone mode, where the HRI Management API is NOT used to complete or fail batches"), required = true)
  var standalone: Boolean = _

  // This says the parameters defined in the class are inclusive. All of them must be defined.
  @ArgGroup(exclusive = false)
  var mgmtParams: MgmtParams = _
}

class MgmtParams {
  @CommandLine.Option(names = Array("-m", "--mgmt-url"), description = Array("Base Url for the HRI Management API, e.g. https://68d40cd8.us-south.apigw.appdomain.cloud/hri"), required = true)
  var mgmtUrl: String = _

  @CommandLine.Option(names = Array("-c", "--client-id"), description = Array("Client ID for getting OAuth access tokens"), required = true)
  var mgmtClientId: String = _

  @CommandLine.Option(names = Array("-s", "--client-secret"), description = Array("Client secret for getting OAuth access tokens"), required = true)
  var mgmtClientSecret: String = _

  @CommandLine.Option(names = Array("-a", "--audience"), description = Array("Audience for getting OAuth access tokens"), required = true)
  var mgmtAudience: String = _

  @CommandLine.Option(names = Array("-o", "--oauth-url"), description = Array("Base Url for the OAuth service"), required = true)
  var oauthServiceBaseUrl: String = _
}

object FhirValidationJob extends App {
  call(args)

  // wrapper for testing
  def call(args:Array[String], errOut:PrintStream = System.err): Int = {
    return new CommandLine(new FhirValidationJob()).setErr(new PrintWriter(errOut, true)).execute(args:_*)
  }
}