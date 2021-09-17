# (C) Copyright IBM Corp. 2021
#
# SPDX-License-Identifier: Apache-2.0

class LogDnaHelper

  def initialize
    @helper = Helper.new
    @base_url = ENV['LOGDNA_URL']
    @host_name = "GITHUB_ACTIONS"
    @app_name = "hri-flink-validation-fhir"
    @basic_auth = { user: ENV['LOGDNA_INGESTION_KEY'], password: '' }
  end

  def log_message(msg, level = 'INFO')
    body = {
      lines: [
        {
          line: msg,
          app: @app_name,
          level: level
        }
      ]
    }.to_json
    headers = { 'charset' => 'UTF-8',
                'Content-Type' => 'application/json' }
    @helper.rest_post("#{@base_url}?hostname=#{@host_name}", body, headers, @basic_auth)
  end

end