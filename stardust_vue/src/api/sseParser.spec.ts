import { describe, expect, it } from "vitest";
import { consumeSseChunk, parseSseBlock } from "@/api/sseParser";
import type { AiStreamEvent } from "@/types/conversation";

/** 用 @ 别名导入，同时验证 vitest 配置的 alias 生效。 */
const sample = (type: AiStreamEvent["type"]): AiStreamEvent => ({
  type,
  requestId: "req-1",
  conversationId: "conv-1",
  messageId: "msg-1",
});

describe("parseSseBlock", () => {
  it("解析合法 data 块并回调事件", () => {
    const events: AiStreamEvent[] = [];
    const block = `data: ${JSON.stringify(sample("delta"))}`;
    parseSseBlock(block, (event) => events.push(event));
    expect(events).toHaveLength(1);
    expect(events[0].type).toBe("delta");
  });

  it("坏 JSON 被跳过而不抛错", () => {
    const events: AiStreamEvent[] = [];
    expect(() =>
      parseSseBlock("data: {oops", (event) => events.push(event))
    ).not.toThrow();
    expect(events).toHaveLength(0);
  });

  it("缺少信封字段的事件被跳过", () => {
    const events: AiStreamEvent[] = [];
    parseSseBlock('data: {"type":"delta"}', (event) => events.push(event));
    expect(events).toHaveLength(0);
  });
});

describe("consumeSseChunk", () => {
  it("跨分片拼接后才回调，并返回剩余缓冲", () => {
    const events: AiStreamEvent[] = [];
    const payload = `data: ${JSON.stringify(sample("delta"))}\n\n`;
    let buffer = consumeSseChunk("", payload.slice(0, 10), (event) =>
      events.push(event)
    );
    expect(events).toHaveLength(0);
    buffer = consumeSseChunk(buffer, payload.slice(10), (event) =>
      events.push(event)
    );
    expect(events).toHaveLength(1);
    expect(buffer).toBe("");
  });

  it("一个分片内的多个事件全部回调", () => {
    const events: AiStreamEvent[] = [];
    const chunk =
      `data: ${JSON.stringify(sample("start"))}\n\n` +
      `data: ${JSON.stringify(sample("done"))}\n\n`;
    consumeSseChunk("", chunk, (event) => events.push(event));
    expect(events.map((event) => event.type)).toEqual(["start", "done"]);
  });
});
