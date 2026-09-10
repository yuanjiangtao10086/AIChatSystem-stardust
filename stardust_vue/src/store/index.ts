import { createStore } from "vuex";
import { setRefreshHandler } from "@/api/client";
import { authModule, RootState } from "./auth";

const store = createStore<RootState>({
  modules: {
    auth: authModule,
  },
});

setRefreshHandler(() => store.dispatch("auth/refresh"));

export default store;
