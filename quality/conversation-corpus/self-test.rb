#!/usr/bin/env ruby
# frozen_string_literal: true

require "json"
require "minitest/autorun"
require "tempfile"
require "yaml"

require_relative "runner"

class CorpusSelfTest < Minitest::Test
  ROOT = File.expand_path(__dir__)
  SCENARIOS = Dir[File.join(ROOT, "scenarios", "*.yml")].sort.freeze
  REQUIRED_SCENARIOS = %w[
    01-happy-first-contact
    02-confused-customer
    03-human-handoff
    04-non-prospect
    05-returning-customer
    06-multimodal
  ].freeze

  def test_every_checked_in_scenario_is_valid_and_its_fixtures_are_available
    refute_empty SCENARIOS

    SCENARIOS.each do |path|
      scenario = Corpus::ScenarioLoader.load(path)
      assert_match(/\A[0-9]{2}-/, scenario.fetch("id"), path)
    end
  end

  def test_schema_rejects_unexpected_root_properties
    scenario = YAML.safe_load(File.read(SCENARIOS.first), permitted_classes: [Date, Time], aliases: false)
    scenario["unexpected"] = true
    path = Tempfile.new(["invalid-scenario", ".yml"])
    path.write(YAML.dump(scenario))
    path.close

    error = assert_raises(Corpus::ValidationError) { Corpus::ScenarioLoader.load(path.path) }
    assert_includes error.message, "additional property"
  ensure
    path&.unlink
  end

  def test_run_scope_derives_contact_event_and_session_keys_per_repetition
    first = Corpus::RunScope.new(
      scenario_id: "01-happy-first-contact",
      base_contact_alias: "happy-ana",
      repetition: 1,
      run_id: "test-run"
    )
    second = Corpus::RunScope.new(
      scenario_id: "01-happy-first-contact",
      base_contact_alias: "happy-ana",
      repetition: 2,
      run_id: "test-run"
    )

    refute_equal first.contact_id, second.contact_id
    refute_equal first.event_id("happy-01"), second.event_id("happy-01")
    refute_equal first.session_isolation_key, second.session_isolation_key
    assert_equal "poc:#{first.contact_alias}", first.contact_id
    refute_equal "happy-01", first.event_id("happy-01")
  end

  def test_unknown_assertion_is_recorded_as_unverified_failure
    result = Corpus::AssertionEvaluator.evaluate(
      { "unknownAssertion" => true },
      receipt: {},
      projection: {},
      event: { "eventId" => "event-1" },
      context: {}
    )

    assert_equal false, result.fetch("passed")
    assert_equal "UNVERIFIED", result.fetch("status")
    assert_includes result.fetch("reason"), "evidence"
  end

  def test_runtime_assertions_use_safe_projection_and_batch_evidence
    previous_projection = {
      "toolInvocations" => [
        { "toolName" => "request_human_handoff", "status" => "SUCCEEDED", "resultCode" => "HANDOFF_REQUESTED" }
      ],
      "hermesChatCalls" => 1
    }
    projection = {
      "conversation" => {},
      "facts" => [],
      "messages" => [],
      "toolInvocations" => previous_projection["toolInvocations"],
      "hermesChatCalls" => 1,
      "lightProbesUsed" => 1,
      "commercialOpportunityCreated" => false
    }
    context = {
      batch_logical_ids: %w[fragment-1 fragment-2],
      batch_runtime_ids: %w[runtime-1 runtime-2],
      batch_window_seconds: 4,
      immediate: false,
      previous_projection: previous_projection
    }

    assert_equal true, Corpus::AssertionEvaluator.evaluate(
      { "batched" => true }, receipt: {}, projection: projection, event: {}, context: context
    ).fetch("passed")
    assert_equal true, Corpus::AssertionEvaluator.evaluate(
      { "sameBatchAs" => "fragment-1" }, receipt: {}, projection: projection, event: {}, context: context
    ).fetch("passed")
    assert_equal true, Corpus::AssertionEvaluator.evaluate(
      { "batchedWindowSeconds" => 4 }, receipt: {}, projection: projection, event: {}, context: context
    ).fetch("passed")
    assert_equal true, Corpus::AssertionEvaluator.evaluate(
      { "tool" => "request_human_handoff" }, receipt: {}, projection: projection, event: {}, context: context
    ).fetch("passed")
    assert_equal true, Corpus::AssertionEvaluator.evaluate(
      { "toolsExecuted" => 0 }, receipt: {}, projection: projection, event: {}, context: context
    ).fetch("passed")
    assert_equal true, Corpus::AssertionEvaluator.evaluate(
      { "hermesChatCalls" => 0 }, receipt: {}, projection: projection, event: {}, context: context
    ).fetch("passed")
    assert_equal true, Corpus::AssertionEvaluator.evaluate(
      { "lightProbesUsed" => 1 }, receipt: {}, projection: projection, event: {}, context: context
    ).fetch("passed")
    assert_equal true, Corpus::AssertionEvaluator.evaluate(
      { "commercialOpportunityCreated" => false }, receipt: {}, projection: projection, event: {}, context: context
    ).fetch("passed")
  end

  def test_runner_flushes_a_queued_text_batch_before_evaluating_its_projection
    path = scenario_file(<<~YAML)
      id: 99-batch-check
      name: Batch check
      purpose: Verify that the corpus releases a queued text batch before asserting it.
      contactAlias: batch-check
      events:
        - eventId: fragment-1
          type: TEXT
          text: "Quero"
          occurredAt: 2026-08-05T12:00:00Z
          assertions:
            - batched: true
        - eventId: fragment-2
          type: TEXT
          text: "decorar"
          occurredAt: 2026-08-05T12:00:03Z
          assertions:
            - sameBatchAs: fragment-1
            - status: COMPLETED
    YAML

    client = BatchingClient.new
    result = Corpus::ScenarioRunner.new(client: client).run(
      scenario_path: path,
      repetition: 1,
      run_id: "batch-run"
    )

    assert result.fetch("passed"), result.fetch("violations").inspect
    assert_equal 1, client.flush_calls
    assert_equal %w[fragment-1 fragment-2], client.events_in_last_batch
  ensure
    File.delete(path) if path && File.exist?(path)
  end

  def test_runner_rebases_historical_batch_timestamps_while_preserving_relative_spacing
    path = scenario_file(<<~YAML)
      id: 99-replay-clock
      name: Replay clock
      purpose: Verify historical fixtures do not race the live batch scheduler.
      contactAlias: replay-clock
      events:
        - eventId: fragment-1
          type: TEXT
          text: "Quero"
          occurredAt: 2020-01-01T12:00:00Z
          assertions:
            - batched: true
        - eventId: fragment-2
          type: TEXT
          text: "decorar"
          occurredAt: 2020-01-01T12:00:03Z
          assertions:
            - sameBatchAs: fragment-1
    YAML

    anchor = Time.parse("2026-08-06T04:00:00Z")
    client = BatchingClient.new
    result = Corpus::ScenarioRunner.new(client: client, clock: -> { anchor }).run(
      scenario_path: path,
      repetition: 1,
      run_id: "replay-clock-run"
    )

    assert result.fetch("passed"), result.fetch("violations").inspect
    sent_times = client.last_batch_payloads.map { |event| Time.iso8601(event.fetch("occurredAt")) }
    assert_equal [anchor, anchor + 3], sent_times
  ensure
    File.delete(path) if path && File.exist?(path)
  end

  def test_contains_and_facts_contains_compare_observed_values_to_expected_values
    receipt = {
      "status" => "COMPLETED",
      "output" => { "message" => "A Urba agradece o contato." }
    }
    projection = {
      "conversation" => {},
      "facts" => [{ "type" => "SELECTED_SERVICE", "value" => "DECOR" }],
      "messages" => []
    }

    contains = Corpus::AssertionEvaluator.evaluate(
      { "output.messageContains" => "Urba" },
      receipt: receipt,
      projection: projection,
      event: {},
      context: {}
    )
    assert_equal true, contains.fetch("passed")
    assert_equal "A Urba agradece o contato.", contains.fetch("observed")

    not_contains = Corpus::AssertionEvaluator.evaluate(
      { "output.messageNotContains" => "R$ 99" },
      receipt: receipt,
      projection: projection,
      event: {},
      context: {}
    )
    assert_equal true, not_contains.fetch("passed")
    assert_equal "A Urba agradece o contato.", not_contains.fetch("observed")

    facts_contains = Corpus::AssertionEvaluator.evaluate(
      { "factsContains" => "SELECTED_SERVICE" },
      receipt: receipt,
      projection: projection,
      event: {},
      context: {}
    )
    assert_equal true, facts_contains.fetch("passed")
    assert_equal ["SELECTED_SERVICE"], facts_contains.fetch("observed")
  end

  def test_audio_transcript_participates_uses_normalized_projection_text_and_fails_closed
    projection = {
      "conversation" => {},
      "facts" => [],
      "messages" => [
        {
          "direction" => "INBOUND",
          "eventId" => "runtime-audio",
          "text" => "transcrição normalizada do áudio"
        }
      ]
    }
    evaluated = Corpus::AssertionEvaluator.evaluate(
      { "transcriptParticipates" => true },
      receipt: { "status" => "COMPLETED" },
      projection: projection,
      event: { "type" => "AUDIO" },
      context: { runtime_event_id: "runtime-audio" }
    )
    assert_equal true, evaluated.fetch("passed")
    assert_equal true, evaluated.fetch("observed")

    without_evidence = Corpus::AssertionEvaluator.evaluate(
      { "transcriptParticipates" => true },
      receipt: { "status" => "COMPLETED" },
      projection: projection.merge("messages" => []),
      event: { "type" => "AUDIO" },
      context: { runtime_event_id: "runtime-audio" }
    )
    assert_equal false, without_evidence.fetch("passed")
    assert_equal "UNVERIFIED", without_evidence.fetch("status")
  end

  def test_memory_without_setup_endpoint_cannot_pass_by_assumption
    path = scenario_file(<<~YAML)
      id: 99-memory-check
      name: Memory check
      purpose: Verify that unavailable setup is explicit.
      contactAlias: memory-check
      memory:
        facts:
          - type: OCCUPATION
            value: DESIGNER
            confidence: CONFIRMED
            sourceMessageId: seed-occupation
      events:
        - eventId: event-1
          type: TEXT
          text: "Oi"
          occurredAt: 2026-08-05T12:00:00Z
          assertions:
            - output.nextAction: AWAIT_CUSTOMER
    YAML

    result = Corpus::ScenarioRunner.new(client: SuccessfulClient.new).run(
      scenario_path: path,
      repetition: 1,
      run_id: "memory-run",
      memory_seed_mode: "verify-only"
    )

    refute result.fetch("passed")
    memory_result = result.fetch("assertions").find { |item| item["assertion"] == "memory.facts[0]" }
    refute_nil memory_result
    assert_equal false, memory_result.fetch("passed")
    assert_equal "UNVERIFIED", memory_result.fetch("status")
  ensure
    File.delete(path) if path && File.exist?(path)
  end

  def test_setup_events_mode_executes_only_declared_setup_and_validates_projection
    path = scenario_file(<<~YAML)
      id: 99-memory-setup
      name: Memory setup
      purpose: Verify the explicit setup-events mode.
      contactAlias: memory-setup
      memory:
        facts:
          - type: OCCUPATION
            value: DESIGNER
            confidence: CONFIRMED
            sourceMessageId: seed-occupation
        setupEvents:
          - eventId: seed-event
            type: TEXT
            text: "seed"
            occurredAt: 2026-08-05T12:00:00Z
            assertions:
              - status: COMPLETED
      events:
        - eventId: event-1
          type: TEXT
          text: "Oi"
          occurredAt: 2026-08-05T12:00:05Z
          assertions:
            - output.nextAction: AWAIT_CUSTOMER
    YAML

    result = Corpus::ScenarioRunner.new(client: SeedClient.new).run(
      scenario_path: path,
      repetition: 1,
      run_id: "memory-setup-run",
      memory_seed_mode: "setup-events"
    )

    assert result.fetch("passed"), result.fetch("violations").inspect
    assert_equal true, result.fetch("memorySetup").fetch("main").fetch("setupEventsExecuted")
    memory_result = result.fetch("assertions").find { |item| item["assertion"] == "memory.facts[0]" }
    assert_equal true, memory_result.fetch("passed")
    setup_event = result.fetch("events").find { |item| item["logicalEventId"] == "seed-event" }
    refute_equal "seed-event", setup_event.fetch("eventId")
  ensure
    File.delete(path) if path && File.exist?(path)
  end

  def test_report_marks_all_assertions_unverified_after_explicit_connection_failure
    path = scenario_file(<<~YAML)
      id: 99-offline-check
      name: Offline check
      purpose: Verify explicit endpoint failures.
      contactAlias: offline-check
      events:
        - eventId: event-1
          type: TEXT
          text: "Oi"
          occurredAt: 2026-08-05T12:00:00Z
          assertions:
            - output.nextAction: AWAIT_CUSTOMER
        - eventId: event-2
          type: TEXT
          text: "Tudo bem?"
          occurredAt: 2026-08-05T12:00:05Z
          assertions:
            - status: COMPLETED
    YAML

    result = Corpus::ScenarioRunner.new(client: FailingClient.new).run(
      scenario_path: path,
      repetition: 1,
      run_id: "offline-run",
      memory_seed_mode: "verify-only"
    )

    refute result.fetch("passed")
    assert_includes result.fetch("environmentError"), "Connection refused"
    assert_equal 2, result.fetch("assertions").length
    assert result.fetch("assertions").all? { |item| item["passed"] == false }
  ensure
    File.delete(path) if path && File.exist?(path)
  end

  def test_runner_rejects_non_2xx_responses_instead_of_returning_false_green
    path = scenario_file(<<~YAML)
      id: 99-http-failure
      name: HTTP failure
      purpose: Verify that a non-success response cannot satisfy an assertion.
      contactAlias: http-failure
      events:
        - eventId: event-1
          type: TEXT
          text: "Oi"
          occurredAt: 2026-08-05T12:00:00Z
          assertions:
            - status: COMPLETED
    YAML

    result = Corpus::ScenarioRunner.new(client: NonSuccessClient.new(503)).run(
      scenario_path: path,
      repetition: 1,
      run_id: "http-failure-run"
    )

    refute result.fetch("passed")
    assert_includes result.fetch("environmentError"), "HTTP 503"
    assert result.fetch("assertions").all? { |item| item.fetch("passed") == false }
  ensure
    File.delete(path) if path && File.exist?(path)
  end

  def test_runner_rejects_payloads_without_minimum_evidence
    path = scenario_file(<<~YAML)
      id: 99-payload-evidence
      name: Missing payload evidence
      purpose: Verify that empty API payloads cannot produce a passing run.
      contactAlias: payload-evidence
      events:
        - eventId: event-1
          type: TEXT
          text: "Oi"
          occurredAt: 2026-08-05T12:00:00Z
          assertions:
            - status: COMPLETED
    YAML

    result = Corpus::ScenarioRunner.new(client: EmptyPayloadClient.new).run(
      scenario_path: path,
      repetition: 1,
      run_id: "payload-evidence-run"
    )

    refute result.fetch("passed")
    assert_includes result.fetch("environmentError"), "minimum evidence"
    assert result.fetch("assertions").all? { |item| item.fetch("passed") == false }
  ensure
    File.delete(path) if path && File.exist?(path)
  end

  def test_runner_rejects_projection_without_minimum_evidence
    path = scenario_file(<<~YAML)
      id: 99-projection-evidence
      name: Missing projection evidence
      purpose: Verify that an empty projection cannot produce a passing run.
      contactAlias: projection-evidence
      events:
        - eventId: event-1
          type: TEXT
          text: "Oi"
          occurredAt: 2026-08-05T12:00:00Z
          assertions:
            - status: COMPLETED
    YAML

    result = Corpus::ScenarioRunner.new(client: EmptyProjectionClient.new).run(
      scenario_path: path,
      repetition: 1,
      run_id: "projection-evidence-run"
    )

    refute result.fetch("passed")
    assert_includes result.fetch("environmentError"), "minimum evidence"
    assert result.fetch("assertions").all? { |item| item.fetch("passed") == false }
  ensure
    File.delete(path) if path && File.exist?(path)
  end

  def test_aggregate_preserves_run_and_repetition_and_counts_assertion_states
    input = Tempfile.new(["records", ".jsonl"])
    output = Tempfile.new(["summary", ".json"])
    input.write(
      JSON.generate(
        "scenario" => "99-test",
        "runId" => "run-a",
        "repetition" => 2,
        "passed" => false,
        "assertions" => [
          { "passed" => true, "status" => "PASSED" },
          { "passed" => false, "status" => "UNVERIFIED" }
        ]
      )
    )
    input.write("\n")
    input.close

    summary = Corpus::Report.aggregate(input.path, output.path)

    assert_equal 1, summary.fetch("totalExecutions")
    assert_equal "run-a", summary.fetch("records").first.fetch("runId")
    assert_equal 2, summary.fetch("records").first.fetch("repetition")
    assert_equal 1, summary.fetch("assertionSummary").fetch("unverified")
  ensure
    input&.unlink
    output&.unlink
  end

  def test_aggregate_gate_requires_the_six_scenario_roster_and_three_distinct_repetitions
    complete = aggregate_records
    passing_summary = aggregate_summary(complete)
    assert_equal true, passing_summary.fetch("gate").fetch("passed")
    assert_equal REQUIRED_SCENARIOS, passing_summary.fetch("gate").fetch("scenarioRoster").fetch("observed")
    assert_equal true, passing_summary.fetch("gate").fetch("repetitions").fetch("passed")

    missing = complete.reject { |record| record.fetch("scenario") == "06-multimodal" }
    missing_summary = aggregate_summary(missing)
    refute missing_summary.fetch("gate").fetch("passed")
    assert_includes missing_summary.fetch("gate").fetch("scenarioRoster").fetch("missing"), "06-multimodal"

    duplicated = aggregate_records
    duplicated.find { |record| record.fetch("scenario") == "01-happy-first-contact" && record.fetch("repetition") == 2 }["repetition"] = 1
    duplicated_summary = aggregate_summary(duplicated)
    refute duplicated_summary.fetch("gate").fetch("passed")
    refute duplicated_summary.fetch("gate").fetch("repetitions").fetch("passed")
  end

  def test_aggregate_evaluates_only_explicit_human_scores_and_rejects_low_average
    without_scores = aggregate_summary(aggregate_records)
    assert_equal false, without_scores.fetch("gate").fetch("humanEvaluation").fetch("evaluated")
    assert_equal true, without_scores.fetch("gate").fetch("humanEvaluation").fetch("passed")

    low_scores = aggregate_records
    low_scores.first["humanEvaluation"] = {
      "naturalness" => 3,
      "clarity" => 3,
      "usefulness" => 3
    }
    low_summary = aggregate_summary(low_scores)
    assert_equal true, low_summary.fetch("gate").fetch("humanEvaluation").fetch("evaluated")
    assert_equal 3.0, low_summary.fetch("gate").fetch("humanEvaluation").fetch("average")
    assert_equal false, low_summary.fetch("gate").fetch("humanEvaluation").fetch("passed")
    refute low_summary.fetch("gate").fetch("passed")
  end

  private

  def scenario_file(contents)
    file = Tempfile.new(["scenario", ".yml"])
    file.write(contents)
    file.close
    file.path
  end

  def aggregate_records
    REQUIRED_SCENARIOS.flat_map do |scenario|
      [1, 2, 3].map do |repetition|
        {
          "scenario" => scenario,
          "runId" => "aggregate-run",
          "repetition" => repetition,
          "passed" => true,
          "assertions" => [{ "passed" => true, "status" => "PASSED" }],
          "humanEvaluation" => { "naturalness" => nil, "clarity" => nil, "usefulness" => nil }
        }
      end
    end
  end

  def aggregate_summary(records)
    input = Tempfile.new(["aggregate-records", ".jsonl"])
    output = Tempfile.new(["aggregate-summary", ".json"])
    records.each { |record| input.puts(JSON.generate(record)) }
    input.close

    Corpus::Report.aggregate(input.path, output.path)
  ensure
    input&.unlink
    output&.unlink
  end

  class SuccessfulClient
    def initialize
      @projection = {
        "conversation" => {
          "mode" => "AI",
          "selectedService" => nil,
          "termsStatus" => "NOT_PRESENTED",
          "paymentStatus" => "NOT_STARTED"
        },
        "facts" => [],
        "messages" => []
      }
    end

    def request_json(_base_url, method, path, payload = nil)
      if method == "POST"
        [202, {
          "eventId" => payload.fetch("eventId"),
          "correlationId" => "correlation-1",
          "status" => "COMPLETED",
          "output" => { "message" => "Olá!", "nextAction" => "AWAIT_CUSTOMER" }
        }]
      elsif path.include?("/conversations/")
        [200, @projection]
      else
        [200, {}]
      end
    end
  end

  class FailingClient
    def request_json(*)
      raise Errno::ECONNREFUSED, "Connection refused"
    end
  end

  class NonSuccessClient
    def initialize(status)
      @status = status
    end

    def request_json(_base_url, _method, _path, _payload = nil)
      [@status, {}]
    end
  end

  class EmptyPayloadClient
    def request_json(_base_url, _method, _path, _payload = nil)
      [202, {}]
    end
  end

  class EmptyProjectionClient
    def request_json(_base_url, method, _path, payload = nil)
      return [202, {
        "eventId" => payload.fetch("eventId"),
        "correlationId" => "correlation-1",
        "status" => "COMPLETED"
      }] if method == "POST"

      [200, {}]
    end
  end

  class SeedClient < SuccessfulClient
    def initialize
      super
      @projection["facts"] = [
        {
          "type" => "OCCUPATION",
          "value" => "DESIGNER",
          "confidence" => "CONFIRMED",
          "sourceMessageId" => "seed-event"
        }
      ]
    end
  end

  class BatchingClient
    attr_reader :flush_calls, :events_in_last_batch, :last_batch_payloads

    def initialize
      @queued = []
      @flush_calls = 0
      @events_in_last_batch = []
      @last_batch_payloads = []
    end

    def request_json(_base_url, method, path, payload = nil)
      if method == "POST" && path.end_with?("/messages")
        @queued << payload
        return [202, {
          "eventId" => payload.fetch("eventId"),
          "correlationId" => "queued-#{payload.fetch('eventId')}",
          "status" => "QUEUED",
          "output" => nil
        }]
      end
      if method == "POST" && path.end_with?("/flush")
        @flush_calls += 1
        @last_batch_payloads = @queued.dup
        @events_in_last_batch = @queued.map { |event| event.fetch("eventId").split("-r1-").first }
        last = @queued.last
        @queued = []
        return [200, [{
          "eventId" => last.fetch("eventId"),
          "correlationId" => "completed",
          "status" => "COMPLETED",
          "output" => { "message" => "ok", "nextAction" => "AWAIT_CUSTOMER" }
        }]]
      end
      if method == "GET" && path.include?("/conversations/")
        return [200, {
          "conversation" => {},
          "facts" => [],
          "messages" => [],
          "toolInvocations" => [],
          "hermesChatCalls" => 0,
          "lightProbesUsed" => 0,
          "commercialOpportunityCreated" => false
        }]
      end
      [200, {}]
    end
  end
end
