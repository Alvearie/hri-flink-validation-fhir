# (C) Copyright IBM Corp. 2021
#
# SPDX-License-Identifier: Apache-2.0

class SlackHelper

  def initialize(slack_webhook)
    @request_helper = Helper.new
    @slack_url = slack_webhook
  end

  def send_slack_message(test_type, build_dir, branch_name, github_job_web_url)
    message = {
      text: "*#{test_type} Test Failure:*
              Repository: #{build_dir.split('/').last},
              Branch: #{branch_name},
              Time: #{Time.now.strftime("%m/%d/%Y %H:%M")},
              Build Link: #{github_job_web_url.gsub('https:///', 'https://https://github.com/Alvearie/hri-flink-validation-fhir/actions/')}

              For instructions on viewing test health on the UnitTH dashboard, consult the following README:
              https://github.com/Alvearie/dev-tools/blob/master/README.md#helm-deploy-for-unitth-test-dashboard"
    }.to_json
    @request_helper.rest_post(@slack_url, message)
  end

end