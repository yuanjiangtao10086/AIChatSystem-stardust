import { createApp } from "vue";
import App from "./App.vue";
import router from "./router";
import store from "./store";
import { initTheme } from "./composables/useTheme";
import "./styles.scss";

// Apply the stored theme before mounting so a dark-mode user never sees a white flash.
initTheme();

createApp(App).use(store).use(router).mount("#app");
