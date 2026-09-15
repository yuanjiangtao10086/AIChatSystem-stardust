import { describe, it, expect } from "vitest";
import { mount, type VueWrapper } from "@vue/test-utils";
import ChatComposer from "@/components/chat/ChatComposer.vue";
import type { FileReference } from "@/types/file";

const file = (id: string): FileReference => ({
  id,
  name: `${id}.txt`,
  mimeType: "text/plain",
  size: 10,
  previewable: false,
  downloadUrl: `/download/${id}`,
});

type SentPayload = { content: string; files: FileReference[] };

const mountComposer = (
  onSend: (payload: SentPayload) => void,
  props: Record<string, unknown> = {}
) =>
  mount(ChatComposer, {
    props: { modelAvailable: true, ...props },
    // 用合成 @send 监听器捕获上抛（emitted() 在本地 vitest 配置下不记录自定义事件）。
    attrs: {
      onSend: (content: string, files: FileReference[]) =>
        onSend({ content, files }),
    },
    global: { stubs: { ChatAttachment: true } },
  });

describe("ChatComposer 发送", () => {
  it("提交时向上抛出内容、携带附件，并立即清空草稿与附件", async () => {
    const sent: SentPayload[] = [];
    const wrapper: VueWrapper = mountComposer((payload) => sent.push(payload));
    // v-model 走真实输入事件，避免直接写内部 ref 不被 setter 接受。
    await wrapper.find("textarea").setValue("  你好  ");
    wrapper.vm.attachments = [file("f1")];
    expect(wrapper.vm.attachments).toHaveLength(1);

    wrapper.vm.submit();
    await wrapper.vm.$nextTick();

    expect(sent).toEqual([{ content: "你好", files: [file("f1")] }]);
    // 清空发生在 emit 之后，证明 submit 没有提前 return。
    expect(wrapper.vm.draft).toBe("");
    expect(wrapper.vm.attachments).toHaveLength(0);
  });

  it("草稿为空时不会提交", async () => {
    const sent: SentPayload[] = [];
    const wrapper = mountComposer((payload) => sent.push(payload));
    await wrapper.find("textarea").setValue("   ");
    wrapper.vm.submit();
    expect(sent).toHaveLength(0);
  });
});

describe("ChatComposer 失败回滚", () => {
  it("restore 把内容和附件填回，避免发送失败时丢失用户输入", async () => {
    const sent: SentPayload[] = [];
    const wrapper = mountComposer((payload) => sent.push(payload));
    const files = [file("f1"), file("f2")];
    wrapper.vm.restore("我的提问", files);
    await wrapper.vm.$nextTick();

    expect(wrapper.vm.draft).toBe("我的提问");
    expect(wrapper.vm.attachments).toEqual(files);
    // restore 不触发发送。
    expect(sent).toHaveLength(0);
  });
});
