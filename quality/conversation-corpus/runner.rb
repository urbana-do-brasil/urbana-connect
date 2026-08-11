#!/usr/bin/env ruby
# frozen_string_literal: true

require "date"
require "digest"
require "json"
require "net/http"
require "securerandom"
require "time"
require "uri"
require "yaml"

module Corpus
  ROOT = File.expand_path(__dir__).freeze
  SCHEMA_PATH = File.join(ROOT, "schema", "scenario.schema.json").freeze
  FIXTURE_ROOT = File.join(ROOT, "fixtures").freeze
  MEMORY_SEED_MODES = %w[verify-only setup-events].freeze

  class ValidationError < StandardError
    attr_reader :path, :errors

    def initialize(path, errors)
      @path = path
      @errors = Array(errors)
      message = @errors.map { |error| "- #{error}" }.join("\n")
      super("scenario #{path} is invalid:\n#{message}")
    end
  end

  class NetworkError < StandardError; end

  # Small JSON Schema 2020-12 subset used by the corpus contract. It is kept
  # dependency-free and deliberately rejects keywords that are not needed by
  # the scenario schema rather than silently treating them as validation.
  class JsonSchemaValidator
    def initialize(schema)
      @schema = schema
    end

    def validate(instance)
      errors = []
      validate_node(@schema, instance, "$", errors, @schema)
      errors
    end

    private

    def validate_node(schema, value, path, errors, root)
      return if schema == true
      if schema == false
        errors << "#{path}: schema rejected the value"
        return
      end

      if schema["$ref"]
        target = resolve_pointer(root, schema.fetch("$ref"))
        validate_node(target, value, path, errors, root)
        return
      end

      validate_combinators(schema, value, path, errors, root)

      type = schema["type"]
      if type && !Array(type).any? { |candidate| type_matches?(candidate, value) }
        errors << "#{path}: expected type #{Array(type).join(' or ')}, got #{json_type(value)}"
        return
      end

      if schema["enum"] && !schema.fetch("enum").include?(value)
        errors << "#{path}: value #{value.inspect} is not in the allowed enum"
      end
      if schema.key?("const") && value != schema["const"]
        errors << "#{path}: value #{value.inspect} does not equal #{schema['const'].inspect}"
      end

      if value.is_a?(String)
        if schema["minLength"] && value.length < schema.fetch("minLength")
          errors << "#{path}: string is shorter than #{schema['minLength']} characters"
        end
        if schema["pattern"] && !Regexp.new(schema.fetch("pattern")).match?(value)
          errors << "#{path}: string does not match #{schema['pattern'].inspect}"
        end
        if schema["format"] == "date-time"
          begin
            Time.iso8601(value)
          rescue ArgumentError
            errors << "#{path}: value is not an ISO-8601 date-time"
          end
        end
      end

      if value.is_a?(Array)
        if schema["minItems"] && value.length < schema.fetch("minItems")
          errors << "#{path}: array contains fewer than #{schema['minItems']} items"
        end
        if schema["maxItems"] && value.length > schema.fetch("maxItems")
          errors << "#{path}: array contains more than #{schema['maxItems']} items"
        end
        if schema["uniqueItems"] && value.uniq.length != value.length
          errors << "#{path}: array items must be unique"
        end
        item_schema = schema["items"]
        value.each_with_index do |item, index|
          validate_node(item_schema, item, "#{path}[#{index}]", errors, root) if item_schema
        end
      end

      return unless value.is_a?(Hash)

      if schema["minProperties"] && value.length < schema.fetch("minProperties")
        errors << "#{path}: object contains fewer than #{schema['minProperties']} properties"
      end
      if schema["maxProperties"] && value.length > schema.fetch("maxProperties")
        errors << "#{path}: object contains more than #{schema['maxProperties']} properties"
      end

      Array(schema["required"]).each do |key|
        errors << "#{path}: missing required property #{key.inspect}" unless value.key?(key)
      end

      properties = schema.fetch("properties", {})
      value.each_key do |key|
        next if properties.key?(key)
        next unless schema["additionalProperties"] == false

        errors << "#{path}: additional property #{key.inspect} is not allowed"
      end
      value.each do |key, child|
        child_schema = properties[key]
        validate_node(child_schema, child, "#{path}.#{key}", errors, root) if child_schema
      end
    end

    def validate_combinators(schema, value, path, errors, root)
      if schema["allOf"]
        schema.fetch("allOf").each { |child| validate_node(child, value, path, errors, root) }
      end
      if schema["anyOf"]
        valid = schema.fetch("anyOf").any? do |child|
          validate_node(child, value, path, [], root).empty?
        end
        errors << "#{path}: value did not match any schema branch" unless valid
      end
      if schema["oneOf"]
        matches = schema.fetch("oneOf").count do |child|
          validate_node(child, value, path, [], root).empty?
        end
        errors << "#{path}: value matched #{matches} schema branches; exactly one is required" unless matches == 1
      end
    end

    def resolve_pointer(root, reference)
      raise "only local JSON Schema references are supported: #{reference}" unless reference.start_with?("#/")

      reference.sub(%r{^#/}, "").split("/").reduce(root) do |current, token|
        decoded = token.gsub("~1", "/").gsub("~0", "~")
        current.fetch(decoded)
      end
    end

    def type_matches?(type, value)
      case type
      when "object" then value.is_a?(Hash)
      when "array" then value.is_a?(Array)
      when "string" then value.is_a?(String)
      when "number" then value.is_a?(Numeric)
      when "integer" then value.is_a?(Integer)
      when "boolean" then value == true || value == false
      when "null" then value.nil?
      else raise "unsupported JSON Schema type #{type.inspect}"
      end
    end

    def json_type(value)
      return "null" if value.nil?
      return "boolean" if value == true || value == false
      return "object" if value.is_a?(Hash)
      return "array" if value.is_a?(Array)
      return "number" if value.is_a?(Numeric)

      "string"
    end
  end

  module ScenarioLoader
    module_function

    def load(path, schema_path: SCHEMA_PATH, fixture_root: FIXTURE_ROOT)
      document = YAML.safe_load(
        File.read(path),
        permitted_classes: [Date, DateTime, Time],
        aliases: false
      )
      schema = JSON.parse(File.read(schema_path))
      normalized = normalize(document)
      errors = JsonSchemaValidator.new(schema).validate(normalized)
      errors.concat(semantic_errors(document, fixture_root))
      raise ValidationError.new(path, errors) unless errors.empty?

      document
    rescue Psych::Exception, JSON::ParserError, Errno::ENOENT => error
      raise ValidationError.new(path, ["#{error.class}: #{error.message}"])
    end

    def validate(path, schema_path: SCHEMA_PATH, fixture_root: FIXTURE_ROOT)
      load(path, schema_path: schema_path, fixture_root: fixture_root)
      []
    rescue ValidationError => error
      error.errors
    end

    def normalize(value)
      case value
      when Hash
        value.each_with_object({}) { |(key, child), result| result[key.to_s] = normalize(child) }
      when Array
        value.map { |child| normalize(child) }
      when Time, DateTime, Date
        value.iso8601
      else
        value
      end
    end

    def semantic_errors(document, fixture_root)
      return ["root value must be an object"] unless document.is_a?(Hash)

      errors = []
      declared_fixtures = Array(document["fixtures"])
      declared_fixtures.each_with_index do |fixture, index|
        errors.concat(fixture_path_errors(fixture, "fixtures[#{index}]", fixture_root))
      end

      referenced_fixtures(document).each do |fixture, locations|
        errors << "#{locations.join(', ')}: media fixture #{fixture.inspect} is not declared in fixtures" \
          unless declared_fixtures.include?(fixture)
      end

      errors.concat(event_id_errors(document["events"], "events"))
      if document["memory"].is_a?(Hash)
        errors.concat(event_id_errors(document["memory"]["setupEvents"], "memory.setupEvents"))
      end
      sentinel = document["isolationSentinel"]
      if sentinel.is_a?(Hash)
        errors.concat(event_id_errors(sentinel["events"], "isolationSentinel.events"))
        memory = sentinel["memory"]
        errors.concat(event_id_errors(memory["setupEvents"], "isolationSentinel.memory.setupEvents")) if memory.is_a?(Hash)
        if document["contactAlias"] == sentinel["contactAlias"]
          errors << "isolationSentinel.contactAlias must differ from contactAlias"
        end
      end

      approval = document["approval"]
      if approval.is_a?(Hash) && document["contactAlias"]
        expected = "/api/poc/conversations/#{document['contactAlias']}/payment-proof/approve"
        errors << "approval.endpoint must target the scenario contactAlias" unless approval["endpoint"] == expected
      end
      errors
    end

    def referenced_fixtures(document)
      references = Hash.new { |hash, key| hash[key] = [] }
      all_events(document).each do |location, event|
        fixture = event["mediaFixture"] if event.is_a?(Hash)
        references[fixture] << location if fixture
      end
      references
    end

    def all_events(document)
      events = []
      Array(document["events"]).each_with_index { |event, index| events << ["events[#{index}]", event] }
      memory = document["memory"]
      if memory.is_a?(Hash)
        Array(memory["setupEvents"]).each_with_index do |event, index|
          events << ["memory.setupEvents[#{index}]", event]
        end
      end
      sentinel = document["isolationSentinel"]
      if sentinel.is_a?(Hash)
        Array(sentinel["events"]).each_with_index do |event, index|
          events << ["isolationSentinel.events[#{index}]", event]
        end
        sentinel_memory = sentinel["memory"]
        if sentinel_memory.is_a?(Hash)
          Array(sentinel_memory["setupEvents"]).each_with_index do |event, index|
            events << ["isolationSentinel.memory.setupEvents[#{index}]", event]
          end
        end
      end
      events
    end

    def event_id_errors(events, location)
      ids = Array(events).map { |event| event["eventId"] if event.is_a?(Hash) }.compact
      ids.group_by(&:itself).map do |event_id, values|
        "#{location}: eventId #{event_id.inspect} is repeated" if values.length > 1
      end.compact
    end

    def fixture_path_errors(fixture, location, fixture_root)
      errors = []
      pattern = %r{\Apoc/[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*(?:\.[A-Za-z0-9_-]+)?\z}
      unless fixture.is_a?(String) && pattern.match?(fixture) && !fixture.split("/").include?("..")
        return ["#{location}: fixture path is not allowlisted"]
      end

      root = File.realpath(fixture_root)
      candidate = File.expand_path(fixture, fixture_root)
      unless candidate == root || candidate.start_with?("#{root}#{File::SEPARATOR}")
        return ["#{location}: fixture path escapes the fixture root"]
      end
      unless File.file?(candidate)
        errors << "#{location}: fixture #{fixture.inspect} does not exist"
        return errors
      end

      real_candidate = File.realpath(candidate)
      unless real_candidate.start_with?("#{root}#{File::SEPARATOR}")
        errors << "#{location}: fixture symlink escapes the fixture root"
      end
      errors
    rescue Errno::ENOENT => error
      ["#{location}: cannot resolve fixture root: #{error.message}"]
    end
  end

  class RunScope
    attr_reader :scenario_id, :base_contact_alias, :repetition, :run_id,
                :contact_alias, :contact_id, :session_isolation_key,
                :session_identifier

    def initialize(scenario_id:, base_contact_alias:, repetition:, run_id:)
      raise ArgumentError, "repetition must be positive" unless repetition.to_i.positive?
      raise ArgumentError, "run_id must not be blank" if run_id.to_s.strip.empty?

      @scenario_id = scenario_id
      @base_contact_alias = base_contact_alias
      @repetition = repetition.to_i
      @run_id = run_id.to_s
      @contact_alias = scoped_alias(base_contact_alias, "main")
      @contact_id = "poc:#{@contact_alias}"
      token = digest("session")
      @session_isolation_key = "corpus-session:#{scenario_id}:r#{@repetition}:#{token}"
      @session_identifier = "corpus-session-#{token}-r#{@repetition}"
    end

    def sentinel_alias(base_alias)
      scoped_alias(base_alias, "sentinel")
    end

    def sentinel_contact_id(base_alias)
      "poc:#{sentinel_alias(base_alias)}"
    end

    def event_id(logical_id, role: "main")
      suffix = "-r#{@repetition}-#{digest("event:#{role}:#{logical_id}")[0, 10]}"
      truncate("#{logical_id}#{suffix}", 128)
    end

    def endpoint_for(endpoint)
      source = "/api/poc/conversations/#{@base_contact_alias}/"
      target = "/api/poc/conversations/#{@contact_alias}/"
      raise ArgumentError, "endpoint is outside the scenario contact scope" unless endpoint.start_with?(source)

      endpoint.sub(source, target)
    end

    def identifiers(role: "main", base_alias: @base_contact_alias)
      alias_value = role == "main" ? @contact_alias : sentinel_alias(base_alias)
      {
        "contactAlias" => alias_value,
        "contactId" => "poc:#{alias_value}",
        "sessionIsolationKey" => "#{@session_isolation_key}:#{role}",
        "sessionIdentifier" => "#{@session_identifier}-#{role}",
        "observedSessionId" => nil,
        "sessionIdVerified" => false,
        "sessionIdEvidence" => "POC projection does not expose hermesSessionId"
      }
    end

    private

    def scoped_alias(base_alias, role)
      suffix = "-r#{@repetition}-#{digest("alias:#{role}")[0, 10]}"
      truncate("#{base_alias}#{suffix}", 64)
    end

    def digest(label)
      Digest::SHA256.hexdigest("#{@run_id}:#{@scenario_id}:#{@repetition}:#{label}")[0, 12]
    end

    def truncate(value, length)
      value.length <= length ? value : "#{value[0, length - 13]}-#{value[-12, 12]}"
    end
  end

  class HttpClient
    def initialize(token: ENV["POC_API_TOKEN"])
      @token = token.to_s.strip
    end

    def request_json(base_uri, method, path, payload = nil)
      uri = URI.join(base_uri.end_with?("/") ? base_uri : "#{base_uri}/", path.sub(%r{^/}, ""))
      request_class = { "GET" => Net::HTTP::Get, "POST" => Net::HTTP::Post }.fetch(method)
      request = request_class.new(uri)
      request["Content-Type"] = "application/json"
      request["Authorization"] = "Bearer #{@token}" unless @token.empty?
      request.body = JSON.generate(payload) if payload

      http = Net::HTTP.new(uri.host, uri.port)
      http.use_ssl = uri.scheme == "https"
      http.open_timeout = 5
      http.read_timeout = 60
      response = http.request(request)
      response_code = response.code.to_i
      unless response_code.between?(200, 299)
        raise NetworkError, "#{method} #{path} returned HTTP #{response_code}"
      end
      parsed = response.body.to_s.empty? ? {} : JSON.parse(response.body)
      [response_code, parsed]
    rescue JSON::ParserError => error
      raise NetworkError, "#{method} #{path} returned invalid JSON: #{error.message}"
    rescue NetworkError
      raise
    rescue StandardError => error
      raise NetworkError, "#{method} #{path} failed: #{error.class}: #{error.message}"
    end
  end

  module AssertionEvaluator
    module_function

    def evaluate(assertion, receipt:, projection:, event:, context:)
      key, expected = assertion.first
      supported, observed, reason = observe(key, expected, receipt, projection, event, context)
      passed = supported && matches?(key, expected, observed)
      result = {
        "assertion" => key,
        "expected" => expected,
        "observed" => observed,
        "passed" => passed,
        "status" => supported ? (passed ? "PASSED" : "FAILED") : "UNVERIFIED"
      }
      result["reason"] = reason if reason
      result
    end

    def observe(key, expected, receipt, projection, event, context)
      case key
      when "status"
        observed_value(receipt, "status", "receipt")
      when "output.nextAction"
        observed_value(receipt, "output.nextAction", "receipt")
      when "output.messageContains"
        text_value(receipt, "output.message", "receipt output.message")
      when "output.messageNotContains"
        text_value(receipt, "output.message", "receipt output.message")
      when "output.handoffReasonContains"
        text_value(receipt, "output.handoffReason", "receipt output.handoffReason")
      when "conversation.mode", "conversation.paymentStatus", "conversation.selectedService", "conversation.termsStatus"
        observed_value(projection, key, "conversation projection")
      when "factsContains"
        available, values = facts_with_evidence(projection)
        if available
          [true, values.map { |fact| fact["type"].to_s }, nil]
        else
          [false, nil, "conversation projection does not expose a facts array; success cannot be inferred"]
        end
      when "factsCount"
        available, values = facts_with_evidence(projection)
        available ? [true, values.length, nil] : [false, nil, "conversation projection does not expose a facts array; success cannot be inferred"]
      when "noIcpFacts"
        available, values = facts_with_evidence(projection)
        available ? [true, values.empty?, nil] : [false, nil, "conversation projection does not expose a facts array; success cannot be inferred"]
      when "briefingReleased"
        payment_supported, payment_status, payment_reason = observed_value(projection, "conversation.paymentStatus", "conversation projection")
        message_supported, response_message, message_reason = text_value(receipt, "output.message", "receipt output.message")
        return [false, nil, payment_reason] unless payment_supported
        return [false, nil, message_reason] unless message_supported

        released = payment_status == "CONFIRMED" && response_message.downcase.include?("briefing")
        [true, released, nil]
      when "noInventedCommercialClaims"
        supported, response_message, reason = text_value(receipt, "output.message", "receipt output.message")
        return [false, nil, reason] unless supported

        [true, !response_message.match?(/desconto|prazo|disponibilidade|r\$\s*\d+|7 dias|20%/i), nil]
      when "inboundPersisted"
        available, projection_messages = messages_with_evidence(projection)
        return [false, nil, "conversation projection does not expose a messages array; success cannot be inferred"] unless available

        [true, projection_messages.any? do |item|
          item["direction"] == "INBOUND" && item["eventId"] == context[:runtime_event_id]
        end, nil]
      when "outboundAiMessages"
        available, projection_messages = messages_with_evidence(projection)
        return [false, nil, "conversation projection does not expose a messages array; success cannot be inferred"] unless available

        previous_ids = messages(context[:previous_projection]).map { |item| item["id"] }.compact
        count = projection_messages.count do |item|
          item["direction"] == "OUTBOUND" && !previous_ids.include?(item["id"])
        end
        [true, count, nil]
      when "transcriptParticipates"
        available, projection_messages = messages_with_evidence(projection)
        return [false, nil, "conversation projection does not expose a messages array; success cannot be inferred"] unless available

        inbound = projection_messages.find do |item|
          item["direction"] == "INBOUND" && item["eventId"] == context[:runtime_event_id]
        end
        return [false, nil, "the inbound AUDIO message is not present in the projection"] unless inbound

        normalized_transcript = projected_transcript(inbound)
        return [false, nil, "the projection contains no normalized transcript evidence"] if normalized_transcript.empty?

        declared_transcript = event["transcript"].to_s.strip
        if declared_transcript.empty? && event["type"] == "AUDIO"
          [true, true, nil]
        elsif declared_transcript.empty?
          [false, nil, "the event does not declare a transcript and is not AUDIO"]
        else
          [true, normalized_transcript == declared_transcript, nil]
        end
      when "originalMediaRetained"
        available, projection_messages = messages_with_evidence(projection)
        return [false, nil, "conversation projection does not expose a messages array; success cannot be inferred"] unless available

        media = event["mediaFixture"].to_s
        [true, !media.empty? && projection_messages.any? do |item|
          item["direction"] == "INBOUND" && item["eventId"] == context[:runtime_event_id] && item["mediaRef"].to_s == media
        end, nil]
      when "imageDoesNotApprovePayment"
        supported, payment_status, reason = observed_value(projection, "conversation.paymentStatus", "conversation projection")
        return [false, nil, reason] unless supported

        [true, payment_status != "CONFIRMED", nil]
      when "paymentApprovalIsHumanOnly"
        payment_supported, payment_status, payment_reason = observed_value(projection, "conversation.paymentStatus", "conversation projection")
        next_action_supported, next_action, next_action_reason = observed_value(receipt, "output.nextAction", "receipt")
        return [false, nil, payment_reason] unless payment_supported
        return [false, nil, next_action_reason] unless next_action_supported

        observed = payment_status == "PROOF_RECEIVED"
        observed &&= next_action == "AWAIT_PAYMENT_APPROVAL"
        [true, observed, nil]
      when "approvalIsNotHermesTool"
        endpoint = context[:approvalEndpoint].to_s
        return [false, nil, "approval endpoint evidence is unavailable"] if endpoint.empty?

        [true, context[:phase] == "approval" && endpoint.include?("payment-proof/approve"), nil]
      when "noCommercialProgression"
        selected_supported, selected_service, selected_reason = observed_value(projection, "conversation.selectedService", "conversation projection")
        terms_supported, terms_status, terms_reason = observed_value(projection, "conversation.termsStatus", "conversation projection")
        payment_supported, payment_status, payment_reason = observed_value(projection, "conversation.paymentStatus", "conversation projection")
        facts_available, projection_facts = facts_with_evidence(projection)
        return [false, nil, selected_reason] unless selected_supported
        return [false, nil, terms_reason] unless terms_supported
        return [false, nil, payment_reason] unless payment_supported
        return [false, nil, "conversation projection does not expose a facts array; success cannot be inferred"] unless facts_available

        observed = selected_service.nil?
        observed &&= terms_status == "NOT_PRESENTED"
        observed &&= payment_status == "NOT_STARTED"
        observed &&= projection_facts.empty?
        [true, observed, nil]
      when "correctionUsesLatestValue"
        facts_available, projection_facts = facts_with_evidence(projection)
        selected_supported, selected, selected_reason = observed_value(projection, "conversation.selectedService", "conversation projection")
        return [false, nil, "conversation projection does not expose a facts array; success cannot be inferred"] unless facts_available
        return [false, nil, selected_reason] unless selected_supported

        current = projection_facts.select { |fact| fact["type"] == "SELECTED_SERVICE" && fact["supersededBy"].nil? }
        [true, !selected.nil? && current.any? { |fact| fact["value"] == selected }, nil]
      when "supersededFactRetained"
        facts_available, projection_facts = facts_with_evidence(projection)
        return [false, nil, "conversation projection does not expose a facts array; success cannot be inferred"] unless facts_available

        grouped = projection_facts.group_by { |fact| fact["type"] }
        retained = projection_facts.any? { |fact| !fact["supersededBy"].nil? } ||
          grouped.values.any? { |items| items.map { |item| item["value"] }.uniq.length > 1 }
        [true, retained, nil]
      when "tool"
        available, invocations = tool_invocations_with_evidence(projection)
        return [false, nil, "conversation projection does not expose tool invocation evidence; success cannot be inferred"] unless available

        [true, invocations.map { |item| item["toolName"].to_s }, nil]
      when "toolsExecuted"
        available, invocations = tool_invocations_with_evidence(projection)
        previous_available, previous_invocations = tool_invocations_with_evidence(context[:previous_projection])
        return [false, nil, "conversation projection does not expose tool invocation evidence; success cannot be inferred"] unless available
        return [false, nil, "previous projection does not expose tool invocation evidence; success cannot be inferred"] unless previous_available

        [true, [invocations.length - previous_invocations.length, 0].max, nil]
      when "hermesChatCalls"
        available, current = observed_value(projection, "hermesChatCalls", "conversation projection")
        previous_available, previous = observed_value(context[:previous_projection], "hermesChatCalls", "previous conversation projection")
        return [false, nil, "conversation projection does not expose hermesChatCalls; success cannot be inferred"] unless available

        previous = 0 unless previous_available
        return [false, nil, "hermesChatCalls is not numeric; success cannot be inferred"] unless current.is_a?(Numeric) && previous.is_a?(Numeric)

        [true, [current - previous, 0].max, nil]
      when "lightProbesUsed", "commercialOpportunityCreated"
        observed_value(projection, key, "conversation projection")
      when "batched"
        batch_ids = Array(context[:batch_logical_ids])
        [true, batch_ids.length > 1, nil]
      when "sameBatchAs"
        batch_ids = Array(context[:batch_logical_ids])
        [true, batch_ids, nil]
      when "batchedWindowSeconds"
        window = context[:batch_window_seconds]
        return [false, nil, "batch window evidence is unavailable"] if window.nil?

        [true, window, nil]
      when "immediate"
        [true, context[:immediate] == true, nil]
      else
        [false, nil, "no API evidence is available to verify #{key.inspect}; success cannot be inferred"]
      end
    end

    def nested(value, path)
      path.split(".").reduce(value) do |current, key|
        current.is_a?(Hash) ? current[key] : nil
      end
    end

    def matches?(key, expected, observed)
      case key
      when "output.messageContains", "output.handoffReasonContains"
        observed.is_a?(String) && observed.include?(expected.to_s)
      when "output.messageNotContains"
        observed.is_a?(String) && !observed.include?(expected.to_s)
      when "factsContains"
        Array(observed).include?(expected.to_s)
      when "tool", "sameBatchAs"
        Array(observed).include?(expected.to_s)
      else
        observed == expected
      end
    end

    def observed_value(value, path, source)
      available, observed = value_at(value, path)
      return [false, nil, "#{source} does not expose #{path}; success cannot be inferred"] unless available

      [true, observed, nil]
    end

    def text_value(value, path, source)
      supported, observed, reason = observed_value(value, path, source)
      return [false, nil, reason] unless supported
      return [false, nil, "#{source} #{path} is blank; success cannot be inferred"] if observed.to_s.empty?

      [true, observed.to_s, nil]
    end

    def value_at(value, path)
      current = value
      path.split(".").each do |key|
        return [false, nil] unless current.is_a?(Hash) && current.key?(key)

        current = current[key]
      end
      [true, current]
    end

    def message(receipt)
      nested(receipt, "output.message").to_s
    end

    def facts(projection)
      Array(projection.is_a?(Hash) ? projection["facts"] : nil).select { |fact| fact.is_a?(Hash) }
    end

    def facts_with_evidence(projection)
      return [false, []] unless projection.is_a?(Hash) && projection.key?("facts") && projection["facts"].is_a?(Array)

      [true, facts(projection)]
    end

    def messages(projection)
      Array(projection.is_a?(Hash) ? projection["messages"] : nil).select { |item| item.is_a?(Hash) }
    end

    def messages_with_evidence(projection)
      return [false, []] unless projection.is_a?(Hash) && projection.key?("messages") && projection["messages"].is_a?(Array)

      [true, messages(projection)]
    end

    def tool_invocations(projection)
      Array(projection.is_a?(Hash) ? projection["toolInvocations"] : nil)
        .select { |item| item.is_a?(Hash) }
    end

    def tool_invocations_with_evidence(projection)
      return [false, []] unless projection.is_a?(Hash) && projection.key?("toolInvocations") && projection["toolInvocations"].is_a?(Array)

      [true, tool_invocations(projection)]
    end

    def projected_transcript(message)
      value = message["text"]
      value = message["transcript"] if value.to_s.strip.empty?
      value.to_s.strip
    end
  end

  class ScenarioRunner
    def initialize(client: HttpClient.new, clock: -> { Time.now.utc })
      @client = client
      @clock = clock
    end

    def run(scenario_path:, repetition: 1, run_id: nil, base_url: "http://127.0.0.1:8081",
            output: nil, memory_seed_mode: "verify-only")
      started = Process.clock_gettime(Process::CLOCK_MONOTONIC)
      effective_run_id = run_id || generated_run_id
      result = {
        "scenario" => nil,
        "runId" => effective_run_id,
        "repetition" => repetition.to_i,
        "passed" => false,
        "environmentError" => nil,
        "identifiers" => nil,
        "sentinelIdentifiers" => nil,
        "memorySeedMode" => memory_seed_mode,
        "memorySetup" => {},
        "events" => [],
        "assertions" => [],
        "violations" => [],
        "manualAssertions" => [],
        "durationMs" => nil,
        "usage" => { "inputTokens" => nil, "outputTokens" => nil, "totalTokens" => nil, "estimatedCost" => nil },
        "humanEvaluation" => { "naturalness" => nil, "clarity" => nil, "usefulness" => nil }
      }
      scenario = nil
      scope = nil
      planned = []
      seen_paths = []

      begin
        raise ArgumentError, "unsupported memory seed mode #{memory_seed_mode.inspect}" unless MEMORY_SEED_MODES.include?(memory_seed_mode)
        scenario = ScenarioLoader.load(scenario_path)
        scope = RunScope.new(
          scenario_id: scenario.fetch("id"),
          base_contact_alias: scenario.fetch("contactAlias"),
          repetition: repetition,
          run_id: effective_run_id
        )
        result["scenario"] = scenario.fetch("id")
        result["identifiers"] = scope.identifiers
        result["sentinelIdentifiers"] = if scenario["isolationSentinel"]
                                            scope.identifiers(
                                              role: "sentinel",
                                              base_alias: scenario.fetch("isolationSentinel").fetch("contactAlias")
                                            )
                                          end
        planned = planned_assertions(scenario)

        main_projection = nil
        root_memory = prepare_memory(
          scenario["memory"],
          scope: scope,
          role: "main",
          contact_alias: scope.contact_alias,
          base_url: base_url,
          result: result,
          seen_paths: seen_paths,
          planned: planned,
          prefix: "memory"
        )
        main_projection = root_memory[:projection]

        main_events = process_events(
          scenario.fetch("events"),
          scope: scope,
          role: "main",
          contact_alias: scope.contact_alias,
          base_url: base_url,
          result: result,
          seen_paths: seen_paths,
          prefix: "events",
          previous_projection: root_memory[:projection]
        )
        main_projection = main_events[:projection] || main_projection

        if scenario["approval"]
          main_projection = process_approval(
            scenario.fetch("approval"),
            scope: scope,
            contact_alias: scope.contact_alias,
            base_url: base_url,
            result: result,
            seen_paths: seen_paths,
            prefix: "approval",
            previous_projection: main_projection
          )
        end

        sentinel = scenario["isolationSentinel"]
        if sentinel
          sentinel_memory = prepare_memory(
            sentinel["memory"],
            scope: scope,
            role: "sentinel",
            contact_alias: scope.sentinel_alias(sentinel.fetch("contactAlias")),
            base_url: base_url,
            result: result,
            seen_paths: seen_paths,
            planned: planned,
            prefix: "isolationSentinel.memory"
          )
          sentinel_events = process_events(
            sentinel.fetch("events"),
            scope: scope,
            role: "sentinel",
            contact_alias: scope.sentinel_alias(sentinel.fetch("contactAlias")),
            base_url: base_url,
            result: result,
            seen_paths: seen_paths,
            prefix: "isolationSentinel.events",
            previous_projection: sentinel_memory[:projection]
          )
          sentinel_projection = sentinel_events[:projection] || sentinel_memory[:projection]
          add_isolation_assertion(
            main_projection,
            sentinel_projection,
            scenario,
            result,
            seen_paths
          )
        end
      rescue StandardError => error
        result["environmentError"] = format_error(error)
      ensure
        append_pending_assertions(result, planned, seen_paths, scope)
        result["violations"] = result.fetch("assertions").reject { |item| item["passed"] == true }
        result["manualAssertions"] = result.fetch("assertions").select { |item| item["status"] == "UNVERIFIED" }
        result["passed"] = result["environmentError"].nil? && result.fetch("assertions").all? { |item| item["passed"] == true }
        result["durationMs"] = ((Process.clock_gettime(Process::CLOCK_MONOTONIC) - started) * 1000).round
        File.write(output, JSON.pretty_generate(result)) if output
      end

      result
    end

    private

    def generated_run_id
      "run-#{Time.now.utc.strftime('%Y%m%dT%H%M%SZ')}-#{SecureRandom.hex(6)}"
    end

    def format_error(error)
      return error.message if error.is_a?(NetworkError) || error.is_a?(ValidationError)

      "#{error.class}: #{error.message}"
    end

    def request_with_evidence(base_url, method, path, payload, kind:)
      code, body = @client.request_json(base_url, method, path, payload)
      response_code = Integer(code)
      unless response_code.between?(200, 299)
        raise NetworkError, "#{method} #{path} returned HTTP #{response_code}"
      end

      case kind
      when :receipt
        validate_receipt_payload(body, method, path)
      when :receipts
        validate_receipts_payload(body, method, path)
      when :projection
        validate_projection_payload(body, method, path)
      else
        raise ArgumentError, "unsupported evidence kind #{kind.inspect}"
      end
      [response_code, body]
    rescue NetworkError
      raise
    rescue StandardError => error
      raise NetworkError, "#{method} #{path} failed: #{error.class}: #{error.message}"
    end

    def validate_receipt_payload(body, method, path)
      return if body.is_a?(Hash) && present_value?(body["eventId"]) && present_value?(body["correlationId"]) &&
                present_value?(body["status"])

      raise NetworkError, "#{method} #{path} returned payload without minimum evidence (eventId, correlationId and status)"
    end

    def validate_receipts_payload(body, method, path)
      return if body.is_a?(Array) && !body.empty? && body.all? do |receipt|
        receipt.is_a?(Hash) && present_value?(receipt["eventId"]) &&
          present_value?(receipt["correlationId"]) && present_value?(receipt["status"])
      end

      raise NetworkError, "#{method} #{path} returned payload without a non-empty receipt list with minimum evidence"
    end

    def validate_projection_payload(body, method, path)
      valid = body.is_a?(Hash) && body["conversation"].is_a?(Hash) && body["facts"].is_a?(Array) &&
        body["messages"].is_a?(Array)
      return if valid

      raise NetworkError, "#{method} #{path} returned payload without minimum evidence (conversation, facts and messages)"
    end

    def present_value?(value)
      !value.nil? && (!value.respond_to?(:empty?) || !value.empty?)
    end

    def planned_assertions(scenario)
      entries = []
      add_memory_entries(entries, scenario["memory"], "memory")
      add_event_entries(entries, scenario["events"], "events", "main")
      if scenario["approval"]
        Array(scenario["approval"]["assertions"]).each_with_index do |assertion, index|
          key, expected = assertion.first
          entries << { path: "approval.assertions[#{index}]", key: key, expected: expected,
                       logical_event_id: "approval", role: "approval", phase: "approval" }
        end
      end
      sentinel = scenario["isolationSentinel"]
      if sentinel
        add_memory_entries(entries, sentinel["memory"], "isolationSentinel.memory")
        add_event_entries(entries, sentinel["events"], "isolationSentinel.events", "sentinel")
        entries << { path: "isolationSentinel.noCrossContactFacts", key: "noCrossContactFacts",
                     expected: true, logical_event_id: nil, role: "sentinel", phase: "isolation" }
      end
      entries
    end

    def add_memory_entries(entries, memory, prefix)
      return unless memory.is_a?(Hash)

      Array(memory["facts"]).each_with_index do |fact, index|
        entries << { path: "#{prefix}.facts[#{index}]", key: "#{prefix}.facts[#{index}]",
                     expected: fact, logical_event_id: nil, role: prefix.include?("isolation") ? "sentinel" : "main",
                     phase: "memory" }
      end
      add_event_entries(entries, memory["setupEvents"], "#{prefix}.setupEvents",
                        prefix.include?("isolation") ? "sentinel" : "main")
    end

    def add_event_entries(entries, events, prefix, role)
      Array(events).each_with_index do |event, event_index|
        Array(event["assertions"]).each_with_index do |assertion, assertion_index|
          key, expected = assertion.first
          entries << { path: "#{prefix}[#{event_index}].assertions[#{assertion_index}]", key: key,
                       expected: expected, logical_event_id: event["eventId"], role: role,
                       phase: prefix.include?("setupEvents") ? "memorySetup" : "event" }
        end
      end
    end

    def prepare_memory(memory, scope:, role:, contact_alias:, base_url:, result:, seen_paths:, planned:, prefix:)
      return { projection: nil, setupEventsExecuted: false } unless memory.is_a?(Hash)

      facts = Array(memory["facts"])
      setup_events = Array(memory["setupEvents"])
      setup_executed = false
      projection = nil
      if !facts.empty? && MEMORY_SEED_MODES.include?(result["memorySeedMode"]) &&
         result["memorySeedMode"] == "setup-events" && !setup_events.empty?
        sequence = process_events(
          setup_events,
          scope: scope,
          role: role,
          contact_alias: contact_alias,
          base_url: base_url,
          result: result,
          seen_paths: seen_paths,
          prefix: "#{prefix}.setupEvents",
          previous_projection: nil
        )
        projection = sequence[:projection]
        setup_executed = true
      end

      facts.each_with_index do |expected, index|
        path = "#{prefix}.facts[#{index}]"
        if setup_executed && projection
          observed = Array(projection["facts"]).find do |fact|
            fact.is_a?(Hash) && fact["type"].to_s == expected["type"].to_s &&
              fact["value"].to_s == expected["value"].to_s &&
              fact["confidence"].to_s == expected["confidence"].to_s
          end
          record_raw(
            result,
            seen_paths,
            path: path,
            assertion: path,
            expected: expected,
            observed: observed,
            passed: !observed.nil?,
            status: observed ? "PASSED" : "FAILED",
            reason: observed ? nil : "memory setup responded but the expected fact was not present",
            phase: "memory",
            logical_event_id: nil,
            runtime_event_id: nil
          )
        else
          mode_reason = if setup_events.empty?
                          "POC API exposes no memory seed endpoint and scenario declares no setupEvents"
                        else
                          "memory setupEvents were not executed in #{result['memorySeedMode']} mode"
                        end
          record_unverified(
            result,
            seen_paths,
            path: path,
            assertion: path,
            expected: expected,
            reason: "#{mode_reason}; use --memory-seed-mode setup-events only with declarative setupEvents",
            phase: "memory",
            logical_event_id: nil,
            runtime_event_id: nil
          )
        end
      end

      unless setup_executed
        Array(memory["setupEvents"]).each_with_index do |event, event_index|
          Array(event["assertions"]).each_with_index do |_assertion, assertion_index|
            path = "#{prefix}.setupEvents[#{event_index}].assertions[#{assertion_index}]"
            entry = planned.find { |item| item[:path] == path }
            record_unverified_entry(result, seen_paths, entry, scope, "setupEvents were not executed in #{result['memorySeedMode']} mode") if entry
          end
        end
      end
      result["memorySetup"][role] = {
        "declared" => true,
        "factsDeclared" => facts.length,
        "setupEventsDeclared" => setup_events.length,
        "setupEventsExecuted" => setup_executed,
        "seedEndpointAvailable" => false
      }
      { projection: projection, setupEventsExecuted: setup_executed }
    end

    def process_events(events, scope:, role:, contact_alias:, base_url:, result:, seen_paths:, prefix:, previous_projection:)
      projection = previous_projection
      phase = prefix.include?("setupEvents") ? "memorySetup" : "event"
      event_index = 0
      batch_groups(Array(events)).each do |batch|
        replay_anchor = @clock.call
        source_anchor = Time.iso8601(iso_time(batch.first.fetch("occurredAt")))
        prepared = batch.map do |event|
          logical_event_id = event.fetch("eventId")
          runtime_event_id = scope.event_id(logical_event_id, role: role)
          payload = event.slice("eventId", "type", "text", "transcript", "mediaFixture", "interactiveReplyId", "occurredAt")
          payload["eventId"] = runtime_event_id
          payload["occurredAt"] = replay_timestamp(event, source_anchor, replay_anchor)
          { event: event, logical_event_id: logical_event_id, runtime_event_id: runtime_event_id, payload: payload }
        end

        endpoint = "/api/poc/conversations/#{contact_alias}/messages"
        posted = prepared.map do |item|
          code, receipt = request_with_evidence(base_url, "POST", endpoint, item[:payload], kind: :receipt)
          item.merge(post_http_status: code, ingress_receipt: receipt)
        end
        final_code = posted.last.fetch(:post_http_status)
        receipt = posted.last.fetch(:ingress_receipt)
        if posted.any? { |item| item[:ingress_receipt]["status"] == "QUEUED" }
          final_code, released = request_with_evidence(
            base_url,
            "POST",
            "/api/poc/conversations/#{contact_alias}/flush",
            nil,
            kind: :receipts
          )
          receipt = released.last
        end

        _, current_projection = request_with_evidence(
          base_url,
          "GET",
          "/api/poc/conversations/#{contact_alias}",
          nil,
          kind: :projection
        )
        batch_logical_ids = prepared.map { |item| item[:logical_event_id] }
        batch_runtime_ids = prepared.map { |item| item[:runtime_event_id] }
        prepared.each do |item|
          event = item[:event]
          logical_event_id = item[:logical_event_id]
          runtime_event_id = item[:runtime_event_id]
          result["events"] << {
            "phase" => phase,
            "logicalEventId" => logical_event_id,
            "eventId" => runtime_event_id,
            "contactAlias" => contact_alias,
            "httpStatus" => final_code,
            "postHttpStatus" => item[:post_http_status],
            "ingressReceipt" => item[:ingress_receipt],
            "receipt" => receipt,
            "batchLogicalEventIds" => batch_logical_ids,
            "batchEventIds" => batch_runtime_ids,
            "projection" => current_projection
          }
          Array(event["assertions"]).each_with_index do |assertion, assertion_index|
            path = "#{prefix}[#{event_index}].assertions[#{assertion_index}]"
            evaluated = AssertionEvaluator.evaluate(
              assertion,
              receipt: receipt,
              projection: current_projection,
              event: event,
              context: {
                phase: phase,
                runtime_event_id: runtime_event_id,
                previous_projection: projection,
                batch_logical_ids: batch_logical_ids,
                batch_runtime_ids: batch_runtime_ids,
                batch_window_seconds: 4,
                immediate: batch.length == 1 && immediate_event?(event)
              }
            )
            record_raw(
              result,
              seen_paths,
              path: path,
              assertion: evaluated["assertion"],
              expected: evaluated["expected"],
              observed: evaluated["observed"],
              passed: evaluated["passed"],
              status: evaluated["status"],
              reason: evaluated["reason"],
              phase: phase,
              logical_event_id: logical_event_id,
              runtime_event_id: runtime_event_id
            )
          end
          event_index += 1
        end
        observe_session(result, current_projection, role)
        projection = current_projection
      end
      { projection: projection }
    end

    def batch_groups(events)
      groups = []
      current = []
      events.each do |event|
        if current.empty?
          current << event
          next
        end

        if immediate_event?(event) || immediate_event?(current.last) || !joinable_events?(current.first, current.last, event)
          groups << current
          current = [event]
        else
          current << event
        end
      end
      groups << current unless current.empty?
      groups
    end

    def immediate_event?(event)
      %w[INTERACTIVE PAYMENT_PROOF].include?(event["type"].to_s) ||
        event["isPaymentProof"] == true
    end

    def joinable_events?(first, last, candidate)
      first_at = Time.iso8601(iso_time(first.fetch("occurredAt")))
      last_at = Time.iso8601(iso_time(last.fetch("occurredAt")))
      candidate_at = Time.iso8601(iso_time(candidate.fetch("occurredAt")))
      candidate_at >= first_at &&
        candidate_at <= last_at + 4 &&
        candidate_at <= first_at + 10
    end

    def process_approval(approval, scope:, contact_alias:, base_url:, result:, seen_paths:, prefix:, previous_projection:)
      endpoint = scope.endpoint_for(approval.fetch("endpoint"))
      code, receipt = request_with_evidence(base_url, "POST", endpoint, nil, kind: :receipt)
      _, projection = request_with_evidence(
        base_url,
        "GET",
        "/api/poc/conversations/#{contact_alias}",
        nil,
        kind: :projection
      )
      result["events"] << {
        "phase" => "approval",
        "logicalEventId" => "approval",
        "eventId" => scope.event_id("approval", role: "approval"),
        "contactAlias" => contact_alias,
        "httpStatus" => code,
        "receipt" => receipt,
        "projection" => projection
      }
      Array(approval["assertions"]).each_with_index do |assertion, index|
        path = "approval.assertions[#{index}]"
        evaluated = AssertionEvaluator.evaluate(
          assertion,
          receipt: receipt,
          projection: projection,
          event: { "eventId" => "approval", "type" => "APPROVAL" },
          context: {
            phase: "approval",
            approvalEndpoint: endpoint,
            runtime_event_id: scope.event_id("approval", role: "approval"),
            previous_projection: previous_projection
          }
        )
        record_raw(
          result,
          seen_paths,
          path: path,
          assertion: evaluated["assertion"],
          expected: evaluated["expected"],
          observed: evaluated["observed"],
          passed: evaluated["passed"],
          status: evaluated["status"],
          reason: evaluated["reason"],
          phase: "approval",
          logical_event_id: "approval",
          runtime_event_id: scope.event_id("approval", role: "approval")
        )
      end
      observe_session(result, projection, "main")
      projection
    end

    def add_isolation_assertion(main_projection, sentinel_projection, scenario, result, seen_paths)
      path = "isolationSentinel.noCrossContactFacts"
      root_facts = Array(scenario["memory"].is_a?(Hash) ? scenario["memory"]["facts"] : nil)
      sentinel = scenario["isolationSentinel"]
      sentinel_facts = Array(sentinel["memory"].is_a?(Hash) ? sentinel["memory"]["facts"] : nil)
      if main_projection && sentinel_projection
        main_values = Array(main_projection["facts"]).map { |fact| [fact["type"], fact["value"]] }
        sentinel_values = Array(sentinel_projection["facts"]).map { |fact| [fact["type"], fact["value"]] }
        root_values = root_facts.map { |fact| [fact["type"], fact["value"]] }
        expected_sentinel_values = sentinel_facts.map { |fact| [fact["type"], fact["value"]] }
        leaked = sentinel_values.any? { |value| root_values.include?(value) } ||
          main_values.any? { |value| expected_sentinel_values.include?(value) }
        record_raw(
          result,
          seen_paths,
          path: path,
          assertion: "noCrossContactFacts",
          expected: true,
          observed: !leaked,
          passed: !leaked,
          status: leaked ? "FAILED" : "PASSED",
          reason: leaked ? "memory value crossed the contact boundary" : nil,
          phase: "isolation",
          logical_event_id: nil,
          runtime_event_id: nil
        )
      else
        record_unverified(
          result,
          seen_paths,
          path: path,
          assertion: "noCrossContactFacts",
          expected: true,
          reason: "one or both contact projections were unavailable; isolation cannot be verified",
          phase: "isolation",
          logical_event_id: nil,
          runtime_event_id: nil
        )
      end
    end

    def observe_session(result, projection, role)
      return unless projection.is_a?(Hash)

      observed = projection["sessionId"] || projection["hermesSessionId"] ||
        projection.dig("conversation", "sessionId") || projection.dig("conversation", "hermesSessionId")
      return if observed.nil?

      target = role == "sentinel" ? result["sentinelIdentifiers"] : result["identifiers"]
      return unless target

      target["observedSessionId"] = observed
      target["sessionIdVerified"] = true
      target["sessionIdEvidence"] = "returned by the POC projection"
    end

    def iso_time(value)
      return value.iso8601 if value.respond_to?(:iso8601)

      value.to_s
    end

    def replay_timestamp(event, source_anchor, replay_anchor)
      source_time = Time.iso8601(iso_time(event.fetch("occurredAt")))
      (replay_anchor + (source_time - source_anchor)).utc.iso8601
    end

    def record_unverified_entry(result, seen_paths, entry, scope, reason)
      return if seen_paths.include?(entry[:path])

      runtime_event_id = if entry[:logical_event_id] && scope
                           scope.event_id(entry[:logical_event_id], role: entry[:role])
                         end
      record_unverified(
        result,
        seen_paths,
        path: entry[:path],
        assertion: entry[:key],
        expected: entry[:expected],
        reason: reason,
        phase: entry[:phase],
        logical_event_id: entry[:logical_event_id],
        runtime_event_id: runtime_event_id
      )
    end

    def record_unverified(result, seen_paths, path:, assertion:, expected:, reason:, phase:, logical_event_id:, runtime_event_id:)
      record_raw(
        result,
        seen_paths,
        path: path,
        assertion: assertion,
        expected: expected,
        observed: nil,
        passed: false,
        status: "UNVERIFIED",
        reason: reason,
        phase: phase,
        logical_event_id: logical_event_id,
        runtime_event_id: runtime_event_id
      )
    end

    def record_raw(result, seen_paths, path:, assertion:, expected:, observed:, passed:, status:, reason:, phase:, logical_event_id:, runtime_event_id:)
      return if seen_paths.include?(path)

      item = {
        "path" => path,
        "phase" => phase,
        "eventId" => logical_event_id,
        "runtimeEventId" => runtime_event_id,
        "assertion" => assertion,
        "expected" => expected,
        "observed" => observed,
        "passed" => passed == true,
        "status" => status
      }
      item["reason"] = reason if reason
      result["assertions"] << item
      seen_paths << path
    end

    def append_pending_assertions(result, planned, seen_paths, scope)
      planned.each do |entry|
        record_unverified_entry(
          result,
          seen_paths,
          entry,
          scope,
          "execution stopped before this assertion could be checked"
        )
      end
    end
  end

  module Report
    module_function

    SCENARIO_ROSTER = %w[
      01-happy-first-contact
      02-confused-customer
      03-human-handoff
      04-non-prospect
      05-returning-customer
      06-multimodal
    ].freeze
    REQUIRED_REPETITIONS = 3
    HUMAN_EVALUATION_FIELDS = %w[naturalness clarity usefulness].freeze

    def aggregate(input, output)
      records = File.readlines(input, chomp: true).reject(&:empty?).map { |line| JSON.parse(line) }
      assertion_results = records.flat_map { |record| Array(record["assertions"]) }
      passed = records.count { |record| record["passed"] == true }
      assertion_summary = {
        "total" => assertion_results.length,
        "passed" => assertion_results.count { |item| item["passed"] == true },
        "failed" => assertion_results.count { |item| item["passed"] == false && item["status"] == "FAILED" },
        "unverified" => assertion_results.count { |item| item["status"] == "UNVERIFIED" }
      }
      scenario_roster = evaluate_scenario_roster(records)
      repetitions = evaluate_repetitions(records)
      human_evaluation = evaluate_human_evaluation(records)
      assertion_gate = {
        "passed" => records.all? { |record| record["passed"] == true } && assertion_summary["unverified"].zero?,
        "unverified" => assertion_summary["unverified"],
        "failed" => assertion_summary["failed"]
      }
      gate_failures = []
      gate_failures << "scenario roster is incomplete or contains unexpected scenarios" unless scenario_roster["passed"]
      gate_failures << "each scenario must have three distinct repetitions" unless repetitions["passed"]
      gate_failures << "one or more executions or assertions failed" unless assertion_gate["passed"]
      gate_failures << "human evaluation average is below 4 or contains invalid scores" unless human_evaluation["passed"]
      summary = {
        "generatedAt" => Time.now.utc.iso8601,
        "totalExecutions" => records.length,
        "passedExecutions" => passed,
        "failedExecutions" => records.length - passed,
        "passRate" => records.empty? ? 0.0 : (passed.to_f / records.length).round(4),
        "requiredScenarioRoster" => SCENARIO_ROSTER,
        "requiredRepetitions" => REQUIRED_REPETITIONS,
        "assertionSummary" => assertion_summary,
        "records" => records,
        "gate" => {
          "commercialBarriersAndHandoff" => "review failed assertion results",
          "memoryIsolation" => "review isolationSentinel assertion results",
          "humanEvaluationMinimum" => 4,
          "manualReviewRequired" => assertion_summary["unverified"].positive?,
          "scenarioRoster" => scenario_roster,
          "repetitions" => repetitions,
          "assertions" => assertion_gate,
          "humanEvaluation" => human_evaluation,
          "passed" => gate_failures.empty?,
          "failures" => gate_failures
        }
      }
      File.write(output, JSON.pretty_generate(summary))
      puts "executions=#{records.length} passed=#{passed} failed=#{records.length - passed} unverified_assertions=#{assertion_summary['unverified']}"
      summary
    end

    def evaluate_scenario_roster(records)
      observed = records.map { |record| record["scenario"] }.compact.uniq.sort
      missing = (SCENARIO_ROSTER - observed).sort
      unexpected = (observed - SCENARIO_ROSTER).sort
      invalid_records = records.count { |record| !SCENARIO_ROSTER.include?(record["scenario"]) }
      {
        "required" => SCENARIO_ROSTER,
        "observed" => observed,
        "missing" => missing,
        "unexpected" => unexpected,
        "invalidRecords" => invalid_records,
        "passed" => missing.empty? && unexpected.empty? && invalid_records.zero?
      }
    end

    def evaluate_repetitions(records)
      by_scenario = records.group_by { |record| record["scenario"] }
      scenarios = SCENARIO_ROSTER.to_h do |scenario|
        repetitions = Array(by_scenario[scenario]).map { |record| record["repetition"] }
        distinct = repetitions.compact.uniq
        counts = repetitions.each_with_object(Hash.new(0)) { |value, tally| tally[value] += 1 }
        duplicates = counts.select { |_value, count| count > 1 }.keys
        [
          scenario,
          {
            "observed" => repetitions,
            "distinct" => distinct,
            "duplicates" => duplicates,
            "passed" => repetitions.length == REQUIRED_REPETITIONS &&
              distinct.length == REQUIRED_REPETITIONS && duplicates.empty?
          }
        ]
      end
      {
        "required" => REQUIRED_REPETITIONS,
        "scenarios" => scenarios,
        "passed" => scenarios.values.all? { |value| value["passed"] }
      }
    end

    def evaluate_human_evaluation(records)
      scores = []
      invalid = []
      records.each_with_index do |record, index|
        evaluation = record["humanEvaluation"]
        next if evaluation.nil?
        unless evaluation.is_a?(Hash)
          invalid << "records[#{index}].humanEvaluation"
          next
        end

        HUMAN_EVALUATION_FIELDS.each do |field|
          next unless evaluation.key?(field)

          value = evaluation[field]
          next if value.nil?
          if value.is_a?(Numeric) && value >= 1 && value <= 5
            scores << value
          else
            invalid << "records[#{index}].humanEvaluation.#{field}"
          end
        end
      end
      evaluated = !scores.empty?
      average = evaluated ? (scores.sum.to_f / scores.length).round(4) : nil
      {
        "evaluated" => evaluated,
        "scoresProvided" => scores.length,
        "average" => average,
        "minimum" => 4,
        "invalid" => invalid,
        "passed" => invalid.empty? && (!evaluated || average >= 4)
      }
    end
  end
end
