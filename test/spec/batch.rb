# (C) Copyright IBM Corp. 2021
#
# SPDX-License-Identifier: Apache-2.0

require_relative '../env'

class Batch
  @@total_batches = 0

  def initialize(id, name, job)
    @id = id
    @name = name
    @job = job
    @batch_processing_complete = Concurrent::AtomicBoolean.new(false)
    @records_sent = Concurrent::AtomicFixnum.new(0)
    @records_received = Concurrent::AtomicFixnum.new(0)
    @info = Concurrent::Hash.new
  end

  # All of these attributes are threadsafe
  attr_reader :id
  attr_reader :name
  attr_reader :batch_processing_complete
  attr_reader :records_sent
  attr_reader :records_received
  attr_reader :info

  # Adds the given record to the input topic with the batchId set in the header.
  def submit_record(record_name, record)
    @records_sent.increment
    @job.kafka_producer.produce(record, key: 0, topic: @job.kafka_input_topic, headers: {recordName: record_name, batchId: @id})
    if @job.kafka_producer.buffer_bytesize > 1048576
      @job.kafka_producer.deliver_messages
    end
  end

  # Update the status of the batch to "sendCompleted"
  def complete(hri_helper, hri_oauth_token)
    # Make sure all records are pushed to kafka before marking the batch as "sendCompleted"
    @job.kafka_producer.deliver_messages

    expected_record_count_hash = {
        expectedRecordCount: @records_sent.value
    }
    response = hri_helper.hri_put_batch(TENANT_ID, @id, 'sendComplete', expected_record_count_hash, {'Authorization' => "Bearer #{hri_oauth_token}"})
    raise "Failed to update the status of batch #{@name} to sendCompleted" unless response.code == 200
  end

  def completed?
    @batch_processing_complete.true? && (@records_received.value == @records_sent.value)
  end

end