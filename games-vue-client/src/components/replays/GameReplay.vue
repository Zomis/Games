<template>
    <div class="game-replay">
        <GameHead :gameInfo="gameInfo"></GameHead>
        <div>
            Started at {{ timeStarted }} - Ended at {{ timeLastAction }}
        </div>
        <v-slider
            v-model="replayStep"
            :max="maxReplayStep"
            :min="0"
          />
        <component :is="gameComponent" :gameInfo="gameInfo" :view="currentView" />
        <GameResult :gameInfo="gameInfo"></GameResult>
    </div>
</template>
<script>
import supportedGames from "@/supportedGames"
import axios from "axios";
import GameHead from "@/components/games/common/GameHead";
import GameResult from "@/components/games/common/GameResult";

function unixtimeToString(date) {
    return new Date(date * 1000).toISOString().replace("T"," ").replace(/\.\d+Z/g,"")
}

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
        GameHead,
        GameResult,
        ...supportedGames.components() // TODO: This might not be needed
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
        timeStarted() {
            return unixtimeToString(this.replay.timeStarted)
        },
        timeLastAction() {
            return unixtimeToString(this.replay.timeLastAction)
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