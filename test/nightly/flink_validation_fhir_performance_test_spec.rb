# (C) Copyright IBM Corp. 2021
#
# SPDX-License-Identifier: Apache-2.0

require_relative '../env'

describe 'HRI Flink FHIR Validation Performance Test' do

  before(:all) do
    TENANT_ID = 'test'
    TEST_TIMEOUT = 700
    MIN_THROUGHPUT = 0
    PARALLELISM = 2
    BATCH_COMPLETION_DELAY = 30000
    ENV['TEST_NAME'] = 'performance' unless ENV['TEST_NAME']

    @flink_helper = FlinkHelper.new(ENV['FLINK_URL'])
    @event_streams_helper = EventStreamsHelper.new
    @flink_api_oauth_token = AppIDHelper.new.get_access_token(Base64.encode64("#{ENV['OIDC_HRI_DATA_INTEGRATOR_CLIENT_ID']}:#{ENV['OIDC_HRI_DATA_INTEGRATOR_CLIENT_SECRET']}").delete("\n"), '', ENV['APPID_FLINK_AUDIENCE'])
    @hri_oauth_token = AppIDHelper.new.get_access_token(Base64.encode64("#{ENV['OIDC_HRI_DATA_INTEGRATOR_CLIENT_ID']}:#{ENV['OIDC_HRI_DATA_INTEGRATOR_CLIENT_SECRET']}").delete("\n"), 'tenant_test hri_data_integrator', ENV['APPID_HRI_AUDIENCE'])
    @kafka = Kafka.new(ENV['KAFKA_BROKERS'], client_id: "hri-flink-validation-fhir-#{@travis_branch}", connect_timeout: 10, socket_timeout: 10, sasl_plain_username: 'token', sasl_plain_password: ENV['SASL_PLAIN_PASSWORD'], ssl_ca_certs_from_system: true)
    @hri_helper = HRIHelper.new(ENV['HRI_URL'])
    @logdna_helper = LogDnaHelper.new
    @validation_jar_id = @flink_helper.upload_jar_from_dir('hri-flink-validation-fhir-nightly-test-jar.jar', '../dependencies', @flink_api_oauth_token)

    @flink_job = FlinkJob.new(@flink_helper, @event_streams_helper, @kafka, @validation_jar_id, TENANT_ID)
    @flink_job.start_job(@flink_api_oauth_token, ENV['INPUT_TOPIC'], ENV['OUTPUT_TOPIC'], ENV['NOTIFICATION_TOPIC'], ENV['INVALID_TOPIC'], PARALLELISM, BATCH_COMPLETION_DELAY)

    @input_dir = File.join(File.dirname(__FILE__), "../test_data/synthetic_mass/fhir")
    @input_records = Dir.children(@input_dir)
    # Custom sorting comparator based on expected filename format of records in the performance bucket.
    @input_records = @input_records.sort do |file_A, file_B|
      # Record filenames are expected to start with a number followed by a "_". This number indicates the order that the
      # record will be pushed in the batch. Records will be submitted to the test batch in the same order everytime the
      # test runs. This will help reduce variability between test runs.

      file_A_number = file_A.split("_")[0].to_i
      file_B_number = file_B.split("_")[0].to_i

      file_A_number <=> file_B_number
    end
  end

  it "should process records in a consistent amount of time" do
    # Callback for when a notification is received by a job's kafka consumer
    @flink_job.on_batch_notification { |batch, notification|

      # When a notification is received for this job, validate that it has an expected status.
      case batch.info['status']
        when nil
          # When a notification was previously unset, then the next status can only be "started"
          raise "#{batch.name} notification message contains an incorrect status. Expected: started, Received: #{notification['status']}" unless notification['status'] == "started"
          Logger.new(STDOUT).info("#{batch.name} received \"started\" notification")

        when "started"
          # When a notification was previously "started", then the next status can only be "sendComplete"
          raise "#{batch.name} notification message contains an incorrect status. Expected: sendComplete, Received: #{notification['status']}" unless notification['status'] == "sendCompleted"
          Logger.new(STDOUT).info("#{batch.name} received \"sendCompleted\" notification")

        when "sendCompleted"
          # When a notification was previously "sendComplete", then the next status can only be "completed"
          raise "#{batch.name} notification message contains an incorrect status. Expected: completed, Received: #{notification['status']}" unless notification['status'] == "completed"
          Logger.new(STDOUT).info("#{batch.name} received \"completed\" notification")
          batch.info['batchProcessingTime'] = DateTime.parse(notification['endDate']).to_time - DateTime.parse(notification['startDate']).to_time

        else
          raise "#{batch.name} has an unexpected status. Local batch status: #{batch.status}, notification status #{notification['status']}"
      end

      batch.info['status'] = notification['status']
    }

    # Callback for when a valid record is received by the job's kafka consumer
    @flink_job.on_output_record { |batch, _, _|
      batch.info['valid_records'] = 0 unless batch.info['valid_records']
      batch.info['last_record_received'] = Time.now
      batch.info['valid_records'] += 1

      # Log a progress update for the batch
      Logger.new(STDOUT).info("#{batch.name} processed #{batch.info['valid_records']} out of #{@input_records.size} records")
    }

    @flink_job.on_invalid_record { |batch, _|
      raise "#{batch.name} encountered an unexpected invalid record"
    }

    begin
      batch = @flink_job.submit_batch(@hri_helper, @hri_oauth_token)

      records_total_size_mb = 0
      start_time = Time.now
      @input_records.each { |file|

        record = File.read(File.join(@input_dir, file))

        # Skip the record if it is over 5 MB
        if record.bytesize > 5242880
          raise "#{file} size is larger than 5 MB"
        end

        records_total_size_mb += record.bytesize / (1024.0 * 1024.0)

        batch.submit_record(file, record)
      }
      batch.complete(@hri_helper, @hri_oauth_token)
      Logger.new(STDOUT).info("#{batch.name} sent all #{@input_records.size} records")
      loop do
        break if batch.completed? || @flink_job.failed? || (TEST_TIMEOUT < (Time.now - start_time))
        sleep(0.5)
      end
    ensure
      @flink_job.stop_job(@flink_api_oauth_token)
    end

    raise "#{batch.name} did not complete within #{TEST_TIMEOUT} seconds" unless batch.info['status'] == "completed"
    raise "#{batch.name} was completed but only #{batch.records_received.value} out of #{batch.records_sent.value} " +
              "records were sent to the output topic" unless batch.records_sent.value == batch.records_received.value

    # The total time the records are processed is determined using the batch's startDate and endDate. The batch
    # completion time is not included because the job is idling during that time.
    total_time = batch.info['batchProcessingTime'] - (BATCH_COMPLETION_DELAY/1000.0)
    batch_throughput_mb = records_total_size_mb / total_time
    batch_throughput_records = batch.records_sent.value / total_time

    throughput_message = "HRI Flink FHIR Validation Performance: #{batch.name} processed #{@input_records.size} records " +
        "with parallelism/partitions set to #{PARALLELISM}. Throughput was #{batch_throughput_records} records/s and " +
        "#{batch_throughput_mb} MB/s"

    Logger.new(STDOUT).info(throughput_message)
    @logdna_helper.log_message(throughput_message)
    Logger.new(STDOUT).info('Results sent to LogDNA')
    raise "#{batch.name} had a throughput lower than #{MIN_THROUGHPUT}" if batch_throughput_mb <= MIN_THROUGHPUT
  end

  after(:all) do
    begin
      @flink_job.cleanup_job(@flink_api_oauth_token)
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