# (C) Copyright IBM Corp. 2021
#
# SPDX-License-Identifier: Apache-2.0

require_relative '../env'

class FlinkJob

  GIT_BRANCH = ENV['BRANCH_NAME']
  @@total_jobs = 0
  @@total_batches = 0

  def initialize(flink_helper, event_streams_helper, kafka_helper, validation_jar_id, tenant)
    @flink_helper = flink_helper
    @event_streams_helper = event_streams_helper
    @kafka_helper = kafka_helper
    @validation_jar_id = validation_jar_id
    @tenant = tenant
    @@total_jobs += 1
    @job_number = @@total_jobs

    @job_started = false
    @batches = Concurrent::Hash.new
    @consumer_lock = Mutex.new
    @flink_monitor_lock = Mutex.new
  end

  attr_reader :kafka_producer
  attr_reader :kafka_input_topic

  def start_job(flink_oauth_token, parallelism, batch_complete_delay, monitor_kafka = false, kafka_topics)
    Logger.new(STDOUT).info("Starting Job #{@job_number}")
    @kafka_producer = @kafka_helper.producer(compression_codec: :zstd)

    if monitor_kafka
      # Create unique kafka topic names for this job
      timestamp = Time.now.to_i
      @kafka_input_topic = kafka_topics[:input_topic].gsub('.in', "-#{GIT_BRANCH}-job#{@job_number}-#{timestamp}.in")
      @kafka_output_topic = kafka_topics[:output_topic].gsub('.out', "-#{GIT_BRANCH}-job#{@job_number}-#{timestamp}.out")
      @kafka_notification_topic = kafka_topics[:notification_topic].gsub('.notification', "-#{GIT_BRANCH}-job#{@job_number}-#{timestamp}.notification")
      @kafka_invalid_topic = kafka_topics[:invalid_topic].gsub('.invalid', "-#{GIT_BRANCH}-job#{@job_number}-#{timestamp}.invalid")

      # Create kafka topics for the job
      @event_streams_helper.create_topic(@kafka_input_topic, parallelism)
      @event_streams_helper.create_topic(@kafka_output_topic, parallelism)
      @event_streams_helper.create_topic(@kafka_notification_topic, 1)
      @event_streams_helper.create_topic(@kafka_invalid_topic, parallelism)
      @event_streams_helper.verify_topic_creation([@kafka_input_topic, @kafka_output_topic, @kafka_notification_topic, @kafka_invalid_topic])

      # Create a kafka consumer to monitor the notification, invalid, and output topics
      consumer_group = "hri-flink-validation-fhir-#{GIT_BRANCH}-job#{@job_number}-#{timestamp}-consumer"
      @kafka_consumer = @kafka_helper.consumer(group_id: consumer_group)
      @kafka_consumer.subscribe(@kafka_notification_topic)
      @kafka_consumer.subscribe(@kafka_invalid_topic)
      @kafka_consumer.subscribe(@kafka_output_topic)
      @kafka_consumer.subscribe(@kafka_input_topic)
      # Ensure that the consumer is set to the latest offset for each topic
      @event_streams_helper.reset_consumer_group(@event_streams_helper.get_groups, consumer_group, @kafka_notification_topic, 'latest')
      @event_streams_helper.reset_consumer_group(@event_streams_helper.get_groups, consumer_group, @kafka_invalid_topic, 'latest')
      @event_streams_helper.reset_consumer_group(@event_streams_helper.get_groups, consumer_group, @kafka_output_topic, 'latest')
      @event_streams_helper.reset_consumer_group(@event_streams_helper.get_groups, consumer_group, @kafka_input_topic, 'latest')

      # Every flink job will have two threads dedicated to monitoring kafka and the flink job (through the flink API).
      # The "Thread.current" settings prevent exceptions from being raised, or printed to the console, until the monitor
      # threads are joined to the main thread.
      @kafka_monitor_thread = Thread.new {
        Thread.current.abort_on_exception = false
        Thread.current.report_on_exception = false
        kafka_monitor_job
      }
    else
      @kafka_input_topic = kafka_topics[:input_topic]
    end

    # Start the flink job and verify that it is running
    @job_id = @flink_helper.start_flink_job(@validation_jar_id, ENV['KAFKA_BROKERS'], ENV['SASL_PLAIN_PASSWORD'], @kafka_input_topic, ENV['HRI_SERVICE_URL'], ENV['OIDC_HRI_INTERNAL_CLIENT_ID'], ENV['OIDC_HRI_INTERNAL_CLIENT_SECRET'], "#{ENV['APPID_URL']}/oauth/v4/#{ENV['APPID_TENANT']}", ENV['APPID_HRI_AUDIENCE'], batch_complete_delay, flink_oauth_token)
    @flink_helper.verify_job_state(@job_id, flink_oauth_token, 'RUNNING')
    @job_started = true

    # wait for the job to completely start up, otherwise it will miss some of the initial messages.
    sleep(5)

    @flink_monitor_thread = Thread.new {
      Thread.current.abort_on_exception = false
      Thread.current.report_on_exception = false
      flink_monitor_job(flink_oauth_token)
    }

    Logger.new(STDOUT).info("Finished starting Job #{@job_number}")
  end

  def kafka_monitor_job
    @kafka_consumer.each_message do |message|
      # Everything between the consumer lock and unlock is threadsafe. The job will have to acquire the consumer_lock
      # before killing the kafka monitor thread. This ensures that the thread won't be killed while an
      # output, notification, or invalid message from kafka is being processed.
      @consumer_lock.lock

      # Send the batch tracking object and kafka message value to the callback proc registered for each topic
      case message.topic
        when @kafka_notification_topic
          batch_notification = JSON.parse(message.value)
          batch = @batches[batch_notification['id']]
          raise "Batch not found: Job#{@job_number} did not have a registered batch with id #{batch_notification['id']}" unless batch
          @batch_notification_callback.call(batch, batch_notification) if @batch_notification_callback
          batch.batch_processing_complete.make_true if batch_notification['status'] == 'completed'
        when @kafka_invalid_topic
          batch = @batches[message.headers['batchId']]
          raise "Batch not found: Job#{@job_number} did not have a registered batch with id #{message.headers['batchId']}" unless batch
          invalid_record = JSON.parse(message.value)
          @batch_invalid_callback.call(batch, invalid_record) if @batch_invalid_callback
          batch.records_received.increment
        when @kafka_output_topic
          batch = @batches[message.headers['batchId']]
          raise "Batch not found: Job#{@job_number} did not have a registered batch with id #{message.headers['batchId']}" unless batch
          @batch_output_callback.call(batch, message.headers['recordName'], message.value) if @batch_output_callback
          batch.records_received.increment
      when @kafka_input_topic
        batch = @batches[message.headers['batchId']]
        raise "Batch not found: Job#{@job_number} did not have a registered batch with id #{message.headers['batchId']}" unless batch
        @batch_input_callback.call(batch, message.headers['recordName'], message.value) if @batch_input_callback
      else
          raise "Message from unhandled topic returned by kafka consumer: #{message.topic}"
      end

      @consumer_lock.unlock
    ensure
      @consumer_lock.unlock if @consumer_lock.owned?
    end
  end

  def flink_monitor_job(flink_oauth_token)
    loop do
      @flink_monitor_lock.lock

      flink_job_exceptions = retrieve_flink_errors(flink_oauth_token)
      raise "#{flink_job_exceptions['root-exception']}" if flink_job_exceptions['root-exception']

      flink_checkpoints = retrieve_flink_checkpoints(flink_oauth_token)
      raise "One or more checkpoints failed" if flink_checkpoints['counts']['failed'] > 0

      @flink_monitor_lock.unlock

      # Only check flink job every 5 seconds
      sleep(5)

    ensure
      @flink_monitor_lock.unlock if @flink_monitor_lock.owned?
    end
  end

  def retrieve_flink_errors(flink_oauth_token)
    flink_job_exceptions_response = @flink_helper.get_job_exceptions(@job_id, {'Authorization' => "Bearer #{flink_oauth_token}"})
    raise 'Failed to get Flink job exceptions' unless flink_job_exceptions_response.code == 200
    JSON.parse(flink_job_exceptions_response)
  end

  def retrieve_flink_checkpoints(flink_oauth_token)
    flink_job_checkpoints_response = @flink_helper.get_job_checkpoints(@job_id, {'Authorization' => "Bearer #{flink_oauth_token}"})
    raise 'Failed to get Flink job checkpoints' unless flink_job_checkpoints_response.code == 200
    JSON.parse(flink_job_checkpoints_response)
  end

  # Thread safe. Returns true if the flink pipeline failed or if an error occurred handling kafka messages
  def failed?
    !@flink_monitor_thread.alive? || !@kafka_monitor_thread.alive?
  end

  def stop_flink_job(flink_oauth_token)
    if @job_started
      response = @flink_helper.stop_job(@job_id, {'Authorization' => "Bearer #{flink_oauth_token}"})
      raise "Failed to stop Flink job with ID: #{@job_id}" unless response.code == 202
      begin
        @flink_helper.verify_job_state(@job_id, flink_oauth_token, 'FINISHED')
        Logger.new(STDOUT).info("Stopped Flink job #{@job_id}")
        @job_started = false
      rescue
        (Timeout::Error)
        Logger.new(STDOUT).warn('Timeout stopping flink job')
      end
    end
  end

  def stop_job(flink_oauth_token)
    # Get the locks on the mutexes used by the monitor threads. This ensures that the consumer won't be killed while it
    # is in the middle of processing an incoming message and the flink monitor will not monitor for exceptions while the
    # job is being cancelled
    @consumer_lock.lock
    @flink_monitor_lock.lock

    exception_occurred = false

    # Cancel flink job. It isn't needed anymore.
    Logger.new(STDOUT).info("Stopping Job #{@job_number}")
    stop_flink_job(flink_oauth_token)

    @kafka_producer.shutdown if @kafka_producer

    # Exit the kafka thread if it is still running, or join the thread to propagate its errors
    if @kafka_monitor_thread
      if @kafka_monitor_thread.alive?
        @kafka_monitor_thread.exit
      else
        begin
          @kafka_monitor_thread.join
        rescue Kafka::ProcessingError => e
          Logger.new(STDOUT).error("Job#{@job_number} (#{@job_id}) encountered an error while monitoring kafka topics:\n#{e.cause.message}")
          exception_occurred = true
        end
      end
    end

    # Exit the flink thread if it is still running, or join the thread to propagate its errors
    if @flink_monitor_thread.alive?
      @flink_monitor_thread.exit
    else
      begin
        @flink_monitor_thread.join
      rescue RuntimeError => e
        Logger.new(STDOUT).error("Job#{@job_number} (#{@job_id}) encountered an error:\n#{e.message}")
        exception_occurred = true
      end
    end

    raise "Flink job#{@job_number} (#{@job_id}) failed" if exception_occurred
    Logger.new(STDOUT).info("Finished stopping Job #{@job_number}")
  end

  def cleanup_job(flink_oauth_token)
    Logger.new(STDOUT).info("Cleaning up after Job #{@job_number}")

    # If flink job wasn't previously canceled, cancel it now
    stop_flink_job(flink_oauth_token)

    # Delete Kafka resources
    Logger.new(STDOUT).info("Deleting Kafka Topics")
    @kafka_consumer.stop if @kafka_consumer
    @event_streams_helper.delete_topic(@kafka_input_topic) if @kafka_input_topic
    @event_streams_helper.delete_topic(@kafka_output_topic) if @kafka_output_topic
    @event_streams_helper.delete_topic(@kafka_notification_topic) if @kafka_notification_topic
    @event_streams_helper.delete_topic(@kafka_invalid_topic) if @kafka_invalid_topic

    # Delete Job's batches
    Logger.new(STDOUT).info('Deleting Batches')
    elastic = HRITestHelpers::ElasticHelper.new({url: ENV['ELASTIC_URL'], username: ENV['ELASTIC_USER'], password: ENV['ELASTIC_PASSWORD']})
    response = elastic.es_delete_by_query(TENANT_ID, "name:hri-flink-validation-fhir-#{GIT_BRANCH}-#{ENV['TEST_NAME']}-test-job#{@job_number}-batch*")
    response.nil? ? (raise 'Elastic batch delete did not return a response') : (raise 'Failed to delete Elastic batches' unless response.code == 200)
    @batches.each_value { |batch| Logger.new(STDOUT).info("Batch #{batch.name} deleted") }

    Logger.new(STDOUT).info("Finished cleaning up after Job #{@job_number}")
  end

  # Starts a new batch using the flink job.
  def submit_batch(hri_helper, hri_oauth_token)
    @@total_batches += 1
    Logger.new(STDOUT).info("Submitting batch #{@@total_batches}")

    batch_name = "hri-flink-validation-fhir-#{GIT_BRANCH}-#{ENV['TEST_NAME']}-test-job#{@job_number}-batch#{@@total_batches}"
    batch_template = {
        name: batch_name,
        dataType: 'hri-flink-validation-fhir-batch',
        topic: @kafka_input_topic
    }
    batch_id = hri_helper.create_batch(@tenant, batch_template, hri_oauth_token)
    batch = Batch.new(batch_id, batch_name, self)
    @batches[batch_id] = batch

    Logger.new(STDOUT).info("Finished submitting batch #{@@total_batches}. Name: #{batch_name}")

    batch
  end

  # Register callback when a batch notification is received by the kafka consumer
  # The callback method will be passed:
  #   - FlinkJob::Batch
  #   - The parsed notification message
  def on_batch_notification(&block)
    @batch_notification_callback = block
  end

  # Register callback when an invalid record is received by the kafka consumer
  # The callback method will be passed:
  #   - FlinkJob::Batch
  #   - The parsed invalid record message
  def on_invalid_record(&block)
    @batch_invalid_callback = block
  end

  # Register callback when an output record is received by the kafka consumer
  # The callback method will be passed:
  #   - FlinkJob::Batch
  #   - The name of the record provided when the record was submitted
  #   - The validated record
  def on_output_record(&block)
    @batch_output_callback = block
  end

  # Register callback when an input record is received by the kafka consumer
  # The callback method will be passed:
  #   - FlinkJob::Batch
  #   - The name of the record provided when the record was submitted
  #   - The record
  def on_input_record(&block)
    @batch_input_callback = block
  end
end
