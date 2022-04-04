class EventStreamsAPIHelper

  def initialize(admin_url, api_key)
    @request_helper = HRITestHelpers::RequestHelper.new
    @admin_url = admin_url
    @headers = { 'X-Auth-Token': api_key }
  end

  def get_topics
    response = @request_helper.rest_get("#{@admin_url}/admin/topics", @headers)
    raise 'Failed to get Event Streams topics' unless response.code == 200
    JSON.parse(response.body).map { |topic| topic['name']}
  end

  def create_topic(topic, partitions)
    unless get_topics.include?(topic)
      response = @request_helper.rest_post("#{@admin_url}/admin/topics", {name: topic, partitions: partitions}.to_json, @headers)
      raise "Failed to create Event Streams topic: #{topic}" unless response.code == 202
      Logger.new(STDOUT).info("Topic #{topic} created.")
    end
  end

  def delete_topic(topic)
    if get_topics.include?(topic)
      response = @request_helper.rest_delete("#{@admin_url}/admin/topics/#{topic}", nil, @headers)
      raise "Failed to delete Event Streams topic: #{topic}" unless response.code == 202
      Logger.new(STDOUT).info("Topic #{topic} deleted.")
    end
  end

  def verify_topic_creation(expected_topics)
    Timeout.timeout(60, nil, 'Kafka topics not created after 60 seconds') do
      topics = get_topics
      until (topics & expected_topics).sort == expected_topics.sort
        sleep 1
        topics = get_topics
      end
    end
  end

  def get_groups
    @request_helper.exec_command("ibmcloud es groups")[:stdout]
  end

  def reset_consumer_group(existing_groups, group, topic, mode)
    if existing_groups.include?(group)
      if @request_helper.exec_command("ibmcloud es group #{group}")[:stdout].include?(topic)
        Timeout.timeout(10) do
          while @request_helper.exec_command("ibmcloud es group-reset #{group} --mode #{mode} --topic #{topic} --execute")[:stdout].split("\n").last != 'OK' &&
            @request_helper.exec_command("ibmcloud es group #{group}")[:stdout].split("\n").select { |s| s.include?(topic) }[0].split(' ')[4].to_i != 0
            Logger.new(STDOUT).warn("Failed to reset consumer group #{group} to the latest offset")
            sleep 0.1
          end
        end
        Logger.new(STDOUT).info("Consumer group #{group} reset to the latest offset")
      end
    end
  end

  def consumer_group_current_offset(group, topic)
    if get_groups.include?(group)
      @request_helper.exec_command("ibmcloud es group #{group}")[:stdout].split("\n").select { |s| s.start_with?(topic) }[0].split(' ')[2].to_i
    end
  end

end