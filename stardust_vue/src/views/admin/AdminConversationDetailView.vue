<template>
  <div v-if="loading" class="admin-loading">加载中…</div>
  <div v-else-if="!detail" class="admin-empty">
    {{ notFound ? "未找到该会话，或已被删除。" : error }}
  </div>
  <div v-else class="admin-conv-detail">
    <p v-if="error" class="admin-error">{{ error }}</p>
    <button class="admin-back-link" type="button" @click="back">
      ← 返回会话列表
    </button>

    <header class="admin-detail-head">
      <div>
        <h2>{{ detail.title }}</h2>
        <p class="admin-detail-sub">
          {{ detail.userName }} · {{ detail.userEmail }}
        </p>
      </div>
      <span class="conv-status" :class="'conv-' + detail.status.toLowerCase()">
        {{ CONV_STATUS_LABELS[detail.status] || detail.status }}
      </span>
    </header>

    <section class="admin-card">
      <dl class="admin-meta">
        <div>
          <dt>用户</dt>
          <dd>
            {{ detail.userName }} <small>{{ detail.userEmail }}</small>
          </dd>
        </div>
        <div>
          <dt>用户 ID</dt>
          <dd class="mono">{{ detail.userId }}</dd>
        </div>
        <div>
          <dt>消息数</dt>
          <dd>{{ detail.messageCount }}</dd>
        </div>
        <div>
          <dt>创建时间</dt>
          <dd>{{ formatDateTime(detail.createdAt) }}</dd>
        </div>
        <div>
          <dt>更新时间</dt>
          <dd>{{ formatDateTime(detail.updatedAt) }}</dd>
        </div>
      </dl>
      <div class="admin-actions">
        <button
          class="row-btn row-danger"
          type="button"
          @click="askDeleteConversation"
        >
          删除整个会话
        </button>
      </div>
    </section>

    <section class="admin-card">
      <h3>消息（共 {{ detail.messageCount }} 条）</h3>
      <div class="msg-list">
        <article
          v-for="m in msgs"
          :key="m.id"
          class="msg"
          :class="'msg--' + m.role.toLowerCase()"
        >
          <div class="msg-head">
            <span class="msg-role">{{ ROLE_LABELS[m.role] || m.role }}</span>
            <span class="msg-seq">#{{ m.sequenceNo }}</span>
            <span class="msg-status">{{
              STATUS_LABELS[m.status] || m.status
            }}</span>
            <span v-if="m.totalTokens" class="msg-tokens"
              >{{ m.totalTokens }} tokens</span
            >
            <span class="msg-time">{{ formatDateTime(m.createdAt) }}</span>
            <button
              class="row-btn row-danger msg-del"
              type="button"
              @click="askDeleteMessage(m)"
            >
              删除
            </button>
          </div>
          <div class="msg-body">
            <ChatMarkdown :content="m.content" />
          </div>
        </article>
      </div>
      <button
        v-if="hasMore"
        class="primary-button"
        type="button"
        @click="loadMore"
      >
        加载更多消息
      </button>
    </section>

    <AdminConfirmModal
      v-model="confirm.open"
      :title="confirm.title"
      :message="confirm.message"
      :busy="confirmBusy"
      :error="confirmError"
      @confirm="runConfirm"
    />
  </div>
</template>
<script lang="ts">
import { computed, defineComponent, onMounted, PropType, ref } from "vue";
import { useRouter } from "vue-router";
import ChatMarkdown from "@/components/chat/ChatMarkdown.vue";
import AdminConfirmModal from "@/components/admin/AdminConfirmModal.vue";
import {
  deleteAdminConversation,
  deleteAdminMessage,
  getAdminConversation,
  listAdminMessages,
} from "@/api/admin";
import { AdminConversationDetail, AdminMessage } from "@/types/admin";
import { describeError, formatDateTime, isNotFoundError } from "@/utils/admin";

