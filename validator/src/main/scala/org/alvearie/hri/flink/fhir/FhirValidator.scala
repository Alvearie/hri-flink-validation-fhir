/*
 * (C) Copyright IBM Corp. 2021
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.alvearie.hri.flink.fhir

import java.io.{PrintWriter, StringWriter}
import java.nio.charset.StandardCharsets

import com.ibm.fhir.model.format.Format
import com.ibm.fhir.model.parser.FHIRParser
import com.ibm.fhir.model.resource.Bundle
import org.alvearie.hri.flink.core.Validator
import org.alvearie.hri.flink.core.serialization.HriRecord
import org.apache.commons.io.IOUtils

import scala.runtime.RichBoolean
import scala.util.{Failure, Success, Try}

class FhirValidator extends Validator {

  def isValid(record: HriRecord): (RichBoolean, String) = {
    try {
      val recordBody = new String(record.value, StandardCharsets.UTF_8)
      val inputStream = IOUtils.toInputStream(recordBody, StandardCharsets.UTF_8)
      val bundleTry: Try[Bundle] = Try(FHIRParser.parser(Format.JSON).parse[Bundle](inputStream))

      IOUtils.closeQuietly(inputStream)

      bundleTry match {
        case Success(_) =>
          (true, "")
        case Failure(ex) =>
          (false, ex.getMessage)
      }
    } catch {
      case ex:Exception => {
        val stringWriter = new StringWriter()
        val printWriter = new PrintWriter(stringWriter)
        ex.printStackTrace(printWriter)
        (false, stringWriter.toString)
      }
    }
  }
}
