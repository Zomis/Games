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
        axios.get(`http://localhost:42638/games/${this.gameUUID}/replay`).then(response => {
            console.log(response)
            this.replay = response.data
        })
    },
    computed: {
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
            let currentView2 = this.replay.views[this.replayStep]
            console.log(this.maxReplayStep, this.replayStep, currentView2)
            return currentView2
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