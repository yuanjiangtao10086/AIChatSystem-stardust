import type { AiStreamEvent } from "../types/conversation";

/**
 * Parse a single SSE block (the text between two blank lines) into an event.
 *
 * An SSE event is a set of `field: value` lines. We only care about `data:`
 * lines; `event:`/`id:`/`retry:` are ignored because the type is carried in
 * the JSON `type` field. A block with no `data:` is a comment/heartbeat.
 *
 * Robustness: a malformed block (bad JSON, or missing the required envelope
 * fields) is skipped instead of throwing. The caller keeps an accumulating
 * buffer, so a single bad event must never abort the whole stream — otherwise
 * the trailing `done`/`error` events would be lost and the UI would stay in the
 * "sending" state forever.
 */
export function parseSseBlock(
  block: string,
  onEvent: (event: AiStreamEvent) => void
): void {
  const data = block
    .split("\n")
    .filter((line) => line.startsWith("data:"))
    .map((line) => line.slice(5).trimStart())
    .join("\n");
  if (!data) return;
  let parsed: AiStreamEvent;
  try {
    parsed = JSON.parse(data) as AiStreamEvent;
  } catch {
    // Malformed JSON chunk: skip silently instead of killing the whole stream.
    return;
  }
  if (!parsed.type || !parsed.requestId || !parsed.messageId) {
    // Incomplete or unexpected envelope: skip instead of throwing so the
    // following `done`/`error` events are still processed by the caller.
    return;
  }
  onEvent(parsed);
}

/**
 * Append one decoded chunk to the persistent SSE buffer, emit every fully
 * received event (`\n\n` delimited), and return the leftover partial buffer.
 *
 * This is the heart of the streaming client: TCP/HTTP streaming may split a
 * single SSE event across multiple chunks, or pack several events into one
 * chunk, or split a multi-byte UTF-8 character across chunks. Keeping the
 * buffer here guarantees correct reassembly. Pure and side-effect free apart
 * from invoking `onEvent`.
 */
export function consumeSseChunk(
  buffer: string,
  chunk: string,
  onEvent: (event: AiStreamEvent) => void
): string {
  const next = buffer + chunk;
  let working = next;
  let boundary = working.indexOf("\n\n");
  while (boundary >= 0) {
    const block = working.slice(0, boundary);
    working = working.slice(boundary + 2);
    try {
      parseSseBlock(block, onEvent);
    } catch (parseError) {
      if (process.env.NODE_ENV !== "production") {
        console.warn("[SSE_PARSE_ERROR]", parseError);
      }
    }
    boundary = working.indexOf("\n\n");
  }
  return working;
}
