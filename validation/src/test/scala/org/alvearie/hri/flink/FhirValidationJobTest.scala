/*
 * (C) Copyright IBM Corp. 2021
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.alvearie.hri.flink

import java.io.{ByteArrayOutputStream, PrintStream}

import org.scalatest.BeforeAndAfter
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers._
import picocli.CommandLine

class FhirValidationJobTest extends AnyFunSuite with BeforeAndAfter {

  private val TestPassword = "FakePassword"
  private val TestPasswordArg = "--password=" + TestPassword
  private val TestBrokerArg = "--brokers=localhost:9092"
  private val TestInputTopicVal = "ingest.22.da.in"
  private val TestInputTopicArg = "--input=" + TestInputTopicVal
  private val TestStandaloneArg = "--standalone"
  private val TestMgmtUrl = "https://mydomain.com/hri"
  private val TestMgmtUrlArg = "--mgmt-url=" + TestMgmtUrl
  private val TestClientId = "myClientId"
  private val TestClientIdArg = "--client-id=" + TestClientId
  private val TestClientSecret = "myClientSecret"
  private val TestClientSecretArg = "--client-secret=" + TestClientSecret
  private val TestAudience = "myAudience"
  private val TestAudienceArg = "--audience=" + TestAudience
  private val TestOAuthBaseUrl = "https://oauthdomain.com/hri"
  private val TestOAuthBaseUrlArg = "--oauth-url=" + TestOAuthBaseUrl

  private val errOut = new ByteArrayOutputStream()
  private val errPs = new PrintStream(errOut)

  before {
    errOut.reset()
  }

  test("it should report error on invalid parameters") {
    val badArg = "3737464=arg"
    val exitCode = FhirValidationJob.call(Array[String](TestBrokerArg, TestInputTopicArg, TestPasswordArg, TestStandaloneArg, badArg), errPs)
    exitCode should equal(CommandLine.ExitCode.USAGE)

    val err = errOut.toString
    err should include ("Unmatched argument at index")
    err should include (badArg)
  }


  test("it should throw exception for missing required command-line arg -> brokers") {
    val exitCode = FhirValidationJob.call(Array[String](TestInputTopicArg, TestPasswordArg, TestStandaloneArg), errPs)
    exitCode should equal(CommandLine.ExitCode.USAGE)

    val err = errOut.toString
    err should include ("Missing required option")
    err should include ("--brokers=<brokers>")
  }

  test("it should throw exception for missing required command-line arg -> topic") {
    val exitCode = FhirValidationJob.call(Array[String](TestBrokerArg, TestPasswordArg, TestStandaloneArg), errPs)
    exitCode should equal(CommandLine.ExitCode.USAGE)

    val err = errOut.toString
    err should include ("Missing required option")
    err should include ("--input=<inputTopic>")
  }

  test("it should throw exception for missing required command-line arg -> password") {
    val exitCode = FhirValidationJob.call(Array[String](TestBrokerArg, TestInputTopicArg, TestStandaloneArg), errPs)
    exitCode should equal(CommandLine.ExitCode.USAGE)

    val err = errOut.toString
    err should include ("Missing required option")
    err should include ("--password=<password>")
  }

  test("it should throw exception for missing required command-line arg -> mgmt-url") {
    val exitCode = FhirValidationJob.call(Array[String](TestBrokerArg, TestInputTopicArg, TestPasswordArg, TestClientIdArg, TestClientSecretArg, TestAudienceArg, TestOAuthBaseUrlArg), errPs)
    exitCode should equal(CommandLine.ExitCode.USAGE)

    val err = errOut.toString
    err should include ("Missing required argument")
    err should include ("--mgmt-url=<mgmtUrl>")
  }

  test("it should throw exception for missing required command-line arg -> client-id") {
    val exitCode = FhirValidationJob.call(Array[String](TestBrokerArg, TestInputTopicArg, TestPasswordArg, TestMgmtUrlArg, TestClientSecretArg, TestAudienceArg, TestOAuthBaseUrlArg), errPs)
    exitCode should equal(CommandLine.ExitCode.USAGE)

    val err = errOut.toString
    err should include ("Missing required argument")
    err should include ("--client-id=<mgmtClientId>")
  }

  test("it should throw exception for missing required command-line arg -> client-secret") {
    val exitCode = FhirValidationJob.call(Array[String](TestBrokerArg, TestInputTopicArg, TestPasswordArg, TestMgmtUrlArg, TestClientIdArg, TestAudienceArg, TestOAuthBaseUrlArg), errPs)
    exitCode should equal(CommandLine.ExitCode.USAGE)

    val err = errOut.toString
    err should include ("Missing required argument")
    err should include ("--client-secret=<mgmtClientSecret>")
  }

  test("it should throw exception for missing required command-line arg -> audience") {
    val exitCode = FhirValidationJob.call(Array[String](TestBrokerArg, TestInputTopicArg, TestPasswordArg, TestMgmtUrlArg, TestClientIdArg, TestClientSecretArg, TestOAuthBaseUrlArg), errPs)
    exitCode should equal(CommandLine.ExitCode.USAGE)

    val err = errOut.toString
    err should include ("Missing required argument")
    err should include ("--audience=<mgmtAudience>")
  }

  test("it should throw exception for missing required command-line arg -> oauth-url") {
    val exitCode = FhirValidationJob.call(Array[String](TestBrokerArg, TestInputTopicArg, TestPasswordArg, TestMgmtUrlArg, TestClientIdArg, TestClientSecretArg, TestAudienceArg), errPs)
    exitCode should equal(CommandLine.ExitCode.USAGE)

    val err = errOut.toString
    err should include ("Missing required argument")
    err should include ("--oauth-url=<oauthServiceBaseUrl>")
  }

  test("it should throw exception for invalid inputTopic Value in Standalone mode") {
    val badInputTopicValue = "ingest-monkey22-noPeriodSeparators"
    val badInputTopicArg = "--input=" + badInputTopicValue

    val exitCode = FhirValidationJob.call(Array[String](TestBrokerArg, badInputTopicArg, TestPasswordArg, TestStandaloneArg), errPs)
    exitCode should equal(CommandLine.ExitCode.SOFTWARE)

    val err = errOut.toString
    err should include ("The Input Topic Name " + badInputTopicValue + " is invalid")
    err should include ("It must start with \"ingest.\"")
  }

  test("it should throw exception for invalid inputTopic Value with the MgmtAPI") {
    val badInputTopicValue = "ingest-monkey22-noPeriodSeparators"
    val badInputTopicArg = "--input=" + badInputTopicValue

    val exitCode = FhirValidationJob.call(Array[String](TestBrokerArg, badInputTopicArg, TestPasswordArg, TestMgmtUrlArg, TestClientIdArg, TestClientSecretArg, TestAudienceArg, TestOAuthBaseUrlArg), errPs)
    exitCode should equal(CommandLine.ExitCode.SOFTWARE)

    val err = errOut.toString
    err should include ("The Input Topic Name " + badInputTopicValue + " is invalid")
    err should include ("It must start with \"ingest.\"")
  }

}
