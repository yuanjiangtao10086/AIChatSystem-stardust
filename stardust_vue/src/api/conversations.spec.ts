import { describe, it, expect, vi, beforeEach } from "vitest";
import {
  streamConversationMessage,
  regenerateMessage,
  editAndResendMessage,
} from "@/api/conversations";
import type { FileReference } from "@/types/file";

vi.mock("@/api/client", () => ({
  authenticatedFetch: vi.fn(),
  apiRequest: vi.fn(),
  ApiError: class extends Error {},
  ApiResult: class {},
}));

// Pull the mocked fetch without importing it as a value (it is used inside the module under test).
const { authenticatedFetch } = await import("@/api/client");

const closedStream = () =>
  new Response(
    new ReadableStream({
      start(controller) {
        controller.close();
      },
    }),
    {
      status: 200,
    }
  );

const file = (id: string): FileReference => ({
  id,
  name: `${id}.txt`,
  mimeType: "text/plain",
  size: 10,
  previewable: false,
  downloadUrl: `/download/${id}`,
});

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(authenticatedFetch).mockResolvedValue(closedStream());
});

describe("streamConversationMessage", () => {
  it("sends attachmentIds derived from the selected files", async () => {
    await streamConversationMessage(
      "c1",
      "hi",
      "m1",
      undefined,
      [file("f1"), file("f2")],
      new AbortController().signal,
      () => undefined
    );
    expect(authenticatedFetch).toHaveBeenCalledTimes(1);
    const [url, init] = vi.mocked(authenticatedFetch).mock.calls[0];
    expect(url).toContain("/api/v1/conversations/c1/messages/stream");
    const body = JSON.parse(init.body as string);
    expect(body).toMatchObject({
      content: "hi",
      contentType: "PLAIN_TEXT",
      modelId: "m1",
      attachmentIds: ["f1", "f2"],
    });
  });

  it("still sends an explicit empty attachmentIds when nothing is selected", async () => {
    await streamConversationMessage(
      "c1",
      "hi",
      "m1",
      undefined,
      [],
      new AbortController().signal,
      () => undefined
    );
    const body = JSON.parse(
      vi.mocked(authenticatedFetch).mock.calls[0][1].body as string
    );
    expect(body.attachmentIds).toEqual([]);
  });
});

describe("editAndResendMessage", () => {
  it("sends attachmentIds for the revised turn", async () => {
    await editAndResendMessage(
      "m1",
      "revised",
      "m1",
      [file("f9")],
      new AbortController().signal,
      () => undefined
    );
    const [url, init] = vi.mocked(authenticatedFetch).mock.calls[0];
    expect(url).toContain("/api/v1/messages/m1/edit-and-resend");
    const body = JSON.parse(init.body as string);
    expect(body).toMatchObject({
      content: "revised",
      attachmentIds: ["f9"],
    });
  });
});

describe("regenerateMessage", () => {
  it("does not send attachments (the original user message carries them)", async () => {
    await regenerateMessage(
      "m1",
      "m1",
      new AbortController().signal,
      () => undefined
    );
    const [url, init] = vi.mocked(authenticatedFetch).mock.calls[0];
    expect(url).toContain("/api/v1/messages/m1/regenerate");
    const body = JSON.parse(init.body as string);
    expect(body).toEqual({ modelId: "m1" });
  });
});
