import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import ChatAttachment from "@/components/chat/ChatAttachment.vue";
import { listFiles, uploadFile } from "@/api/files";
import type { FilePage, UserFile } from "@/types/file";

vi.mock("@/api/files", () => ({
  listFiles: vi.fn(),
  uploadFile: vi.fn(),
  formatBytes: (value: number) => `${value} B`,
}));

const sample = (id: string, name: string): UserFile => ({
  id,
  name,
  mimeType: "application/pdf",
  size: 1024,
  previewable: false,
  downloadUrl: `/download/${id}`,
  detectedMimeType: "application/pdf",
  extension: "pdf",
  sha256: "hash",
  storageProvider: "LOCAL",
  status: "AVAILABLE",
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
});

const pageOf = (items: UserFile[]): FilePage => ({
  items,
  page: 0,
  size: 50,
  totalElements: items.length,
  totalPages: 1,
  hasNext: false,
});

/** jsdom 不保证 PointerEvent 构造器可用，用普通 Event 即可触发监听器。 */
const pointerDown = (target: EventTarget) => {
  target.dispatchEvent(new Event("pointerdown", { bubbles: true }));
};

let wrapper: VueWrapper;
/** 组件向上抛出的每次新选中列表（等价于父组件 v-model 收到的值）。 */
const updates: UserFile[][] = [];

const open = async (
  items: UserFile[] = [sample("f1", "a.pdf")],
  selected: UserFile[] = []
) => {
  vi.mocked(listFiles).mockResolvedValue(pageOf(items));
  wrapper = mount(ChatAttachment, {
    props: {
      modelValue: selected,
      "onUpdate:modelValue": (value: UserFile[]) => {
        updates.push(value);
        void wrapper.setProps({ modelValue: value });
      },
    },
    global: { stubs: { "router-link": true } },
    attachTo: document.body,
  });
  await wrapper.find(".attachment").trigger("click");
  await flushPromises();
};

/** 直接给隐藏 input 注入文件并触发 change（比拖拽事件更稳定）。 */
const upload = async (name: string) => {
  const input = wrapper.find('input[type="file"]').element as HTMLInputElement;
  Object.defineProperty(input, "files", {
    value: [new File(["x"], name, { type: "application/pdf" })],
  });
  await wrapper.find('input[type="file"]').trigger("change");
  await flushPromises();
};

beforeEach(() => {
  document.body.innerHTML = "";
});

afterEach(() => {
  wrapper?.unmount();
  updates.length = 0;
  vi.clearAllMocks();
});

describe("ChatAttachment 面板开合", () => {
  it("点击 + 打开面板并渲染云盘文件", async () => {
    await open();
    expect(wrapper.find(".picker").exists()).toBe(true);
    expect(wrapper.text()).toContain("a.pdf");
  });

  it("点击面板内部不会关闭", async () => {
    await open();
    pointerDown(wrapper.find(".picker").element);
    await flushPromises();
    expect(wrapper.find(".picker").exists()).toBe(true);
  });

  it("点击面板外部自动关闭", async () => {
    await open();
    pointerDown(document.body);
    await flushPromises();
    expect(wrapper.find(".picker").exists()).toBe(false);
  });

  it("按 ESC 关闭面板", async () => {
    await open();
    document.dispatchEvent(
      new KeyboardEvent("keydown", { key: "Escape", bubbles: true })
    );
    await flushPromises();
    expect(wrapper.find(".picker").exists()).toBe(false);
  });
});

describe("ChatAttachment 选择附件", () => {
  it("点击文件后向上抛出新的已选列表", async () => {
    await open();
    await wrapper.findAll(".picker li button")[0].trigger("click");
    expect(updates).toHaveLength(1);
    expect(updates[0].map((file) => file.id)).toEqual(["f1"]);
  });

  it("关闭面板不会清空已选（重新打开仍为选中态）", async () => {
    await open();
    await wrapper.findAll(".picker li button")[0].trigger("click");
    pointerDown(document.body);
    await flushPromises();
    expect(wrapper.find(".picker").exists()).toBe(false);

    await wrapper.find(".attachment").trigger("click");
    await flushPromises();
    expect(wrapper.find(".picker li button").classes()).toContain("selected");
  });

  it("上传成功后自动进入列表并自动选中", async () => {
    await open([]);
    vi.mocked(uploadFile).mockResolvedValue(sample("up-1", "简历.pdf"));
    await upload("简历.pdf");

    // 立即出现在面板列表中
    expect(wrapper.text()).toContain("简历.pdf");
    // 自动成为当前消息附件
    expect(updates).toHaveLength(1);
    expect(updates[0].map((file) => file.id)).toEqual(["up-1"]);
  });

  it("上传失败的文件不会进入已选", async () => {
    await open([]);
    vi.mocked(uploadFile).mockRejectedValue(new Error("boom"));
    await upload("bad.pdf");

    expect(wrapper.text()).toContain("上传失败");
    expect(updates).toHaveLength(0);
  });

  it("已选达到 10 个后不再追加", async () => {
    const picked = Array.from({ length: 10 }, (_, index) =>
      sample(`s${index}`, `s${index}.pdf`)
    );
    await open([...picked, sample("f1", "a.pdf")], picked);
    // 列表中第 11 个（a.pdf）点击后不应被追加
    const buttons = wrapper.findAll(".picker li button");
    await buttons[buttons.length - 1].trigger("click");
    await flushPromises();
    expect(updates).toHaveLength(0);
    expect(wrapper.text()).toContain("最多选择 10 个文件");
  });
});