const CONV_STATUS_LABELS: Record<string, string> = {
  ACTIVE: "进行中",
  ARCHIVED: "已归档",
};
const ROLE_LABELS: Record<string, string> = {
  USER: "用户",
  ASSISTANT: "星语 AI",
  SYSTEM: "系统",
  TOOL: "工具",
};
const STATUS_LABELS: Record<string, string> = {
  PENDING: "等待中",
  STREAMING: "生成中",
  COMPLETED: "已完成",
  STOPPED: "已停止",
  FAILED: "失败",
};

interface ConfirmState {
  open: boolean;
  title: string;
  message: string;
  kind: "conversation" | "message";
  messageId?: string;
}

export default defineComponent({
  name: "AdminConversationDetailView",
  components: { ChatMarkdown, AdminConfirmModal },
  props: { id: { type: String, required: true } },
  setup(props) {
    const router = useRouter();
    const detail = ref<AdminConversationDetail | null>(null);
    const msgs = ref<AdminMessage[]>([]);
    const nextMsgPage = ref(1);
    const loading = ref(false);
    const error = ref("");
    const notFound = ref(false);

    const hasMore = computed(
      () => !!detail.value && detail.value.messages.hasNext
    );

    const load = async () => {
      loading.value = true;
      error.value = "";
      notFound.value = false;
      try {
        const d = await getAdminConversation(props.id);
        detail.value = d;
        msgs.value = d.messages.items;
        nextMsgPage.value = 1;
      } catch (e) {
        detail.value = null;
        notFound.value = isNotFoundError(e);
        error.value = describeError(e, "加载会话详情失败");
      } finally {
        loading.value = false;
      }
    };

    const loadMore = async () => {
      try {
        const page = await listAdminMessages(props.id, nextMsgPage.value);
        msgs.value.push(...page.items);
        nextMsgPage.value += 1;
      } catch (e) {
        error.value = e instanceof Error ? e.message : "加载更多消息失败";
      }
    };

    const back = () => router.push({ name: "admin-conversations" });

    const confirm = ref<ConfirmState>({
      open: false,
      title: "",
      message: "",
      kind: "conversation",
    });
    const confirmBusy = ref(false);
    const confirmError = ref("");
    const askDeleteConversation = () => {
      confirm.value = {
        open: true,
        title: "删除整个会话",
        message: `确认软删除会话「${detail.value?.title}」？该操作会写入审计日志。`,
        kind: "conversation",
      };
      confirmError.value = "";
    };
    const askDeleteMessage = (m: AdminMessage) => {
      confirm.value = {
        open: true,
        title: "删除消息",
        message: `确认删除第 #${m.sequenceNo} 条消息（${
          ROLE_LABELS[m.role] || m.role
        }）？该操作会写入审计日志。`,
        kind: "message",
        messageId: m.id,
      };
      confirmError.value = "";
    };
    const runConfirm = async () => {
      confirmBusy.value = true;
      confirmError.value = "";
      try {
        if (confirm.value.kind === "conversation") {
          await deleteAdminConversation(props.id);
          confirm.value.open = false;
          router.push({ name: "admin-conversations" });
        } else {
          const mid = confirm.value.messageId;
          if (!mid) return;
          await deleteAdminMessage(mid);
          msgs.value = msgs.value.filter((m) => m.id !== mid);
          if (detail.value && detail.value.messageCount > 0)
            detail.value.messageCount -= 1;
          confirm.value.open = false;
        }
      } catch (e) {
        confirmError.value = e instanceof Error ? e.message : "操作失败";
      } finally {
        confirmBusy.value = false;
      }
    };

    onMounted(load);

    return {
      detail,
      msgs,
      hasMore,
      loading,
      error,
      notFound,
      loadMore,
      back,
      confirm,
      confirmBusy,
      confirmError,
      askDeleteConversation,
      askDeleteMessage,
      runConfirm,
      CONV_STATUS_LABELS,
      ROLE_LABELS,
      STATUS_LABELS,
      formatDateTime,
    };
  },
});
</script>
