/*
 * Copyright 2024-2026 Embabel Pty Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.embabel.acceptance.agent;

import java.util.*;

/**
 * Aggregates key metrics from Zipkin trace spans produced by Spring AI.
 * <p>
 * Think of this as the "receipt" for an agentic run: it tells you
 * how many LLM calls were made, how many tokens were consumed (and by which model),
 * how many tools were invoked, whether anything went wrong, and how long
 * the LLM work actually took.
 */
public class TraceSummary {

    // ------------------------------------------------------------------
    // Spring AI / OpenTelemetry semantic convention tag keys
    // ------------------------------------------------------------------

    /**
     * Distinguishes chat vs tool vs framework operations.
     */
    static final String TAG_OPERATION_NAME = "gen_ai.operation.name";

    /**
     * The model actually used for the response (may differ from the requested model).
     */
    static final String TAG_RESPONSE_MODEL = "gen_ai.response.model";

    /**
     * The model requested by the caller.
     */
    static final String TAG_REQUEST_MODEL = "gen_ai.request.model";

    /**
     * Token counts emitted by Spring AI's Micrometer instrumentation.
     */
    static final String TAG_INPUT_TOKENS = "gen_ai.usage.input_tokens";
    static final String TAG_OUTPUT_TOKENS = "gen_ai.usage.output_tokens";
    static final String TAG_TOTAL_TOKENS = "gen_ai.usage.total_tokens";

    /**
     * Embabel/Spring AI tool name tag (present on tool invocation spans).
     */
    static final String TAG_TOOL_NAME = "toolName";

    /**
     * OpenTelemetry error tag – present only when a span records a failure.
     */
    static final String TAG_ERROR = "error";

    /**
     * OpenTelemetry status code tag – value "ERROR" signals a failure.
     */
    static final String TAG_OTEL_STATUS_CODE = "otel.status_code";

    /**
     * Spring/Brave exception tag. Value is "none" when there is no error;
     * otherwise it contains the exception class name.
     */
    static final String TAG_EXCEPTION = "exception";
    static final String EXCEPTION_NONE = "none";

    /**
     * Operation name value for chat (LLM) calls.
     */
    static final String OP_CHAT = "chat";

    /**
     * Operation name value for tool invocations.
     */
    static final String OP_TOOL = "tool";

    // ------------------------------------------------------------------
    // Aggregated metrics
    // ------------------------------------------------------------------

    private int traceCount;
    private int modelCallCount;
    private int toolCallCount;
    private int errorCount;
    private long totalLlmDurationMicros;

    /**
     * Per-model token breakdown.
     * Key = model name (from {@code gen_ai.response.model}, falling back to
     * {@code gen_ai.request.model}), Value = accumulated {@link TokenUsage}.
     */
    private final Map<String, TokenUsage> tokenUsageByModel = new LinkedHashMap<>();

    /**
     * Names of all tools that were invoked during the trace.
     */
    private final List<String> toolNames = new ArrayList<>();

    /**
     * Span names that were flagged as errors.
     */
    private final List<String> errorSpanNames = new ArrayList<>();

    // ------------------------------------------------------------------
    // Factory
    // ------------------------------------------------------------------

    /**
     * Build a summary by aggregating across all traces returned by Zipkin.
     *
     * @param traces the full Zipkin response — a list of traces, each of which is a list of spans.
     */
    @SuppressWarnings("unchecked")
    public static TraceSummary fromTraces(List<List<Map<String, Object>>> traces) {
        TraceSummary summary = new TraceSummary();
        summary.traceCount = traces.size();

        for (List<Map<String, Object>> spans : traces) {
            for (Map<String, Object> span : spans) {
                Map<String, String> tags = (Map<String, String>) span.getOrDefault("tags", Collections.emptyMap());
                String operationName = tags.getOrDefault(TAG_OPERATION_NAME, "");
                String spanName = (String) span.getOrDefault("name", "<unknown>");

                // ---- LLM (chat) calls ----
                if (OP_CHAT.equals(operationName)) {
                    summary.modelCallCount++;

                    // Accumulate duration
                    Object durationObj = span.get("duration");
                    if (durationObj instanceof Number duration) {
                        summary.totalLlmDurationMicros += duration.longValue();
                    }

                    // Resolve model name: prefer response model, fall back to request model
                    String model = tags.getOrDefault(TAG_RESPONSE_MODEL, tags.getOrDefault(TAG_REQUEST_MODEL, "unknown"));
                    if ("none".equalsIgnoreCase(model)) {
                        model = tags.getOrDefault(TAG_REQUEST_MODEL, "unknown");
                    }

                    summary.tokenUsageByModel
                            .computeIfAbsent(model, k -> new TokenUsage())
                            .accumulate(
                                    parseLong(tags.get(TAG_INPUT_TOKENS)),
                                    parseLong(tags.get(TAG_OUTPUT_TOKENS)),
                                    parseLong(tags.get(TAG_TOTAL_TOKENS))
                            );
                }

                // ---- Tool calls ----
                // Spring AI uses gen_ai.operation.name=tool, but Embabel tool spans
                // may instead carry a "toolName" tag without the gen_ai operation marker.
                String toolName = tags.get(TAG_TOOL_NAME);
                if (OP_TOOL.equals(operationName) || toolName != null) {
                    summary.toolCallCount++;
                    summary.toolNames.add(toolName != null ? toolName : spanName);
                }

                // ---- Errors ----
                boolean hasErrorTag = tags.containsKey(TAG_ERROR);
                boolean hasOtelError = "ERROR".equals(tags.get(TAG_OTEL_STATUS_CODE));
                String exceptionValue = tags.get(TAG_EXCEPTION);
                boolean hasException = exceptionValue != null && !EXCEPTION_NONE.equalsIgnoreCase(exceptionValue);

                if (hasErrorTag || hasOtelError || hasException) {
                    summary.errorCount++;
                    summary.errorSpanNames.add(spanName);
                }
            }
        }

        return summary;
    }

