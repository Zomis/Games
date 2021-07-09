<template>
  <div>
    <StartScreen v-if="!displayCompactList" />
    <LobbyCompactList v-if="displayCompactList" />
  </div>
</template>

<script>
import StartScreen from "@/components/StartScreen";
import LobbyCompactList from "@/components/lobby/LobbyCompactList";

import { mapState } from "vuex";

export default {
  name: "StartSelector",
  components: {
    StartScreen,
    LobbyCompactList
  },
  computed: {
    ...mapState('settings', {
      displayCompactList(state) { return state.compactList }
    }),
  },
};
</script>
