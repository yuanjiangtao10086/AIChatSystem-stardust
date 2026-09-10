<template>
  <section>
    <div class="admin-toolbar">
      <input
        v-model="search"
        placeholder="按邮箱或姓名搜索"
        @keyup.enter="load"
      />
      <select v-model="status" @change="load">
        <option value="">全部状态</option>
        <option v-for="s in statuses" :key="s">{{ s }}</option>
      </select>
      <button @click="load">搜索</button
      ><button class="primary" @click="create">+ 新建用户</button>
    </div>
    <div class="admin-table-wrap">
      <table class="admin-table">
        <thead>
          <tr>
            <th>用户</th>
            <th>角色</th>
            <th>状态</th>
            <th>存储</th>
            <th>AI 用量</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="user in users" :key="user.id">
            <td>
              <strong>{{ user.displayName }}</strong
              ><small>{{ user.email }}<br />{{ shortId(user.id) }}</small>
            </td>
            <td>
              <span v-for="role in user.roles" :key="role" class="tag">{{
                role
              }}</span>
            </td>
            <td>
              <span class="status" :class="user.status.toLowerCase()">{{
                user.status
              }}</span>
            </td>
            <td>
              {{ formatBytes(user.usedBytes)
              }}<small>共 {{ formatBytes(user.quotaBytes) }}</small>
            </td>
            <td>
              <template v-if="user.usage">
                {{ formatNumber(user.usage.usedTokens) }} /
                {{ formatNumber(user.usage.quotaTokens)
                }}<small
                  >{{ user.aiRequests }} 次请求 ·
                  {{ Number(user.usage.usedCost).toFixed(4) }}
                  {{ user.usage.currency }}</small
                >
              </template>
              <template v-else>
                {{ formatNumber(user.aiTokens)
                }}<small>{{ user.aiRequests }} 次请求</small>
              </template>
            </td>
            <td class="actions">
              <button @click="edit(user)">编辑</button
              ><button @click="roles(user)">角色</button
              ><button @click="adjustUsage(user)">额度</button
              ><button @click="cycleStatus(user)">
                {{ user.status === "NORMAL" ? "封禁" : "恢复" }}</button
              ><button @click="resetPassword(user)">重置密码</button
              ><button class="danger" @click="remove(user)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div class="pager">
      <button
        :disabled="page === 0"
        @click="
          page--;
          load();
        "
      >
        上一页</button
      ><span>第 {{ page + 1 }} / {{ totalPages || 1 }} 页</span
      ><button
        :disabled="page + 1 >= totalPages"
        @click="
          page++;
          load();
        "
      >
        下一页
      </button>
    </div>
    <p v-if="error" class="admin-error">{{ error }}</p>
  </section>
</template>
<script lang="ts">
import { defineComponent, onMounted, ref } from "vue";
import {
  adjustAdminUsage,
  createAdminUser,
  deleteAdminUser,
  listAdminUsers,
  resetAdminPassword,
  setAdminUserRoles,
  setAdminUserStatus,
  updateAdminUser,
} from "@/api/admin";
import { AdminUser } from "@/types/admin";
import { UserRole, UserStatus } from "@/types/auth";
import { confirmDanger, formatBytes, formatNumber } from "@/utils/admin";
export default defineComponent({
  name: "AdminUsers",
  setup() {
    const users = ref<AdminUser[]>([]),
      search = ref(""),
      status = ref(""),
      page = ref(0),
      totalPages = ref(0),
      error = ref("");
    const statuses: UserStatus[] = ["NORMAL", "BANNED", "DISABLED", "DELETED"];
    const load = async () => {
      try {
        const data = await listAdminUsers(
          page.value,
          search.value,
          status.value
        );
        users.value = data.items;
        totalPages.value = data.totalPages;
        error.value = "";
      } catch (e) {
        error.value = e instanceof Error ? e.message : "用户列表加载失败";
      }
    };
    const create = async () => {
      const email = prompt("邮箱");
      const displayName = prompt("昵称");
      const password = prompt("临时密码（至少 12 位）");
      if (!email || !displayName || !password) return;
      try {
        await createAdminUser({
          email,
          displayName,
          password,
          roles: ["USER"],
        });
        await load();
      } catch (e) {
        error.value = e instanceof Error ? e.message : "创建用户失败";
      }
    };
    const edit = async (user: AdminUser) => {
      const email = prompt("邮箱", user.email),
        displayName = prompt("昵称", user.displayName);
      if (!email || !displayName) return;
      await updateAdminUser(user.id, { email, displayName });
      await load();
    };
    const roles = async (user: AdminUser) => {
      const value = prompt("角色（英文逗号分隔）", user.roles.join(","));
      if (!value) return;
      const next = value
        .split(",")
        .map((v) => v.trim().toUpperCase()) as UserRole[];
      if (
        !confirmDanger(
          `确认将 ${user.email} 的角色修改为 ${next.join(", ")} 吗？`
        )
      )
        return;
      await setAdminUserRoles(user.id, next);
      await load();
    };
    const cycleStatus = async (user: AdminUser) => {
      const next: UserStatus = user.status === "NORMAL" ? "BANNED" : "NORMAL";
      const reason =
        next === "BANNED" ? prompt("封禁原因") || undefined : undefined;
      if (
        !confirmDanger(
          `${next === "BANNED" ? "封禁" : "恢复"}用户 ${user.email}`
        )
      )
        return;
      await setAdminUserStatus(user.id, next, reason);
      await load();
    };
    const adjustUsage = async (user: AdminUser) => {
      const delta = prompt("Token 调整量（正数追加消耗，负数返还）", "0");
      if (delta === null) return;
      const tokenDelta = Number(delta);
      if (!Number.isFinite(tokenDelta) || tokenDelta === 0) return;
      const reason = prompt("调整原因（将写入审计日志）");
      if (!reason) return;
      if (!confirmDanger(`确认调整 ${user.email} 的额度 ${tokenDelta} tokens`))
        return;
      try {
        await adjustAdminUsage(user.id, {
          tokenDelta,
          costDelta: "0",
          reason,
        });
        await load();
      } catch (e) {
        error.value = e instanceof Error ? e.message : "额度调整失败";
      }
    };
    const resetPassword = async (user: AdminUser) => {
      const password = prompt("新密码（至少 12 位）");
      if (!password || !confirmDanger(`确认重置用户 ${user.email} 的密码`))
        return;
      await resetAdminPassword(user.id, password);
    };
    const remove = async (user: AdminUser) => {
      if (!confirmDanger(`确认删除用户 ${user.email} 吗？`)) return;
      await deleteAdminUser(user.id);
      await load();
    };
    onMounted(load);
    return {
      users,
      search,
      status,
      page,
      totalPages,
      error,
      statuses,
      load,
      create,
      edit,
      roles,
      adjustUsage,
      cycleStatus,
      resetPassword,
      remove,
      formatBytes,
      formatNumber,
      shortId: (id: string) => id.slice(0, 8),
    };
  },
});
</script>
