<template>
    <div class="game-replay">
        <v-slider
            v-model="replayStep"
            :max="maxReplayStep"
            :min="0"
          />
        <component :is="gameComponent" :gameInfo="gameInfo" :showRules="false" :fixedView="currentView" />
    </div>
</template>
<script>
import supportedGames from "@/supportedGames"
import axios from "axios";

export default {
    name: "GameReplay",
    props: ["gameUUID"],
    data() {
        return {
            replayStep: 0,
            replay: null
        }
    },
    components: {
        ...supportedGames.components()
    },
    created() {
        axios.get(`${this.baseURL}games/${this.gameUUID}/replay`).then(response => {
            console.log(response)
            this.replay = response.data
        })
    },
    computed: {
        baseURL() {
            return process.env.VUE_APP_URL
        },
        maxReplayStep() {
            if (this.replay == null) { return 0 }
            return this.replay.views.length - 1
        },
        gameComponent() {
            if (this.replay == null) { return null }
            return supportedGames.games[this.replay.gameType].component
        },
        playerNames() {
            if (this.replay == null) { return [] }
            return this.replay.playersInGame.map(pig => pig.player.name)
        },
        currentView() {
            if (this.replay == null) { return null }
            return this.replay.views[this.replayStep]
        },
        gameInfo() {
            if (this.replay == null) { return null }
            return {
                gameType: this.replay.gameType,
                players: this.playerNames,
                gameId: this.gameUUID,
                yourIndex: -1
            }
        }
    }
}
</script>