    // ------------------------------------------------------------------
    // Formatted output
    // ------------------------------------------------------------------

    /**
     * Produce a human-readable summary block suitable for test output.
     */
    public String format() {
        StringBuilder sb = new StringBuilder();
        String ruler = "═══════════════════════════════════════════════════════════";

        sb.append(ruler).append("\n");
        sb.append("                   TRACE SUMMARY\n");
        sb.append(ruler).append("\n");

        sb.append(String.format("  Traces                : %d%n", traceCount));
        sb.append(String.format("  Model calls           : %d%n", modelCallCount));
        sb.append(String.format("  Tool calls            : %d%n", toolCallCount));

        if (!toolNames.isEmpty()) {
            sb.append(String.format("  Tools invoked         : %s%n", String.join(", ", toolNames)));
        }

        sb.append(String.format("  Errors / Exceptions   : %d%n", errorCount));

        if (!errorSpanNames.isEmpty()) {
            sb.append(String.format("  Error spans           : %s%n", String.join(", ", errorSpanNames)));
        }

        sb.append(String.format("  Total LLM time        : %,.2f s%n", totalLlmDurationMicros / 1_000_000.0));

        sb.append("\n");
        sb.append("  Token Usage by Model:\n");

        if (tokenUsageByModel.isEmpty()) {
            sb.append("    (no token data recorded)\n");
        } else {
            sb.append(String.format("    %-40s %10s %10s %10s%n", "Model", "Input", "Output", "Total"));
            sb.append(String.format("    %-40s %10s %10s %10s%n", "─".repeat(40), "─".repeat(10), "─".repeat(10), "─".repeat(10)));

            long grandInput = 0, grandOutput = 0, grandTotal = 0;

            for (Map.Entry<String, TokenUsage> entry : tokenUsageByModel.entrySet()) {
                TokenUsage usage = entry.getValue();
                sb.append(String.format("    %-40s %,10d %,10d %,10d%n",
                        entry.getKey(), usage.inputTokens, usage.outputTokens, usage.totalTokens));
                grandInput += usage.inputTokens;
                grandOutput += usage.outputTokens;
                grandTotal += usage.totalTokens;
            }

            if (tokenUsageByModel.size() > 1) {
                sb.append(String.format("    %-40s %10s %10s %10s%n", "", "─".repeat(10), "─".repeat(10), "─".repeat(10)));
                sb.append(String.format("    %-40s %,10d %,10d %,10d%n",
                        "TOTAL", grandInput, grandOutput, grandTotal));
            }
        }

        sb.append(ruler).append("\n");

        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Getters
    // ------------------------------------------------------------------

    public int getTraceCount() {
        return traceCount;
    }

    public int getModelCallCount() {
        return modelCallCount;
    }

    public int getToolCallCount() {
        return toolCallCount;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public long getTotalLlmDurationMicros() {
        return totalLlmDurationMicros;
    }

    public Map<String, TokenUsage> getTokenUsageByModel() {
        return Collections.unmodifiableMap(tokenUsageByModel);
    }

    public List<String> getToolNames() {
        return Collections.unmodifiableList(toolNames);
    }

    public List<String> getErrorSpanNames() {
        return Collections.unmodifiableList(errorSpanNames);
    }

    // ------------------------------------------------------------------
    // Inner class
    // ------------------------------------------------------------------

    /**
     * Mutable accumulator for token counts associated with a single model.
     */
    public static class TokenUsage {

        private long inputTokens;
        private long outputTokens;
        private long totalTokens;

        void accumulate(long input, long output, long total) {
            this.inputTokens += input;
            this.outputTokens += output;
            this.totalTokens += total;
        }

        public long getInputTokens() {
            return inputTokens;
        }

        public long getOutputTokens() {
            return outputTokens;
        }

        public long getTotalTokens() {
            return totalTokens;
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static long parseLong(String value) {
        if (value == null || value.isBlank()) return 0;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
