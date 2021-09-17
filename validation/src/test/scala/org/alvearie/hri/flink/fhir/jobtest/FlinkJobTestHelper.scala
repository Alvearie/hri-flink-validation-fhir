/*
 * (C) Copyright IBM Corp. 2021
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.alvearie.hri.flink.fhir.jobtest

import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime

import org.alvearie.hri.api.BatchNotification
import org.alvearie.hri.flink.core.TestHelper
import org.alvearie.hri.flink.core.jobtest.sources.{TestBatchNotification, TestRecordHeaders}
import org.alvearie.hri.flink.core.serialization.{HriRecord, NotificationRecord}
import org.apache.kafka.common.header.internals.RecordHeaders

object FlinkJobTestHelper {

  val ValidFhirOneRecord = """{"resourceType":"Bundle","type":"collection","entry":[{"resource":{"resourceType":"Claim","identifier":[{"value":"000000000000000000016372981"}],"status":"active","type":{"text":"pharmacy"},"use":"claim","patient":{"identifier":{"value":"DPFO199578"}},"created":"2020-03-18","provider":{"identifier":{"value":"1019343"}},"priority":{"text":"normal"},"insurance":[{"sequence":1,"focal":true,"coverage":{"identifier":{"value":"placeholder"}}}]}},{"resource":{"resourceType":"ClaimResponse","status":"active","type":{"text":"pharmacy"},"use":"claim","patient":{"identifier":{"value":"DPFO199578"}},"created":"2020-03-18","insurer":{"identifier":{"value":"placeholder"}},"outcome":"queued","adjudication":[{"category":{"text":"insurance"},"amount":{"value":0.00}},{"category":{"text":"copay"},"amount":{"value":0.00}},{"category":{"text":"deductible"},"amount":{"value":0.00}},{"category":{"text":"despensingfee"},"amount":{"value":0.40}},{"category":{"text":"ingredient cost"},"amount":{"value":12.27}},{"category":{"text":"Net Payment"},"amount":{"value":27.67}},{"category":{"text":"sales tax"},"amount":{"value":0.00}},{"category":{"text":"Tax Amount"},"amount":{"value":0.00}},{"category":{"text":"Third Party Amount"},"amount":{"value":0.00}}]}},{"resource":{"resourceType":"MedicationDispense","status":"in-progress","medicationCodeableConcept":{"text":"中文"},"quantity":{"value":0.5},"daysSupply":{"value":1.0}}},{"resource":{"resourceType":"Medication","ingredient":[{"itemCodeableConcept":{"text":"中文"}}]}}]}"""
  val DefaultTestBatchId = "batch-06"
  val PassThruHeaderVal = "PassThruHeaderValue"
  val DefaultValidFhirJson = ""
  private val DefaultBatchName = "TestBatchName"
  private val DefaultDataType = "procedure"
  private val DefaultStartDate = "2020-04-08T03:02:23Z"
  private val DefaultEndDate = "2020-04-11T16:02:44Z"
  val DefaultTopic = "ingest.porcupine.data-int1.in"
  private val DefaultPartition = 1
  private val DefaultOffset = 1234L


  def getDefaultTestHeaders() : TestRecordHeaders = {
    val headers = new TestRecordHeaders()
    headers.add("batchId", DefaultTestBatchId.getBytes(StandardCharsets.UTF_8))
    headers.add("passThru", PassThruHeaderVal.getBytes(StandardCharsets.UTF_8))
    headers
  }

  def getHeadersWithDelayHeader(delayTime:Long) : TestRecordHeaders = {
    val headers = getDefaultTestHeaders()
    headers.add(TestHelper.DelayHeaderKey,
      TestHelper.convertLongToByteArray(delayTime))
    headers
  }

  def createOneValidHriRecord(headers: TestRecordHeaders, key: Array[Byte]): HriRecord = {
    val newHriRecBody = ValidFhirOneRecord
    new HriRecord(headers, key, newHriRecBody.getBytes(StandardCharsets.UTF_8), DefaultTopic, DefaultPartition, DefaultOffset)
  }

  def createOneInvalidHriRecord(headers: TestRecordHeaders, key: Array[Byte]): HriRecord = {
    val newHriRecBody = TestClaimInvalidFhirBadResourceType
    new HriRecord(headers, key, newHriRecBody.getBytes(StandardCharsets.UTF_8), DefaultTopic, DefaultPartition, DefaultOffset)
  }

  def createTestNotification(headers: RecordHeaders, key: Array[Byte],
                             batchId: String, expectedRecCount: Int,
                             status: BatchNotification.Status,
                             invalidThreshold: Int = 5): NotificationRecord = {

    val batchNotification = createTestBatchNotification(batchId, expectedRecCount,
      status, invalidThreshold, DefaultBatchName)
    new NotificationRecord(headers, key, batchNotification)
  }

  def createTestBatchNotification(batchId: String, expectedRecCount: Int,
                                  status: BatchNotification.Status,
                                  invalidThreshold: Int,
                                  batchName: String): TestBatchNotification = {
    return new TestBatchNotification()
      .withId(batchId)
      .withName(batchName)
      .withStatus(status)
      .withDataType(DefaultDataType)
      .withStartDate(OffsetDateTime.parse(DefaultStartDate))
      .withEndDate(OffsetDateTime.parse(DefaultEndDate))
      .withExpectedRecordCount(expectedRecCount)
      .withTopic(DefaultTopic)
      .withInvalidThreshold(invalidThreshold).asInstanceOf[TestBatchNotification]
  }

  val TestClaimInvalidFhirBadResourceType =
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


}