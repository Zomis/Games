<template>
  <div>
    <v-card
      v-show="showRight"
      class="floatie"
    >
      <v-card-text>
        <v-text-field
          v-model="aiName"
          label="AI Name"
        />
        <v-text-field
          v-model="playerIndex"
          label="Player Index"
        />

        <div>
          <p>Heuristic: {{ heuristic }}</p>
        </div>
      </v-card-text>

      <v-list class="transparent">
        <v-list-item
          v-for="(action, index) in actionsEvaluated"
          :key="index"
        >
          <v-list-item-title>{{ action.parameter }}</v-list-item-title>

          <v-list-item-subtitle class="text-right">
            {{ action.score }}
          </v-list-item-subtitle>
        </v-list-item>
      </v-list>
      <v-card-actions>
        <v-btn @click="updateActions()" />
      </v-card-actions>
    </v-card>
    <v-btn
      fixed
      small
      bottom
      right
      color="pink"
      fab
      dark
      @click="showRight = !showRight"
    />
  </div>
</template>
<script>
import axios from "axios";

export default {
  name: "AiQuery",
  props: ["gameInfo", "gamePosition"],
  data() {
    return {
      showRight: false,
      actionsEvaluated: [],
      heuristic: undefined,
      aiName: "",
      playerIndex: 0
    }
  },
  methods: {
    updateActions() {
      let cleanURI = encodeURI(`${this.baseURL}games/${this.gameInfo.gameId}/analyze/${this.aiName}/${this.gamePosition}/${this.playerIndex}`).replace(/#/g, '%23')
      axios.get(cleanURI).then(response => {
        console.log(response)
        this.actionsEvaluated = response.data.scores
        this.heuristic = response.data.heuristic
      })
    },
  },
  mounted() {

  },
  computed: {
    playerCount() {
      return this.gameInfo.players.length;
    },
    baseURL() {
      return process.env.VUE_APP_URL
    }
  }
}
</script>
<style scoped>
.floatie {
    position: fixed;
    right: 15px;
    bottom: 40px;
    width: 400px;
    height: 500px;
    overflow-x: hidden;
    overflow-y: scroll;
}
</style>