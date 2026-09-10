<template>
  <AdminShell :name="name" :role="role">
    <router-view />
  </AdminShell>
</template>
<script lang="ts">
import { computed, defineComponent } from "vue";
import { useStore } from "vuex";
import AdminShell from "@/components/admin/AdminShell.vue";

export default defineComponent({
  name: "AdminLayoutView",
  components: { AdminShell },
  setup() {
    const store = useStore();
    const user = computed(() => store.getters["auth/user"]);
    return {
      name: computed(() => user.value?.displayName || "管理员"),
      role: computed(() => user.value?.roles?.[0] || "ADMIN"),
    };
  },
});
</script>
