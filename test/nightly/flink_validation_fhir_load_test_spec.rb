# (C) Copyright IBM Corp. 2021
#
# SPDX-License-Identifier: Apache-2.0

require_relative '../env'

describe 'Flink FHIR Validation Load Test' do
  before(:all) do
    TENANT_ID = 'test'
    TEST_TIMEOUT = 450
    MIN_THROUGHPUT = 0
    INPUT_RECORD_NUMBER = (ENV['LOAD_TEST_RECORD_AMOUNT'] || 200).to_i
    PARALLELISM = 2
    BATCH_COMPLETION_DELAY = 30000
    ENV['TEST_NAME'] = 'load' unless ENV['TEST_NAME']

    @flink_helper = HRITestHelpers::FlinkHelper.new(ENV['FLINK_URL'])
    @event_streams_helper = HRITestHelpers::EventStreamsHelper.new
    @iam_token = HRITestHelpers::IAMHelper.new(ENV['IAM_CLOUD_URL']).get_access_token(ENV['CLOUD_API_KEY'])
    @appid_helper = HRITestHelpers::AppIDHelper.new(ENV['APPID_URL'], ENV['APPID_TENANT'], @iam_token, ENV['JWT_AUDIENCE_ID'])
    @flink_api_oauth_token = @appid_helper.get_access_token('hri_integration_tenant_test_data_integrator', '', ENV['APPID_FLINK_AUDIENCE'])
    @hri_oauth_token = @appid_helper.get_access_token('hri_integration_tenant_test_data_integrator', 'tenant_test hri_data_integrator', ENV['APPID_HRI_AUDIENCE'])
    @kafka = Kafka.new(ENV['KAFKA_BROKERS'], client_id: "hri-flink-validation-fhir-#{@branch_name}-#{Time.now.to_i}", connect_timeout: 10, socket_timeout: 10, sasl_plain_username: 'token', sasl_plain_password: ENV['SASL_PLAIN_PASSWORD'], ssl_ca_certs_from_system: true)
    @mgmt_api_helper = HRITestHelpers::MgmtAPIHelper.new(ENV['HRI_INGRESS_URL'], @iam_token)
    @validation_jar_id = @flink_helper.upload_jar_from_dir("hri-flink-validation-fhir-#{@git_branch}.jar", File.join(File.dirname(__FILE__), '../../validation/build/libs'), @flink_api_oauth_token, /hri-flink-validation-fhir-.+.jar/)

    @flink_job = FlinkJob.new(@flink_helper, @event_streams_helper, @kafka, @validation_jar_id, TENANT_ID)
    @flink_job.start_job(@flink_api_oauth_token, PARALLELISM, BATCH_COMPLETION_DELAY, true, {input_topic: ENV['INPUT_TOPIC'], output_topic: ENV['OUTPUT_TOPIC'], notification_topic: ENV['NOTIFICATION_TOPIC'], invalid_topic: ENV['INVALID_TOPIC']})

    @background_flink_job = FlinkJob.new(@flink_helper, @event_streams_helper, @kafka, @validation_jar_id, TENANT_ID)
    @background_flink_job.start_job(@flink_api_oauth_token, PARALLELISM, BATCH_COMPLETION_DELAY, true, {input_topic: ENV['INPUT_TOPIC'], output_topic: ENV['OUTPUT_TOPIC'], notification_topic: ENV['NOTIFICATION_TOPIC'], invalid_topic: ENV['INVALID_TOPIC']})

    @input_dir = File.join(File.dirname(__FILE__), "../test_data/synthea/fhir")
    @input_records = Dir.children(@input_dir)
  end

  it "should process records under load" do
    begin
      # Submit a batch. This batch will load a set amount of records and will be used for a throughput calculation
      main_batch = @flink_job.submit_batch(@mgmt_api_helper, @hri_oauth_token)
      main_batch_submitted = Time.now

      # Submit two more batches. These batches will continuously upload records, simulating high traffic through flink
      background_batch_1 = @flink_job.submit_batch(@mgmt_api_helper, @hri_oauth_token)
      background_batch_2 = @background_flink_job.submit_batch(@mgmt_api_helper, @hri_oauth_token)

      # Callback for when a notification is received by a job's kafka consumer
      batch_notification_callback = lambda { |batch, notification|

        # When a notification is received for this job, validate that it has an expected status.
        case batch.info['status']
          when nil
            # When a notification was previously unset, then the next status can only be "started"
            raise "Notification message contains an incorrect status. Expected: started, Received: #{notification['status']}" unless notification['status'] == "started"
            Logger.new(STDOUT).info("#{batch.name} received \"started\" notification")
          when "started"
            # When a notification was previously "started", then the next status can only be "sendComplete"
            raise "Notification message contains an incorrect status. Expected: sendComplete, Received: #{notification['status']}" unless notification['status'] == "sendCompleted"
            Logger.new(STDOUT).info("#{batch.name} received \"sendCompleted\" notification")
          when "sendCompleted"
            # When a notification was previously "sendComplete", then the next status can only be "completed"
            raise "Notification message contains an incorrect status. Expected: completed, Received: #{notification['status']}" unless notification['status'] == "completed"
            Logger.new(STDOUT).info("#{batch.name} received \"completed\" notification")
            batch.info['batchProcessingTime'] = DateTime.parse(notification['endDate']).to_time - DateTime.parse(notification['startDate']).to_time
          else
            raise "#{batch.name} has an unexpected status. Local batch status: #{batch.status}, notification status #{notification['status']}"
        end

        batch.info['status'] = notification['status']
      }
      @flink_job.on_batch_notification &batch_notification_callback
      @background_flink_job.on_batch_notification &batch_notification_callback

      # Callback for when an invalid record is received by the job's kafka consumer. Invalid records for the
      # flink-background job are ignored.
      invalid_records_callback = lambda { |batch,_|
        batch.info['invalid_records'] = 0 unless batch.info['invalid_records']
        batch.info['invalid_records'] += 1

        # Log a progress update for the main batch
        if batch.name == main_batch.name
          valid_records = batch.info['valid_records'] || 0
          total_records_received = batch.info['invalid_records'] + valid_records
          Logger.new(STDOUT).info("#{batch.name} received an invalid record notification. #{total_records_received} records processed")
        end
      }
      @flink_job.on_invalid_record &invalid_records_callback

      # Callback for when a valid record is received by the job's kafka consumer. Valid records for the
      # flink-background job are ignored.
      valid_records_callback = lambda { |batch,record_name,_|
        batch.info['valid_records'] = 0 unless batch.info['valid_records']
        batch.info['valid_records'] += 1

        # Log a progress update for the main batch
        if batch.name == main_batch.name
          invalid_records = batch.info['invalid_records'] || 0
          total_records_received = invalid_records + batch.info['valid_records']
          Logger.new(STDOUT).info("#{batch.name} received valid record: #{record_name}. #{total_records_received} records processed")
        end
      }
      @flink_job.on_output_record &valid_records_callback

      # Callback for when an input record is received by the job's kafka consumer. Input records for the background
      # batches are ignored or filtered out
      input_record_number = 0
      input_records_callback = lambda { |batch,record_name,_|
        # Log each record in the main batch that has been pushed to the input topic.
        if batch.name == main_batch.name
          input_record_number += 1
          Logger.new(STDOUT).info("#{batch.name} received input record: #{record_name}. #{input_record_number} records received in input topic")
          Logger.new(STDOUT).error("#{batch.name} received more records than expected in the input topic." +
                                       "This should cause the batch to fail validation") if input_record_number > INPUT_RECORD_NUMBER
        end
      }
      @flink_job.on_input_record &input_records_callback

      main_batch_send_completed = false
      main_batch_first_record_sent = nil
      main_batch_total_size_mb = 0

      start_time = Time.now
      record_key = 0
      while (TEST_TIMEOUT > Time.now - start_time) && !@flink_job.failed? && !@background_flink_job.failed? && !main_batch.completed?
        # Choose a randomly selected input record
        record_name = @input_records.sample
        record = File.read(File.join(@input_dir, record_name))

        # Skip the record if it is over 5 MB
        if record.bytesize > 5242880
          next
        end

        record_key += 1

        # Submit the record to the background batches
        background_batch_1.submit_record("background_batch_1_#{record_key}", record)
        background_batch_2.submit_record("background_batch_2_#{record_key}", record)

        # Start submitting records to this batch after 30 seconds. This ensures that the Flink cluster is operating
        # at a full load for this test
        if (30 < Time.now - start_time) && (main_batch.records_sent.value < INPUT_RECORD_NUMBER)
          main_batch_first_record_sent = Time.now unless main_batch_first_record_sent
          main_batch.submit_record("main_batch_#{record_key}", record)
          main_batch_total_size_mb = main_batch_total_size_mb + record.bytesize / (1024.0 * 1024.0)
        end

        if main_batch.records_sent.value == INPUT_RECORD_NUMBER && !main_batch_send_completed
          # After a certain number of records are loaded, complete the batch
          main_batch.complete(@mgmt_api_helper, @hri_oauth_token)
          main_batch_send_completed = true
          Logger.new(STDOUT).info("#{main_batch.name} sent all #{INPUT_RECORD_NUMBER} records")
        end

      end

    ensure
      Logger.new(STDOUT).info("Cancelling flink jobs. Job1: #{@flink_job.failed? ? 'failed' : 'passed'}, " +
        "Job2: #{@background_flink_job.failed? ? 'failed' : 'passed'}")

      # Cancel the flink jobs. The jobs will log any errors encountered.
      job_failures_occurred = false
      begin
        @flink_job.stop_job(@flink_api_oauth_token)
      rescue Exception => e
        Logger.new(STDOUT).error(e.message)
        job_failures_occurred = true
      end

      begin
        @background_flink_job.stop_job(@flink_api_oauth_token)
      rescue Exception => e
        Logger.new(STDOUT).error(e.message)
        job_failures_occurred = true
      end

      raise 'One or both flink jobs encountered an error' if job_failures_occurred
    end

    raise "#{main_batch.name} did not complete within #{TEST_TIMEOUT} seconds" unless main_batch.info['status'] == 'completed'
    invalid_records = main_batch.info['invalid_records'] || 0
    valid_records = main_batch.info['valid_records'] || 0
    completed_records = invalid_records + valid_records
    raise "#{main_batch.name} was completed but only #{completed_records} out of #{main_batch.records_sent.value} " +
              "records were processed" unless main_batch.records_sent.value == completed_records

    # Total time is the difference between the batch startDate and endDate. Records weren't submitted to the batch
    # immediately, so the time between the batch's submission and sending the first record is subtracted. The batch
    # completion delay is also removed from the total time.
    total_time = main_batch.info['batchProcessingTime'] - (main_batch_first_record_sent - main_batch_submitted) - (BATCH_COMPLETION_DELAY/1000.0)
    batch_throughput_mb = main_batch_total_size_mb / total_time
    batch_throughput_records = main_batch.records_sent.value / total_time
    Logger.new(STDOUT).info("#{main_batch.name} processed #{INPUT_RECORD_NUMBER} with parallelism/partitions " +
                                "set to #{PARALLELISM}. Throughput was #{batch_throughput_records} records/s and " +
                                "#{batch_throughput_mb} MB/s")
    raise "#{main_batch.name} had a throughput lower than #{MIN_THROUGHPUT}" if batch_throughput_mb <= MIN_THROUGHPUT

  end

  after(:all) do

    begin
      @flink_job.cleanup_job(@flink_api_oauth_token)
    rescue Exception => e
      Logger.new(STDOUT).error(e)
    end

    begin
      @background_flink_job.cleanup_job(@flink_api_oauth_token)
    rescue Exception => e
      Logger.new(STDOUT).error(e)
    end

    if @validation_jar_id
      Logger.new(STDOUT).info("Deleting Validation Jar")
      response = @flink_helper.delete_jar(@validation_jar_id, {'Authorization' => "Bearer #{@flink_api_oauth_token}"})
      raise "Failed to delete Flink jar with ID: #{@validation_jar_id}" unless response.code == 200
      @flink_helper.verify_jar_deleted(@validation_jar_id, @flink_api_oauth_token)
    end
  end
end