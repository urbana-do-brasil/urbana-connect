#!/usr/bin/env ruby
# frozen_string_literal: true

require "optparse"
require_relative "runner"

command = ARGV.shift

case command
when "validate"
  options = {}
  parser = OptionParser.new do |opts|
    opts.on("--scenario PATH") { |value| options[:scenario] = value }
  end
  parser.parse!(ARGV)
  raise "missing --scenario" unless options[:scenario]

  scenario = Corpus::ScenarioLoader.load(options.fetch(:scenario))
  puts "valid scenario=#{scenario.fetch('id')} path=#{options.fetch(:scenario)}"
  exit 0
when "run"
  options = {
    base_url: "http://127.0.0.1:8081",
    repetition: 1,
    memory_seed_mode: "verify-only"
  }
  parser = OptionParser.new do |opts|
    opts.on("--scenario PATH") { |value| options[:scenario] = value }
    opts.on("--repetition N", Integer) { |value| options[:repetition] = value }
    opts.on("--run-id ID") { |value| options[:run_id] = value }
    opts.on("--base-url URL") { |value| options[:base_url] = value }
    opts.on("--output PATH") { |value| options[:output] = value }
    opts.on("--memory-seed-mode MODE", Corpus::MEMORY_SEED_MODES) { |value| options[:memory_seed_mode] = value }
  end
  parser.parse!(ARGV)
  raise "missing --scenario" unless options[:scenario]
  raise "missing --output" unless options[:output]

  result = Corpus::ScenarioRunner.new.run(
    scenario_path: options.fetch(:scenario),
    repetition: options.fetch(:repetition),
    run_id: options[:run_id],
    base_url: options.fetch(:base_url),
    output: options.fetch(:output),
    memory_seed_mode: options.fetch(:memory_seed_mode)
  )
  exit(result.fetch("passed") ? 0 : 1)
when "aggregate"
  options = {}
  parser = OptionParser.new do |opts|
    opts.on("--input PATH") { |value| options[:input] = value }
    opts.on("--output PATH") { |value| options[:output] = value }
  end
  parser.parse!(ARGV)
  summary = Corpus::Report.aggregate(options.fetch(:input), options.fetch(:output))
  exit(summary.fetch("gate").fetch("passed") ? 0 : 1)
else
  warn "usage: report.rb validate --scenario PATH | run --scenario PATH --output PATH | aggregate --input PATH --output PATH"
  exit 2
end
